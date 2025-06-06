package com.gamesmith.assistantapp.domain.tool

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import com.gamesmith.assistantapp.data.model.ToolUiSchema

interface NativeTool {
    val name: String
    val description: String
    val parametersJsonSchema: String // For dynamic declaration, can be empty for simple tools
    val defaultBehavior: String? // "NON_BLOCKING" or null
    val defaultScheduling: String? // "WHEN_IDLE", "INTERRUPT", "SILENT", or null
    suspend fun execute(args: Map<String, Any>, serviceContext: Context, serviceScope: CoroutineScope): ToolExecutionResult
}

sealed class ToolExecutionResult {
    data class Success(val data: Map<String, Any>) : ToolExecutionResult()
    data class Error(val message: String) : ToolExecutionResult()
    data class NeedsConfirmation(
        val prompt: String,
        val details: Map<String, Any>,
        val schema: ToolUiSchema? = null // If present, triggers schema-driven overlay
    ) : ToolExecutionResult()
} 