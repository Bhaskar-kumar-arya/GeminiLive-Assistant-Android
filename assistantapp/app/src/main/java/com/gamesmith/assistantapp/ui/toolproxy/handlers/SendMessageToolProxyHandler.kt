package com.gamesmith.assistantapp.ui.toolproxy.handlers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyActivity
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyHandler
import java.net.URLEncoder

class SendMessageToolProxyHandler : ToolProxyHandler {
    override fun canHandle(action: String): Boolean =
        action == ToolProxyActivity.ACTION_SEND_MESSAGE

    override fun start(activity: Activity, intent: Intent, onResult: (Intent) -> Unit) {
        val resultBroadcastAction = intent.getStringExtra("result_broadcast_action")
        val app = intent.getStringExtra("app") ?: "whatsapp"
        val message = intent.getStringExtra("message")
        val recipient = intent.getStringExtra("recipient")
        val mediaUri = intent.getStringExtra("mediaUri")
        try {
            val sendIntent = when (app) {
                "whatsapp" -> {
                    if (mediaUri.isNullOrBlank()) {
                        // Text only: require message
                        if (message.isNullOrBlank()) {
                            sendResult(activity, resultBroadcastAction, false, "Missing message parameter.", onResult)
                            return
                        }
                        if (recipient.isNullOrBlank()) {
                            throw IllegalArgumentException("Recipient is required for WhatsApp text message.")
                        }
                        val url = "https://wa.me/${recipient.replace(" ", "").replace("+", "")}?text=" +
                                URLEncoder.encode(message, "UTF-8")
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            setPackage("com.whatsapp")
                        }
                    } else {
                        // Media: message is optional
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            if (!message.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, message)
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaUri))
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                }
                "sms" -> {
                    Intent().apply {
                        if (!mediaUri.isNullOrBlank()) {
                            action = Intent.ACTION_SEND
                            type = "image/*"
                            if (!message.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, message)
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            action = Intent.ACTION_SENDTO
                            if (message.isNullOrBlank()) {
                                sendResult(activity, resultBroadcastAction, false, "Missing message parameter.", onResult)
                                return
                            }
                            putExtra("sms_body", message)
                        }
                        if (!recipient.isNullOrBlank()) {
                            data = Uri.parse("smsto:$recipient")
                        } else {
                            data = Uri.parse("smsto:")
                        }
                    }
                }
                else -> throw IllegalArgumentException("Unsupported app: $app")
            }
            val chooser = Intent.createChooser(sendIntent, "Send message")
            activity.startActivity(chooser)
            // Assume success if no exception
            sendResult(activity, resultBroadcastAction, true, null, onResult)
        } catch (e: Exception) {
            sendResult(activity, resultBroadcastAction, false, e.message, onResult)
        }
    }

    private fun sendResult(activity: Activity, action: String?, success: Boolean, error: String?, onResult: (Intent) -> Unit) {
        val resultIntent = Intent().apply {
            if (!action.isNullOrBlank()) this.action = action
            putExtra("success", success)
            if (!error.isNullOrBlank()) putExtra("error", error)
        }
        onResult(resultIntent)
        if (!action.isNullOrBlank()) {
            activity.sendBroadcast(resultIntent)
        }
    }
} 