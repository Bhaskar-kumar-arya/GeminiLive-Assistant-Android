package com.gamesmith.assistantapp.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// GEMINI_CONNECTED has no payload

@Serializable
data class GeminiDisconnectedPayload(
    val reason: String? = null
) : ServerMessagePayload

@Serializable
data class GeminiErrorPayload(
    val message: String,
    val details: JsonElement? = null // Using JsonElement for flexible error details structure
) : ServerMessagePayload

// Updated to match server structure: { "serverContent": ModelTurn | TurnComplete | Interrupted }
@Serializable
data class ContentMessagePayload(
    val serverContent: ServerContent
) : ServerMessagePayload

@Serializable
data class ServerContent(
    val modelTurn: Content? = null,
    val userTurn: Content? = null,
    val interrupted: Boolean? = null,
    val usageMetadata: UsageMetadata? = null,
    val turnComplete: Boolean? = null
)

@Serializable
data class ToolCallMessagePayload(
    val toolCall: ToolCallDetails
) : ServerMessagePayload

@Serializable
data class ToolCallDetails(
    val functionCalls: List<FunctionCall>
)

@Serializable
data class ToolCallCancellationMessagePayload(
    val toolCallCancellation: ToolCallCancellationDetails
) : ServerMessagePayload

@Serializable
data class ToolCallCancellationDetails(
    val ids: List<String> // Changed from single id to ids array to match server
)

@Serializable
data class SetupCompleteMessagePayload(
    val setupComplete: SetupCompleteDetails
) : ServerMessagePayload

@Serializable
data class SetupCompleteDetails(
    val success: Boolean = true, // Made optional with default value to handle empty objects from server
    val error: GeminiErrorPayload? = null // Reusing GeminiErrorPayload for error details
)

// INTERRUPTED has optional payload, not strictly defined yet, can be JsonElement if needed.
// For now, we can assume no specific payload structure or define one if it becomes clear.
@Serializable
data class InterruptedPayload(
    val details: JsonElement? = null // Placeholder for any details about the interruption
) : ServerMessagePayload

// TURN_COMPLETE has optional payload, can also be JsonElement or defined if structure is known.
@Serializable
data class TurnCompletePayload(
    val details: JsonElement? = null // Placeholder for any details about turn completion
) : ServerMessagePayload

@Serializable
data class AudioChunkPayload(
    val data: String // Base64 encoded audio data
) : ServerMessagePayload

@Serializable
data class LogMessagePayload(
    val date: String, // ISO date string
    val type: String, // Changed back to 'type' to match server JSON structure
    val message: JsonElement,
    val count: Int? = null
) : ServerMessagePayload

@Serializable
// Custom: Gemini is actively speaking (audio is playing)
data class AssistantSpeakingPayload(
    val speaking: Boolean = true // Always true for this message
) : ServerMessagePayload

@Serializable
data class ImageGenerationResultPayload(
    val imageUrl: String? = null, // Server might still send this, or it could be removed
    val imageData: String? = null, // Add field for Base64 encoded image data
    val success: Boolean? = null,
    val error: String? = null
) : ServerMessagePayload