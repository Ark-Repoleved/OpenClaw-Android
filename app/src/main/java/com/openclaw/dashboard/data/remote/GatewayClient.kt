package com.openclaw.dashboard.data.remote

import android.util.Log
import com.openclaw.dashboard.data.local.DeviceAuthStore
import com.openclaw.dashboard.data.local.DeviceIdentityStore
import com.openclaw.dashboard.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * OpenClaw Gateway WebSocket Client
 * Handles connection, authentication, and message exchange with the Gateway server.
 */
class GatewayClient {
    
    private lateinit var identityStore: DeviceIdentityStore
    private lateinit var authStore: DeviceAuthStore
    private var connectNonce: String? = null

    fun initialize(identityStore: DeviceIdentityStore, authStore: DeviceAuthStore) {
        this.identityStore = identityStore
        this.authStore = authStore
    }

    companion object {
        private const val TAG = "GatewayClient"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 0L  // No timeout for WebSocket
        private const val WRITE_TIMEOUT_SECONDS = 30L
        private const val PING_INTERVAL_SECONDS = 30L
        
        // Auto-reconnect settings
        private const val RECONNECT_INITIAL_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 30000L
        private const val RECONNECT_MAX_ATTEMPTS = 10
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true  // Ensure mode field is serialized
        isLenient = true
    }
    
    private var webSocket: WebSocket? = null
    private val pendingRequests = ConcurrentHashMap<String, Channel<ResponseFrame>>()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _eventChannel = Channel<GatewayEvent>(UNLIMITED)
    val events: Flow<GatewayEvent> = _eventChannel.receiveAsFlow()
    
    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()
    
    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()
    
    // Auto-reconnect state
    private var autoReconnectEnabled = true
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var lastConnectionParams: ConnectionParams? = null
    
    private data class ConnectionParams(
        val baseUrl: String,
        val hfToken: String?,
        val gatewayToken: String?,
        val gatewayPassword: String?
    )

    
    /**
     * Connect to the Gateway server
     */
    fun connect(
        baseUrl: String,
        hfToken: String? = null,
        gatewayToken: String? = null,
        gatewayPassword: String? = null
    ) {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting) {
            Log.w(TAG, "Already connected or connecting")
            return
        }
        
        // Save connection params for reconnect
        lastConnectionParams = ConnectionParams(baseUrl, hfToken, gatewayToken, gatewayPassword)
        reconnectAttempts = 0
        autoReconnectEnabled = true
        reconnectJob?.cancel()
        
        _connectionState.value = ConnectionState.Connecting
        
        val wsUrl = buildWebSocketUrl(baseUrl)
        Log.d(TAG, "Connecting to: $wsUrl")
        
        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
        
        val requestBuilder = Request.Builder().url(wsUrl)
        
        // Add Hugging Face token for private spaces
        if (!hfToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $hfToken")
        }
        
        val request = requestBuilder.build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                reconnectAttempts = 0  // Reset on successful connection
                sendConnect(gatewayToken, gatewayPassword)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                cleanup()
                scheduleReconnect()
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                _connectionState.value = ConnectionState.Disconnected
                cleanup()
                // Auto-reconnect unless user explicitly disconnected
                if (code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    /**
     * Schedule auto-reconnect with exponential backoff
     */
    private fun scheduleReconnect() {
        if (!autoReconnectEnabled || lastConnectionParams == null) {
            Log.d(TAG, "Auto-reconnect disabled or no connection params")
            return
        }
        
        if (reconnectAttempts >= RECONNECT_MAX_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached")
            _connectionState.value = ConnectionState.Error("MAX_RECONNECT_ATTEMPTS")
            return
        }
        
        reconnectAttempts++
        val delay = minOf(
            RECONNECT_INITIAL_DELAY_MS * (1L shl (reconnectAttempts - 1)),
            RECONNECT_MAX_DELAY_MS
        )
        
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${delay}ms")
        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempts, RECONNECT_MAX_ATTEMPTS)
        
        reconnectJob = CoroutineScope(Dispatchers.Main).launch {
            delay(delay)
            lastConnectionParams?.let { params ->
                Log.d(TAG, "Attempting reconnect...")
                connect(params.baseUrl, params.hfToken, params.gatewayToken, params.gatewayPassword)
            }
        }
    }
    
    /**
     * Disconnect from the Gateway
     */
    fun disconnect() {
        autoReconnectEnabled = false  // Disable auto-reconnect on manual disconnect
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        cleanup()
    }
    
    /**
     * Send a request and wait for response
     */
    suspend fun request(method: String, params: JsonElement? = null): Result<JsonElement?> {
        if (_connectionState.value !is ConnectionState.Connected) {
            return Result.failure(IllegalStateException("Not connected"))
        }
        
        val requestId = UUID.randomUUID().toString()
        val responseChannel = Channel<ResponseFrame>(1)
        pendingRequests[requestId] = responseChannel
        
        val frame = RequestFrame(
            id = requestId,
            method = method,
            params = params
        )
        
        val frameJson = json.encodeToString(frame)
        webSocket?.send(frameJson)
        
        return try {
            val response = responseChannel.receive()
            pendingRequests.remove(requestId)
            
            if (response.ok) {
                Result.success(response.payload)
            } else {
                val errorMsg = response.error?.message ?: "Unknown error"
                Result.failure(GatewayException(response.error?.code ?: "unknown", errorMsg))
            }
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Result.failure(e)
        }
    }
    
    /**
     * Send chat message with optional attachments
     */
    suspend fun sendChatMessage(
        sessionKey: String, 
        message: String,
        attachments: List<ChatAttachment>? = null
    ): Result<Unit> {
        val idempotencyKey = UUID.randomUUID().toString()
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", message)
            put("idempotencyKey", idempotencyKey)
            if (!attachments.isNullOrEmpty()) {
                put("attachments", json.encodeToJsonElement(attachments))
            }
        }
        
        return request("chat.send", params).map { }
    }
    
    /**
     * Get chat history for a session
     */
    suspend fun getChatHistory(sessionKey: String, limit: Int = 100): Result<ChatHistoryResult> {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("limit", limit)
        }
        
        return request("chat.history", params).mapCatching { payload ->
            payload?.let {
                json.decodeFromJsonElement<ChatHistoryResult>(it)
            } ?: ChatHistoryResult(sessionKey, null, emptyList())
        }
    }
    
    /**
     * Get raw config
     */
    suspend fun getConfig(): Result<ConfigSnapshot> {
        return request("config.get", buildJsonObject {}).mapCatching { payload ->
            payload?.let {
                json.decodeFromJsonElement<ConfigSnapshot>(it)
            } ?: ConfigSnapshot(false, false, null, null, null)
        }
    }
    
    /**
     * Set raw config
     */
    suspend fun setConfig(raw: String, baseHash: String): Result<ConfigSetResult> {
        val params = buildJsonObject {
            put("raw", raw)
            put("baseHash", baseHash)
        }
        
        return request("config.set", params).mapCatching { payload ->
            payload?.let {
                json.decodeFromJsonElement<ConfigSetResult>(it)
            } ?: ConfigSetResult(false, null)
        }
    }
    
    /**
     * Get sessions list
     */
    suspend fun getSessions(
        limit: Int = 50,
        includeDerivedTitles: Boolean = true,
        includeLastMessage: Boolean = true
    ): Result<List<SessionInfo>> {
        val params = buildJsonObject {
            put("limit", limit)
            put("includeDerivedTitles", includeDerivedTitles)
            put("includeLastMessage", includeLastMessage)
        }
        
        return request("sessions.list", params).mapCatching { payload ->
            payload?.let {
                val result = json.decodeFromJsonElement<SessionsListResult>(it)
                result.sessions
            } ?: emptyList()
        }
    }
    
    /**
     * Get usage statistics
     */
    suspend fun getUsage(
        startDate: String? = null,
        endDate: String? = null,
        limit: Int = 50
    ): Result<SessionsUsageResult> {
        val params = buildJsonObject {
            startDate?.let { put("startDate", it) }
            endDate?.let { put("endDate", it) }
            put("limit", limit)
        }
        
        return request("sessions.usage", params).mapCatching { payload ->
            payload?.let {
                json.decodeFromJsonElement<SessionsUsageResult>(it)
            } ?: SessionsUsageResult(emptyList())
        }
    }
    
    /**
     * Delete a session
     */
    suspend fun deleteSession(key: String, deleteTranscript: Boolean = false): Result<Unit> {
        val params = buildJsonObject {
            put("key", key)
            put("deleteTranscript", deleteTranscript)
        }
        
        return request("sessions.delete", params).map { }
    }
    
    // ============== Agent Files ==============
    
    /**
     * Get list of agent files
     */
    suspend fun getAgentFilesList(agentId: String): Result<AgentFilesListResult> {
        val params = buildJsonObject {
            put("agentId", agentId)
        }
        
        return request("agents.files.list", params).mapCatching { payload ->
            payload?.let {
                json.decodeFromJsonElement<AgentFilesListResult>(it)
            } ?: AgentFilesListResult(agentId, "", emptyList())
        }
    }
    
    /**
     * Get content of a specific agent file
     */
    suspend fun getAgentFile(agentId: String, name: String): Result<AgentFileGetResult> {
        val params = buildJsonObject {
            put("agentId", agentId)
            put("name", name)
        }
        
        return request("agents.files.get", params).mapCatching { payload ->
            payload?.let {
                json.decodeFromJsonElement<AgentFileGetResult>(it)
            } ?: throw Exception("Empty response")
        }
    }
    
    /**
     * Save content to an agent file
     */
    suspend fun setAgentFile(agentId: String, name: String, content: String): Result<AgentFileSetResult> {
        val params = buildJsonObject {
            put("agentId", agentId)
            put("name", name)
            put("content", content)
        }
        
        return request("agents.files.set", params).mapCatching { payload ->
            payload?.let {
                json.decodeFromJsonElement<AgentFileSetResult>(it)
            } ?: throw Exception("Empty response")
        }
    }
    
    // ============== Private Methods ==============
    
    private fun buildWebSocketUrl(baseUrl: String): String {
        val cleanUrl = baseUrl.trimEnd('/')
        val wsUrl = when {
            cleanUrl.startsWith("https://") -> cleanUrl.replace("https://", "wss://")
            cleanUrl.startsWith("http://") -> cleanUrl.replace("http://", "ws://")
            cleanUrl.startsWith("wss://") || cleanUrl.startsWith("ws://") -> cleanUrl
            else -> "wss://$cleanUrl"
        }
        return "$wsUrl/gateway"
    }
    
    private fun sendConnect(token: String?, password: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            val identity = identityStore.loadOrCreate()
            val deviceId = identity.deviceId
            val role = "operator"
            val scopes = listOf("operator.read", "operator.write", "operator.admin")
            
            val storedToken = authStore.loadToken(deviceId, role)
            val effectiveToken = if (!storedToken.isNullOrBlank()) storedToken else token?.trim()
            
            val signedAtMs = System.currentTimeMillis()
            val authPayload = buildDeviceAuthPayload(
                deviceId = deviceId,
                clientId = "openclaw-android",
                clientMode = "ui",
                role = role,
                scopes = scopes,
                signedAtMs = signedAtMs,
                token = effectiveToken,
                nonce = connectNonce
            )
            
            val signature = identityStore.signPayload(authPayload, identity)
            val publicKey = identityStore.publicKeyBase64Url(identity)

            // Build connect params
            val connectParams = buildJsonObject {
                put("minProtocol", 3)
                put("maxProtocol", 3)
                put("role", role)
                putJsonArray("scopes") {
                    scopes.forEach { add(it) }
                }
                
                // Build client object
                putJsonObject("client") {
                    put("id", "openclaw-android")
                    put("displayName", "OpenClaw Android")
                    put("version", "1.0.0")
                    put("platform", "android")
                    put("deviceFamily", android.os.Build.MANUFACTURER)
                    put("mode", "ui")
                }
                
                // Add device identity if signature is available
                if (signature != null && publicKey != null) {
                    putJsonObject("device") {
                        put("id", deviceId)
                        put("publicKey", publicKey)
                        put("signature", signature)
                        put("signedAt", signedAtMs)
                        connectNonce?.let { put("nonce", it) }
                    }
                }
                
                // Optional caps
                putJsonArray("caps") {
                    add("tool-events")
                }
                
                // Only include auth if we have credentials
                val passwordTrimmed = password?.trim()
                if (!effectiveToken.isNullOrBlank() || !passwordTrimmed.isNullOrBlank()) {
                    putJsonObject("auth") {
                        if (!effectiveToken.isNullOrBlank()) put("token", effectiveToken)
                        if (!passwordTrimmed.isNullOrBlank()) put("password", passwordTrimmed)
                    }
                }
            }
            
            // The handshake must be a request frame: { type: "req", id: "...", method: "connect", params: ConnectParams }
            val requestFrame = buildJsonObject {
                put("type", "req")
                put("id", UUID.randomUUID().toString())
                put("method", "connect")
                put("params", connectParams)
            }
            
            val frameJson = json.encodeToString(requestFrame)
            Log.d(TAG, "Sending connect frame: $frameJson")
            withContext(Dispatchers.Main) {
                webSocket?.send(frameJson)
            }
        }
    }

    private fun buildDeviceAuthPayload(
        deviceId: String,
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String?
    ): String {
        val scopeString = scopes.joinToString(",")
        val authToken = token ?: ""
        val version = if (nonce.isNullOrBlank()) "v1" else "v2"
        val parts = mutableListOf(
            version,
            deviceId,
            clientId,
            clientMode,
            role,
            scopeString,
            signedAtMs.toString(),
            authToken
        )
        if (!nonce.isNullOrBlank()) {
            parts.add(nonce)
        }
        return parts.joinToString("|")
    }
    
    private fun handleMessage(text: String) {
        try {
            val jsonElement = json.parseToJsonElement(text)
            val type = jsonElement.jsonObject["type"]?.jsonPrimitive?.contentOrNull
            
            when (type) {
                "hello-ok" -> handleHelloOk(jsonElement)
                "res" -> handleResponse(jsonElement)
                "event" -> handleEvent(jsonElement)
                else -> Log.w(TAG, "Unknown message type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }
    
    private fun handleHelloOk(jsonElement: JsonElement) {
        try {
            val helloOk = json.decodeFromJsonElement<HelloOk>(jsonElement)
            _serverInfo.value = helloOk.server
            _snapshot.value = helloOk.snapshot
            _connectionState.value = ConnectionState.Connected
            Log.d(TAG, "Connected to server v${helloOk.server.version}")
            
            // Store device token if present
            helloOk.auth?.let { auth ->
                val identity = identityStore.loadOrCreate()
                CoroutineScope(Dispatchers.IO).launch {
                    authStore.saveToken(identity.deviceId, auth.role, auth.deviceToken)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse hello-ok: ${e.message}")
            _connectionState.value = ConnectionState.Error("Invalid server response")
        }
    }
    
    private fun handleResponse(jsonElement: JsonElement) {
        try {
            val response = json.decodeFromJsonElement<ResponseFrame>(jsonElement)
            
            // Handle connect failure (connect sends directly, not via request())
            if (!response.ok && pendingRequests[response.id] == null) {
                val errorMsg = response.error?.message ?: "Connection rejected"
                Log.e(TAG, "Connect failed: $errorMsg")
                _connectionState.value = ConnectionState.Error(errorMsg)
                return
            }
            
            // Check if this is the connect response (payload has type: "hello-ok")
            val payload = response.payload
            if (response.ok && payload != null) {
                val payloadType = payload.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                if (payloadType == "hello-ok") {
                    handleHelloOk(payload)
                    return
                }
            }
            
            // Handle other responses
            pendingRequests[response.id]?.trySend(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: ${e.message}")
        }
    }
    
    private fun handleEvent(jsonElement: JsonElement) {
        try {
            val eventFrame = json.decodeFromJsonElement<EventFrame>(jsonElement)
            
            val gatewayEvent = when (eventFrame.event) {
                "tick" -> {
                    eventFrame.payload?.let {
                        val tickEvent = json.decodeFromJsonElement<TickEvent>(it)
                        GatewayEvent.Tick(tickEvent.ts)
                    }
                }
                "chat" -> {
                    eventFrame.payload?.let {
                        val chatEvent = json.decodeFromJsonElement<ChatEvent>(it)
                        GatewayEvent.Chat(chatEvent)
                    }
                }
                "presence" -> {
                    eventFrame.payload?.let {
                        val presenceList = json.decodeFromJsonElement<List<PresenceEntry>>(it)
                        _snapshot.value = _snapshot.value?.copy(presence = presenceList)
                        GatewayEvent.Presence(presenceList)
                    }
                }
                "connect.challenge" -> {
                    eventFrame.payload?.let { payload ->
                        val nonce = payload.jsonObject["nonce"]?.jsonPrimitive?.contentOrNull
                        if (nonce != null) {
                            Log.d(TAG, "Received connect challenge nonce: $nonce")
                            connectNonce = nonce
                        }
                    }
                    null
                }
                "agent" -> {
                    eventFrame.payload?.let {
                        val agentEvent = json.decodeFromJsonElement<AgentEvent>(it)
                        GatewayEvent.Agent(agentEvent)
                    }
                }
                "shutdown" -> {
                    eventFrame.payload?.let {
                        val shutdownEvent = json.decodeFromJsonElement<ShutdownEvent>(it)
                        GatewayEvent.Shutdown(shutdownEvent.reason, shutdownEvent.restartExpectedMs)
                    }
                }
                else -> GatewayEvent.Unknown(eventFrame.event, eventFrame.payload)
            }
            
            gatewayEvent?.let { _eventChannel.trySend(it) }
            
            // Update state version
            eventFrame.stateVersion?.let { version ->
                _snapshot.value = _snapshot.value?.copy(stateVersion = version)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse event: ${e.message}")
        }
    }
    
    private fun cleanup() {
        pendingRequests.values.forEach { it.close() }
        pendingRequests.clear()
        webSocket = null
    }
}

/**
 * Connection state
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Gateway events
 */
sealed class GatewayEvent {
    data class Tick(val timestamp: Long) : GatewayEvent()
    data class Chat(val event: ChatEvent) : GatewayEvent()
    data class Agent(val event: AgentEvent) : GatewayEvent()
    data class Presence(val entries: List<PresenceEntry>) : GatewayEvent()
    data class Shutdown(val reason: String, val restartExpectedMs: Int?) : GatewayEvent()
    data class Unknown(val event: String, val payload: JsonElement?) : GatewayEvent()
}

/**
 * Gateway exception
 */
class GatewayException(val code: String, message: String) : Exception(message)
