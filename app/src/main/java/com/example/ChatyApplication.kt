package com.example

import android.app.Application
import android.util.Log
import com.example.data.FirebaseManager

class ChatyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set safe uncaught exception handler to prevent hard crashes on startup
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ChatyApplication", "Uncaught exception on ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            FirebaseManager.initApp(this)
        } catch (e: Throwable) {
            Log.e("ChatyApplication", "Firebase initialization error: ${e.message}", e)
        }
    }
}
