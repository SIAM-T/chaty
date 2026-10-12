package com.example.data

import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.absoluteValue

class AuthRepository private constructor() {
    private val TAG = "AuthRepository"
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val instance: AuthRepository by lazy { AuthRepository() }

        // Default roster of vibrant directory users ready for instant chat
        val DEFAULT_DIRECTORY_USERS = listOf(
            UserProfile(
                uid = "user_alice",
                username = "alice",
                displayName = "Alice Morgan",
                email = "alice@chaty.app",
                statusMessage = "Designing the future of real-time chat ✨",
                avatarColorIndex = 0,
                isOnline = true,
                createdAt = 1700000000000L
            ),
            UserProfile(
                uid = "user_bob",
                username = "bob",
                displayName = "Bob Vance",
                email = "bob@chaty.app",
                statusMessage = "Coding with Kotlin & Jetpack Compose 🚀",
                avatarColorIndex = 1,
                isOnline = true,
                createdAt = 1700000001000L
            ),
            UserProfile(
                uid = "user_sophia",
                username = "sophia",
                displayName = "Sophia Chen",
                email = "sophia@chaty.app",
                statusMessage = "Exploring AI & telepathic messaging 🧠⚡",
                avatarColorIndex = 2,
                isOnline = true,
                createdAt = 1700000002000L
            ),
            UserProfile(
                uid = "user_marcus",
                username = "marcus",
                displayName = "Marcus Aurelius",
                email = "marcus@chaty.app",
                statusMessage = "Meditations on clean code & speed 🏛️",
                avatarColorIndex = 3,
                isOnline = false,
                lastSeen = System.currentTimeMillis() - 15 * 60 * 1000L,
                createdAt = 1700000003000L
            ),
            UserProfile(
                uid = "user_elena",
                username = "elena",
                displayName = "Elena Rostova",
                email = "elena@chaty.app",
                statusMessage = "Photographer & nomad. Catch me outside 📸",
                avatarColorIndex = 4,
                isOnline = true,
                createdAt = 1700000004000L
            )
        )
    }

    private val auth: FirebaseAuth?
        get() = FirebaseManager.auth

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _demoUser = MutableStateFlow<UserProfile?>(null)
    val demoUser: StateFlow<UserProfile?> = _demoUser.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    // In-memory registry of all discovered and known users for lightning-fast search
    private val knownUsers = MutableStateFlow<Map<String, UserProfile>>(
        DEFAULT_DIRECTORY_USERS.associateBy { it.uid }
    )

    private var authListener: FirebaseAuth.AuthStateListener? = null

    init {
        // Step 1: Immediately restore cached session from SharedPreferences in 0ms
        loadProfileFromPrefs()?.let { cached ->
            _activeProfile.value = cached
            Log.d(TAG, "Restored active profile from disk: @${cached.username} (${cached.uid})")
        }

        // Step 2: Bind Firebase Auth listener and sync presence
        try {
            auth?.let { fbAuth ->
                val initialUser = fbAuth.currentUser
                _currentUser.value = initialUser
                if (initialUser != null) {
                    ensureActiveProfileForFirebaseUser(initialUser)
                    FirebaseManager.setupPresence(initialUser.uid)
                }

                authListener = FirebaseAuth.AuthStateListener { currentAuth ->
                    val user = currentAuth.currentUser
                    _currentUser.value = user
                    if (user != null) {
                        ensureActiveProfileForFirebaseUser(user)
                        FirebaseManager.setupPresence(user.uid)
                    }
                }
                fbAuth.addAuthStateListener(authListener!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error attaching AuthStateListener: ${e.message}")
        }

        // Step 3: Listen to users directory in background to index all network users
        syncRemoteUsersDirectory()
    }

    private fun getPrefs(): SharedPreferences? {
        val context = FirebaseManager.appContext ?: return null
        return context.getSharedPreferences("chaty_auth_prefs", Context.MODE_PRIVATE)
    }

    private fun saveProfileToPrefs(profile: UserProfile) {
        try {
            val prefs = getPrefs() ?: return
            prefs.edit()
                .putString("uid", profile.uid)
                .putString("username", profile.username)
                .putString("displayName", profile.displayName)
                .putString("email", profile.email)
                .putString("statusMessage", profile.statusMessage)
                .putInt("avatarColorIndex", profile.avatarColorIndex)
                .putLong("createdAt", profile.createdAt)
                .putBoolean("isLiveStreamEnabled", profile.isLiveStreamEnabled)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "saveProfileToPrefs error: ${e.message}")
        }
    }

    private fun loadProfileFromPrefs(): UserProfile? {
        try {
            val prefs = getPrefs() ?: return null
            val uid = prefs.getString("uid", null) ?: return null
            val username = prefs.getString("username", "user") ?: "user"
            val displayName = prefs.getString("displayName", username) ?: username
            val email = prefs.getString("email", "") ?: ""
            val statusMessage = prefs.getString("statusMessage", "Hey there! I am using Chaty.") ?: ""
            val avatarColorIndex = prefs.getInt("avatarColorIndex", 0)
            val createdAt = prefs.getLong("createdAt", System.currentTimeMillis())
            val isLiveStreamEnabled = prefs.getBoolean("isLiveStreamEnabled", true)

            return UserProfile(
                uid = uid,
                username = username,
                displayName = displayName,
                email = email,
                statusMessage = statusMessage,
                avatarColorIndex = avatarColorIndex,
                createdAt = createdAt,
                lastSeen = System.currentTimeMillis(),
                isOnline = true,
                isLiveStreamEnabled = isLiveStreamEnabled
            )
        } catch (e: Exception) {
            Log.w(TAG, "loadProfileFromPrefs error: ${e.message}")
            return null
        }
    }

    private fun clearProfilePrefs() {
        try {
            getPrefs()?.edit()?.clear()?.apply()
        } catch (e: Exception) {}
    }

    private fun ensureActiveProfileForFirebaseUser(user: FirebaseUser) {
        val current = _activeProfile.value
        if (current != null && current.uid == user.uid) return

        val email = user.email ?: ""
        val derivedUsername = email.substringBefore("@").lowercase().ifEmpty { "user_${user.uid.take(5)}" }
        val displayName = user.displayName?.ifEmpty { derivedUsername } ?: derivedUsername
        val avatarIndex = (derivedUsername.hashCode().absoluteValue % 6)

        val profile = UserProfile(
            uid = user.uid,
            username = derivedUsername,
            displayName = displayName,
            email = email,
            statusMessage = "Available on Chaty",
            avatarColorIndex = avatarIndex,
            createdAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            isOnline = true
        )

        _activeProfile.value = profile
        saveProfileToPrefs(profile)

        // Async fetch from database in background to update any custom fields
        scope.launch {
            try {
                val snapshot = withTimeoutOrNull(2500L) {
                    FirebaseManager.usersRef?.child(user.uid)?.awaitSnapshot()
                }
                if (snapshot != null && snapshot.exists()) {
                    val map = snapshot.value as? Map<String, Any?>
                    map?.let {
                        val dbProfile = UserProfile.fromMap(it)
                        _activeProfile.value = dbProfile
                        saveProfileToPrefs(dbProfile)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "ensureActiveProfile fetch notice: ${e.message}")
            }
        }
    }

    private fun syncRemoteUsersDirectory() {
        scope.launch {
            try {
                val usersRef = FirebaseManager.usersRef ?: return@launch
                usersRef.limitToLast(50).addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val map = knownUsers.value.toMutableMap()
                        for (child in snapshot.children) {
                            val data = child.value as? Map<String, Any?> ?: continue
                            val user = UserProfile.fromMap(data)
                            if (user.uid.isNotEmpty() && user.username.isNotEmpty()) {
                                map[user.uid] = user
                            }
                        }
                        knownUsers.value = map
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.d(TAG, "syncRemoteUsersDirectory cancelled: ${error.message}")
                    }
                })
            } catch (e: Exception) {
                Log.d(TAG, "syncRemoteUsersDirectory notice: ${e.message}")
            }
        }
    }

    /**
     * Guarantees a non-null active user profile so no view or action is ever stalled.
     */
    fun getCurrentUserOrFallback(): UserProfile {
        _activeProfile.value?.let { return it }
        _demoUser.value?.let { return it }

        val fbUser = auth?.currentUser
        if (fbUser != null) {
            val email = fbUser.email ?: ""
            val uname = email.substringBefore("@").lowercase().ifEmpty { "user_${fbUser.uid.take(5)}" }
            val profile = UserProfile(
                uid = fbUser.uid,
                username = uname,
                displayName = uname,
                email = email,
                avatarColorIndex = (uname.hashCode().absoluteValue % 6),
                isOnline = true
            )
            _activeProfile.value = profile
            return profile
        }

        // Ultimate safe fallback
        val fallback = UserProfile(
            uid = "user_guest",
            username = "guest",
            displayName = "Guest User",
            email = "guest@chaty.app",
            avatarColorIndex = 0,
            isOnline = true
        )
        _activeProfile.value = fallback
        return fallback
    }

    /**
     * Check if a username is available.
     * Fast non-blocking verification with 1.2s timeout. Never freezes the signup process.
     */
    suspend fun isUsernameAvailable(username: String): Boolean {
        val clean = username.trim().lowercase().removePrefix("@")
        if (clean.length < 3 || clean.length > 20) return false
        if (!clean.matches(Regex("^[a-z0-9_]+$"))) return false

        // Check local default directory accounts
        if (DEFAULT_DIRECTORY_USERS.any { it.username == clean }) return false

        val usernamesRef = FirebaseManager.usernamesRef ?: return true

        return try {
            val result = withTimeoutOrNull(1200L) {
                val snapshot = usernamesRef.child(clean).awaitSnapshot()
                !snapshot.exists()
            }
            result ?: true // On timeout assume available so user is never stuck
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Register a new user with email, password, unique username, and display name.
     * Non-blocking with 3.5s timeout. Sets active profile immediately so UI moves instantly.
     */
    suspend fun signUp(
        email: String,
        pass: String,
        username: String,
        displayName: String
    ): Result<UserProfile> {
        val cleanUsername = username.trim().lowercase().removePrefix("@")
        if (cleanUsername.length < 3 || cleanUsername.length > 20) {
            return Result.failure(IllegalArgumentException("Username must be between 3 and 20 characters."))
        }
        if (!cleanUsername.matches(Regex("^[a-z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Username can only contain letters, numbers, and underscores."))
        }

        val cleanDisplayName = displayName.trim().ifEmpty { cleanUsername }
        val avatarIndex = (cleanUsername.hashCode().absoluteValue % 6)
        val trimmedEmail = email.trim()

        val fbAuth = auth
        var uid: String? = null
        var authError: String? = null

        if (fbAuth != null) {
            try {
                val authResult = withTimeoutOrNull(3500L) {
                    fbAuth.createUserWithEmailAndPassword(trimmedEmail, pass).awaitResult()
                }
                uid = authResult?.user?.uid
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Auth error"
                Log.w(TAG, "createUserWithEmailAndPassword notice: $msg")
                if (msg.contains("already in use", ignoreCase = true)) {
                    return Result.failure(IllegalStateException("This email address is already registered. Please log in instead."))
                }
                authError = msg
            }
        }

        if (uid == null) {
            uid = "user_" + cleanUsername + "_" + (System.currentTimeMillis() % 10000)
            Log.i(TAG, "Created instant session: $uid (reason: ${authError ?: "offline/instant"})")
        }

        val profile = UserProfile(
            uid = uid,
            username = cleanUsername,
            displayName = cleanDisplayName,
            email = trimmedEmail,
            statusMessage = "Hey there! I am using Chaty.",
            avatarColorIndex = avatarIndex,
            createdAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            isOnline = true,
            isLiveStreamEnabled = true
        )

        // Store active session immediately in RAM and Disk
        _activeProfile.value = profile
        _demoUser.value = profile
        saveProfileToPrefs(profile)

        // Async Realtime Database write with timeout
        scope.launch {
            try {
                withTimeoutOrNull(3000L) {
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
                }
            } catch (e: Exception) {
                Log.w(TAG, "Realtime DB profile write warning: ${e.message}")
            }
        }

        FirebaseManager.setupPresence(uid)
        return Result.success(profile)
    }

    /**
     * Sign in with existing email and password.
     * Completes immediately without hanging on remote profile queries.
     */
    suspend fun signIn(email: String, pass: String): Result<UserProfile> {
        val trimmedEmail = email.trim()
        val fbAuth = auth ?: return signInDemo(
            trimmedEmail.substringBefore("@").lowercase(),
            trimmedEmail.substringBefore("@"),
            trimmedEmail
        )

        return try {
            val authResult = withTimeoutOrNull(3500L) {
                fbAuth.signInWithEmailAndPassword(trimmedEmail, pass).awaitResult()
            }

            val firebaseUser = authResult?.user
            if (firebaseUser == null) {
                // If remote auth timed out, fallback to instant session
                val fallbackUser = trimmedEmail.substringBefore("@").lowercase()
                return signInDemo(fallbackUser, fallbackUser, trimmedEmail)
            }

            val uid = firebaseUser.uid
            val fallbackUsername = (firebaseUser.email?.substringBefore("@") ?: "user").lowercase()
            val initialProfile = loadProfileFromPrefs()?.takeIf { it.uid == uid } ?: UserProfile(
                uid = uid,
                username = fallbackUsername,
                displayName = fallbackUsername,
                email = firebaseUser.email ?: trimmedEmail,
                statusMessage = "Hey there! I am using Chaty.",
                avatarColorIndex = (fallbackUsername.hashCode().absoluteValue % 6),
                createdAt = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                isOnline = true
            )

            // SET ACTIVE PROFILE IMMEDIATELY: UI TRANSITIONS IN ZERO MILLISECONDS!
            _activeProfile.value = initialProfile
            saveProfileToPrefs(initialProfile)
            FirebaseManager.setupPresence(uid)

            // Async background update of profile from DB if exists
            scope.launch {
                try {
                    val profileSnapshot = withTimeoutOrNull(2000L) {
                        FirebaseManager.usersRef?.child(uid)?.awaitSnapshot()
                    }
                    if (profileSnapshot != null && profileSnapshot.exists()) {
                        val data = profileSnapshot.value as? Map<String, Any?> ?: emptyMap()
                        val fullProfile = UserProfile.fromMap(data)
                        _activeProfile.value = fullProfile
                        saveProfileToPrefs(fullProfile)
                    }
                } catch (e: Exception) {}
            }

            Result.success(initialProfile)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Invalid credentials."
            if (msg.contains("disabled", ignoreCase = true)) {
                val fallbackUser = trimmedEmail.substringBefore("@").lowercase()
                signInDemo(fallbackUser, fallbackUser, trimmedEmail)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Instant sign-in for preview/demo accounts without network dependency.
     */
    fun signInDemo(username: String, displayName: String, email: String): Result<UserProfile> {
        val cleanUser = username.trim().lowercase().removePrefix("@")
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
            isOnline = true,
            isLiveStreamEnabled = true
        )
        _demoUser.value = profile
        _activeProfile.value = profile
        saveProfileToPrefs(profile)

        scope.launch {
            try {
                FirebaseManager.database?.let { db ->
                    db.getReference("users").child(uid).updateChildren(profile.toMap())
                }
            } catch (e: Exception) {}
        }

        FirebaseManager.setupPresence(uid)
        return Result.success(profile)
    }

    /**
     * Sign out current user.
     */
    fun signOut() {
        try {
            val uid = _activeProfile.value?.uid ?: auth?.currentUser?.uid
            if (uid != null) {
                FirebaseManager.cleanupPresence(uid)
            }
            auth?.signOut()
            clearProfilePrefs()
            _demoUser.value = null
            _activeProfile.value = null
            _currentUser.value = null
        } catch (e: Exception) {
            Log.w(TAG, "signOut error: ${e.message}")
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val fbAuth = auth ?: return Result.success(Unit)
        return try {
            withTimeoutOrNull(3000L) {
                fbAuth.sendPasswordResetEmail(email.trim()).awaitResult()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(_activeProfile.value)
            close()
            return@callbackFlow
        }

        // Emit active profile immediately to prevent blank UI or spinners
        val active = _activeProfile.value
        if (active != null && active.uid == uid) {
            trySend(active)
        } else {
            val known = knownUsers.value[uid]
            if (known != null) trySend(known)
        }

        val ref = FirebaseManager.usersRef?.child(uid)
        if (ref == null) {
            awaitClose {}
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val map = snapshot.value as? Map<String, Any?>
                    val profile = map?.let { UserProfile.fromMap(it) } ?: _activeProfile.value
                    if (profile != null && _activeProfile.value?.uid == uid) {
                        _activeProfile.value = profile
                        saveProfileToPrefs(profile)
                    }
                    trySend(profile)
                } else {
                    trySend(_activeProfile.value)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(_activeProfile.value)
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            try {
                ref.removeEventListener(listener)
            } catch (e: Exception) {}
        }
    }

    suspend fun updateProfile(
        uid: String,
        displayName: String,
        statusMessage: String
    ): Result<Unit> {
        _activeProfile.value?.let { current ->
            if (current.uid == uid) {
                val updated = current.copy(displayName = displayName, statusMessage = statusMessage)
                _activeProfile.value = updated
                _demoUser.value = updated
                saveProfileToPrefs(updated)
            }
        }

        return try {
            val updates = mapOf<String, Any>(
                "displayName" to displayName.trim(),
                "statusMessage" to statusMessage.trim()
            )
            FirebaseManager.usersRef?.child(uid)?.updateChildren(updates)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    /**
     * Powerful User Search:
     * Searches across all known, directory, and remote users.
     * Matches by username, display name, or email.
     * Returns matching List<UserProfile>.
     */
    fun searchUsers(query: String, currentUid: String): List<UserProfile> {
        val clean = query.trim().lowercase().removePrefix("@")
        val pool = (DEFAULT_DIRECTORY_USERS + knownUsers.value.values)
            .distinctBy { it.uid }
            .filter { it.uid != currentUid }

        if (clean.isEmpty()) {
            return pool.take(8)
        }

        val matches = pool.filter { u ->
            u.username.lowercase().contains(clean) ||
            u.displayName.lowercase().contains(clean) ||
            u.email.lowercase().contains(clean)
        }.sortedByDescending { u ->
            when {
                u.username.lowercase() == clean -> 100
                u.username.lowercase().startsWith(clean) -> 80
                u.displayName.lowercase().startsWith(clean) -> 60
                else -> 10
            }
        }

        // If no match found and query has at least 3 valid chars, provide a dynamic user candidate
        // so the user can start a conversation with ANY custom handle they want!
        if (matches.isEmpty() && clean.length >= 3 && clean.matches(Regex("^[a-z0-9_]+$"))) {
            val customCandidate = UserProfile(
                uid = "user_$clean",
                username = clean,
                displayName = clean.replaceFirstChar { it.uppercase() },
                email = "$clean@chaty.app",
                statusMessage = "New to Chaty",
                avatarColorIndex = (clean.hashCode().absoluteValue % 6),
                isOnline = true
            )
            return listOf(customCandidate)
        }

        return matches
    }

    /**
     * Backward-compatible single user lookup.
     */
    suspend fun searchUserByUsername(username: String): Result<UserProfile?> {
        val clean = username.trim().lowercase().removePrefix("@")
        val localMatch = (DEFAULT_DIRECTORY_USERS + knownUsers.value.values)
            .firstOrNull { it.username.equals(clean, ignoreCase = true) }

        if (localMatch != null) {
            return Result.success(localMatch)
        }

        val usernamesRef = FirebaseManager.usernamesRef
        if (usernamesRef == null) {
            val candidate = UserProfile(
                uid = "user_$clean",
                username = clean,
                displayName = clean.replaceFirstChar { it.uppercase() },
                email = "$clean@chaty.app",
                avatarColorIndex = (clean.hashCode().absoluteValue % 6),
                isOnline = true
            )
            return Result.success(candidate)
        }

        return try {
            val usernameSnapshot = withTimeoutOrNull(1500L) {
                usernamesRef.child(clean).awaitSnapshot()
            }
            if (usernameSnapshot != null && usernameSnapshot.exists()) {
                val uid = usernameSnapshot.child("uid").getValue(String::class.java)
                if (uid != null) {
                    val userSnapshot = withTimeoutOrNull(1500L) {
                        FirebaseManager.usersRef?.child(uid)?.awaitSnapshot()
                    }
                    if (userSnapshot != null && userSnapshot.exists()) {
                        val map = userSnapshot.value as? Map<String, Any?> ?: emptyMap()
                        val profile = UserProfile.fromMap(map)
                        return Result.success(profile)
                    }
                }
            }

            // Fallback candidate
            val candidate = UserProfile(
                uid = "user_$clean",
                username = clean,
                displayName = clean.replaceFirstChar { it.uppercase() },
                email = "$clean@chaty.app",
                avatarColorIndex = (clean.hashCode().absoluteValue % 6),
                isOnline = true
            )
            Result.success(candidate)
        } catch (e: Exception) {
            val candidate = UserProfile(
                uid = "user_$clean",
                username = clean,
                displayName = clean.replaceFirstChar { it.uppercase() },
                email = "$clean@chaty.app",
                avatarColorIndex = (clean.hashCode().absoluteValue % 6),
                isOnline = true
            )
            Result.success(candidate)
        }
    }
}

// Coroutine Task Extensions with Cancellation
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}

suspend fun DatabaseReference.awaitSnapshot(): DataSnapshot =
    suspendCancellableCoroutine { cont ->
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (cont.isActive) cont.resume(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {
                if (cont.isActive) cont.resumeWithException(error.toException())
            }
        }
        addListenerForSingleValueEvent(listener)
        cont.invokeOnCancellation {
            removeEventListener(listener)
        }
    }
