package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.ChatRepository
import com.example.model.ConversationItem
import com.example.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentUserProfile: UserProfile? = null,
    val conversations: List<ConversationItem> = emptyList(),
    val searchQuery: String = "",
    val isLoadingConversations: Boolean = false, // Never block UI with indefinite spinners
    // New Chat Dialog & User Search
    val isNewChatDialogOpen: Boolean = false,
    val newChatQuery: String = "",
    val isSearchingUser: Boolean = false,
    val searchResults: List<UserProfile> = emptyList(),
    val selectedUser: UserProfile? = null,
    val newChatError: String? = null,
    val isCreatingChat: Boolean = false,
    // Profile Dialog
    val isProfileDialogOpen: Boolean = false,
    val editDisplayName: String = "",
    val editStatusMessage: String = "",
    val isSavingProfile: Boolean = false,
    // Dark Theme preference
    val isDarkTheme: Boolean = true
) {
    val filteredConversations: List<ConversationItem>
        get() {
            if (searchQuery.isBlank()) return conversations
            val query = searchQuery.trim().lowercase()
            return conversations.filter {
                it.otherDisplayName.lowercase().contains(query) ||
                it.otherUsername.lowercase().contains(query) ||
                it.lastMessage.lowercase().contains(query)
            }
        }
}

class HomeViewModel(
    private val authRepository: AuthRepository = AuthRepository.instance,
    private val chatRepository: ChatRepository = ChatRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var profileJob: Job? = null
    private var conversationsJob: Job? = null
    private var searchJob: Job? = null

    init {
        // Step 1: Immediately pick up active profile if available
        authRepository.activeProfile.value?.let { profile ->
            _uiState.update {
                it.copy(
                    currentUserProfile = profile,
                    editDisplayName = profile.displayName,
                    editStatusMessage = profile.statusMessage
                )
            }
            startObservingConversations(profile.uid)
        }

        // Step 2: Observe active profile stream
        viewModelScope.launch {
            authRepository.activeProfile.collectLatest { profile ->
                if (profile != null) {
                    _uiState.update {
                        it.copy(
                            currentUserProfile = profile,
                            editDisplayName = profile.displayName,
                            editStatusMessage = profile.statusMessage
                        )
                    }
                    startObservingUser(profile.uid)
                    startObservingConversations(profile.uid)
                }
            }
        }

        // Step 3: Fallback listener for auth state
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { fbUser ->
                if (fbUser != null) {
                    startObservingConversations(fbUser.uid)
                }
            }
        }

        // Step 4: Loading timeout safety - force loading to false after 400ms so user is NEVER trapped
        viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(isLoadingConversations = false) }
        }
    }

    private fun startObservingUser(uid: String) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            authRepository.observeUserProfile(uid).collectLatest { profile ->
                if (profile != null) {
                    _uiState.update {
                        it.copy(
                            currentUserProfile = profile,
                            editDisplayName = profile.displayName,
                            editStatusMessage = profile.statusMessage
                        )
                    }
                }
            }
        }
    }

    private fun startObservingConversations(uid: String) {
        conversationsJob?.cancel()
        conversationsJob = viewModelScope.launch {
            chatRepository.observeConversations(uid).collectLatest { list ->
                _uiState.update {
                    it.copy(
                        conversations = list,
                        isLoadingConversations = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openNewChatDialog() {
        val currentUid = _uiState.value.currentUserProfile?.uid ?: authRepository.getCurrentUserOrFallback().uid
        val initialSuggested = authRepository.searchUsers("", currentUid)
        _uiState.update {
            it.copy(
                isNewChatDialogOpen = true,
                newChatQuery = "",
                searchResults = initialSuggested,
                selectedUser = null,
                newChatError = null,
                isSearchingUser = false,
                isCreatingChat = false
            )
        }
    }

    fun closeNewChatDialog() {
        _uiState.update {
            it.copy(
                isNewChatDialogOpen = false,
                newChatQuery = "",
                searchResults = emptyList(),
                selectedUser = null,
                newChatError = null,
                isCreatingChat = false
            )
        }
    }

    /**
     * Instant and powerful multi-user search.
     * Updates matching users list without latency.
     */
    fun onNewChatQueryChange(query: String) {
        _uiState.update { it.copy(newChatQuery = query, newChatError = null) }
        searchJob?.cancel()

        val currentUid = _uiState.value.currentUserProfile?.uid ?: authRepository.getCurrentUserOrFallback().uid

        // Instant local search (0ms)
        val instantMatches = authRepository.searchUsers(query, currentUid)
        _uiState.update { it.copy(searchResults = instantMatches, isSearchingUser = false) }

        // If query is non-empty, run short debounced remote pass to index any newly registered users
        if (query.trim().isNotEmpty()) {
            searchJob = viewModelScope.launch {
                delay(200)
                val refreshed = authRepository.searchUsers(query, currentUid)
                _uiState.update { it.copy(searchResults = refreshed) }
            }
        }
    }

    fun selectUserToChat(user: UserProfile) {
        _uiState.update { it.copy(selectedUser = user) }
    }

    /**
     * Start conversation with target user with zero-latency handoff.
     */
    fun startChatWithTargetUser(
        targetUser: UserProfile,
        onSuccess: (conversationId: String, otherProfile: UserProfile) -> Unit
    ) {
        val currentUser = _uiState.value.currentUserProfile ?: authRepository.getCurrentUserOrFallback()

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingChat = true, newChatError = null) }
            val result = chatRepository.getOrCreateDirectChat(currentUser, targetUser)
            result.fold(
                onSuccess = { convId ->
                    _uiState.update {
                        it.copy(
                            isCreatingChat = false,
                            isNewChatDialogOpen = false,
                            selectedUser = null
                        )
                    }
                    onSuccess(convId, targetUser)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCreatingChat = false,
                            newChatError = error.localizedMessage ?: "Could not start conversation."
                        )
                    }
                }
            )
        }
    }

    fun openProfileDialog() {
        val profile = _uiState.value.currentUserProfile ?: authRepository.getCurrentUserOrFallback()
        _uiState.update {
            it.copy(
                isProfileDialogOpen = true,
                editDisplayName = profile.displayName,
                editStatusMessage = profile.statusMessage
            )
        }
    }

    fun closeProfileDialog() {
        _uiState.update { it.copy(isProfileDialogOpen = false) }
    }

    fun onEditDisplayNameChange(name: String) {
        _uiState.update { it.copy(editDisplayName = name) }
    }

    fun onEditStatusMessageChange(status: String) {
        _uiState.update { it.copy(editStatusMessage = status) }
    }

    fun saveProfile() {
        val profile = _uiState.value.currentUserProfile ?: authRepository.getCurrentUserOrFallback()
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true) }
            authRepository.updateProfile(
                uid = profile.uid,
                displayName = _uiState.value.editDisplayName,
                statusMessage = _uiState.value.editStatusMessage
            )
            _uiState.update { it.copy(isSavingProfile = false, isProfileDialogOpen = false) }
        }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun signOut(onSignedOut: () -> Unit) {
        authRepository.signOut()
        _uiState.update { HomeUiState() }
        onSignedOut()
    }
}
