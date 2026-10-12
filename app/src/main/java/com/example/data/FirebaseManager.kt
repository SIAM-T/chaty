package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    const val DATABASE_URL = "https://chaty-b563a-default-rtdb.asia-southeast1.firebasedatabase.app"

    private var isInitialized = false
    private var cachedDatabase: FirebaseDatabase? = null
    private var cachedAuth: FirebaseAuth? = null

    fun initApp(context: Context) {
        if (isInitialized) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val app = FirebaseApp.initializeApp(context)
                if (app == null) {
                    initExplicit(context)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard Firebase initialization failed: ${e.message}, trying explicit options")
            initExplicit(context)
        }

        // Initialize Database instance once and enable persistence safely
        try {
            val db = FirebaseDatabase.getInstance(DATABASE_URL)
            try {
                db.setPersistenceEnabled(true)
            } catch (e: Exception) {
                Log.d(TAG, "Persistence notice: ${e.message}")
            }
            cachedDatabase = db
        } catch (e: Exception) {
            try {
                cachedDatabase = FirebaseDatabase.getInstance()
            } catch (ex: Exception) {
                Log.w(TAG, "Fallback FirebaseDatabase init notice: ${ex.message}")
            }
        }

        // Initialize Auth instance once
        try {
            cachedAuth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth init notice: ${e.message}")
        }

        isInitialized = true
    }

    private fun initExplicit(context: Context) {
        try {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:543562869843:android:5ed87c9e422e9a9e6b2aa6")
                .setApiKey("AIzaSyAwNBtsdkeB1-HENKhQEIhtrOK88xArO9Y")
                .setDatabaseUrl(DATABASE_URL)
                .setProjectId("chaty-b563a")
                .setStorageBucket("chaty-b563a.firebasestorage.app")
                .setGcmSenderId("543562869843")
                .build()
            FirebaseApp.initializeApp(context, options)
            Log.d(TAG, "Explicit FirebaseApp initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Explicit FirebaseApp initialization error: ${e.message}")
        }
    }

    val auth: FirebaseAuth?
        get() {
            if (cachedAuth != null) return cachedAuth
            return try {
                val a = FirebaseAuth.getInstance()
                cachedAuth = a
                a
            } catch (e: Exception) {
                null
            }
        }

    val database: FirebaseDatabase?
        get() {
            if (cachedDatabase != null) return cachedDatabase
            return try {
                val db = FirebaseDatabase.getInstance(DATABASE_URL)
                cachedDatabase = db
                db
            } catch (e: Exception) {
                try {
                    val db = FirebaseDatabase.getInstance()
                    cachedDatabase = db
                    db
                } catch (ex: Exception) {
                    null
                }
            }
        }

    // Database Reference shortcuts (returns null safely if database is unavailable)
    val usersRef: DatabaseReference? get() = database?.getReference("users")
    val usernamesRef: DatabaseReference? get() = database?.getReference("usernames")
    val conversationsRef: DatabaseReference? get() = database?.getReference("conversations")
    val userConversationsRef: DatabaseReference? get() = database?.getReference("user_conversations")
    val messagesRef: DatabaseReference? get() = database?.getReference("messages")
    val typingRef: DatabaseReference? get() = database?.getReference("typing")
    val presenceRef: DatabaseReference? get() = database?.getReference("presence")
    val reactionsRef: DatabaseReference? get() = database?.getReference("reactions")
    val starredMessagesRef: DatabaseReference? get() = database?.getReference("starred_messages")

    private var connectedListener: ValueEventListener? = null
    private var connectedRef: DatabaseReference? = null

    /**
     * Set up real-time presence for the authenticated user.
     */
    fun setupPresence(uid: String) {
        if (uid.isEmpty()) return
        val db = database ?: return

        try {
            cleanupPresence()

            val userPresenceRef = db.getReference("presence").child(uid)
            val userProfileRef = db.getReference("users").child(uid)
            connectedRef = db.getReference(".info/connected")

            connectedListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        try {
                            userPresenceRef.child("isOnline").onDisconnect().setValue(false)
                            userPresenceRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

                            userProfileRef.child("isOnline").onDisconnect().setValue(false)
                            userProfileRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

                            userPresenceRef.child("isOnline").setValue(true)
                            userPresenceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)

                            userProfileRef.child("isOnline").setValue(true)
                            userProfileRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
                        } catch (e: Exception) {
                            Log.w(TAG, "Presence write error: ${e.message}")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Presence listener cancelled: ${error.message}")
                }
            }

            connectedRef?.addValueEventListener(connectedListener!!)
        } catch (e: Exception) {
            Log.w(TAG, "setupPresence failed: ${e.message}")
        }
    }

    /**
     * Clean up presence listeners and mark user offline.
     */
    fun cleanupPresence(uid: String? = null) {
        try {
            if (uid != null && uid.isNotEmpty()) {
                database?.let { db ->
                    db.getReference("presence").child(uid).child("isOnline").setValue(false)
                    db.getReference("presence").child(uid).child("lastSeen").setValue(ServerValue.TIMESTAMP)
                    db.getReference("users").child(uid).child("isOnline").setValue(false)
                    db.getReference("users").child(uid).child("lastSeen").setValue(ServerValue.TIMESTAMP)
                }
            }

            connectedListener?.let { listener ->
                connectedRef?.removeEventListener(listener)
            }
            connectedListener = null
            connectedRef = null
        } catch (e: Exception) {
            Log.w(TAG, "cleanupPresence error: ${e.message}")
        }
    }
}
