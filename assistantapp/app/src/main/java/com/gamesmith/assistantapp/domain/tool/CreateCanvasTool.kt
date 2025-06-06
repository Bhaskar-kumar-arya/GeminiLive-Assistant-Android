package com.gamesmith.assistantapp.domain.tool

import android.content.Context
import android.util.Log
import com.gamesmith.assistantapp.data.model.ToolUiSchema
import com.gamesmith.assistantapp.service.ToolUIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class CreateCanvasTool(private val toolUIManager: ToolUIManager) : NativeTool {
    override val name: String = "create_canvas_tool"
    override val description: String = """
Creates a new scrollable canvas overlay (destroying any previous canvas). Use this to start a new UI sheet for the user. You can use this to show user different types of content on the screen.choose wisely when you should display things and when you should speak instead, as sometimes reading is better than listening. The 'schema' argument MUST be a JSON string (all keys and string values use double quotes) describing the initial elements to display.
Supported element types:
- text: The 'value' field supports HTML formatting for text (e.g., '<i>italic</i>', '<b>bold</b>', '<a href=\"url\">link</a>').
- image: Provide 'src' as a data URI or URL.
- input: Define 'label', 'id', and optional 'hint'.
- button: Define 'label' and 'action'.
Example schema:

{"type":"canvas","elements":[{"type":"text","value":"Welcome! You can use <b>HTML</b> here."},{"type":"button","label":"OK","action":"confirm"}]}

Call this before using append_to_canvas_tool.
"""
    override val parametersJsonSchema: String = """{"type":"object","properties":{"schema":{"type":"string","description":"A JSON string (all double quotes) representing ToolUiSchema."}},"required":["schema"]}"""
    override val defaultBehavior: String? = "NON_BLOCKING"
    override val defaultScheduling: String? = "WHEN_IDLE"

    override suspend fun execute(
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        Log.d("CreateCanvasTool", "Executing create_canvas_tool with args: $args")
        // If user input is present (after overlay interaction), return it as success
        if (args.containsKey("_user_input")) {
            @Suppress("UNCHECKED_CAST")
            val userInput = args["_user_input"] as? Map<String, Any> ?: emptyMap()
            return ToolExecutionResult.Success(userInput)
        }
        // Parse the schema from args
        val schemaJson = args["schema"] as? String
        if (schemaJson.isNullOrBlank()) {
            Log.e("CreateCanvasTool", "Missing or invalid 'schema' argument.")
            return ToolExecutionResult.Error("Missing or invalid 'schema' argument.")
        }
        val schema = try {
            Json.decodeFromString<ToolUiSchema>(schemaJson)
        } catch (e: Exception) {
            Log.e("CreateCanvasTool", "Failed to parse ToolUiSchema: ${e.message}")
            return ToolExecutionResult.Error("Failed to parse ToolUiSchema: ${e.message}")
        }
        // Return NeedsConfirmation to trigger overlay
        return ToolExecutionResult.NeedsConfirmation(
            prompt = "Please interact with the canvas.",
            details = emptyMap(),
            schema = schema
        )
    }
} 