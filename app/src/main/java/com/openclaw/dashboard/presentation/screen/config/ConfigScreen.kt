package com.openclaw.dashboard.presentation.screen.config

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.dashboard.presentation.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: MainViewModel
) {
    val configState by viewModel.configState.collectAsState()
    val scope = rememberCoroutineScope()
    
    var editedConfig by remember { mutableStateOf("") }
    var hasChanges by remember { mutableStateOf(false) }
    var baseHash by remember { mutableStateOf<String?>(null) }
    
    // Load config when screen opens
    LaunchedEffect(Unit) {
        viewModel.loadConfig()
    }
    
    // Update edited config when loaded
    LaunchedEffect(configState) {
        if (configState is ConfigUiState.Loaded) {
            val loaded = configState as ConfigUiState.Loaded
            editedConfig = loaded.raw
            baseHash = loaded.hash
            hasChanges = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置編輯器") },
                actions = {
                    // Reload button
                    IconButton(
                        onClick = { viewModel.loadConfig() },
                        enabled = configState !is ConfigUiState.Loading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新載入")
                    }
                    
                    // Save button
                    IconButton(
                        onClick = {
                            baseHash?.let { hash ->
                                viewModel.saveConfig(editedConfig, hash)
                            }
                        },
                        enabled = hasChanges && configState is ConfigUiState.Loaded
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "儲存",
                            tint = if (hasChanges) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = configState) {
                is ConfigUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is ConfigUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.loadConfig() }) {
                            Text("重試")
                        }
                    }
                }
                
                is ConfigUiState.Loaded -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Status bar
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (hasChanges) "已修改" else "未修改",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hasChanges) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Hash: ${state.hash?.take(8) ?: "N/A"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Config editor
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            BasicTextField(
                                value = editedConfig,
                                onValueChange = { newValue ->
                                    editedConfig = newValue
                                    hasChanges = newValue != state.raw
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
                
                is ConfigUiState.Saving -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("儲存中...")
                        }
                    }
                }
                
                is ConfigUiState.Saved -> {
                    // Reload after save
                    LaunchedEffect(Unit) {
                        viewModel.loadConfig()
                    }
                }
            }
        }
    }
}

// Config UI State
sealed class ConfigUiState {
    data object Loading : ConfigUiState()
    data class Loaded(val raw: String, val hash: String?) : ConfigUiState()
    data class Error(val message: String) : ConfigUiState()
    data object Saving : ConfigUiState()
    data object Saved : ConfigUiState()
}
