package com.example.data

import com.example.model.UserProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
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

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseManager.auth
) {
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { fbAuth ->
        _currentUser.value = fbAuth.currentUser
        fbAuth.currentUser?.uid?.let { uid ->
            FirebaseManager.setupPresence(uid)
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.uid?.let { uid ->
            FirebaseManager.setupPresence(uid)
        }
    }

    /**
     * Check if a username is available.
     */
    suspend fun isUsernameAvailable(username: String): Boolean {
        val clean = username.trim().lowercase()
        if (clean.length < 3 || clean.length > 20) return false
        if (!clean.matches(Regex("^[a-z0-9_]+$"))) return false

        return try {
            val snapshot = FirebaseManager.usernamesRef.child(clean).awaitSnapshot()
            !snapshot.exists()
        } catch (e: Exception) {
            false
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
            return Result.failure(IllegalArgumentException("Username must be 3-20 characters."))
        }
        if (!cleanUsername.matches(Regex("^[a-z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Username can only contain letters, numbers, and underscores."))
        }

        val cleanDisplayName = displayName.trim().ifEmpty { cleanUsername }

        return try {
            // Step 1: Check username availability
            val usernameSnapshot = FirebaseManager.usernamesRef.child(cleanUsername).awaitSnapshot()
            if (usernameSnapshot.exists()) {
                return Result.failure(IllegalStateException("Username '$cleanUsername' is already taken. Please choose another."))
            }

            // Step 2: Create Auth User
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).awaitResult()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Failed to create user account."))

            val uid = firebaseUser.uid
            val avatarIndex = (cleanUsername.hashCode().absoluteValue % 6)

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

            // Step 3: Atomic reservation and profile creation
            val updates = mapOf<String, Any>(
                "usernames/$cleanUsername" to mapOf(
                    "uid" to uid,
                    "createdAt" to ServerValue.TIMESTAMP
                ),
                "users/$uid" to profile.toMap()
            )

            FirebaseManager.database.reference.updateChildren(updates).awaitResult()
            FirebaseManager.setupPresence(uid)

            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign in with existing email and password.
     */
    suspend fun signIn(email: String, pass: String): Result<UserProfile> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).awaitResult()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("User not found."))

            val uid = firebaseUser.uid
            val profileSnapshot = FirebaseManager.usersRef.child(uid).awaitSnapshot()

            val profile = if (profileSnapshot.exists()) {
                val data = profileSnapshot.value as? Map<String, Any?> ?: emptyMap()
                UserProfile.fromMap(data)
            } else {
                // Fallback in case user profile was missing
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
            Result.failure(e)
        }
    }

    /**
     * Sign out current user.
     */
    fun signOut() {
        auth.currentUser?.uid?.let { uid ->
            FirebaseManager.cleanupPresence(uid)
        }
        auth.signOut()
    }

    /**
     * Send password reset email.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).awaitResult()
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

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val map = snapshot.value as? Map<String, Any?>
                    val profile = map?.let { UserProfile.fromMap(it) }
                    trySend(profile)
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        val ref = FirebaseManager.usersRef.child(uid)
        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
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
        return try {
            val updates = mapOf<String, Any>(
                "displayName" to displayName.trim(),
                "statusMessage" to statusMessage.trim()
            )
            FirebaseManager.usersRef.child(uid).updateChildren(updates).awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Find another user by exact username.
     */
    suspend fun searchUserByUsername(username: String): Result<UserProfile?> {
        val clean = username.trim().lowercase()
        return try {
            val usernameSnapshot = FirebaseManager.usernamesRef.child(clean).awaitSnapshot()
            if (!usernameSnapshot.exists()) {
                return Result.success(null)
            }

            val uid = usernameSnapshot.child("uid").getValue(String::class.java)
                ?: return Result.success(null)

            val userSnapshot = FirebaseManager.usersRef.child(uid).awaitSnapshot()
            if (!userSnapshot.exists()) {
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

suspend fun com.google.firebase.database.DatabaseReference.awaitSnapshot(): DataSnapshot =
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
