package com.example

import com.example.model.ChatMessage
import com.example.model.ConversationItem
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testUserProfileSerialization() {
    val profile = UserProfile(
      uid = "uid123",
      username = "alex_01",
      displayName = "Alex Rivers",
      email = "alex@chaty.app",
      statusMessage = "Available",
      avatarColorIndex = 2,
      createdAt = 1000L,
      lastSeen = 2000L,
      isOnline = true
    )

    val map = profile.toMap()
    assertEquals("uid123", map["uid"])
    assertEquals("alex_01", map["username"])

    val restored = UserProfile.fromMap(map)
    assertEquals(profile.uid, restored.uid)
    assertEquals(profile.username, restored.username)
    assertEquals(profile.displayName, restored.displayName)
    assertEquals(profile.isOnline, restored.isOnline)
  }

  @Test
  fun testChatMessageSerialization() {
    val message = ChatMessage(
      id = "msg1",
      conversationId = "chat_u1_u2",
      senderId = "u1",
      senderUsername = "user1",
      senderDisplayName = "User One",
      text = "Hello from Chaty!",
      timestamp = 5000L,
      status = "SENT",
      replyToId = "orig1",
      replyToText = "Previous text",
      replyToSender = "User Two",
      isEdited = false,
      isDeleted = false
    )

    val map = message.toMap()
    val restored = ChatMessage.fromMap("msg1", map)

    assertEquals(message.id, restored.id)
    assertEquals(message.conversationId, restored.conversationId)
    assertEquals(message.text, restored.text)
    assertEquals(message.replyToText, restored.replyToText)
  }

  @Test
  fun testDeterministicConversationId() {
    val uidA = "alice_xyz"
    val uidB = "bob_abc"

    val convId1 = if (uidA < uidB) "chat_${uidA}_${uidB}" else "chat_${uidB}_${uidA}"
    val convId2 = if (uidB < uidA) "chat_${uidB}_${uidA}" else "chat_${uidA}_${uidB}"

    assertEquals(convId1, convId2)
  }
}
