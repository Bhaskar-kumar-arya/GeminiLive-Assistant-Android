package com.gamesmith.assistantapp.domain.tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyActivity
import kotlin.coroutines.resume

class SendMessageTool : NativeTool {
    override val name: String = "send_message_tool"
    override val description: String = "Sends a message or media via WhatsApp (default) or SMS. Params: recipient number (required for text), message (required for text), mediaUri (optional, required for media), app (optional: 'whatsapp' or 'sms'). If sending media, message is optional."
    override val parametersJsonSchema: String = """{"type":"object","properties":{"recipient":{"type":"string"},"message":{"type":"string"},"mediaUri":{"type":"string"},"app":{"type":"string","enum":["whatsapp","sms"]}},"required":[]}"""
    override val defaultBehavior: String? = "NON_BLOCKING"
    override val defaultScheduling: String? = "WHEN_IDLE"

    override suspend fun execute(
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        val message = args["message"] as? String
        val recipient = args["recipient"] as? String
        val mediaUri = args["mediaUri"] as? String
        val app = (args["app"] as? String)?.lowercase() ?: "whatsapp"
        if (mediaUri.isNullOrBlank()) {
            // Text-only: require both recipient and message
            if (message.isNullOrBlank()) {
                return ToolExecutionResult.Error("Missing required 'message' parameter for text-only send.")
            }
            if (recipient.isNullOrBlank()) {
                return ToolExecutionResult.Error("Missing required 'recipient' parameter for text-only send.")
            }
        }
        if (app != "whatsapp" && app != "sms") {
            return ToolExecutionResult.Error("Unsupported app: $app. Only 'whatsapp' and 'sms' are supported.")
        }
        val action = "com.gamesmith.assistantapp.SEND_MESSAGE_RESULT_${System.currentTimeMillis()}"
        val intent = ToolProxyActivity.createSendMessageIntent(serviceContext).apply {
            putExtra("result_broadcast_action", action)
            putExtra("message", message)
            if (!recipient.isNullOrBlank()) putExtra("recipient", recipient)
            if (!mediaUri.isNullOrBlank()) putExtra("mediaUri", mediaUri)
            putExtra("app", app)
        }
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == action) {
                        val success = intent.getBooleanExtra("success", false)
                        val errorMsg = intent.getStringExtra("error")
                        if (success) {
                            cont.resume(ToolExecutionResult.Success(mapOf("status" to "sent")))
                        } else {
                            cont.resume(ToolExecutionResult.Error(errorMsg ?: "Failed to send message."))
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
                cont.resume(ToolExecutionResult.Error("Failed to launch send message intent: ${e.message}"))
            }
        }
    }
} 