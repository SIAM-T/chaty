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
import kotlinx.coroutines.flow.callbackFlow

class ChatRepository {
    private val TAG = "ChatRepository"

    /**
     * Observe the list of conversations for a user in real-time.
     */
    fun observeConversations(uid: String): Flow<List<ConversationItem>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.userConversationsRef.child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ConversationItem>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val map = child.value as? Map<String, Any?> ?: continue
                    list.add(ConversationItem.fromMap(id, map))
                }
                // Sort descending by last message timestamp
                list.sortByDescending { it.lastMessageTimestamp }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeConversations error: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    /**
     * Get or create a 1-on-1 direct conversation between two users.
     * Generates a deterministic ID to guarantee only 1 conversation exists between them.
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

        return try {
            val convSnapshot = FirebaseManager.conversationsRef.child(conversationId).awaitSnapshot()
            if (!convSnapshot.exists()) {
                val initialConv = mapOf<String, Any>(
                    "id" to conversationId,
                    "participants" to mapOf(uid1 to true, uid2 to true),
                    "createdAt" to ServerValue.TIMESTAMP,
                    "lastActivity" to ServerValue.TIMESTAMP
                )

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

                val updates = mapOf<String, Any>(
                    "conversations/$conversationId" to initialConv,
                    "user_conversations/$uid1/$conversationId" to user1Item.toMap(),
                    "user_conversations/$uid2/$conversationId" to user2Item.toMap()
                )

                FirebaseManager.database.reference.updateChildren(updates).awaitResult()
            }

            Result.success(conversationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

        val query = FirebaseManager.messagesRef.child(conversationId).limitToLast(limit)
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
                Log.e(TAG, "observeMessages error: ${error.message}")
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose {
            query.removeEventListener(listener)
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

        val msgRef = FirebaseManager.messagesRef.child(conversationId).push()
        val messageId = msgRef.key ?: return Result.failure(IllegalStateException("Could not generate message key."))

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

        return try {
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

            FirebaseManager.database.reference.updateChildren(updates).awaitResult()
            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

        return try {
            val msgSnapshot = FirebaseManager.messagesRef.child(conversationId).child(messageId).awaitSnapshot()
            val senderId = msgSnapshot.child("senderId").getValue(String::class.java)
            if (senderId != currentUid) {
                return Result.failure(IllegalAccessException("You can only edit your own messages."))
            }

            val updates = mapOf<String, Any>(
                "text" to trimmed,
                "isEdited" to true,
                "editedAt" to ServerValue.TIMESTAMP
            )

            FirebaseManager.messagesRef.child(conversationId).child(messageId).updateChildren(updates).awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a sent message (soft deletion with placeholder).
     */
    suspend fun deleteMessage(
        conversationId: String,
        messageId: String,
        currentUid: String
    ): Result<Unit> {
        return try {
            val msgSnapshot = FirebaseManager.messagesRef.child(conversationId).child(messageId).awaitSnapshot()
            val senderId = msgSnapshot.child("senderId").getValue(String::class.java)
            if (senderId != currentUid) {
                return Result.failure(IllegalAccessException("You can only delete your own messages."))
            }

            val updates = mapOf<String, Any>(
                "text" to "This message was deleted",
                "isDeleted" to true,
                "editedAt" to ServerValue.TIMESTAMP
            )

            FirebaseManager.messagesRef.child(conversationId).child(messageId).updateChildren(updates).awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a conversation as read (resets unread counter).
     */
    fun markConversationAsRead(conversationId: String, currentUid: String) {
        if (conversationId.isEmpty() || currentUid.isEmpty()) return
        FirebaseManager.userConversationsRef
            .child(currentUid)
            .child(conversationId)
            .child("unreadCount")
            .setValue(0)
    }

    /**
     * Set typing indicator for current user.
     */
    fun setTyping(conversationId: String, currentUid: String, isTyping: Boolean) {
        if (conversationId.isEmpty() || currentUid.isEmpty()) return
        val ref = FirebaseManager.typingRef.child(conversationId).child(currentUid)
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

    /**
     * Observe typing status for the other participant in a conversation.
     */
    fun observeTyping(conversationId: String, currentUid: String): Flow<Boolean> = callbackFlow {
        if (conversationId.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.typingRef.child(conversationId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var otherIsTyping = false
                val now = System.currentTimeMillis()
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    if (uid != currentUid) {
                        val isTyping = child.child("isTyping").getValue(Boolean::class.java) ?: false
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        // Typing status valid within 10 seconds
                        if (isTyping && (now - timestamp < 10000L)) {
                            otherIsTyping = true
                            break
                        }
                    }
                }
                trySend(otherIsTyping)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            ref.removeEventListener(listener)
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

        val ref = FirebaseManager.presenceRef.child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                trySend(Pair(isOnline, lastSeen))
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            ref.removeEventListener(listener)
        }
    }
}
