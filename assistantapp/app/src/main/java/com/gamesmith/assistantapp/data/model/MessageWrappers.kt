package com.gamesmith.assistantapp.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Generic wrapper for messages sent FROM the client (mobile app) TO the server
@Serializable
data class ClientMessageWrapper(
    val type: String,
    val payload: JsonElement? = null // Using JsonElement for flexibility since UPDATE_CONFIG needs LiveConfig directly
)

// Generic wrapper for messages sent FROM the server TO the client (mobile app)
@Serializable
data class ServerMessageWrapper(
    val type: String,
    val payload: ServerMessagePayload? = null // Payload can be null for messages like GEMINI_CONNECTED
)

// --- Client Payloads ---
@Serializable
sealed interface ClientMessagePayload

// --- Server Payloads ---
@Serializable
sealed interface ServerMessagePayload 