package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConversationItem
import com.example.model.UserProfile
import kotlin.math.absoluteValue
import com.example.ui.components.ChatyBrandLogo
import com.example.ui.components.UserAvatar
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onConversationSelected: (conversationId: String, otherUid: String, otherUsername: String, otherDisplayName: String, otherAvatarIndex: Int) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    selectedConversationId: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearchExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChatyBrandLogo(size = 32.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Chaty",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            uiState.currentUserProfile?.let { user ->
                                Text(
                                    text = "@${user.username}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Cyan400
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Search Toggle
                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Theme Toggle
                    IconButton(
                        onClick = viewModel::toggleTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // User Profile Button
                    IconButton(
                        onClick = viewModel::openProfileDialog,
                        modifier = Modifier.testTag("profile_button")
                    ) {
                        val profile = uiState.currentUserProfile
                        UserAvatar(
                            name = profile?.displayName?.ifEmpty { profile.username } ?: "Me",
                            avatarIndex = profile?.avatarColorIndex ?: 0,
                            size = 32.dp,
                            showOnlineIndicator = true,
                            isOnline = true
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openNewChatDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Conversation")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar (Collapsible / Expandable)
            AnimatedVisibility(visible = isSearchExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        placeholder = { Text("Filter chats or messages...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search icon")
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("conversation_search_input")
                    )
                }
            }

            // Conversations List / Loading / Empty State
            when {
                uiState.isLoadingConversations -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                uiState.filteredConversations.isEmpty() -> {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "No chats",
                                modifier = Modifier.size(44.dp),
                                tint = Cyan400
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = if (uiState.searchQuery.isNotEmpty()) "No results found" else "No conversations yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (uiState.searchQuery.isNotEmpty()) {
                                "No chats match '${uiState.searchQuery}'."
                            } else {
                                "Connect instantly! Tap the + button to search someone by their @username and start chatting."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        if (uiState.searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = viewModel::openNewChatDialog,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start First Chat")
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.filteredConversations, key = { it.id }) { item ->
                            ConversationCard(
                                item = item,
                                isSelected = item.id == selectedConversationId,
                                onClick = {
                                    onConversationSelected(
                                        item.id,
                                        item.otherUid,
                                        item.otherUsername,
                                        item.otherDisplayName,
                                        item.otherAvatarIndex
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // New Chat Dialog
    if (uiState.isNewChatDialogOpen) {
        NewChatDialog(
            query = uiState.newChatQuery,
            onQueryChange = viewModel::onNewChatQueryChange,
            isSearching = uiState.isSearchingUser,
            searchResults = uiState.searchResults,
            error = uiState.newChatError,
            isCreating = uiState.isCreatingChat,
            onDismiss = viewModel::closeNewChatDialog,
            onSelectUser = { targetUser ->
                viewModel.startChatWithTargetUser(targetUser) { convId, otherUser ->
                    onConversationSelected(
                        convId,
                        otherUser.uid,
                        otherUser.username,
                        otherUser.displayName,
                        otherUser.avatarColorIndex
                    )
                }
            }
        )
    }

    // Profile & Settings Dialog
    if (uiState.isProfileDialogOpen) {
        ProfileDialog(
            user = uiState.currentUserProfile,
            editDisplayName = uiState.editDisplayName,
            editStatusMessage = uiState.editStatusMessage,
            onDisplayNameChange = viewModel::onEditDisplayNameChange,
            onStatusMessageChange = viewModel::onEditStatusMessageChange,
            isSaving = uiState.isSavingProfile,
            onSave = viewModel::saveProfile,
            onDismiss = viewModel::closeProfileDialog,
            onSignOut = {
                viewModel.closeProfileDialog()
                viewModel.signOut(onSignOut)
            }
        )
    }
}

@Composable
fun ConversationCard(
    item: ConversationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        color = backgroundColor,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("conversation_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            UserAvatar(
                name = item.otherDisplayName.ifEmpty { item.otherUsername },
                avatarIndex = item.otherAvatarIndex,
                size = 52.dp,
                showOnlineIndicator = true,
                isOnline = item.otherIsOnline
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.otherDisplayName.ifEmpty { item.otherUsername },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (item.lastMessageTimestamp > 0) {
                        Text(
                            text = formatTimestamp(item.lastMessageTimestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.unreadCount > 0) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!item.activeLiveDraft.isNullOrEmpty()) {
                        Text(
                            text = "⚡ \"${item.activeLiveDraft}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cyan400,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = item.lastMessage.ifEmpty { "Started conversation" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (item.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Unread Count Badge
                    if (item.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Cyan500)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewChatDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<UserProfile>,
    error: String?,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onSelectUser: (UserProfile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Start a Conversation",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 460.dp)
            ) {
                Text(
                    text = "Search by username (@alice), display name, or email:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search or type @username...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Cyan400)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_chat_username_input")
                )

                // Error Banner
                error?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (query.isBlank()) "Suggested Contacts" else "Search Results (${searchResults.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Cyan400
                    )
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Cyan400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Results / Contacts List
                if (searchResults.isEmpty()) {
                    val cleanHandle = query.trim().lowercase().removePrefix("@")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No user found matching \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (cleanHandle.length >= 3) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val custom = UserProfile(
                                        uid = "user_$cleanHandle",
                                        username = cleanHandle,
                                        displayName = cleanHandle.replaceFirstChar { it.uppercase() },
                                        email = "$cleanHandle@chaty.app",
                                        avatarColorIndex = (cleanHandle.hashCode().absoluteValue % 6),
                                        isOnline = true
                                    )
                                    onSelectUser(custom)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Chat with @$cleanHandle")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults, key = { it.uid }) { user ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isCreating) {
                                        onSelectUser(user)
                                    }
                                    .testTag("user_result_${user.username}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        name = user.displayName.ifEmpty { user.username },
                                        avatarIndex = user.avatarColorIndex,
                                        size = 44.dp,
                                        showOnlineIndicator = true,
                                        isOnline = user.isOnline
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.displayName.ifEmpty { user.username },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "@${user.username}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Cyan400
                                        )
                                        if (user.statusMessage.isNotEmpty()) {
                                            Text(
                                                text = user.statusMessage,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = { onSelectUser(user) },
                                        enabled = !isCreating,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("chat_now_${user.username}")
                                    ) {
                                        Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ProfileDialog(
    user: UserProfile?,
    editDisplayName: String,
    editStatusMessage: String,
    onDisplayNameChange: (String) -> Unit,
    onStatusMessageChange: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit
) {
    var showSignOutConfirm by remember { mutableStateOf(false) }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Log Out?") },
            text = { Text("Are you sure you want to log out of Chaty?") },
            confirmButton = {
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Cyan400)
                Spacer(modifier = Modifier.width(8.dp))
                Text("My Profile & Settings", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                user?.let { u ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UserAvatar(
                            name = u.displayName.ifEmpty { u.username },
                            avatarIndex = u.avatarColorIndex,
                            size = 56.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "@${u.username}",
                                fontWeight = FontWeight.Bold,
                                color = Cyan400,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = u.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text("Display Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editStatusMessage,
                        onValueChange = onStatusMessageChange,
                        label = { Text("Status Message") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sign Out Action
                    Button(
                        onClick = { showSignOutConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save Changes")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 24 * 60 * 60_000 -> {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
        diff < 48 * 60 * 60_000 -> "Yesterday"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
