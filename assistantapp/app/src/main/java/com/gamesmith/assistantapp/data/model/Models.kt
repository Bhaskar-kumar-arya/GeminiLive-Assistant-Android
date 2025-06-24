package com.gamesmith.assistantapp.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.SerialName

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
    // val functionCall: FunctionCall? = null, // Will be defined if model can send function calls in Parts
    val functionResponse: FunctionResponse? = null // For when a Part contains a function response
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String // Base64 encoded data
)

@Serializable
data class Content(
    val parts: List<Part>,
    val role: String? = null // e.g., "user", "model", "function"
)

@Serializable
data class FunctionCall(
    val id: String? = null, // ID might be optional if it's part of a request rather than a direct call
    val name: String,
    val args: JsonObject // Arguments for the function
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject, // The result from the function execution
    val scheduling: String? = null // "INTERRUPT", "WHEN_IDLE", "SILENT"
)

@Serializable
data class GenerativeContentBlob(
    val mimeType: String, // e.g., "audio/pcm;rate=16000". For text, use the text field in SendRealtimeInputPayload instead.
    val data: String // Base64 encoded audio/video data
)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

// --- Tool UI Schema for System Alert Window overlays ---
@Serializable
data class ToolUiSchema(
    val type: String = "canvas", // For now, only "canvas" is supported
    val elements: List<ToolUiElement>
)

@Serializable
sealed class ToolUiElement {
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : ToolUiElement()

    @Serializable
    @SerialName("image")
    data class Image(val src: String) : ToolUiElement()

    @Serializable
    @SerialName("button")
    data class Button(val label: String, val action: String) : ToolUiElement()

    @Serializable
    @SerialName("input")
    data class InputField(val label: String, val id: String, val hint: String? = null, val default: String? = null) : ToolUiElement()

    @Serializable
    @SerialName("dismiss_button")
    data class DismissButton(val label: String = "Dismiss") : ToolUiElement()

    // Add more as needed: audio, video, list, form, etc.
}

@Serializable
// Placeholder for protocol compatibility
data class ActivityStart(val dummy: String = "")

@Serializable
// Placeholder for protocol compatibility
data class ActivityEnd(val dummy: String = "") 
@Serializable
data class GenerateImagePayload(
    val text: String,
    val imageUri: String? = null // Optional URI for input image
)

