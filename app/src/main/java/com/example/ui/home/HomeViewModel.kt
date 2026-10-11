package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.ChatRepository
import com.example.data.FirebaseManager
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
    val isLoadingConversations: Boolean = true,
    // New Chat Dialog
    val isNewChatDialogOpen: Boolean = false,
    val newChatQuery: String = "",
    val isSearchingUser: Boolean = false,
    val searchResultUser: UserProfile? = null,
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
    private val authRepository: AuthRepository = AuthRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var profileJob: Job? = null
    private var conversationsJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { fbUser ->
                if (fbUser != null) {
                    val uid = fbUser.uid
                    startObservingUser(uid)
                    startObservingConversations(uid)
                } else {
                    _uiState.update {
                        it.copy(
                            currentUserProfile = null,
                            conversations = emptyList(),
                            isLoadingConversations = false
                        )
                    }
                }
            }
        }
    }

    private fun startObservingUser(uid: String) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            authRepository.observeUserProfile(uid).collectLatest { profile ->
                _uiState.update {
                    it.copy(
                        currentUserProfile = profile,
                        editDisplayName = profile?.displayName ?: "",
                        editStatusMessage = profile?.statusMessage ?: ""
                    )
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
        _uiState.update {
            it.copy(
                isNewChatDialogOpen = true,
                newChatQuery = "",
                searchResultUser = null,
                newChatError = null
            )
        }
    }

    fun closeNewChatDialog() {
        _uiState.update {
            it.copy(
                isNewChatDialogOpen = false,
                newChatQuery = "",
                searchResultUser = null,
                newChatError = null
            )
        }
    }

    fun onNewChatQueryChange(query: String) {
        _uiState.update { it.copy(newChatQuery = query, newChatError = null) }
        searchJob?.cancel()
        val clean = query.trim().lowercase()
        if (clean.length < 3) {
            _uiState.update { it.copy(searchResultUser = null, isSearchingUser = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingUser = true) }
            delay(350) // Debounce
            val result = authRepository.searchUserByUsername(clean)
            result.fold(
                onSuccess = { foundUser ->
                    val isSelf = foundUser?.uid == _uiState.value.currentUserProfile?.uid
                    _uiState.update {
                        it.copy(
                            isSearchingUser = false,
                            searchResultUser = if (isSelf) null else foundUser,
                            newChatError = when {
                                isSelf -> "You cannot start a direct conversation with yourself."
                                foundUser == null -> "No user found with username '@$clean'."
                                else -> null
                            }
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSearchingUser = false,
                            newChatError = error.localizedMessage ?: "Failed to search user."
                        )
                    }
                }
            )
        }
    }

    fun startChatWithFoundUser(
        onSuccess: (conversationId: String, otherProfile: UserProfile) -> Unit
    ) {
        val targetUser = _uiState.value.searchResultUser ?: return
        val currentUser = _uiState.value.currentUserProfile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingChat = true, newChatError = null) }
            val result = chatRepository.getOrCreateDirectChat(currentUser, targetUser)
            result.fold(
                onSuccess = { convId ->
                    _uiState.update { it.copy(isCreatingChat = false, isNewChatDialogOpen = false) }
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
        val profile = _uiState.value.currentUserProfile
        _uiState.update {
            it.copy(
                isProfileDialogOpen = true,
                editDisplayName = profile?.displayName ?: "",
                editStatusMessage = profile?.statusMessage ?: ""
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
        val profile = _uiState.value.currentUserProfile ?: return
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
        onSignedOut()
    }
}
