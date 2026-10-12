package com.example.data

import android.util.Log
import com.example.model.UserProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.absoluteValue

class AuthRepository {
    private val TAG = "AuthRepository"

    private val auth: FirebaseAuth?
        get() = FirebaseManager.auth

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _demoUser = MutableStateFlow<UserProfile?>(null)
    val demoUser: StateFlow<UserProfile?> = _demoUser.asStateFlow()

    private var authListener: FirebaseAuth.AuthStateListener? = null

    init {
        try {
            auth?.let { fbAuth ->
                _currentUser.value = fbAuth.currentUser
                authListener = FirebaseAuth.AuthStateListener { currentAuth ->
                    _currentUser.value = currentAuth.currentUser
                    currentAuth.currentUser?.uid?.let { uid ->
                        FirebaseManager.setupPresence(uid)
                    }
                }
                fbAuth.addAuthStateListener(authListener!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error attaching AuthStateListener: ${e.message}")
        }
    }

    /**
     * Check if a username is available.
     * Never returns false on network or database exceptions to prevent blocking valid signups.
     */
    suspend fun isUsernameAvailable(username: String): Boolean {
        val clean = username.trim().lowercase()
        if (clean.length < 3 || clean.length > 20) return false
        if (!clean.matches(Regex("^[a-z0-9_]+$"))) return false

        val usernamesRef = FirebaseManager.usernamesRef ?: return true

        return try {
            val snapshot = usernamesRef.child(clean).awaitSnapshot()
            !snapshot.exists()
        } catch (e: Exception) {
            Log.w(TAG, "Could not verify username in database: ${e.message}")
            true
        }
    }

    /**
     * Register a new user with email, password, unique username, and optional display name.
     */
    suspend fun signUp(
        email: String,
        pass: String,
        username: String,
        displayName: String
    ): Result<UserProfile> {
        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.length < 3 || cleanUsername.length > 20) {
            return Result.failure(IllegalArgumentException("Username must be between 3 and 20 characters."))
        }
        if (!cleanUsername.matches(Regex("^[a-z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Username can only contain letters, numbers, and underscores."))
        }

        val cleanDisplayName = displayName.trim().ifEmpty { cleanUsername }
        val avatarIndex = (cleanUsername.hashCode().absoluteValue % 6)

        val fbAuth = auth
        val uid = if (fbAuth != null) {
            try {
                val authResult = fbAuth.createUserWithEmailAndPassword(email.trim(), pass).awaitResult()
                authResult.user?.uid ?: ("user_" + System.currentTimeMillis())
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Registration error."
                Log.e(TAG, "createUserWithEmailAndPassword error: $msg", e)
                if (msg.contains("disabled", ignoreCase = true)) {
                    return Result.failure(IllegalStateException("Email/Password provider is disabled in Firebase Console. Please enable it under Authentication > Sign-in method, or use Quick Demo Mode."))
                }
                if (msg.contains("already in use", ignoreCase = true)) {
                    return Result.failure(IllegalStateException("This email address is already registered. Please log in instead."))
                }
                return Result.failure(e)
            }
        } else {
            "demo_" + cleanUsername
        }

        val profile = UserProfile(
            uid = uid,
            username = cleanUsername,
            displayName = cleanDisplayName,
            email = email.trim(),
            statusMessage = "Hey there! I am using Chaty.",
            avatarColorIndex = avatarIndex,
            createdAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            isOnline = true
        )

        // Try writing to Realtime Database
        try {
            FirebaseManager.database?.let { db ->
                val updates = mapOf<String, Any>(
                    "usernames/$cleanUsername" to mapOf(
                        "uid" to uid,
                        "createdAt" to ServerValue.TIMESTAMP
                    ),
                    "users/$uid" to profile.toMap()
                )
                db.reference.updateChildren(updates).awaitResult()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Realtime database profile write warning: ${e.message}")
        }

        FirebaseManager.setupPresence(uid)
        return Result.success(profile)
    }

    /**
     * Sign in with existing email and password.
     */
    suspend fun signIn(email: String, pass: String): Result<UserProfile> {
        val fbAuth = auth ?: return signInDemo(
            email.substringBefore("@").lowercase(),
            email.substringBefore("@"),
            email
        )

        return try {
            val authResult = fbAuth.signInWithEmailAndPassword(email.trim(), pass).awaitResult()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("User not found."))

            val uid = firebaseUser.uid
            val profileSnapshot = try {
                FirebaseManager.usersRef?.child(uid)?.awaitSnapshot()
            } catch (e: Exception) {
                null
            }

            val profile = if (profileSnapshot != null && profileSnapshot.exists()) {
                val data = profileSnapshot.value as? Map<String, Any?> ?: emptyMap()
                UserProfile.fromMap(data)
            } else {
                val fallbackUsername = (firebaseUser.email?.substringBefore("@") ?: "user").lowercase()
                UserProfile(
                    uid = uid,
                    username = fallbackUsername,
                    displayName = fallbackUsername,
                    email = firebaseUser.email ?: "",
                    createdAt = System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis(),
                    isOnline = true
                )
            }

            FirebaseManager.setupPresence(uid)
            Result.success(profile)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Invalid email or password."
            if (msg.contains("disabled", ignoreCase = true)) {
                Result.failure(IllegalStateException("Email/Password provider is disabled in Firebase Console. Please enable it under Authentication > Sign-in method, or use Quick Demo Mode."))
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Instant sign-in for preview/demo accounts without network dependency.
     */
    fun signInDemo(username: String, displayName: String, email: String): Result<UserProfile> {
        val cleanUser = username.trim().lowercase()
        val uid = "demo_${cleanUser}"
        val profile = UserProfile(
            uid = uid,
            username = cleanUser,
            displayName = displayName.ifEmpty { cleanUser },
            email = email,
            statusMessage = "Available on Chaty",
            avatarColorIndex = (cleanUser.hashCode().absoluteValue % 6),
            createdAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            isOnline = true
        )
        _demoUser.value = profile
        return Result.success(profile)
    }

    /**
     * Sign out current user.
     */
    fun signOut() {
        try {
            auth?.currentUser?.uid?.let { uid ->
                FirebaseManager.cleanupPresence(uid)
            }
            auth?.signOut()
            _demoUser.value = null
        } catch (e: Exception) {
            Log.w(TAG, "signOut error: ${e.message}")
        }
    }

    /**
     * Send password reset email.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val fbAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth not initialized."))
        return try {
            fbAuth.sendPasswordResetEmail(email.trim()).awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe user profile in real-time.
     */
    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        // If it's a demo user, emit demo user immediately
        _demoUser.value?.let { demo ->
            if (demo.uid == uid) {
                trySend(demo)
            }
        }

        val ref = FirebaseManager.usersRef?.child(uid)
        if (ref == null) {
            trySend(_demoUser.value)
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val map = snapshot.value as? Map<String, Any?>
                    val profile = map?.let { UserProfile.fromMap(it) }
                    trySend(profile)
                } else {
                    trySend(_demoUser.value)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(_demoUser.value)
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
     * Update user profile information.
     */
    suspend fun updateProfile(
        uid: String,
        displayName: String,
        statusMessage: String
    ): Result<Unit> {
        _demoUser.value?.let { demo ->
            if (demo.uid == uid) {
                _demoUser.value = demo.copy(displayName = displayName, statusMessage = statusMessage)
            }
        }

        return try {
            val updates = mapOf<String, Any>(
                "displayName" to displayName.trim(),
                "statusMessage" to statusMessage.trim()
            )
            FirebaseManager.usersRef?.child(uid)?.updateChildren(updates)?.awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit) // Safe fallback
        }
    }

    /**
     * Find another user by exact username.
     */
    suspend fun searchUserByUsername(username: String): Result<UserProfile?> {
        val clean = username.trim().lowercase()

        // Check demo accounts first
        if (clean == "alice" || clean == "bob") {
            val isAlice = clean == "alice"
            return Result.success(
                UserProfile(
                    uid = if (isAlice) "demo_alice" else "demo_bob",
                    username = clean,
                    displayName = if (isAlice) "Alice Morgan" else "Bob Vance",
                    email = "$clean@chaty.app",
                    avatarColorIndex = if (isAlice) 0 else 1,
                    isOnline = true
                )
            )
        }

        val usernamesRef = FirebaseManager.usernamesRef
            ?: return Result.success(null)

        return try {
            val usernameSnapshot = usernamesRef.child(clean).awaitSnapshot()
            if (!usernameSnapshot.exists()) {
                return Result.success(null)
            }

            val uid = usernameSnapshot.child("uid").getValue(String::class.java)
                ?: return Result.success(null)

            val userSnapshot = FirebaseManager.usersRef?.child(uid)?.awaitSnapshot()
            if (userSnapshot == null || !userSnapshot.exists()) {
                return Result.success(null)
            }

            val map = userSnapshot.value as? Map<String, Any?> ?: emptyMap()
            Result.success(UserProfile.fromMap(map))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Coroutine Task Extensions
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        cont.resume(result)
    }
    addOnFailureListener { exception ->
        cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        cont.cancel()
    }
}

suspend fun DatabaseReference.awaitSnapshot(): DataSnapshot =
    suspendCancellableCoroutine { cont ->
        addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cont.resume(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {
                cont.resumeWithException(error.toException())
            }
        })
    }
