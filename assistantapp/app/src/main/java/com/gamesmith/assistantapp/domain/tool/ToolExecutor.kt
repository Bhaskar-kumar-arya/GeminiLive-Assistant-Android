package com.gamesmith.assistantapp.domain.tool

import android.content.Context
import kotlinx.coroutines.CoroutineScope

class ToolExecutor(
    internal val tools: Set<NativeTool> // To be provided via DI (Hilt multibindings) in the future
) {
    suspend fun executeTool(
        name: String,
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        val tool = tools.find { it.name == name }
        return if (tool != null) {
            tool.execute(args, serviceContext, serviceScope)
        } else {
            ToolExecutionResult.Error("Tool '$name' not found.")
        }
    }
    // TODO: Add logging, metrics, and more robust error handling as needed.
    // TODO: Integrate with DI (Hilt multibindings) for SOLID compliance.
} 