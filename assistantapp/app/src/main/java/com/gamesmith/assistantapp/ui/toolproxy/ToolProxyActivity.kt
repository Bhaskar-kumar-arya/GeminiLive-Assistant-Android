package com.gamesmith.assistantapp.ui.toolproxy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gamesmith.assistantapp.ui.toolproxy.handlers.CameraToolProxyHandler
import com.gamesmith.assistantapp.ui.toolproxy.handlers.SendMessageToolProxyHandler
import com.gamesmith.assistantapp.ui.toolproxy.handlers.SendScreenSnapshotToolProxyHandler

class ToolProxyActivity : AppCompatActivity() {
    private val handlers: List<ToolProxyHandler> = listOf(CameraToolProxyHandler(), SendMessageToolProxyHandler(), SendScreenSnapshotToolProxyHandler())

    private var screenShareResultHandler: ((resultCode: Int, data: Intent?) -> Unit)? = null

    fun setScreenShareResultHandler(handler: (resultCode: Int, data: Intent?) -> Unit) {
        screenShareResultHandler = handler
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.gamesmith.assistantapp.ui.toolproxy.handlers.SendScreenSnapshotToolProxyHandler.REQUEST_CODE_SCREEN_CAPTURE) {
            screenShareResultHandler?.invoke(resultCode, data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra(EXTRA_TOOL_ACTION)
        val handler = handlers.find { it.canHandle(action ?: "") }
        if (handler != null) {
            handler.start(this, intent) { resultIntent ->
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    companion object {
        const val EXTRA_TOOL_ACTION = "extra_tool_action"
        const val ACTION_TAKE_PHOTO = "take_photo"
        const val EXTRA_IMAGE_URI = "image_uri"
        const val ACTION_SEND_MESSAGE = "send_message"
        const val ACTION_SHARE_SCREEN = "share_screen"

        fun createTakePhotoIntent(context: Context): Intent {
            return Intent(context, ToolProxyActivity::class.java).apply {
                putExtra(EXTRA_TOOL_ACTION, ACTION_TAKE_PHOTO)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun createSendMessageIntent(context: Context): Intent {
            return Intent(context, ToolProxyActivity::class.java).apply {
                putExtra(EXTRA_TOOL_ACTION, ACTION_SEND_MESSAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun createShareScreenIntent(context: Context): Intent {
            return Intent(context, ToolProxyActivity::class.java).apply {
                putExtra(EXTRA_TOOL_ACTION, ACTION_SHARE_SCREEN)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
} 