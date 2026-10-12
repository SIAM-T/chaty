package com.example

import android.app.Application
import android.util.Log
import com.example.data.FirebaseManager

class ChatyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseManager.initApp(this)
        } catch (e: Exception) {
            Log.e("ChatyApplication", "Firebase initialization error: ${e.message}", e)
        }
    }
}
