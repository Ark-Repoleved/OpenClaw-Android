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
import com.openclaw.dashboard.data.repository.ThemeMode
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
    
    // Theme settings
    val themeMode = settingsRepository.themeMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeMode.SYSTEM
    )
    
    val useDynamicColor = settingsRepository.useDynamicColor.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )
    
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }
    
    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseDynamicColor(enabled)
        }
    }
    
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
    
    // AI typing indicator
    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping.asStateFlow()
    
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
                        val chatEvent = event.event
                        if (chatEvent.sessionKey == _currentSessionKey.value) {
                            // Update typing indicator based on state
                            // Show typing for delta (streaming) and any processing state
                            when (chatEvent.state) {
                                "final", "error" -> _isAiTyping.value = false
                                else -> _isAiTyping.value = true  // delta, processing, etc
                            }
                            
                            // Only process final messages, and dedupe by runId
                            if (chatEvent.state == "final" || chatEvent.state == "error") {
                                _chatMessages.update { messages ->
                                    // Remove any existing message with same runId (update instead of dup)
                                    val filtered = messages.filter { it.runId != chatEvent.runId }
                                    filtered + chatEvent
                                }
                            }
                        }
                    }
                    is GatewayEvent.Shutdown -> {
                        // Handle shutdown
                    }
                    else -> { /* Ignore other events */ }
                }
            }
        }
        
        // Watch connection state and load data when connected
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    // Load sessions when connected
                    loadSessions()
                    // Restore last selected session
                    settingsRepository.lastSessionKey.first()?.let { savedSessionKey ->
                        if (_currentSessionKey.value == null) {
                            _currentSessionKey.value = savedSessionKey
                            loadChatHistory(savedSessionKey)
                        }
                    }
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
        loadChatHistory(key)
        // Persist last session selection
        viewModelScope.launch {
            settingsRepository.setLastSessionKey(key)
        }
    }
    
    /**
     * Load chat history for a session
     */
    private fun loadChatHistory(sessionKey: String) {
        viewModelScope.launch {
            gatewayClient.getChatHistory(sessionKey, 100).onSuccess { result ->
                // Convert history messages to ChatEvent format for display
                val historyEvents = result.messages.mapIndexed { index, msg ->
                    ChatEvent(
                        runId = "history-$index",
                        sessionKey = sessionKey,
                        seq = index,
                        state = "final",
                        message = com.openclaw.dashboard.data.model.ChatMessage(
                            role = msg.role,
                            content = msg.content,
                            timestamp = msg.timestamp,
                            stopReason = msg.stopReason,
                            usage = msg.usage
                        )
                    )
                }
                _chatMessages.value = historyEvents
            }
        }
    }
    
    /**
     * Send chat message
     */
    fun sendMessage(message: String) {
        val sessionKey = _currentSessionKey.value ?: return
        
        viewModelScope.launch {
            // Add optimistic user message
            val runId = java.util.UUID.randomUUID().toString()
            val optimisticMessage = ChatEvent(
                runId = runId,
                sessionKey = sessionKey,
                seq = 0,
                state = "final",
                message = com.openclaw.dashboard.data.model.ChatMessage(
                    role = "user",
                    timestamp = System.currentTimeMillis()
                ),
                delta = message
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
    
    // ============== Config ==============
    
    private val _configState = MutableStateFlow<com.openclaw.dashboard.presentation.screen.config.ConfigUiState>(
        com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Loading
    )
    val configState: StateFlow<com.openclaw.dashboard.presentation.screen.config.ConfigUiState> = _configState.asStateFlow()
    
    /**
     * Load config from gateway
     */
    fun loadConfig() {
        viewModelScope.launch {
            _configState.value = com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Loading
            gatewayClient.getConfig()
                .onSuccess { snapshot ->
                    if (snapshot.exists && snapshot.raw != null) {
                        _configState.value = com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Loaded(
                            raw = snapshot.raw,
                            hash = snapshot.hash
                        )
                    } else {
                        _configState.value = com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Error("配置不存在")
                    }
                }
                .onFailure { e ->
                    _configState.value = com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Error(
                        e.message ?: "載入失敗"
                    )
                }
        }
    }
    
    /**
     * Save config to gateway
     */
    fun saveConfig(raw: String, baseHash: String) {
        viewModelScope.launch {
            _configState.value = com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Saving
            gatewayClient.setConfig(raw, baseHash)
                .onSuccess { result ->
                    if (result.ok) {
                        _configState.value = com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Saved
                    } else {
                        _configState.value = com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Error("儲存失敗")
                    }
                }
                .onFailure { e ->
                    _configState.value = com.openclaw.dashboard.presentation.screen.config.ConfigUiState.Error(
                        e.message ?: "儲存失敗"
                    )
                }
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

