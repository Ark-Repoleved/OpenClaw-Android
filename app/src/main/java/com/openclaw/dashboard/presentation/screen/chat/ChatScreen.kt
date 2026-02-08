package com.openclaw.dashboard.presentation.screen.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.openclaw.dashboard.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openclaw.dashboard.R
import com.openclaw.dashboard.data.model.ChatAttachment
import com.openclaw.dashboard.data.model.ChatEvent
import com.openclaw.dashboard.presentation.MainViewModel
import com.openclaw.dashboard.presentation.components.MarkdownText
import com.openclaw.dashboard.util.TextUtils
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel
) {
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionKey by viewModel.currentSessionKey.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val isAiTyping by viewModel.isAiTyping.collectAsState()
    val attachments by viewModel.chatAttachments.collectAsState()
    
    val context = LocalContext.current
    var messageText by remember { mutableStateOf("") }
    var showSessionPicker by remember { mutableStateOf(false) }
    
    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            // Convert image to base64
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                // Compress and encode
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                outputStream.close()
                
                viewModel.addAttachment("image/jpeg", base64)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
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
                        Text(stringResource(R.string.chat_title))
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
                        Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.chat_select_session))
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                value = messageText,
                onValueChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank() || attachments.isNotEmpty()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                onAttach = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                attachments = attachments,
                onRemoveAttachment = { index -> viewModel.removeAttachment(index) },
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
                    // Filter out messages that shouldn't be shown to users
                    val filteredMessages = messages.filter { msg ->
                        val role = msg.message?.role?.lowercase() ?: ""
                        // Hide tool results, tool calls, and system messages
                        if (role in listOf("toolresult", "tool", "system", "toolcall")) {
                            return@filter false
                        }
                        
                        // Also filter out messages with empty content
                        val content = extractMessageContent(msg)
                        content.isNotBlank()
                    }
                    
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredMessages) { message ->
                            ChatBubble(message = message)
                        }
                        
                        // Show typing indicator when AI is responding
                        if (isAiTyping) {
                            item {
                                TypingIndicator()
                            }
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
    onAttach: () -> Unit,
    attachments: List<ChatAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    enabled: Boolean
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Attachment previews
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attachments.forEachIndexed { index, attachment ->
                        AttachmentPreview(
                            base64Content = attachment.content,
                            onRemove = { onRemoveAttachment(index) }
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach button
                IconButton(
                    onClick = onAttach,
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.chat_attach_image),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
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
                    enabled = enabled && (value.isNotBlank() || attachments.isNotEmpty())
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                }
            }
        }
    }
}

@Composable
fun AttachmentPreview(
    base64Content: String,
    onRemove: () -> Unit
) {
    val bitmap = remember(base64Content) {
        try {
            val bytes = Base64.decode(base64Content, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
    
    Box(
        modifier = Modifier.size(72.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.chat_attachment_preview),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = CircleShape
                )
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.chat_remove_attachment),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * Helper function to extract text content from a ChatEvent for filtering
 */
private fun extractMessageContent(message: ChatEvent): String {
    val rawContent = message.delta ?: run {
        val contentElement = message.message?.content
        if (contentElement != null) {
            try {
                if (contentElement.toString().startsWith("\"")) {
                    contentElement.toString().trim('"')
                } else {
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
    
    // Process and filter thinking tags for AI messages
    val isUser = message.message?.role == "user"
    val processedContent = rawContent
        .replace("\\n", "\n")
        .replace("\\t", "\t")
    
    return if (!isUser) {
        TextUtils.stripThinkingTags(processedContent)
    } else {
        processedContent
    }
}

/**
 * Typing indicator shown when AI is responding
 */
@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // AI avatar
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
        
        // Typing bubble
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated dots
                val infiniteTransition = rememberInfiniteTransition(label = "typing")
                repeat(3) { index ->
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 200)
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                    )
                }
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
    
    // Process content:
    // 1. Convert escaped newlines to actual newlines
    // 2. Strip thinking tags from AI responses (not from user messages)
    val processedContent = rawContent
        .replace("\\n", "\n")
        .replace("\\t", "\t")
    
    val content = if (!isUser) {
        TextUtils.stripThinkingTags(processedContent)
    } else {
        processedContent
    }
    
    // Don't render empty messages
    if (content.isBlank()) {
        return
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
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
            text = stringResource(R.string.chat_select_a_session),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FilledTonalButton(onClick = onSelectSession) {
            Icon(Icons.Filled.List, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.chat_select_session))
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
            text = stringResource(R.string.chat_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = stringResource(R.string.chat_input_hint),
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
        title = { Text(stringResource(R.string.chat_select_session)) },
        text = {
            if (sessions.isEmpty()) {
                Text(stringResource(R.string.chat_no_session))
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
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
