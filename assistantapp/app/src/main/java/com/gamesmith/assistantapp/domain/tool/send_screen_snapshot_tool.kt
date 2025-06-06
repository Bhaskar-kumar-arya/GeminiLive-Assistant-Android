package com.gamesmith.assistantapp.domain.tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyActivity
import com.gamesmith.assistantapp.ui.toolproxy.handlers.SendScreenSnapshotToolProxyHandler
import kotlin.coroutines.resume

class SendScreenSnapshotTool : NativeTool {
    override val name: String = "send_screen_snapshot_tool"
    override val description: String = "Captures and sends a snapshot of the current device screen to you."
    override val parametersJsonSchema: String = """{"type":"object","properties":{},"required":[]}"""
    override val defaultBehavior: String? = "BLOCKING"
    override val defaultScheduling: String? = "WHEN_IDLE"

    override suspend fun execute(
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        val action = "com.gamesmith.assistantapp.SHARE_SCREEN_RESULT_${System.currentTimeMillis()}"
        val intent = ToolProxyActivity.createShareScreenIntent(serviceContext).apply {
            putExtra("result_broadcast_action", action)
        }
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == action) {
                        val base64 = intent.getStringExtra(SendScreenSnapshotToolProxyHandler.EXTRA_SCREENSHOT_BASE64)
                        if (base64 != null) {
                            cont.resume(ToolExecutionResult.Success(mapOf("screenshot_base64" to base64)))
                        } else {
                            cont.resume(ToolExecutionResult.Error("Screen capture failed or cancelled."))
                        }
                        serviceContext.unregisterReceiver(this)
                    }
                }
            }
            serviceContext.registerReceiver(receiver, IntentFilter(action))
            try {
                serviceContext.startActivity(intent)
            } catch (e: Exception) {
                serviceContext.unregisterReceiver(receiver)
                cont.resume(ToolExecutionResult.Error("Failed to launch screen share: "+e.message))
            }
        }
    }
} 