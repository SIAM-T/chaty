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
}
