package com.example.data

import android.util.Log
import com.example.model.ChatMessage
import com.example.model.ConversationItem
import com.example.model.UserProfile
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow

class ChatRepository {
    private val TAG = "ChatRepository"

    // In-memory fallback caches for offline/demo resilience
    private val localConversations = MutableStateFlow<Map<String, List<ConversationItem>>>(emptyMap())
    private val localMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val localTyping = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * Observe the list of conversations for a user in real-time.
     */
    fun observeConversations(uid: String): Flow<List<ConversationItem>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.userConversationsRef?.child(uid)
        if (ref == null) {
            trySend(localConversations.value[uid] ?: emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ConversationItem>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val map = child.value as? Map<String, Any?> ?: continue
                    list.add(ConversationItem.fromMap(id, map))
                }
                list.sortByDescending { it.lastMessageTimestamp }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeConversations cancelled: ${error.message}")
                trySend(localConversations.value[uid] ?: emptyList())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            try {
                ref.removeEventListener(listener)
            } catch (e: Exception) {}
        }
    }

    /**
     * Get or create a 1-on-1 direct conversation between two users.
     */
    suspend fun getOrCreateDirectChat(
        currentProfile: UserProfile,
        targetProfile: UserProfile
    ): Result<String> {
        val uid1 = currentProfile.uid
        val uid2 = targetProfile.uid
        if (uid1.isEmpty() || uid2.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invalid user IDs."))
        }

        val conversationId = if (uid1 < uid2) "chat_${uid1}_${uid2}" else "chat_${uid2}_${uid1}"

        val user1Item = ConversationItem(
            id = conversationId,
            otherUid = targetProfile.uid,
            otherUsername = targetProfile.username,
            otherDisplayName = targetProfile.displayName,
            otherAvatarIndex = targetProfile.avatarColorIndex,
            lastMessage = "Started a conversation",
            lastMessageSenderId = uid1,
            lastMessageTimestamp = System.currentTimeMillis(),
            unreadCount = 0
        )

        val user2Item = ConversationItem(
            id = conversationId,
            otherUid = currentProfile.uid,
            otherUsername = currentProfile.username,
            otherDisplayName = currentProfile.displayName,
            otherAvatarIndex = currentProfile.avatarColorIndex,
            lastMessage = "Started a conversation",
            lastMessageSenderId = uid1,
            lastMessageTimestamp = System.currentTimeMillis(),
            unreadCount = 0
        )

        // Update local memory
        val curList1 = (localConversations.value[uid1] ?: emptyList()).toMutableList()
        if (curList1.none { it.id == conversationId }) {
            curList1.add(user1Item)
            localConversations.value = localConversations.value + (uid1 to curList1)
        }

        val db = FirebaseManager.database
        if (db != null) {
            try {
                val convRef = FirebaseManager.conversationsRef?.child(conversationId)
                val convSnapshot = convRef?.awaitSnapshot()
                if (convSnapshot == null || !convSnapshot.exists()) {
                    val initialConv = mapOf<String, Any>(
                        "id" to conversationId,
                        "participants" to mapOf(uid1 to true, uid2 to true),
                        "createdAt" to ServerValue.TIMESTAMP,
                        "lastActivity" to ServerValue.TIMESTAMP
                    )

                    val updates = mapOf<String, Any>(
                        "conversations/$conversationId" to initialConv,
                        "user_conversations/$uid1/$conversationId" to user1Item.toMap(),
                        "user_conversations/$uid2/$conversationId" to user2Item.toMap()
                    )

                    db.reference.updateChildren(updates).awaitResult()
                }
            } catch (e: Exception) {
                Log.w(TAG, "getOrCreateDirectChat Firebase notice: ${e.message}")
            }
        }

        return Result.success(conversationId)
    }

    /**
     * Observe messages in a conversation in real-time.
     */
    fun observeMessages(conversationId: String, limit: Int = 100): Flow<List<ChatMessage>> = callbackFlow {
        if (conversationId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = FirebaseManager.messagesRef?.child(conversationId)?.limitToLast(limit)
        if (query == null) {
            trySend(localMessages.value[conversationId] ?: emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val map = child.value as? Map<String, Any?> ?: continue
                    list.add(ChatMessage.fromMap(id, map))
                }
                list.sortBy { it.timestamp }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeMessages cancelled: ${error.message}")
                trySend(localMessages.value[conversationId] ?: emptyList())
            }
        }

        query.addValueEventListener(listener)
        awaitClose {
            try {
                query.removeEventListener(listener)
            } catch (e: Exception) {}
        }
    }

    /**
     * Send a real-time message with optional reply metadata and atomic updates.
     */
    suspend fun sendMessage(
        conversationId: String,
        currentProfile: UserProfile,
        otherUid: String,
        otherUsername: String,
        otherDisplayName: String,
        otherAvatarIndex: Int,
        text: String,
        replyTo: ChatMessage? = null
    ): Result<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }

        val messageId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"

        val message = ChatMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = currentProfile.uid,
            senderUsername = currentProfile.username,
            senderDisplayName = currentProfile.displayName,
            text = trimmed,
            timestamp = System.currentTimeMillis(),
            status = "SENT",
            replyToId = replyTo?.id,
            replyToText = replyTo?.text,
            replyToSender = replyTo?.senderDisplayName?.ifEmpty { replyTo.senderUsername },
            isEdited = false,
            isDeleted = false
        )

        // Update local memory cache immediately
        val currentMsgs = (localMessages.value[conversationId] ?: emptyList()).toMutableList()
        currentMsgs.add(message)
        localMessages.value = localMessages.value + (conversationId to currentMsgs)

        val db = FirebaseManager.database
        if (db != null) {
            try {
                val updates = mutableMapOf<String, Any>()
                updates["messages/$conversationId/$messageId"] = message.toMap()
                updates["conversations/$conversationId/lastActivity"] = ServerValue.TIMESTAMP

                // Update Current User's Conversation Preview
                updates["user_conversations/${currentProfile.uid}/$conversationId/lastMessage"] = trimmed
                updates["user_conversations/${currentProfile.uid}/$conversationId/lastMessageSenderId"] = currentProfile.uid
                updates["user_conversations/${currentProfile.uid}/$conversationId/lastMessageTimestamp"] = ServerValue.TIMESTAMP
                updates["user_conversations/${currentProfile.uid}/$conversationId/unreadCount"] = 0
                updates["user_conversations/${currentProfile.uid}/$conversationId/otherUid"] = otherUid
                updates["user_conversations/${currentProfile.uid}/$conversationId/otherUsername"] = otherUsername
                updates["user_conversations/${currentProfile.uid}/$conversationId/otherDisplayName"] = otherDisplayName
                updates["user_conversations/${currentProfile.uid}/$conversationId/otherAvatarIndex"] = otherAvatarIndex

                // Update Recipient's Conversation Preview
                updates["user_conversations/$otherUid/$conversationId/lastMessage"] = trimmed
                updates["user_conversations/$otherUid/$conversationId/lastMessageSenderId"] = currentProfile.uid
                updates["user_conversations/$otherUid/$conversationId/lastMessageTimestamp"] = ServerValue.TIMESTAMP
                updates["user_conversations/$otherUid/$conversationId/unreadCount"] = ServerValue.increment(1)
                updates["user_conversations/$otherUid/$conversationId/otherUid"] = currentProfile.uid
                updates["user_conversations/$otherUid/$conversationId/otherUsername"] = currentProfile.username
                updates["user_conversations/$otherUid/$conversationId/otherDisplayName"] = currentProfile.displayName
                updates["user_conversations/$otherUid/$conversationId/otherAvatarIndex"] = currentProfile.avatarColorIndex

                db.reference.updateChildren(updates).awaitResult()
            } catch (e: Exception) {
                Log.w(TAG, "sendMessage Firebase remote write notice: ${e.message}")
            }
        }

        return Result.success(messageId)
    }

    /**
     * Edit a sent message.
     */
    suspend fun editMessage(
        conversationId: String,
        messageId: String,
        newText: String,
        currentUid: String
    ): Result<Unit> {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }

        // Update local memory
        val msgs = (localMessages.value[conversationId] ?: emptyList()).map { msg ->
            if (msg.id == messageId && msg.senderId == currentUid) {
                msg.copy(text = trimmed, isEdited = true, editedAt = System.currentTimeMillis())
            } else msg
        }
        localMessages.value = localMessages.value + (conversationId to msgs)

        try {
            FirebaseManager.messagesRef?.child(conversationId)?.child(messageId)?.let { msgRef ->
                val updates = mapOf<String, Any>(
                    "text" to trimmed,
                    "isEdited" to true,
                    "editedAt" to ServerValue.TIMESTAMP
                )
                msgRef.updateChildren(updates).awaitResult()
            }
        } catch (e: Exception) {
            Log.w(TAG, "editMessage remote update notice: ${e.message}")
        }
        return Result.success(Unit)
    }

    /**
     * Delete a sent message (soft deletion with placeholder).
     */
    suspend fun deleteMessage(
        conversationId: String,
        messageId: String,
        currentUid: String
    ): Result<Unit> {
        // Update local memory
        val msgs = (localMessages.value[conversationId] ?: emptyList()).map { msg ->
            if (msg.id == messageId && msg.senderId == currentUid) {
                msg.copy(text = "This message was deleted", isDeleted = true, editedAt = System.currentTimeMillis())
            } else msg
        }
        localMessages.value = localMessages.value + (conversationId to msgs)

        try {
            FirebaseManager.messagesRef?.child(conversationId)?.child(messageId)?.let { msgRef ->
                val updates = mapOf<String, Any>(
                    "text" to "This message was deleted",
                    "isDeleted" to true,
                    "editedAt" to ServerValue.TIMESTAMP
                )
                msgRef.updateChildren(updates).awaitResult()
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteMessage remote update notice: ${e.message}")
        }
        return Result.success(Unit)
    }

    /**
     * Mark a conversation as read.
     */
    fun markConversationAsRead(conversationId: String, currentUid: String) {
        if (conversationId.isEmpty() || currentUid.isEmpty()) return
        try {
            FirebaseManager.userConversationsRef
                ?.child(currentUid)
                ?.child(conversationId)
                ?.child("unreadCount")
                ?.setValue(0)
        } catch (e: Exception) {
            Log.w(TAG, "markConversationAsRead error: ${e.message}")
        }
    }

    /**
     * Set typing indicator for current user.
     */
    fun setTyping(conversationId: String, currentUid: String, isTyping: Boolean) {
        if (conversationId.isEmpty() || currentUid.isEmpty()) return
        try {
            val ref = FirebaseManager.typingRef?.child(conversationId)?.child(currentUid)
            if (ref != null) {
                if (isTyping) {
                    ref.onDisconnect().removeValue()
                    ref.setValue(
                        mapOf(
                            "isTyping" to true,
                            "timestamp" to ServerValue.TIMESTAMP
                        )
                    )
                } else {
                    ref.removeValue()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "setTyping error: ${e.message}")
        }
    }

    /**
     * Observe typing status for the other participant in a conversation.
     */
    fun observeTyping(conversationId: String, currentUid: String): Flow<Boolean> = callbackFlow {
        if (conversationId.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.typingRef?.child(conversationId)
        if (ref == null) {
            trySend(false)
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var otherIsTyping = false
                val now = System.currentTimeMillis()
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    if (uid != currentUid) {
                        val isTyping = child.child("isTyping").getValue(Boolean::class.java) ?: false
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        if (isTyping && (now - timestamp < 10000L)) {
                            otherIsTyping = true
                            break
                        }
                    }
                }
                trySend(otherIsTyping)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(false)
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            try {
                ref.removeEventListener(listener)
            } catch (e: Exception) {}
        }
    }

    /**
     * Observe real-time presence of a specific user.
     */
    fun observePresence(uid: String): Flow<Pair<Boolean, Long>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(Pair(false, 0L))
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.presenceRef?.child(uid)
        if (ref == null) {
            trySend(Pair(true, System.currentTimeMillis()))
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                trySend(Pair(isOnline, lastSeen))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Pair(false, 0L))
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            try {
                ref.removeEventListener(listener)
            } catch (e: Exception) {}
        }
    }
}
