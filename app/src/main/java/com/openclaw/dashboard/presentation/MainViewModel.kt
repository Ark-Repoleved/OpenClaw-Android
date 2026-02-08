package com.openclaw.dashboard.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.dashboard.data.model.ChatEvent
import com.openclaw.dashboard.data.model.PresenceEntry
import com.openclaw.dashboard.data.model.SessionInfo
import com.openclaw.dashboard.data.model.Snapshot
import com.openclaw.dashboard.data.remote.ConnectionState
import com.openclaw.dashboard.data.remote.GatewayClient
import com.openclaw.dashboard.data.remote.GatewayEvent
import com.openclaw.dashboard.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the app
 * Manages gateway connection and shared state across screens
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val settingsRepository = SettingsRepository(application)
    private val gatewayClient = GatewayClient()
    
    // Connection settings
    val connectionSettings = settingsRepository.connectionSettings
    val isConfigured = settingsRepository.isConfigured.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    
    // Connection state
    val connectionState = gatewayClient.connectionState
    val snapshot = gatewayClient.snapshot
    val serverInfo = gatewayClient.serverInfo
    
    // Sessions
    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()
    
    private val _sessionsLoading = MutableStateFlow(false)
    val sessionsLoading: StateFlow<Boolean> = _sessionsLoading.asStateFlow()
    
    // Chat
    private val _chatMessages = MutableStateFlow<List<ChatEvent>>(emptyList())
    val chatMessages: StateFlow<List<ChatEvent>> = _chatMessages.asStateFlow()
    
    private val _currentSessionKey = MutableStateFlow<String?>(null)
    val currentSessionKey: StateFlow<String?> = _currentSessionKey.asStateFlow()
    
    // Connected instances (from presence)
    val connectedInstances: StateFlow<List<PresenceEntry>> = snapshot
        .map { it?.presence ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Health status
    val isHealthy: StateFlow<Boolean> = snapshot
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    // Uptime formatted
    val uptimeFormatted: StateFlow<String> = snapshot
        .map { snapshot ->
            snapshot?.uptimeMs?.let { formatUptime(it) } ?: "N/A"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "N/A")
    
    init {
        // Observe events
        viewModelScope.launch {
            gatewayClient.events.collect { event ->
                when (event) {
                    is GatewayEvent.Chat -> {
                        if (event.event.sessionKey == _currentSessionKey.value) {
                            _chatMessages.update { it + event.event }
                        }
                    }
                    is GatewayEvent.Shutdown -> {
                        // Handle shutdown
                    }
                    else -> { /* Ignore other events */ }
                }
            }
        }
        
        // Auto-connect if configured
        viewModelScope.launch {
            connectionSettings.first().let { settings ->
                if (settings.isConfigured && settings.dashboardUrl.isNotBlank()) {
                    connect(
                        settings.dashboardUrl,
                        settings.hfToken,
                        settings.gatewayToken,
                        settings.gatewayPassword
                    )
                }
            }
        }
    }
    
    /**
     * Save settings and connect
     */
    fun saveAndConnect(
        dashboardUrl: String,
        hfToken: String,
        gatewayToken: String,
        gatewayPassword: String
    ) {
        viewModelScope.launch {
            settingsRepository.saveConnectionSettings(
                dashboardUrl,
                hfToken,
                gatewayToken,
                gatewayPassword
            )
            connect(dashboardUrl, hfToken, gatewayToken, gatewayPassword)
        }
    }
    
    /**
     * Connect to gateway
     */
    fun connect(
        dashboardUrl: String,
        hfToken: String,
        gatewayToken: String,
        gatewayPassword: String
    ) {
        gatewayClient.connect(
            baseUrl = dashboardUrl,
            hfToken = hfToken.ifBlank { null },
            gatewayToken = gatewayToken.ifBlank { null },
            gatewayPassword = gatewayPassword.ifBlank { null }
        )
    }
    
    /**
     * Disconnect
     */
    fun disconnect() {
        gatewayClient.disconnect()
    }
    
    /**
     * Load sessions
     */
    fun loadSessions() {
        viewModelScope.launch {
            _sessionsLoading.value = true
            gatewayClient.getSessions(
                limit = 100,
                includeDerivedTitles = true,
                includeLastMessage = true
            ).onSuccess { sessions ->
                _sessions.value = sessions
            }.onFailure {
                // Handle error
            }
            _sessionsLoading.value = false
        }
    }
    
    /**
     * Delete session
     */
    fun deleteSession(key: String) {
        viewModelScope.launch {
            gatewayClient.deleteSession(key).onSuccess {
                _sessions.update { sessions ->
                    sessions.filter { it.key != key }
                }
            }
        }
    }
    
    /**
     * Select session for chat
     */
    fun selectSession(key: String) {
        _currentSessionKey.value = key
        _chatMessages.value = emptyList()
    }
    
    /**
     * Send chat message
     */
    fun sendMessage(message: String) {
        val sessionKey = _currentSessionKey.value ?: return
        
        viewModelScope.launch {
            // Add optimistic message
            val optimisticMessage = ChatEvent(
                sessionKey = sessionKey,
                role = "user",
                content = message,
                timestamp = System.currentTimeMillis()
            )
            _chatMessages.update { it + optimisticMessage }
            
            gatewayClient.sendChatMessage(sessionKey, message)
        }
    }
    
    /**
     * Clear settings and disconnect
     */
    fun logout() {
        viewModelScope.launch {
            disconnect()
            settingsRepository.clearSettings()
        }
    }
    
    private fun formatUptime(uptimeMs: Long): String {
        val seconds = uptimeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}天 ${hours % 24}小時"
            hours > 0 -> "${hours}小時 ${minutes % 60}分鐘"
            minutes > 0 -> "${minutes}分鐘"
            else -> "${seconds}秒"
        }
    }
}
