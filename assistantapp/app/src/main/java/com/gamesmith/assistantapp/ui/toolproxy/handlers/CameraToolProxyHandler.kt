package com.gamesmith.assistantapp.ui.toolproxy.handlers

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyHandler
import com.gamesmith.assistantapp.ui.toolproxy.ToolProxyActivity
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraToolProxyHandler : ToolProxyHandler {
    private var photoUri: Uri? = null
    private var resultBroadcastAction: String? = null
    private var onResultCallback: ((Intent) -> Unit)? = null

    override fun canHandle(action: String): Boolean =
        action == ToolProxyActivity.ACTION_TAKE_PHOTO

    override fun start(activity: Activity, intent: Intent, onResult: (Intent) -> Unit) {
        this.onResultCallback = onResult
        resultBroadcastAction = intent.getStringExtra("result_broadcast_action")
        val photoFile = createImageFile(activity)
        photoUri = FileProvider.getUriForFile(
            activity,
            activity.applicationContext.packageName + ".fileprovider",
            photoFile
        )
        if (activity is ActivityResultCaller) {
            val cameraLauncher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                handleCameraResult(activity, success)
            }
            val uri = photoUri
            if (uri != null) {
                cameraLauncher.launch(uri)
            } else {
                finishWithCancel(activity)
            }
        } else {
            finishWithCancel(activity)
        }
    }

    private fun handleCameraResult(activity: Activity, success: Boolean) {
        if (success && photoUri != null) {
            val uriString = photoUri.toString()
            val galleryUri = saveImageToGallery(activity, photoUri!!)
            val resultIntent = Intent().apply {
                putExtra(ToolProxyActivity.EXTRA_IMAGE_URI, uriString)
                putExtra("gallery_uri", galleryUri?.toString())
            }
            onResultCallback?.invoke(resultIntent)
            resultBroadcastAction?.let { action ->
                val broadcast = Intent(action).apply {
                    putExtra(ToolProxyActivity.EXTRA_IMAGE_URI, uriString)
                    putExtra("gallery_uri", galleryUri?.toString())
                }
                activity.sendBroadcast(broadcast)
            }
        } else {
            finishWithCancel(activity)
        }
    }

    private fun finishWithCancel(activity: Activity) {
        onResultCallback?.invoke(Intent())
        resultBroadcastAction?.let { action ->
            val broadcast = Intent(action)
            activity.sendBroadcast(broadcast)
        }
    }

    private fun createImageFile(context: Activity): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun saveImageToGallery(context: Activity, imageUri: Uri): Uri? {
        return try {
            val resolver = context.applicationContext.contentResolver
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val inputStream = resolver.openInputStream(imageUri) ?: return null
            val galleryUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AssistantApp")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                uri
            } else {
                val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val destFile = File(imagesDir, fileName)
                FileInputStream(File(imageUri.path!!)).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, destFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                uri
            }
            inputStream.close()
            galleryUri
        } catch (e: Exception) {
            null
        }
    }
} 