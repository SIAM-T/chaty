package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.ChatRepository
import com.example.model.ChatMessage
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
    val isOtherTyping: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val replyingToMessage: ChatMessage? = null,
    val editingMessage: ChatMessage? = null,
    val isSending: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentUser: UserProfile? = null
    private var messagesJob: Job? = null
    private var typingJob: Job? = null
    private var presenceJob: Job? = null
    private var stopTypingJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collectLatest { fbUser ->
                if (fbUser != null) {
                    authRepository.observeUserProfile(fbUser.uid).collectLatest { profile ->
                        currentUser = profile
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
                editingMessage = null
            )
        }

        // Mark as read immediately upon entering
        currentUser?.uid?.let { uid ->
            chatRepository.markConversationAsRead(conversationId, uid)
        }

        // Observe messages
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collectLatest { list ->
                _uiState.update { it.copy(messages = list) }
                // Also mark as read when new messages arrive while in chat
                currentUser?.uid?.let { uid ->
                    chatRepository.markConversationAsRead(conversationId, uid)
                }
            }
        }

        // Observe typing
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            currentUser?.uid?.let { uid ->
                chatRepository.observeTyping(conversationId, uid).collectLatest { isTyping ->
                    _uiState.update { it.copy(isOtherTyping = isTyping) }
                }
            }
        }

        // Observe presence
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

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }

        // Send typing indicator
        val convId = _uiState.value.conversationId
        val uid = currentUser?.uid ?: return
        if (convId.isNotEmpty()) {
            chatRepository.setTyping(convId, uid, true)
            stopTypingJob?.cancel()
            stopTypingJob = viewModelScope.launch {
                delay(2500) // stop typing after 2.5 seconds of inactivity
                chatRepository.setTyping(convId, uid, false)
            }
        }
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

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty()) return

        val user = currentUser ?: return
        val convId = state.conversationId
        if (convId.isEmpty()) return

        // Stop typing immediately
        stopTypingJob?.cancel()
        chatRepository.setTyping(convId, user.uid, false)

        if (state.editingMessage != null) {
            // Save Edit
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

        // Normal Send
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
                            inputText = text, // restore text on failure
                            error = error.localizedMessage ?: "Failed to send message. Please retry."
                        )
                    }
                }
            )
        }
    }

    fun deleteMessage(message: ChatMessage) {
        val user = currentUser ?: return
        val convId = _uiState.value.conversationId
        if (convId.isEmpty()) return

        viewModelScope.launch {
            val result = chatRepository.deleteMessage(
                conversationId = convId,
                messageId = message.id,
                currentUid = user.uid
            )
            if (result.isFailure) {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.localizedMessage) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure typing indicator is cleared when leaving chat
        val convId = _uiState.value.conversationId
        val uid = currentUser?.uid
        if (convId.isNotEmpty() && uid != null) {
            chatRepository.setTyping(convId, uid, false)
        }
    }
}
