package com.example.ui.chat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.ui.components.FormattedChatMessageText
import com.example.ui.components.UserAvatar
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan700
import com.example.ui.theme.Emerald400
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    currentUserId: String,
    modifier: Modifier = Modifier,
    isTwoPane: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (!isTwoPane) {
        BackHandler {
            onBack()
        }
    }

    // Auto-scroll on new message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        if (!isTwoPane) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("chat_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                name = uiState.otherDisplayName.ifEmpty { uiState.otherUsername },
                                avatarIndex = uiState.otherAvatarIndex,
                                size = 40.dp,
                                showOnlineIndicator = true,
                                isOnline = uiState.otherIsOnline
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = uiState.otherDisplayName.ifEmpty { uiState.otherUsername },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Live status indicator showing the actual characters being typed in real time!
                                val liveDraft = uiState.otherLiveDraft
                                when {
                                    liveDraft != null && liveDraft.draftText.isNotEmpty() -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Cyan400)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "⚡ \"${liveDraft.draftText}\"",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Cyan400,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    uiState.otherIsOnline -> {
                                        Text(
                                            text = "Online",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Emerald400
                                        )
                                    }
                                    uiState.otherLastSeen > 0 -> {
                                        Text(
                                            text = "Last seen ${formatTimestampShort(uiState.otherLastSeen)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "@${uiState.otherUsername}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        // In-Chat Search Button
                        IconButton(
                            onClick = viewModel::toggleSearch,
                            modifier = Modifier.testTag("in_chat_search_button")
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search in chat",
                                tint = if (uiState.isSearchOpen) Cyan400 else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // In-Chat Search Bar
                AnimatedVisibility(visible = uiState.isSearchOpen) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.inChatSearchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                placeholder = { Text("Search messages in this chat...") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = {
                                    if (uiState.inChatSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("in_chat_search_input")
                            )
                        }
                    }
                }

                // Pinned Message Banner
                AnimatedVisibility(visible = uiState.pinnedMessageText != null) {
                    uiState.pinnedMessageText?.let { pText ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, Cyan500.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Cyan400,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Pinned Message",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Cyan400
                                    )
                                    Text(
                                        text = pText,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Messages Feed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val displayMessages = uiState.filteredMessages

                if (displayMessages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        UserAvatar(
                            name = uiState.otherDisplayName.ifEmpty { uiState.otherUsername },
                            avatarIndex = uiState.otherAvatarIndex,
                            size = 64.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (uiState.inChatSearchQuery.isNotEmpty()) "No matches found" else "Chatting with ${uiState.otherDisplayName.ifEmpty { uiState.otherUsername }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.inChatSearchQuery.isNotEmpty()) "Try searching for a different phrase." else "Features real-time keystroke live stream & rich text formatting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        items(displayMessages, key = { it.id }) { message ->
                            val isOutgoing = message.senderId == currentUserId
                            val isPinned = message.id == uiState.pinnedMessageId
                            val messageReactions = uiState.reactions[message.id] ?: emptyMap()

                            MessageBubble(
                                message = message,
                                isOutgoing = isOutgoing,
                                isPinned = isPinned,
                                reactions = messageReactions,
                                currentUserId = currentUserId,
                                onReply = { viewModel.setReplyTo(message) },
                                onEdit = { viewModel.startEditing(message) },
                                onDelete = { viewModel.deleteMessage(message) },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(message.text))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onTogglePin = { viewModel.togglePinMessage(message) },
                                onReact = { emoji -> viewModel.toggleReaction(message, emoji) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Real-Time Keystroke Ghost Stream Component ("Live Drafting")
            AnimatedVisibility(
                visible = uiState.otherLiveDraft != null && uiState.otherLiveDraft!!.draftText.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                uiState.otherLiveDraft?.let { draft ->
                    RealtimeGhostStreamCard(draftText = draft.draftText, senderName = draft.displayName.ifEmpty { draft.username })
                }
            }

            // Replying Banner
            AnimatedVisibility(visible = uiState.replyingToMessage != null) {
                uiState.replyingToMessage?.let { replyMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .background(Cyan400, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to ${replyMsg.senderDisplayName.ifEmpty { replyMsg.senderUsername }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Cyan400,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = replyMsg.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setReplyTo(null) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                            }
                        }
                    }
                }
            }

            // Editing Banner
            AnimatedVisibility(visible = uiState.editingMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Cyan400, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Editing message",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cyan400,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = viewModel::cancelEditing,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel edit")
                        }
                    }
                }
            }

            // Quick Express Text Chips (WhatsApp Killer Feature)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickChips = listOf(
                        "Got it! 👍",
                        "On my way! 🏃",
                        "Sounds great! ✨",
                        "Call you later 📞",
                        "Thanks! 🙏",
                        "Haha nice 😂"
                    )
                    items(quickChips.size) { index ->
                        val chip = quickChips[index]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.clickable { viewModel.sendQuickResponse(chip) }
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // WhatsApp-Style Text Formatting Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Format:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    FormatChip(label = "B", desc = "Bold *text*") {
                        viewModel.applyFormatting("*", "*")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    FormatChip(label = "I", desc = "Italic _text_") {
                        viewModel.applyFormatting("_", "_")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    FormatChip(label = "S", desc = "Strikethrough ~text~") {
                        viewModel.applyFormatting("~", "~")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    FormatChip(label = "</>", desc = "Code `text`") {
                        viewModel.applyFormatting("`", "`")
                    }
                }
            }

            // Message Composer Input
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::onInputTextChanged,
                        placeholder = { Text("Message... (*bold*, _italic_, ~strike~)") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Tactile Send Action Button
                    FloatingActionButton(
                        onClick = viewModel::sendMessage,
                        containerColor = if (uiState.inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        if (uiState.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = if (uiState.editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real-Time Keystroke Ghost Stream Card
 * Renders the actual characters as they are typed live!
 */
@Composable
fun RealtimeGhostStreamCard(
    draftText: String,
    senderName: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "cursorBlink"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Cyan400.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Glowing Live Beacon Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF43F5E))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE STREAM • $senderName is typing:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan400,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = draftText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
                // Blinking live cursor
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .width(2.dp)
                        .height(14.dp)
                        .background(Cyan400.copy(alpha = pulseAlpha))
                )
            }
        }
    }
}

@Composable
fun FormatChip(
    label: String,
    desc: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontFamily = if (label == "</>") FontFamily.Monospace else FontFamily.Default,
            fontSize = 12.sp,
            color = Cyan400,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isOutgoing: Boolean,
    isPinned: Boolean,
    reactions: Map<String, String>, // uid -> emoji
    currentUserId: String,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    val bubbleColor = if (isOutgoing) {
        Cyan700
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface

    val quickEmojis = listOf("❤️", "😂", "👍", "🔥", "😮", "👏")

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clickable { showMenu = true }
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Pinned Badge
                    if (isPinned) {
                        Row(
                            modifier = Modifier.padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = if (isOutgoing) Color.White.copy(alpha = 0.8f) else Cyan400,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOutgoing) Color.White.copy(alpha = 0.8f) else Cyan400
                            )
                        }
                    }

                    // Quoted Reply Preview
                    if (message.replyToText != null) {
                        Surface(
                            color = if (isOutgoing) Color(0xFF0891B2).copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(24.dp)
                                        .background(if (isOutgoing) Color.White else Cyan400, RoundedCornerShape(1.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = message.replyToSender ?: "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOutgoing) Color.White else Cyan400
                                    )
                                    Text(
                                        text = message.replyToText,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isOutgoing) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Rich Formatted Message Text
                    FormattedChatMessageText(
                        text = message.text,
                        textColor = textColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Footer: Timestamp, Edited tag, Delivery status
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "edited ",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = formatTimeOnly(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (message.status) {
                                "SENDING" -> {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = "Sending",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                "READ" -> {
                                    Icon(
                                        Icons.Default.DoneAll,
                                        contentDescription = "Read",
                                        modifier = Modifier.size(14.dp),
                                        tint = Cyan400
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Sent",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Reaction Floating Picker
            if (showReactionPicker) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .align(if (isOutgoing) Alignment.TopEnd else Alignment.TopStart)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                        quickEmojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .clickable {
                                        showReactionPicker = false
                                        onReact(emoji)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Context Menu Dropdown
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("React") },
                    leadingIcon = { Text("❤️", fontSize = 16.sp) },
                    onClick = {
                        showMenu = false
                        showReactionPicker = true
                    }
                )

                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onReply()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Copy Text") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onCopy()
                    }
                )

                DropdownMenuItem(
                    text = { Text(if (isPinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onTogglePin()
                    }
                )

                if (isOutgoing && !message.isDeleted) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }

        // Reaction Badges Attached to Bubble Bottom
        if (reactions.isNotEmpty()) {
            val emojiCounts = reactions.values.groupingBy { it }.eachCount()
            Row(
                modifier = Modifier.padding(top = 2.dp, start = if (isOutgoing) 0.dp else 6.dp, end = if (isOutgoing) 6.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                emojiCounts.forEach { (emoji, count) ->
                    val userReactedWithThis = reactions[currentUserId] == emoji
                    Surface(
                        color = if (userReactedWithThis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { onReact(emoji) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = emoji, fontSize = 12.sp)
                            if (count > 1) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = count.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (userReactedWithThis) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTimeOnly(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatTimestampShort(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "just now"
        diff < 60 * 60_000 -> "${diff / 60_000}m ago"
        diff < 24 * 60 * 60_000 -> "${diff / (60 * 60_000)}h ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
