package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isSignUpMode: Boolean = false,
    val email: String = "",
    val pass: String = "",
    val confirmPass: String = "",
    val username: String = "",
    val displayName: String = "",
    val isCheckingUsername: Boolean = false,
    val isUsernameAvailable: Boolean? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val authenticatedUser: UserProfile? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var usernameCheckJob: Job? = null

    fun toggleMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(pass: String) {
        _uiState.update { it.copy(pass = pass, errorMessage = null) }
    }

    fun onConfirmPasswordChange(confirmPass: String) {
        _uiState.update { it.copy(confirmPass = confirmPass, errorMessage = null) }
    }

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, errorMessage = null) }
        checkUsernameAvailability(username)
    }

    fun onDisplayNameChange(displayName: String) {
        _uiState.update { it.copy(displayName = displayName, errorMessage = null) }
    }

    private fun checkUsernameAvailability(username: String) {
        usernameCheckJob?.cancel()
        val clean = username.trim().lowercase()
        if (clean.length < 3) {
            _uiState.update { it.copy(isCheckingUsername = false, isUsernameAvailable = null) }
            return
        }

        usernameCheckJob = viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUsername = true) }
            delay(400) // Debounce
            val available = authRepository.isUsernameAvailable(clean)
            _uiState.update {
                it.copy(
                    isCheckingUsername = false,
                    isUsernameAvailable = available
                )
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.pass.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter both email and password.") }
            return
        }

        if (state.isSignUpMode) {
            if (state.username.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Please choose a username.") }
                return
            }
            if (state.username.trim().length < 3) {
                _uiState.update { it.copy(errorMessage = "Username must be at least 3 characters.") }
                return
            }
            if (state.pass != state.confirmPass) {
                _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
                return
            }
            if (state.pass.length < 6) {
                _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
                return
            }
            if (state.isUsernameAvailable == false) {
                _uiState.update { it.copy(errorMessage = "Username is already taken. Please choose another.") }
                return
            }

            performSignUp()
        } else {
            performSignIn()
        }
    }

    fun quickDemoSignIn(username: String, displayName: String, email: String) {
        val result = authRepository.signInDemo(username, displayName, email)
        result.fold(
            onSuccess = { user ->
                _uiState.update {
                    it.copy(
                        authenticatedUser = user,
                        successMessage = "Signed in as ${user.displayName}."
                    )
                }
            },
            onFailure = { error ->
                _uiState.update { it.copy(errorMessage = error.localizedMessage) }
            }
        )
    }

    private fun performSignUp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            val result = authRepository.signUp(
                email = state.email,
                pass = state.pass,
                username = state.username,
                displayName = state.displayName
            )

            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authenticatedUser = user,
                            successMessage = "Welcome to Chaty, ${user.displayName}!"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Sign up failed. Please try again."
                        )
                    }
                }
            )
        }
    }

    private fun performSignIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            val result = authRepository.signIn(
                email = state.email,
                pass = state.pass
            )

            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authenticatedUser = user
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Invalid credentials. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your email address to receive a password reset link.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.sendPasswordReset(email)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Password reset email sent. Check your inbox."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Could not send reset email."
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun resetAuthState() {
        _uiState.update { AuthUiState() }
    }
}
