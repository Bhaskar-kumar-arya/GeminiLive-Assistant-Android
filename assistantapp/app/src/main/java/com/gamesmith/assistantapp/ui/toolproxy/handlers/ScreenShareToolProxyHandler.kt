package com.gamesmith.assistantapp.ui.toolproxy.handlers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.gamesmith.assistantapp.service.ScreenShareForegroundService
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyActivity
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyHandler

class SendScreenSnapshotToolProxyHandler : ToolProxyHandler {
    override fun canHandle(action: String): Boolean =
        action == ToolProxyActivity.ACTION_SHARE_SCREEN

    override fun start(activity: Activity, intent: Intent, onResult: (Intent) -> Unit) {
        val resultBroadcastAction = intent.getStringExtra("result_broadcast_action")
        // Start the foreground service before requesting screen capture
        val serviceIntent = Intent(activity, ScreenShareForegroundService::class.java)
        ContextCompat.startForegroundService(activity, serviceIntent)
        val mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(screenCaptureIntent, REQUEST_CODE_SCREEN_CAPTURE)
        if (activity is ToolProxyActivity) {
            activity.setScreenShareResultHandler { resultCode: Int, data: Intent? ->
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // Pass the result to the service
                    val serviceIntentWithData = Intent(activity, ScreenShareForegroundService::class.java).apply {
                        putExtra(ScreenShareForegroundService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(ScreenShareForegroundService.EXTRA_DATA, data)
                    }
                    ContextCompat.startForegroundService(activity, serviceIntentWithData)
                    // Listen for the screenshot result broadcast
                    val filter = IntentFilter(ScreenShareForegroundService.ACTION_SCREENSHOT_RESULT)
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            val base64Jpeg = intent?.getStringExtra(ScreenShareForegroundService.EXTRA_SCREENSHOT_BASE64)
                            val resultIntent = Intent().apply {
                                if (base64Jpeg != null) putExtra(EXTRA_SCREENSHOT_BASE64, base64Jpeg)
                            }
                            onResult(resultIntent)
                            resultBroadcastAction?.let { action ->
                                val broadcast = Intent(action).apply {
                                    if (base64Jpeg != null) putExtra(EXTRA_SCREENSHOT_BASE64, base64Jpeg)
                                }
                                activity.sendBroadcast(broadcast)
                            }
                            activity.unregisterReceiver(this)
                        }
                    }
                    activity.registerReceiver(receiver, filter, null, Handler(Looper.getMainLooper()))
                } else {
                    onResult(Intent())
                }
            }
        } else {
            onResult(Intent())
        }
    }

    companion object {
        const val REQUEST_CODE_SCREEN_CAPTURE = 9001
        const val EXTRA_SCREENSHOT_BASE64 = "screenshot_base64"
    }
} 