package com.openclaw.dashboard.presentation.screen.overview

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openclaw.dashboard.R
import com.openclaw.dashboard.data.remote.ConnectionState
import com.openclaw.dashboard.presentation.MainViewModel
import com.openclaw.dashboard.presentation.components.SettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: MainViewModel
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val serverInfo by viewModel.serverInfo.collectAsState()
    val isHealthy by viewModel.isHealthy.collectAsState()
    val uptimeFormatted by viewModel.uptimeFormatted.collectAsState()
    val connectedInstances by viewModel.connectedInstances.collectAsState()
    
    // Theme settings state
    val themeMode by viewModel.themeMode.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Settings Dialog
    SettingsDialog(
        isOpen = showSettingsDialog,
        onDismiss = { showSettingsDialog = false },
        currentThemeMode = themeMode,
        onThemeModeChange = { viewModel.setThemeMode(it) },
        useDynamicColor = useDynamicColor,
        onDynamicColorChange = { viewModel.setUseDynamicColor(it) },
        notificationsEnabled = notificationsEnabled,
        onNotificationsEnabledChange = { viewModel.setNotificationsEnabled(it) }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_overview)) },
                actions = {
                    // Connection status indicator
                    val (statusColor, statusIcon) = when (connectionState) {
                        is ConnectionState.Connected -> Color(0xFF4CAF50) to Icons.Filled.CheckCircle
                        is ConnectionState.Connecting -> Color(0xFFFF9800) to Icons.Filled.Sync
                        is ConnectionState.Reconnecting -> Color(0xFFFF9800) to Icons.Filled.Refresh
                        is ConnectionState.Disconnected -> Color(0xFF9E9E9E) to Icons.Filled.Cancel
                        is ConnectionState.Error -> Color(0xFFF44336) to Icons.Filled.ErrorOutline
                    }
                    
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = stringResource(R.string.overview_connection_status),
                        tint = statusColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    // Settings button
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Health Status Card
            item {
                HealthStatusCard(isHealthy = isHealthy)
            }
            
            // Gateway Info Card
            item {
                GatewayInfoCard(
                    version = serverInfo?.version ?: "N/A",
                    host = serverInfo?.host ?: "N/A",
                    uptime = uptimeFormatted
                )
            }
            
            // Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        title = stringResource(R.string.overview_connected_clients),
                        value = connectedInstances.size.toString(),
                        icon = Icons.Filled.Devices,
                        modifier = Modifier.weight(1f)
                    )
                    
                    StatCard(
                        title = "Sessions",
                        value = snapshot?.sessionDefaults?.mainKey?.split(":")?.size?.toString() ?: "0",
                        icon = Icons.Filled.Chat,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Connected Instances Preview
            if (connectedInstances.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.overview_connected_clients),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                items(
                    count = minOf(connectedInstances.size, 3),
                    key = { connectedInstances[it].instanceId ?: it.toString() }
                ) { index ->
                    val instance = connectedInstances[index]
                    InstancePreviewCard(
                        platform = instance.platform ?: "Unknown",
                        host = instance.host ?: instance.ip ?: "Unknown",
                        mode = instance.mode ?: "Unknown"
                    )
                }
            }
        }
    }
}

@Composable
fun HealthStatusCard(isHealthy: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHealthy) {
                Color(0xFF4CAF50).copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isHealthy) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (isHealthy) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = if (isHealthy) stringResource(R.string.overview_system_health) else stringResource(R.string.overview_health_error),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isHealthy) stringResource(R.string.overview_all_services_ok) else stringResource(R.string.overview_services_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GatewayInfoCard(
    version: String,
    host: String,
    uptime: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_gateway_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            InfoRow(label = stringResource(R.string.overview_version), value = version, icon = Icons.Filled.Info)
            InfoRow(label = stringResource(R.string.overview_host), value = host, icon = Icons.Filled.Computer)
            InfoRow(label = stringResource(R.string.overview_uptime), value = uptime, icon = Icons.Filled.Schedule)
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InstancePreviewCard(
    platform: String,
    host: String,
    mode: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (platform.lowercase()) {
                    "android" -> Icons.Filled.PhoneAndroid
                    "ios" -> Icons.Filled.PhoneIphone
                    "macos", "darwin" -> Icons.Filled.DesktopMac
                    "windows" -> Icons.Filled.DesktopWindows
                    "linux" -> Icons.Filled.Computer
                    else -> Icons.Filled.Devices
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platform,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AssistChip(
                onClick = { },
                label = { Text(mode) }
            )
        }
    }
}
