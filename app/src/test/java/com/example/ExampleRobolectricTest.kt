package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun testAppContextAndAppName() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertNotNull(context)
    val appName = context.getString(R.string.app_name)
    assertEquals("Chaty", appName)
  }

  @Test
  fun testUserSearchMechanism() {
    val authRepo = com.example.data.AuthRepository.instance

    // Test suggested directory contacts on empty query
    val suggested = authRepo.searchUsers("", "user_me")
    org.junit.Assert.assertTrue(suggested.isNotEmpty())

    // Test search by username
    val aliceMatch = authRepo.searchUsers("alice", "user_me")
    org.junit.Assert.assertTrue(aliceMatch.any { it.username == "alice" })

    // Test search by display name (partial match)
    val vanceMatch = authRepo.searchUsers("vance", "user_me")
    org.junit.Assert.assertTrue(vanceMatch.any { it.username == "bob" })

    // Test fallback user guarantees non-null
    val currentUser = authRepo.getCurrentUserOrFallback()
    assertNotNull(currentUser)
    org.junit.Assert.assertTrue(currentUser.uid.isNotEmpty())
  }
}
