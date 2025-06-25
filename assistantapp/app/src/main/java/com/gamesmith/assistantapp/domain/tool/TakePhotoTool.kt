package com.gamesmith.assistantapp.domain.tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class TakePhotoTool : NativeTool {
    override val name: String = "take_photo_tool"
    override val description: String = "opens the device camera,and returns the image URI after the user has taken the photo.wait till you recieve one and dont assume on your own"
    override val parametersJsonSchema: String = """{"type":"object","properties":{},"required":[]}"""
    override val defaultBehavior: String? = "BLOCKING"
    override val defaultScheduling: String? = "INTERRUPT"

    override suspend fun execute(
        args: Map<String, Any>,
        serviceContext: Context,
        serviceScope: CoroutineScope
    ): ToolExecutionResult {
        // Check camera permission
        val hasCamera = serviceContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val cameraPerm = ContextCompat.checkSelfPermission(serviceContext, android.Manifest.permission.CAMERA)
        if (!hasCamera || cameraPerm != PackageManager.PERMISSION_GRANTED) {
            return ToolExecutionResult.Error("Camera not available or permission not granted.")
        }
        // Prepare to receive result via broadcast
        val action = "com.gamesmith.assistantapp.TAKE_PHOTO_RESULT_${System.currentTimeMillis()}"
        val intent = ToolProxyActivity.createTakePhotoIntent(serviceContext).apply {
            putExtra("result_broadcast_action", action)
        }
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == action) {
                        val uri = intent.getStringExtra(ToolProxyActivity.EXTRA_IMAGE_URI)
                        if (uri != null) {
                            cont.resume(ToolExecutionResult.Success(mapOf("image_uri" to uri)))
                        } else {
                            cont.resume(ToolExecutionResult.Error("Photo capture failed or cancelled."))
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
                cont.resume(ToolExecutionResult.Error("Failed to launch camera: ${e.message}"))
            }
        }
    }

    private fun createImageFile(context: Context): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
} 