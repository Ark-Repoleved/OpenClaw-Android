package com.openclaw.dashboard.data.remote

import android.util.Log
import com.openclaw.dashboard.data.model.*
import kotlinx.coroutines.channels.Channel
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
    
    companion object {
        private const val TAG = "GatewayClient"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 0L  // No timeout for WebSocket
        private const val WRITE_TIMEOUT_SECONDS = 30L
        private const val PING_INTERVAL_SECONDS = 30L
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
    
    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()
    
    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()
    
    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()
    
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
                sendConnect(gatewayToken, gatewayPassword)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                cleanup()
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                _connectionState.value = ConnectionState.Disconnected
                cleanup()
            }
        })
    }
    
    /**
     * Disconnect from the Gateway
     */
    fun disconnect() {
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
     * Send chat message
     */
    suspend fun sendChatMessage(sessionKey: String, message: String): Result<Unit> {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", message)
        }
        
        return request("chat.send", params).map { }
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
        // Build hello frame manually to avoid null values
        val helloFrame = buildJsonObject {
            put("type", "hello")
            put("minProtocol", 1)
            put("maxProtocol", 1)
            
            // Build client object
            putJsonObject("client") {
                put("id", "openclaw-android")
                put("displayName", "OpenClaw Android")
                put("version", "1.0.0")
                put("platform", "android")
                put("deviceFamily", android.os.Build.MANUFACTURER)
                put("mode", "ui")
            }
            
            // Optional caps
            putJsonArray("caps") {
                add("tool-events")
            }
            
            // Only include auth if we have credentials
            if (!token.isNullOrBlank() || !password.isNullOrBlank()) {
                putJsonObject("auth") {
                    if (!token.isNullOrBlank()) put("token", token)
                    if (!password.isNullOrBlank()) put("password", password)
                }
            }
        }
        
        val frameJson = json.encodeToString(helloFrame)
        Log.d(TAG, "Sending hello frame: $frameJson")
        webSocket?.send(frameJson)
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse hello-ok: ${e.message}")
            _connectionState.value = ConnectionState.Error("Invalid server response")
        }
    }
    
    private fun handleResponse(jsonElement: JsonElement) {
        try {
            val response = json.decodeFromJsonElement<ResponseFrame>(jsonElement)
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
                "shutdown" -> {
                    eventFrame.payload?.let {
                        val shutdownEvent = json.decodeFromJsonElement<ShutdownEvent>(it)
                        GatewayEvent.Shutdown(shutdownEvent.reason, shutdownEvent.restartExpectedMs)
                    }
                }
                else -> GatewayEvent.Unknown(eventFrame.event, eventFrame.payload)
            }
            
            gatewayEvent?.let { _events.tryEmit(it) }
            
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
    data class Error(val message: String) : ConnectionState()
}

/**
 * Gateway events
 */
sealed class GatewayEvent {
    data class Tick(val timestamp: Long) : GatewayEvent()
    data class Chat(val event: ChatEvent) : GatewayEvent()
    data class Presence(val entries: List<PresenceEntry>) : GatewayEvent()
    data class Shutdown(val reason: String, val restartExpectedMs: Int?) : GatewayEvent()
    data class Unknown(val event: String, val payload: JsonElement?) : GatewayEvent()
}

/**
 * Gateway exception
 */
class GatewayException(val code: String, message: String) : Exception(message)
