package com.gamesmith.assistantapp.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LiveConfig(
    val model: String, // e.g., "models/gemini-2.0-flash-exp"
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null,
    val tools: List<Tool>? = null,
    val realtimeInputConfig: RealtimeInputConfig? = null
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val candidateCount: Int? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val responseMimeType: String? = null, // e.g., "text/plain", "application/json"
    val responseModalities: List<String>? = null, // e.g., ["text"], ["audio"], ["text", "audio"]
    val speechConfig: SpeechConfig? = null
)

@Serializable
data class SpeechConfig(
    val voiceConfig: VoiceConfig
)

@Serializable
data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

@Serializable
data class PrebuiltVoiceConfig(
    val voiceName: String // e.g., "Aoede", "Puck", "Charon", "Kore", "Fenrir"
)

@Serializable
data class SafetySetting(
    val category: String, // e.g., "HARM_CATEGORY_HARASSMENT"
    val threshold: String // e.g., "BLOCK_MEDIUM_AND_ABOVE"
)

@Serializable
data class Tool(
    val functionDeclarations: List<FunctionDeclaration>? = null,
    val googleSearch: JsonObject? = null // Using JsonObject for an empty object {}
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: FunctionParameters? = null, // Optional
    val behavior: String? = null // "NON_BLOCKING" or null
)

@Serializable
data class FunctionParameters(
    val type: String = "OBJECT", // Typically "OBJECT" for structured params
    val properties: Map<String, FunctionParameterProperty>,
    val required: List<String>? = null // Optional list of required parameter names
)

@Serializable
data class FunctionParameterProperty(
    val type: String, // e.g., "STRING", "NUMBER", "BOOLEAN", "ARRAY", "OBJECT"
    val description: String? = null, // Optional
    val enum: List<String>? = null, // Optional, for restricted string values
    val items: FunctionParameterProperty? = null // Optional, for type "ARRAY" to describe array item type
)

@Serializable
data class Schema(
    val type: String, // e.g., "OBJECT", "STRING", "NUMBER", "BOOLEAN", "ARRAY"
    val properties: Map<String, SchemaProperty>? = null,
    val required: List<String>? = null,
    val description: String? = null,
    val format: String? = null, // e.g. "int32", "double", "date-time"
    val enum: List<String>? = null,
    val items: Schema? = null // For type "ARRAY"
)

@Serializable
data class SchemaProperty(
    val type: String,
    val description: String? = null,
    val format: String? = null,
    val enum: List<String>? = null,
    val items: Schema? = null // For type "ARRAY"
)

@Serializable
data class RealtimeInputConfig(
    val automaticActivityDetection: AutomaticActivityDetection
)

@Serializable
data class AutomaticActivityDetection(
    val disabled: Boolean = false,
    val startOfSpeechSensitivity: String = "START_SENSITIVITY_LOW",
    val endOfSpeechSensitivity: String = "END_SENSITIVITY_LOW", 
    val prefixPaddingMs: Int = 20,
    val silenceDurationMs: Int = 100,
)

// Example FunctionDeclaration for test_gui_tool:
// FunctionDeclaration(
//     name = "test_gui_tool",
//     description = "A test tool that shows a GUI overlay and echoes the input.",
//     parameters = FunctionParameters(
//         type = "OBJECT",
//         properties = emptyMap(),
//         required = emptyList()
//     )
// ) 