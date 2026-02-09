package com.openclaw.dashboard.presentation.screen.agentfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.dashboard.R
import com.openclaw.dashboard.data.model.AgentFileEntry
import com.openclaw.dashboard.presentation.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentFilesScreen(
    viewModel: MainViewModel
) {
    val files by viewModel.agentFilesList.collectAsState()
    val isLoading by viewModel.agentFilesLoading.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val fileDraft by viewModel.agentFileDraft.collectAsState()
    val isModified by viewModel.isAgentFileModified.collectAsState()
    val isSaving by viewModel.isAgentFileSaving.collectAsState()
    
    // Load files on first launch
    LaunchedEffect(Unit) {
        if (files.isEmpty()) {
            viewModel.loadAgentFiles()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agent_files_title)) },
                actions = {
                    // Modified indicator
                    if (isModified && selectedFileName != null) {
                        AssistChip(
                            onClick = { },
                            label = { Text(stringResource(R.string.agent_files_modified)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    
                    // Save button
                    if (selectedFileName != null) {
                        IconButton(
                            onClick = { viewModel.saveAgentFile() },
                            enabled = isModified && !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Save, 
                                    contentDescription = stringResource(R.string.agent_files_save)
                                )
                            }
                        }
                    }
                    
                    // Reload button
                    IconButton(
                        onClick = { viewModel.loadAgentFiles() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            Icons.Filled.Refresh, 
                            contentDescription = stringResource(R.string.action_reload)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Left panel: File list
            FileListPanel(
                files = files,
                selectedFileName = selectedFileName,
                isLoading = isLoading,
                onFileSelect = { viewModel.selectAgentFile(it) },
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight()
            )
            
            // Divider
            VerticalDivider()
            
            // Right panel: Editor
            EditorPanel(
                selectedFileName = selectedFileName,
                content = fileDraft,
                onContentChange = { viewModel.updateAgentFileDraft(it) },
                isLoading = isLoading,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
fun FileListPanel(
    files: List<AgentFileEntry>,
    selectedFileName: String?,
    isLoading: Boolean,
    onFileSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
    ) {
        if (isLoading && files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.agent_files_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(files, key = { it.name }) { file ->
                    FileListItem(
                        file = file,
                        isSelected = file.name == selectedFileName,
                        onClick = { onFileSelect(file.name) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileListItem(
    file: AgentFileEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                file.size?.let { size ->
                    Text(
                        text = formatFileSize(size),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EditorPanel(
    selectedFileName: String?,
    content: String,
    onContentChange: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            isLoading && selectedFileName != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            selectedFileName == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.agent_files_select_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                BasicTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Int): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
