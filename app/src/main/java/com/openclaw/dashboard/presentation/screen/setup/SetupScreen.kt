package com.openclaw.dashboard.presentation.screen.setup

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openclaw.dashboard.data.remote.ConnectionState
import com.openclaw.dashboard.presentation.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: MainViewModel,
    onConnected: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val settings by viewModel.connectionSettings.collectAsState(initial = null)
    
    var dashboardUrl by remember { mutableStateOf("") }
    var hfToken by remember { mutableStateOf("") }
    var gatewayToken by remember { mutableStateOf("") }
    var gatewayPassword by remember { mutableStateOf("") }
    var showHfToken by remember { mutableStateOf(false) }
    var showGatewayPassword by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    
    // Initialize from saved settings
    LaunchedEffect(settings) {
        settings?.let {
            dashboardUrl = it.dashboardUrl
            hfToken = it.hfToken
            gatewayToken = it.gatewayToken
            gatewayPassword = it.gatewayPassword
        }
    }
    
    // Navigate when connected
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            onConnected()
        }
    }
    
    val isLoading = connectionState is ConnectionState.Connecting
    val errorMessage = (connectionState as? ConnectionState.Error)?.message
    
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text("OpenClaw Dashboard")
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon
            Icon(
                imageVector = Icons.Filled.Pets,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "連線到您的 AI 代理",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "輸入您的 Dashboard URL 來開始管理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Dashboard URL
            OutlinedTextField(
                value = dashboardUrl,
                onValueChange = { dashboardUrl = it },
                label = { Text("Dashboard URL") },
                placeholder = { Text("your dashboard url") },
                leadingIcon = {
                    Icon(Icons.Filled.Link, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Hugging Face Token
            OutlinedTextField(
                value = hfToken,
                onValueChange = { hfToken = it },
                label = { Text("Hugging Face Token") },
                placeholder = { Text("hf_xxxxx（私有 Space 必填）") },
                leadingIcon = {
                    Icon(Icons.Filled.Key, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { showHfToken = !showHfToken }) {
                        Icon(
                            imageVector = if (showHfToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showHfToken) "隱藏" else "顯示"
                        )
                    }
                },
                visualTransformation = if (showHfToken) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            
            // Optional section
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f))
                Text(
                    text = "  選填  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider(modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Gateway Token
            OutlinedTextField(
                value = gatewayToken,
                onValueChange = { gatewayToken = it },
                label = { Text("Gateway Token（選填）") },
                leadingIcon = {
                    Icon(Icons.Filled.Token, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Gateway Password
            OutlinedTextField(
                value = gatewayPassword,
                onValueChange = { gatewayPassword = it },
                label = { Text("Gateway Password（選填）") },
                leadingIcon = {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { showGatewayPassword = !showGatewayPassword }) {
                        Icon(
                            imageVector = if (showGatewayPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showGatewayPassword) "隱藏" else "顯示"
                        )
                    }
                },
                visualTransformation = if (showGatewayPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            
            // Error message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Connect Button
            Button(
                onClick = {
                    viewModel.saveAndConnect(
                        dashboardUrl = dashboardUrl,
                        hfToken = hfToken,
                        gatewayToken = gatewayToken,
                        gatewayPassword = gatewayPassword
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = dashboardUrl.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("連線中...")
                } else {
                    Icon(Icons.Filled.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("連線", style = MaterialTheme.typography.titleMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Help text
            Text(
                text = "提示：HF Token 可在 huggingface.co/settings/tokens 建立",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
