package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.ChatRepository
import com.example.model.ChatMessage
import com.example.model.LiveDraftState
import com.example.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: String = "",
    val otherUid: String = "",
    val otherUsername: String = "",
    val otherDisplayName: String = "",
    val otherAvatarIndex: Int = 0,
    val otherIsOnline: Boolean = false,
    val otherLastSeen: Long = 0L,
    // Realtime Ghost Stream: shows actual text the other user is typing live!
    val otherLiveDraft: LiveDraftState? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val replyingToMessage: ChatMessage? = null,
    val editingMessage: ChatMessage? = null,
    val isSending: Boolean = false,
    val error: String? = null,
    // Reactions: messageId -> (uid -> emoji)
    val reactions: Map<String, Map<String, String>> = emptyMap(),
    // Pinned Message
    val pinnedMessageId: String? = null,
    val pinnedMessageText: String? = null,
    // In-Chat Search
    val isSearchOpen: Boolean = false,
    val inChatSearchQuery: String = "",
    // Quick Reaction Picker
    val reactionPickerMessage: ChatMessage? = null
) {
    val filteredMessages: List<ChatMessage>
        get() {
            if (!isSearchOpen || inChatSearchQuery.isBlank()) return messages
            val q = inChatSearchQuery.trim().lowercase()
            return messages.filter { it.text.lowercase().contains(q) }
        }
}

class ChatViewModel(
    private val authRepository: AuthRepository = AuthRepository.instance,
    private val chatRepository: ChatRepository = ChatRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentUser: UserProfile? = null
    private var messagesJob: Job? = null
    private var liveDraftJob: Job? = null
    private var reactionsJob: Job? = null
    private var pinnedJob: Job? = null
    private var presenceJob: Job? = null
    private var clearDraftJob: Job? = null

    init {
        currentUser = authRepository.activeProfile.value ?: authRepository.demoUser.value

        viewModelScope.launch {
            authRepository.activeProfile.collectLatest { profile ->
                if (profile != null) currentUser = profile
            }
        }
        viewModelScope.launch {
            authRepository.demoUser.collectLatest { demo ->
                if (demo != null) currentUser = demo
            }
        }
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { fbUser ->
                if (fbUser != null && currentUser == null) {
                    authRepository.observeUserProfile(fbUser.uid).collectLatest { profile ->
                        if (profile != null) currentUser = profile
                    }
                }
            }
        }
    }

    fun initChat(
        conversationId: String,
        otherUid: String,
        otherUsername: String,
        otherDisplayName: String,
        otherAvatarIndex: Int
    ) {
        _uiState.update {
            it.copy(
                conversationId = conversationId,
                otherUid = otherUid,
                otherUsername = otherUsername,
                otherDisplayName = otherDisplayName,
                otherAvatarIndex = otherAvatarIndex,
                inputText = "",
                replyingToMessage = null,
                editingMessage = null,
                otherLiveDraft = null,
                isSearchOpen = false,
                inChatSearchQuery = "",
                reactionPickerMessage = null
            )
        }

        val uid = currentUser?.uid ?: authRepository.activeProfile.value?.uid ?: ""
        if (uid.isNotEmpty()) {
            chatRepository.markConversationAsRead(conversationId, uid)
        }

        // Observe messages
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collectLatest { list ->
                _uiState.update { it.copy(messages = list) }
                val currentUid = currentUser?.uid ?: authRepository.activeProfile.value?.uid ?: ""
                if (currentUid.isNotEmpty()) {
                    chatRepository.markConversationAsRead(conversationId, currentUid)
                }
            }
        }

        // Observe Real-Time Keystroke Ghost Stream of the other user!
        liveDraftJob?.cancel()
        liveDraftJob = viewModelScope.launch {
            val currentUid = currentUser?.uid ?: authRepository.activeProfile.value?.uid ?: ""
            chatRepository.observeLiveDraft(conversationId, currentUid).collectLatest { draft ->
                _uiState.update { it.copy(otherLiveDraft = draft) }
            }
        }

        // Observe Reactions
        reactionsJob?.cancel()
        reactionsJob = viewModelScope.launch {
            chatRepository.observeReactions(conversationId).collectLatest { rxMap ->
                _uiState.update { it.copy(reactions = rxMap) }
            }
        }

        // Observe Pinned Message
        pinnedJob?.cancel()
        pinnedJob = viewModelScope.launch {
            chatRepository.observePinnedMessage(conversationId).collectLatest { (pId, pText) ->
                _uiState.update {
                    it.copy(
                        pinnedMessageId = pId,
                        pinnedMessageText = pText
                    )
                }
            }
        }

        // Observe Presence
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            chatRepository.observePresence(otherUid).collectLatest { (isOnline, lastSeen) ->
                _uiState.update {
                    it.copy(
                        otherIsOnline = isOnline,
                        otherLastSeen = lastSeen
                    )
                }
            }
        }
    }

    /**
     * Broadcasts user's keystrokes in real time to the other party!
     */
    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }

        val convId = _uiState.value.conversationId
        val user = currentUser ?: authRepository.activeProfile.value ?: return
        if (convId.isNotEmpty()) {
            chatRepository.setLiveDraft(convId, user, text)

            // Clear draft after 5 seconds of idle
            clearDraftJob?.cancel()
            clearDraftJob = viewModelScope.launch {
                delay(5000)
                chatRepository.clearLiveDraft(convId, user.uid)
            }
        }
    }

    fun applyFormatting(prefix: String, suffix: String) {
        val current = _uiState.value.inputText
        val updated = "$current$prefix$suffix"
        onInputTextChanged(updated)
    }

    fun setReplyTo(message: ChatMessage?) {
        _uiState.update { it.copy(replyingToMessage = message, editingMessage = null) }
    }

    fun startEditing(message: ChatMessage) {
        _uiState.update {
            it.copy(
                editingMessage = message,
                inputText = message.text,
                replyingToMessage = null
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingMessage = null, inputText = "") }
    }

    fun openReactionPicker(message: ChatMessage) {
        _uiState.update { it.copy(reactionPickerMessage = message) }
    }

    fun closeReactionPicker() {
        _uiState.update { it.copy(reactionPickerMessage = null) }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        val convId = _uiState.value.conversationId
        val uid = currentUser?.uid ?: authRepository.activeProfile.value?.uid ?: return
        chatRepository.toggleReaction(convId, message.id, uid, emoji)
        _uiState.update { it.copy(reactionPickerMessage = null) }
    }

    fun togglePinMessage(message: ChatMessage) {
        val convId = _uiState.value.conversationId
        val isCurrentlyPinned = _uiState.value.pinnedMessageId == message.id
        if (isCurrentlyPinned) {
            chatRepository.pinMessage(convId, null, null)
        } else {
            chatRepository.pinMessage(convId, message.id, message.text)
        }
    }

    fun toggleSearch() {
        _uiState.update {
            it.copy(
                isSearchOpen = !it.isSearchOpen,
                inChatSearchQuery = if (it.isSearchOpen) "" else it.inChatSearchQuery
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(inChatSearchQuery = query) }
    }

    fun sendQuickResponse(text: String) {
        onInputTextChanged(text)
        sendMessage()
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty()) return

        val user = currentUser ?: authRepository.activeProfile.value ?: return
        val convId = state.conversationId
        if (convId.isEmpty()) return

        // Clear live draft immediately
        clearDraftJob?.cancel()
        chatRepository.clearLiveDraft(convId, user.uid)

        if (state.editingMessage != null) {
            val messageToEdit = state.editingMessage
            viewModelScope.launch {
                val result = chatRepository.editMessage(
                    conversationId = convId,
                    messageId = messageToEdit.id,
                    newText = text,
                    currentUid = user.uid
                )
                if (result.isSuccess) {
                    _uiState.update { it.copy(editingMessage = null, inputText = "") }
                } else {
                    _uiState.update { it.copy(error = result.exceptionOrNull()?.localizedMessage) }
                }
            }
            return
        }

        // Send normal message
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, inputText = "", error = null) }
            val replyTo = state.replyingToMessage

            val result = chatRepository.sendMessage(
                conversationId = convId,
                currentProfile = user,
                otherUid = state.otherUid,
                otherUsername = state.otherUsername,
                otherDisplayName = state.otherDisplayName,
                otherAvatarIndex = state.otherAvatarIndex,
                text = text,
                replyTo = replyTo
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            replyingToMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = text,
                            error = error.localizedMessage ?: "Failed to send message."
                        )
                    }
                }
            )
        }
    }

    fun deleteMessage(message: ChatMessage) {
        val user = currentUser ?: authRepository.activeProfile.value ?: return
        val convId = _uiState.value.conversationId
        if (convId.isEmpty()) return

        viewModelScope.launch {
            chatRepository.deleteMessage(
                conversationId = convId,
                messageId = message.id,
                currentUid = user.uid
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        val convId = _uiState.value.conversationId
        val uid = currentUser?.uid ?: authRepository.activeProfile.value?.uid
        if (convId.isNotEmpty() && uid != null) {
            chatRepository.clearLiveDraft(convId, uid)
        }
    }
}
