package com.example.data

import android.util.Log
import com.example.model.ChatMessage
import com.example.model.ConversationItem
import com.example.model.LiveDraftState
import com.example.model.UserProfile
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ChatRepository private constructor() {
    private val TAG = "ChatRepository"
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val instance: ChatRepository by lazy { ChatRepository() }
    }

    // In-memory fallback caches for instant UI response and offline resilience
    private val localConversations = MutableStateFlow<Map<String, List<ConversationItem>>>(emptyMap())
    private val localMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val localLiveDraft = MutableStateFlow<Map<String, LiveDraftState>>(emptyMap())
    private val localReactions = MutableStateFlow<Map<String, Map<String, Map<String, String>>>>(emptyMap()) // convId -> (msgId -> (uid -> emoji))
    private val localPinned = MutableStateFlow<Map<String, Pair<String?, String?>>>(emptyMap())

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
                localConversations.value = localConversations.value + (uid to list)
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeConversations notice: ${error.message}")
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

        // Update local memory immediately
        val curList1 = (localConversations.value[uid1] ?: emptyList()).toMutableList()
        if (curList1.none { it.id == conversationId }) {
            curList1.add(0, user1Item)
            localConversations.value = localConversations.value + (uid1 to curList1)
        }

        // Asynchronously sync with Firebase
        scope.launch {
            try {
                val db = FirebaseManager.database
                if (db != null) {
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

                    db.reference.updateChildren(updates)
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
    fun observeMessages(conversationId: String, limit: Int = 120): Flow<List<ChatMessage>> = callbackFlow {
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
                localMessages.value = localMessages.value + (conversationId to list)
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeMessages notice: ${error.message}")
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

        // Clear live draft text upon sending
        clearLiveDraft(conversationId, currentProfile.uid)

        // Update local memory cache immediately (Zero Latency)
        val currentMsgs = (localMessages.value[conversationId] ?: emptyList()).toMutableList()
        currentMsgs.add(message)
        localMessages.value = localMessages.value + (conversationId to currentMsgs)

        // Update conversation previews locally
        updateLocalConversationPreview(
            uid = currentProfile.uid,
            conversationId = conversationId,
            lastMessage = trimmed,
            otherUid = otherUid,
            otherUsername = otherUsername,
            otherDisplayName = otherDisplayName,
            otherAvatarIndex = otherAvatarIndex
        )

        // Asynchronously update Firebase Realtime Database
        scope.launch {
            try {
                val db = FirebaseManager.database
                if (db != null) {
                    val updates = mutableMapOf<String, Any>()
                    updates["messages/$conversationId/$messageId"] = message.toMap()
                    updates["conversations/$conversationId/lastActivity"] = ServerValue.TIMESTAMP

                    // Update Sender's Conversation Preview
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

                    db.reference.updateChildren(updates)
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendMessage Firebase notice: ${e.message}")
            }
        }

        return Result.success(messageId)
    }

    private fun updateLocalConversationPreview(
        uid: String,
        conversationId: String,
        lastMessage: String,
        otherUid: String,
        otherUsername: String,
        otherDisplayName: String,
        otherAvatarIndex: Int
    ) {
        val list = (localConversations.value[uid] ?: emptyList()).toMutableList()
        val index = list.indexOfFirst { it.id == conversationId }
        val updated = ConversationItem(
            id = conversationId,
            otherUid = otherUid,
            otherUsername = otherUsername,
            otherDisplayName = otherDisplayName,
            otherAvatarIndex = otherAvatarIndex,
            lastMessage = lastMessage,
            lastMessageSenderId = uid,
            lastMessageTimestamp = System.currentTimeMillis(),
            unreadCount = 0
        )
        if (index >= 0) {
            list[index] = updated
        } else {
            list.add(0, updated)
        }
        list.sortByDescending { it.lastMessageTimestamp }
        localConversations.value = localConversations.value + (uid to list)
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

        // Update local memory immediately
        val msgs = (localMessages.value[conversationId] ?: emptyList()).map { msg ->
            if (msg.id == messageId && msg.senderId == currentUid) {
                msg.copy(text = trimmed, isEdited = true, editedAt = System.currentTimeMillis())
            } else msg
        }
        localMessages.value = localMessages.value + (conversationId to msgs)

        scope.launch {
            try {
                FirebaseManager.messagesRef?.child(conversationId)?.child(messageId)?.let { msgRef ->
                    val updates = mapOf<String, Any>(
                        "text" to trimmed,
                        "isEdited" to true,
                        "editedAt" to ServerValue.TIMESTAMP
                    )
                    msgRef.updateChildren(updates)
                }
            } catch (e: Exception) {
                Log.w(TAG, "editMessage remote update notice: ${e.message}")
            }
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
        val msgs = (localMessages.value[conversationId] ?: emptyList()).map { msg ->
            if (msg.id == messageId && msg.senderId == currentUid) {
                msg.copy(text = "This message was deleted", isDeleted = true, editedAt = System.currentTimeMillis())
            } else msg
        }
        localMessages.value = localMessages.value + (conversationId to msgs)

        scope.launch {
            try {
                FirebaseManager.messagesRef?.child(conversationId)?.child(messageId)?.let { msgRef ->
                    val updates = mapOf<String, Any>(
                        "text" to "This message was deleted",
                        "isDeleted" to true,
                        "editedAt" to ServerValue.TIMESTAMP
                    )
                    msgRef.updateChildren(updates)
                }
            } catch (e: Exception) {
                Log.w(TAG, "deleteMessage remote update notice: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    /**
     * Set Real-Time Keystroke Draft ("Telepathic Live Stream").
     * Broadcasts what the user is typing character-by-character in real time.
     */
    fun setLiveDraft(conversationId: String, currentProfile: UserProfile, draftText: String) {
        if (conversationId.isEmpty() || currentProfile.uid.isEmpty()) return

        val state = LiveDraftState(
            uid = currentProfile.uid,
            username = currentProfile.username,
            displayName = currentProfile.displayName,
            isTyping = draftText.isNotEmpty(),
            draftText = draftText,
            timestamp = System.currentTimeMillis()
        )

        // Local cache update
        localLiveDraft.value = localLiveDraft.value + (conversationId to state)

        scope.launch {
            try {
                val ref = FirebaseManager.typingRef?.child(conversationId)?.child(currentProfile.uid)
                if (ref != null) {
                    if (draftText.isNotEmpty()) {
                        ref.onDisconnect().removeValue()
                        ref.setValue(
                            mapOf(
                                "isTyping" to true,
                                "draftText" to draftText,
                                "displayName" to currentProfile.displayName,
                                "username" to currentProfile.username,
                                "timestamp" to ServerValue.TIMESTAMP
                            )
                        )
                    } else {
                        ref.removeValue()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "setLiveDraft notice: ${e.message}")
            }
        }
    }

    /**
     * Clear live draft on send or erase.
     */
    fun clearLiveDraft(conversationId: String, uid: String) {
        if (conversationId.isEmpty() || uid.isEmpty()) return
        localLiveDraft.value = localLiveDraft.value - conversationId
        scope.launch {
            try {
                FirebaseManager.typingRef?.child(conversationId)?.child(uid)?.removeValue()
            } catch (e: Exception) {}
        }
    }

    /**
     * Observe the other user's real-time live typing draft.
     */
    fun observeLiveDraft(conversationId: String, currentUid: String): Flow<LiveDraftState?> = callbackFlow {
        if (conversationId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.typingRef?.child(conversationId)
        if (ref == null) {
            val local = localLiveDraft.value[conversationId]
            trySend(if (local?.uid != currentUid) local else null)
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var otherDraft: LiveDraftState? = null
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    if (uid == currentUid) continue // Ignore own typing

                    val isTyping = child.child("isTyping").getValue(Boolean::class.java) ?: false
                    val text = child.child("draftText").getValue(String::class.java) ?: ""
                    val name = child.child("displayName").getValue(String::class.java) ?: ""
                    val uname = child.child("username").getValue(String::class.java) ?: ""
                    val ts = (child.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()

                    if (isTyping && text.isNotEmpty()) {
                        otherDraft = LiveDraftState(
                            uid = uid,
                            username = uname,
                            displayName = name,
                            isTyping = true,
                            draftText = text,
                            timestamp = ts
                        )
                        break
                    }
                }
                trySend(otherDraft)
            }

            override fun onCancelled(error: DatabaseError) {
                val local = localLiveDraft.value[conversationId]
                trySend(if (local?.uid != currentUid) local else null)
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
     * Toggle an emoji reaction on a message.
     */
    fun toggleReaction(conversationId: String, messageId: String, uid: String, emoji: String) {
        if (conversationId.isEmpty() || messageId.isEmpty() || uid.isEmpty()) return

        // Update local reaction map
        val currentConvReactions = (localReactions.value[conversationId] ?: emptyMap()).toMutableMap()
        val currentMsgReactions = (currentConvReactions[messageId] ?: emptyMap()).toMutableMap()

        if (currentMsgReactions[uid] == emoji) {
            currentMsgReactions.remove(uid)
        } else {
            currentMsgReactions[uid] = emoji
        }
        currentConvReactions[messageId] = currentMsgReactions
        localReactions.value = localReactions.value + (conversationId to currentConvReactions)

        scope.launch {
            try {
                val ref = FirebaseManager.reactionsRef?.child(conversationId)?.child(messageId)?.child(uid)
                if (currentMsgReactions[uid] == emoji) {
                    ref?.setValue(emoji)
                } else {
                    ref?.removeValue()
                }
            } catch (e: Exception) {
                Log.w(TAG, "toggleReaction notice: ${e.message}")
            }
        }
    }

    /**
     * Observe reactions for all messages in a conversation.
     */
    fun observeReactions(conversationId: String): Flow<Map<String, Map<String, String>>> = callbackFlow {
        if (conversationId.isEmpty()) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.reactionsRef?.child(conversationId)
        if (ref == null) {
            trySend(localReactions.value[conversationId] ?: emptyMap())
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, Map<String, String>>()
                for (msgChild in snapshot.children) {
                    val msgId = msgChild.key ?: continue
                    val userReactions = mutableMapOf<String, String>()
                    for (userChild in msgChild.children) {
                        val uid = userChild.key ?: continue
                        val emoji = userChild.getValue(String::class.java) ?: continue
                        userReactions[uid] = emoji
                    }
                    map[msgId] = userReactions
                }
                localReactions.value = localReactions.value + (conversationId to map)
                trySend(map)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(localReactions.value[conversationId] ?: emptyMap())
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
     * Pin or unpin a message in the conversation.
     */
    fun pinMessage(conversationId: String, messageId: String?, messageText: String?) {
        if (conversationId.isEmpty()) return
        localPinned.value = localPinned.value + (conversationId to Pair(messageId, messageText))

        scope.launch {
            try {
                val ref = FirebaseManager.conversationsRef?.child(conversationId)
                if (messageId != null) {
                    ref?.updateChildren(
                        mapOf(
                            "pinnedMessageId" to messageId,
                            "pinnedMessageText" to (messageText ?: "")
                        )
                    )
                } else {
                    ref?.child("pinnedMessageId")?.removeValue()
                    ref?.child("pinnedMessageText")?.removeValue()
                }
            } catch (e: Exception) {}
        }
    }

    /**
     * Observe pinned message.
     */
    fun observePinnedMessage(conversationId: String): Flow<Pair<String?, String?>> = callbackFlow {
        if (conversationId.isEmpty()) {
            trySend(Pair(null, null))
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.conversationsRef?.child(conversationId)
        if (ref == null) {
            trySend(localPinned.value[conversationId] ?: Pair(null, null))
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pId = snapshot.child("pinnedMessageId").getValue(String::class.java)
                val pText = snapshot.child("pinnedMessageText").getValue(String::class.java)
                trySend(Pair(pId, pText))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(localPinned.value[conversationId] ?: Pair(null, null))
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
     * Observe online presence of another user.
     */
    fun observePresence(uid: String): Flow<Pair<Boolean, Long>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(Pair(false, 0L))
            close()
            return@callbackFlow
        }

        val ref = FirebaseManager.presenceRef?.child(uid)
        if (ref == null) {
            trySend(Pair(false, 0L))
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastSeen = (snapshot.child("lastSeen").value as? Number)?.toLong() ?: 0L
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

    /**
     * Mark a conversation as read (resets unread counter and marks messages as READ).
     */
    fun markConversationAsRead(conversationId: String, currentUid: String) {
        if (conversationId.isEmpty() || currentUid.isEmpty()) return

        // Update local unread counter
        val list = (localConversations.value[currentUid] ?: emptyList()).map { item ->
            if (item.id == conversationId) item.copy(unreadCount = 0) else item
        }
        localConversations.value = localConversations.value + (currentUid to list)

        scope.launch {
            try {
                FirebaseManager.userConversationsRef?.child(currentUid)?.child(conversationId)?.child("unreadCount")?.setValue(0)
            } catch (e: Exception) {}
        }
    }
}
