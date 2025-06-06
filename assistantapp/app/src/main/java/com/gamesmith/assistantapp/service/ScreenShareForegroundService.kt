package com.gamesmith.assistantapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.gamesmith.assistantapp.R
import java.io.ByteArrayOutputStream
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.gamesmith.assistantapp.data.remote.GeminiRepository
import com.gamesmith.assistantapp.data.model.GenerativeContentBlob

@AndroidEntryPoint
class ScreenShareForegroundService : Service() {
    @Inject
    lateinit var geminiRepository: GeminiRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ScreenShareChannel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_SCREENSHOT_RESULT = "com.gamesmith.assistantapp.SCREENSHOT_RESULT"
        const val EXTRA_SCREENSHOT_BASE64 = "screenshot_base64"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data_intent"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Screen Sharing")
            .setContentText("Capturing your screen for Gemini...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            captureScreenshot(resultCode, data)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun captureScreenshot(resultCode: Int, data: Intent) {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenShareForegroundService",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, Handler(Looper.getMainLooper())
        )
        Handler(Looper.getMainLooper()).postDelayed({
            val image = imageReader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                val outputStream = ByteArrayOutputStream()
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val jpegBytes = outputStream.toByteArray()
                // Save to file
                val picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "AssistantApp")
                if (!appDir.exists()) appDir.mkdirs()
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(appDir, "screenshot_$timeStamp.jpg")
                FileOutputStream(file).use { it.write(jpegBytes) }
                // Encode base64
                val base64Jpeg = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                // Send to Gemini as SEND_REALTIME_INPUT
                serviceScope.launch {
                    geminiRepository.sendRealtimeInput(
                        video = GenerativeContentBlob(
                            mimeType = "image/jpeg",
                            data = base64Jpeg
                        )
                    )
                }
                image.close()
                bitmap.recycle()
                croppedBitmap.recycle()
                val resultIntent = Intent(ACTION_SCREENSHOT_RESULT).apply {
                    putExtra(EXTRA_SCREENSHOT_BASE64, base64Jpeg)
                    putExtra("screenshot_file_path", file.absolutePath)
                }
                sendBroadcast(resultIntent)
            }
            imageReader.close()
            virtualDisplay.release()
            mediaProjection.stop()
            stopSelf()
        }, 500) // Wait 500ms for the first frame
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screen Sharing",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
} 