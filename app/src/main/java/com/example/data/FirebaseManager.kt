package com.example.data

import android.util.Log
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

    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    val database: FirebaseDatabase by lazy {
        try {
            val db = FirebaseDatabase.getInstance(DATABASE_URL)
            try {
                db.setPersistenceEnabled(true)
            } catch (e: Exception) {
                Log.d(TAG, "Persistence already enabled or not supported in test environment: ${e.message}")
            }
            db
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseDatabase: ${e.message}")
            FirebaseDatabase.getInstance()
        }
    }

    // Database Reference shortcuts
    val usersRef: DatabaseReference get() = database.getReference("users")
    val usernamesRef: DatabaseReference get() = database.getReference("usernames")
    val conversationsRef: DatabaseReference get() = database.getReference("conversations")
    val userConversationsRef: DatabaseReference get() = database.getReference("user_conversations")
    val messagesRef: DatabaseReference get() = database.getReference("messages")
    val typingRef: DatabaseReference get() = database.getReference("typing")
    val presenceRef: DatabaseReference get() = database.getReference("presence")

    private var connectedListener: ValueEventListener? = null
    private var connectedRef: DatabaseReference? = null

    /**
     * Set up real-time presence for the authenticated user.
     * Uses Firebase Realtime Database's .info/connected and onDisconnect handlers.
     */
    fun setupPresence(uid: String) {
        if (uid.isEmpty()) return

        cleanupPresence()

        val userPresenceRef = presenceRef.child(uid)
        val userProfileRef = usersRef.child(uid)
        connectedRef = database.getReference(".info/connected")

        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    // When device connects, configure disconnect handlers first
                    userPresenceRef.child("isOnline").onDisconnect().setValue(false)
                    userPresenceRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

                    userProfileRef.child("isOnline").onDisconnect().setValue(false)
                    userProfileRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

                    // Now mark current state as online
                    userPresenceRef.child("isOnline").setValue(true)
                    userPresenceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)

                    userProfileRef.child("isOnline").setValue(true)
                    userProfileRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Presence listener cancelled: ${error.message}")
            }
        }

        connectedRef?.addValueEventListener(connectedListener!!)
    }

    /**
     * Clean up presence listeners and mark user offline on logout.
     */
    fun cleanupPresence(uid: String? = null) {
        if (uid != null && uid.isNotEmpty()) {
            presenceRef.child(uid).child("isOnline").setValue(false)
            presenceRef.child(uid).child("lastSeen").setValue(ServerValue.TIMESTAMP)
            usersRef.child(uid).child("isOnline").setValue(false)
            usersRef.child(uid).child("lastSeen").setValue(ServerValue.TIMESTAMP)
        }

        connectedListener?.let { listener ->
            connectedRef?.removeEventListener(listener)
        }
        connectedListener = null
        connectedRef = null
    }
}
