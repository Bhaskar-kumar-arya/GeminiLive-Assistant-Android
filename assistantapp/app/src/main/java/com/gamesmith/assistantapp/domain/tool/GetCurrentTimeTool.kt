package com.gamesmith.assistantapp.domain.tool

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GetCurrentTimeTool : NativeTool {
    override val name: String = "get_current_time"
    override val description: String = "Returns the current device time as a formatted string."
    override val parametersJsonSchema: String = "{}" // No parameters
    override val defaultBehavior: String? = "NON_BLOCKING"
    override val defaultScheduling: String? = "WHEN_IDLE"

    override suspend fun execute(
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        return try {
            val now = LocalDateTime.now()
            val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            ToolExecutionResult.Success(mapOf("current_time" to formatted))
        } catch (e: Exception) {
            ToolExecutionResult.Error("Failed to get current time: ${e.message}")
        }
    }
} 