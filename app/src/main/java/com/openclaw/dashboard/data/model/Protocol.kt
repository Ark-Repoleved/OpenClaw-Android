package com.openclaw.dashboard.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Gateway Protocol Frame Types
 * Based on OpenClaw Gateway WebSocket Protocol
 */

// ============== Frame Types ==============

@Serializable
sealed class GatewayFrame {
    abstract val type: String
}

@Serializable
@SerialName("req")
data class RequestFrame(
    override val type: String = "req",
    val id: String,
    val method: String,
    val params: JsonElement? = null
) : GatewayFrame()

@Serializable
@SerialName("res")
data class ResponseFrame(
    override val type: String = "res",
    val id: String,
    val ok: Boolean,
    val payload: JsonElement? = null,
    val error: ErrorShape? = null
) : GatewayFrame()

@Serializable
@SerialName("event")
data class EventFrame(
    override val type: String = "event",
    val event: String,
    val payload: JsonElement? = null,
    val seq: Int? = null,
    val stateVersion: StateVersion? = null
) : GatewayFrame()

@Serializable
data class ErrorShape(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
    val retryable: Boolean? = null,
    val retryAfterMs: Int? = null
)

@Serializable
data class StateVersion(
    val presence: Int,
    val health: Int
)

// ============== Connect ==============

@Serializable
data class ConnectParams(
    val minProtocol: Int = 1,
    val maxProtocol: Int = 1,
    val client: ClientInfo,
    val caps: List<String>? = null,
    val auth: AuthParams? = null
)

@Serializable
data class ClientInfo(
    val id: String,
    val displayName: String? = null,
    val version: String,
    val platform: String,
    val deviceFamily: String? = null,
    val mode: String = "dashboard"
)

@Serializable
data class AuthParams(
    val token: String? = null,
    val password: String? = null
)

@Serializable
data class HelloOk(
    val type: String,
    val protocol: Int,
    val server: ServerInfo,
    val features: Features,
    val snapshot: Snapshot,
    val canvasHostUrl: String? = null,
    val auth: AuthResult? = null,
    val policy: Policy
)

@Serializable
data class ServerInfo(
    val version: String,
    val commit: String? = null,
    val host: String? = null,
    val connId: String
)

@Serializable
data class Features(
    val methods: List<String>,
    val events: List<String>
)

@Serializable
data class AuthResult(
    val deviceToken: String,
    val role: String,
    val scopes: List<String>,
    val issuedAtMs: Long? = null
)

@Serializable
data class Policy(
    val maxPayload: Int,
    val maxBufferedBytes: Int,
    val tickIntervalMs: Int
)

// ============== Snapshot ==============

@Serializable
data class Snapshot(
    val presence: List<PresenceEntry> = emptyList(),
    val health: JsonElement? = null,
    val stateVersion: StateVersion,
    val uptimeMs: Long,
    val configPath: String? = null,
    val stateDir: String? = null,
    val sessionDefaults: SessionDefaults? = null
)

@Serializable
data class PresenceEntry(
    val host: String? = null,
    val ip: String? = null,
    val version: String? = null,
    val platform: String? = null,
    val deviceFamily: String? = null,
    val modelIdentifier: String? = null,
    val mode: String? = null,
    val lastInputSeconds: Int? = null,
    val reason: String? = null,
    val tags: List<String>? = null,
    val text: String? = null,
    val ts: Long,
    val deviceId: String? = null,
    val roles: List<String>? = null,
    val scopes: List<String>? = null,
    val instanceId: String? = null
)

@Serializable
data class SessionDefaults(
    val defaultAgentId: String,
    val mainKey: String,
    val mainSessionKey: String,
    val scope: String? = null
)

// ============== Sessions ==============

@Serializable
data class SessionInfo(
    val key: String,
    val agentId: String? = null,
    val label: String? = null,
    val createdAt: Long? = null,
    val lastActivityAt: Long? = null,
    val messageCount: Int? = null,
    val derivedTitle: String? = null,
    val lastMessage: String? = null,
    val tokensUsed: Long? = null
)

@Serializable
data class SessionsListParams(
    val limit: Int? = null,
    val activeMinutes: Int? = null,
    val includeGlobal: Boolean? = null,
    val includeUnknown: Boolean? = null,
    val includeDerivedTitles: Boolean? = null,
    val includeLastMessage: Boolean? = null,
    val label: String? = null,
    val agentId: String? = null,
    val search: String? = null
)

@Serializable
data class SessionsListResult(
    val sessions: List<SessionInfo>
)

@Serializable
data class SessionsDeleteParams(
    val key: String,
    val deleteTranscript: Boolean? = null
)

@Serializable
data class SessionsUsageParams(
    val key: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val limit: Int? = null,
    val includeContextWeight: Boolean? = null
)

@Serializable
data class SessionUsageInfo(
    val key: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val estimatedCost: Double? = null
)

@Serializable
data class SessionsUsageResult(
    val sessions: List<SessionUsageInfo>,
    val totals: UsageTotals? = null
)

@Serializable
data class UsageTotals(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val estimatedCost: Double? = null
)

// ============== Chat ==============

@Serializable
data class ChatSendParams(
    val sessionKey: String,
    val message: String,
    val attachments: List<JsonElement>? = null
)

@Serializable
data class ChatEvent(
    val sessionKey: String,
    val role: String,
    val content: String? = null,
    val toolCalls: List<JsonElement>? = null,
    val timestamp: Long? = null,
    val messageId: String? = null,
    @SerialName("type") val eventType: String? = null
)

// ============== Tick & Shutdown ==============

@Serializable
data class TickEvent(
    val ts: Long
)

@Serializable
data class ShutdownEvent(
    val reason: String,
    val restartExpectedMs: Int? = null
)
