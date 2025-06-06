package com.gamesmith.assistantapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectGeminiPayload(
    val initialConfig: LiveConfig
) : ClientMessagePayload

@Serializable
data class SendMessagePayload(
    val parts: List<Part>,
    val turnComplete: Boolean
) : ClientMessagePayload

@Serializable
@Deprecated("Use audio, video, or text instead of chunks")
data class SendRealtimeInputPayload(
    val text: String? = null, // JSON-encoded user input for real-time text (canvas, etc.)
    val audio: GenerativeContentBlob? = null, // Realtime audio input
    val video: GenerativeContentBlob? = null, // Realtime video (image frame)
    val activityStart: ActivityStart? = null,
    val activityEnd: ActivityEnd? = null,
    val audioStreamEnd: Boolean? = null,
    val chunks: List<GenerativeContentBlob>? = null // Deprecated
) : ClientMessagePayload

@Serializable
data class SendToolResponsePayload(
    val toolResponse: ToolResponseWrapper // Renamed from direct "toolResponse" to avoid conflict with a potential ServerToolResponse
) : ClientMessagePayload

@Serializable
data class ToolResponseWrapper(
    val functionResponses: List<FunctionResponsePart>
)

@Serializable
data class FunctionResponsePart(
    val id: String, // Corresponds to the FunctionCall id
    val name: String, // Name of the function/tool
    val response: kotlinx.serialization.json.JsonObject // Direct JSON object result from the tool
)

// UPDATE_CONFIG payload is now just LiveConfig directly (no wrapper)
// The wrapper will be added at the ClientMessageWrapper level

// DISCONNECT_GEMINI has no payload, so no specific data class needed here for ClientMessagePayload 