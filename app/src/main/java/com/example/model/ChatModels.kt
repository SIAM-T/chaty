package com.example.model

/**
 * Represents a registered user's profile on Chaty.
 */
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    val statusMessage: String = "Hey there! I am using Chaty.",
    val avatarColorIndex: Int = 0,
    val createdAt: Long = 0L,
    val lastSeen: Long = 0L,
    val isOnline: Boolean = false,
    val isLiveStreamEnabled: Boolean = true // Live Keystroke Stream privacy setting
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "username" to username,
        "displayName" to displayName,
        "email" to email,
        "statusMessage" to statusMessage,
        "avatarColorIndex" to avatarColorIndex,
        "createdAt" to createdAt,
        "lastSeen" to lastSeen,
        "isOnline" to isOnline,
        "isLiveStreamEnabled" to isLiveStreamEnabled
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): UserProfile {
            return UserProfile(
                uid = map["uid"] as? String ?: "",
                username = map["username"] as? String ?: "",
                displayName = map["displayName"] as? String ?: "",
                email = map["email"] as? String ?: "",
                statusMessage = map["statusMessage"] as? String ?: "Hey there! I am using Chaty.",
                avatarColorIndex = (map["avatarColorIndex"] as? Number)?.toInt() ?: 0,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                lastSeen = (map["lastSeen"] as? Number)?.toLong() ?: 0L,
                isOnline = map["isOnline"] as? Boolean ?: false,
                isLiveStreamEnabled = map["isLiveStreamEnabled"] as? Boolean ?: true
            )
        }
    }
}

/**
 * Metadata representation of a conversation in the user's conversation list.
 * Stored at /user_conversations/{uid}/{conversationId}
 */
data class ConversationItem(
    val id: String = "",
    val otherUid: String = "",
    val otherUsername: String = "",
    val otherDisplayName: String = "",
    val otherAvatarIndex: Int = 0,
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val otherIsOnline: Boolean = false,
    val otherLastSeen: Long = 0L,
    val pinnedMessageId: String? = null,
    val pinnedMessageText: String? = null,
    val activeLiveDraft: String? = null // When the other user is typing live right now!
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "otherUid" to otherUid,
        "otherUsername" to otherUsername,
        "otherDisplayName" to otherDisplayName,
        "otherAvatarIndex" to otherAvatarIndex,
        "lastMessage" to lastMessage,
        "lastMessageSenderId" to lastMessageSenderId,
        "lastMessageTimestamp" to lastMessageTimestamp,
        "unreadCount" to unreadCount,
        "pinnedMessageId" to pinnedMessageId,
        "pinnedMessageText" to pinnedMessageText
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ConversationItem {
            return ConversationItem(
                id = id,
                otherUid = map["otherUid"] as? String ?: "",
                otherUsername = map["otherUsername"] as? String ?: "",
                otherDisplayName = map["otherDisplayName"] as? String ?: "",
                otherAvatarIndex = (map["otherAvatarIndex"] as? Number)?.toInt() ?: 0,
                lastMessage = map["lastMessage"] as? String ?: "",
                lastMessageSenderId = map["lastMessageSenderId"] as? String ?: "",
                lastMessageTimestamp = (map["lastMessageTimestamp"] as? Number)?.toLong() ?: 0L,
                unreadCount = (map["unreadCount"] as? Number)?.toInt() ?: 0,
                pinnedMessageId = map["pinnedMessageId"] as? String,
                pinnedMessageText = map["pinnedMessageText"] as? String
            )
        }
    }
}

/**
 * Message object stored under /messages/{conversationId}/{messageId}
 */
data class ChatMessage(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val status: String = "SENT", // "SENDING", "SENT", "DELIVERED", "READ"
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val isEdited: Boolean = false,
    val editedAt: Long? = null,
    val isDeleted: Boolean = false,
    val reactions: Map<String, String> = emptyMap(), // uid -> emoji ("❤️", "🔥", "😂", "👍", "😮", "😢")
    val isStarred: Boolean = false,
    val isPinned: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "conversationId" to conversationId,
        "senderId" to senderId,
        "senderUsername" to senderUsername,
        "senderDisplayName" to senderDisplayName,
        "text" to text,
        "timestamp" to timestamp,
        "status" to status,
        "replyToId" to replyToId,
        "replyToText" to replyToText,
        "replyToSender" to replyToSender,
        "isEdited" to isEdited,
        "editedAt" to editedAt,
        "isDeleted" to isDeleted
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ChatMessage {
            val rawReactions = map["reactions"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val parsedReactions = rawReactions.mapNotNull { (k, v) ->
                if (k is String && v is String) k to v else null
            }.toMap()

            return ChatMessage(
                id = id,
                conversationId = map["conversationId"] as? String ?: "",
                senderId = map["senderId"] as? String ?: "",
                senderUsername = map["senderUsername"] as? String ?: "",
                senderDisplayName = map["senderDisplayName"] as? String ?: "",
                text = map["text"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                status = map["status"] as? String ?: "SENT",
                replyToId = map["replyToId"] as? String,
                replyToText = map["replyToText"] as? String,
                replyToSender = map["replyToSender"] as? String,
                isEdited = map["isEdited"] as? Boolean ?: false,
                editedAt = (map["editedAt"] as? Number)?.toLong(),
                isDeleted = map["isDeleted"] as? Boolean ?: false,
                reactions = parsedReactions,
                isStarred = map["isStarred"] as? Boolean ?: false,
                isPinned = map["isPinned"] as? Boolean ?: false
            )
        }
    }
}

/**
 * Real-time keystroke live-draft state ("Telepathic Keystroke Stream").
 * Stored at /typing/{conversationId}/{uid}
 * Enables seeing the actual characters/words as the user types them live!
 */
data class LiveDraftState(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val isTyping: Boolean = false,
    val draftText: String = "",
    val timestamp: Long = 0L
)
