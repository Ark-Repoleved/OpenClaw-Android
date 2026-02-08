package com.openclaw.dashboard.presentation.screen.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.openclaw.dashboard.data.model.ChatEvent
import com.openclaw.dashboard.presentation.MainViewModel
import com.openclaw.dashboard.presentation.components.MarkdownText
import com.openclaw.dashboard.util.TextUtils
import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel
) {
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionKey by viewModel.currentSessionKey.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    var showSessionPicker by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Load sessions if empty
    LaunchedEffect(Unit) {
        if (sessions.isEmpty()) {
            viewModel.loadSessions()
        }
    }
    
    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("聊天")
                        currentSessionKey?.let { key ->
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSessionPicker = true }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "切換 Session")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                value = messageText,
                onValueChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                enabled = currentSessionKey != null
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                currentSessionKey == null -> {
                    NoSessionSelected(
                        onSelectSession = { showSessionPicker = true },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                messages.isEmpty() -> {
                    EmptyChatState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { message ->
                            ChatBubble(message = message)
                        }
                    }
                }
            }
        }
    }
    
    // Session picker dialog
    if (showSessionPicker) {
        SessionPickerDialog(
            sessions = sessions.map { it.key to (it.derivedTitle ?: it.label ?: it.key) },
            currentKey = currentSessionKey,
            onSelect = { key ->
                viewModel.selectSession(key)
                showSessionPicker = false
            },
            onDismiss = { showSessionPicker = false }
        )
    }
}

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("輸入訊息...") },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(24.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank()
            ) {
                Icon(Icons.Filled.Send, contentDescription = "傳送")
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatEvent) {
    val isUser = message.message?.role == "user"
    
    // Extract text content from message or delta
    val rawContent = message.delta ?: run {
        // Try to parse content from message.content (can be string or array)
        val contentElement = message.message?.content
        if (contentElement != null) {
            try {
                // Check if it's a string
                if (contentElement.toString().startsWith("\"")) {
                    contentElement.toString().trim('"')
                } else {
                    // It's likely an array of content blocks
                    val array = contentElement as? kotlinx.serialization.json.JsonArray
                    array?.mapNotNull { item ->
                        val obj = item as? kotlinx.serialization.json.JsonObject
                        val type = obj?.get("type")?.toString()?.trim('"')
                        if (type == "text") {
                            obj["text"]?.toString()?.trim('"')
                        } else null
                    }?.joinToString("\n") ?: ""
                }
            } catch (e: Exception) {
                contentElement.toString()
            }
        } else ""
    }
    
    // Strip thinking tags from AI responses (not from user messages)
    val content = if (!isUser) {
        TextUtils.stripThinkingTags(rawContent)
    } else {
        rawContent
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            // Use Markwon for full GFM Markdown support
            MarkdownText(
                markdown = content,
                modifier = Modifier.padding(12.dp),
                isUserMessage = isUser
            )
        }
        
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun NoSessionSelected(
    onSelectSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Forum,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "選擇一個 Session",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FilledTonalButton(onClick = onSelectSession) {
            Icon(Icons.Filled.List, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("選擇 Session")
        }
    }
}

@Composable
fun EmptyChatState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "開始對話",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "在下方輸入訊息開始聊天",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun SessionPickerDialog(
    sessions: List<Pair<String, String>>,
    currentKey: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇 Session") },
        text = {
            if (sessions.isEmpty()) {
                Text("目前沒有可用的 Session")
            } else {
                LazyColumn {
                    items(sessions) { (key, title) ->
                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = { Text(key, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = {
                                RadioButton(
                                    selected = key == currentKey,
                                    onClick = { onSelect(key) }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
