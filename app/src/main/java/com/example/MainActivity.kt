package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AuthRepository
import com.example.data.FirebaseManager
import com.example.model.UserProfile
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.components.ChatyBrandLogo
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.theme.ChatyTheme
import com.example.ui.theme.Cyan400

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatyApp()
        }
    }
}

data class ActiveConversation(
    val conversationId: String,
    val otherUid: String,
    val otherUsername: String,
    val otherDisplayName: String,
    val otherAvatarIndex: Int
)

@Composable
fun ChatyApp() {
    val authViewModel: AuthViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    val homeUiState by homeViewModel.uiState.collectAsState()
    val authUiState by authViewModel.uiState.collectAsState()

    val activeProfile by AuthRepository.instance.activeProfile.collectAsState()
    var activeConversation by remember { mutableStateOf<ActiveConversation?>(null) }
    var currentUid by remember { mutableStateOf(FirebaseManager.auth?.currentUser?.uid) }

    LaunchedEffect(Unit) {
        currentUid = FirebaseManager.auth?.currentUser?.uid ?: AuthRepository.instance.activeProfile.value?.uid
    }

    val isAuthenticated = currentUid != null || authUiState.authenticatedUser != null || activeProfile != null
    val effectiveUid = currentUid ?: authUiState.authenticatedUser?.uid ?: activeProfile?.uid ?: ""

    ChatyTheme(darkTheme = homeUiState.isDarkTheme) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_root_surface"),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 700.dp

                if (!isAuthenticated) {
                    // Auth Screen
                    AuthScreen(
                        viewModel = authViewModel,
                        onAuthSuccess = { user ->
                            currentUid = user.uid
                        }
                    )
                } else {
                    // Authenticated Experience

                    if (isWideScreen) {
                        // Desktop / Tablet Two-Panel Layout
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Panel: Conversations List
                            Box(
                                modifier = Modifier
                                    .width(360.dp)
                                    .fillMaxHeight()
                            ) {
                                HomeScreen(
                                    viewModel = homeViewModel,
                                    selectedConversationId = activeConversation?.conversationId,
                                    onConversationSelected = { convId, otherUid, otherUsername, otherDisplayName, otherAvatarIndex ->
                                        activeConversation = ActiveConversation(
                                            conversationId = convId,
                                            otherUid = otherUid,
                                            otherUsername = otherUsername,
                                            otherDisplayName = otherDisplayName,
                                            otherAvatarIndex = otherAvatarIndex
                                        )
                                        chatViewModel.initChat(
                                            conversationId = convId,
                                            otherUid = otherUid,
                                            otherUsername = otherUsername,
                                            otherDisplayName = otherDisplayName,
                                            otherAvatarIndex = otherAvatarIndex
                                        )
                                    },
                                    onSignOut = {
                                        activeConversation = null
                                        currentUid = null
                                        authViewModel.resetAuthState()
                                    }
                                )
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            )

                            // Right Panel: Active Chat or Empty Placeholder
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                if (activeConversation != null) {
                                    ChatScreen(
                                        viewModel = chatViewModel,
                                        currentUserId = effectiveUid,
                                        isTwoPane = true,
                                        onBack = {
                                            activeConversation = null
                                        }
                                    )
                                } else {
                                    // Wide screen empty chat placeholder
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                    ) {
                                        ChatyBrandLogo(size = 56.dp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Welcome to Chaty",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Select a conversation from the sidebar or click + to start a new chat.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Phone Single-Panel Flow with Smooth Transitions
                        Crossfade(
                            targetState = activeConversation,
                            label = "screen_transition"
                        ) { activeChat ->
                            if (activeChat != null) {
                                ChatScreen(
                                    viewModel = chatViewModel,
                                    currentUserId = effectiveUid,
                                    isTwoPane = false,
                                    onBack = {
                                        activeConversation = null
                                    }
                                )
                            } else {
                                HomeScreen(
                                    viewModel = homeViewModel,
                                    selectedConversationId = null,
                                    onConversationSelected = { convId, otherUid, otherUsername, otherDisplayName, otherAvatarIndex ->
                                        activeConversation = ActiveConversation(
                                            conversationId = convId,
                                            otherUid = otherUid,
                                            otherUsername = otherUsername,
                                            otherDisplayName = otherDisplayName,
                                            otherAvatarIndex = otherAvatarIndex
                                        )
                                        chatViewModel.initChat(
                                            conversationId = convId,
                                            otherUid = otherUid,
                                            otherUsername = otherUsername,
                                            otherDisplayName = otherDisplayName,
                                            otherAvatarIndex = otherAvatarIndex
                                        )
                                    },
                                    onSignOut = {
                                        activeConversation = null
                                        currentUid = null
                                        authViewModel.resetAuthState()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
