package com.gamesmith.assistantapp.service




import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button
import android.widget.EditText
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.ViewGroup
import androidx.core.content.getSystemService
import com.gamesmith.assistantapp.R
import com.gamesmith.assistantapp.data.model.ToolUiSchema
import com.gamesmith.assistantapp.data.model.ToolUiElement

// OverlayManager is now deprecated. Use ToolUIManager for all overlays.
@Deprecated("Use ToolUIManager for all overlays.")
class OverlayManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var statusTextView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    fun showOverlay(statusText: String = "Gemini Assistant Running") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { showOverlay(statusText) }
            return
        }
        if (overlayView != null) return // Already shown
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        // For now, use a simple TextView as overlay. In future, inflate a custom layout.
        val textView = TextView(context).apply {
            text = statusText
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 16, 32, 16)
            textSize = 16f
        }
        statusTextView = textView
        overlayView = textView
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 100
        windowManager?.addView(overlayView, params)
        scheduleHideOverlay()
    }

    fun hideOverlay() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        overlayView?.let { view ->
            windowManager?.removeView(view)
            overlayView = null
        }
    }

    fun updateStatus(newStatus: String) {
        if (overlayView == null) {
            showOverlay(newStatus)
            return
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            statusTextView?.text = newStatus
        } else {
            Handler(Looper.getMainLooper()).post {
                statusTextView?.text = newStatus
            }
        }
        scheduleHideOverlay()
    }

    private fun scheduleHideOverlay() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = Runnable { hideOverlay() }
        handler.postDelayed(hideRunnable!!, 2000)
    }

    // --- SCHEMA-DRIVEN TOOL UI OVERLAY (stub) ---
    fun showToolUiOverlay(schema: ToolUiSchema, onResult: (Map<String, Any>) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { showToolUiOverlay(schema, onResult) }
            return
        }
        if (overlayView != null) return // Already shown
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)
        // --- Dynamic vertical layout for all elements ---
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xAA222244.toInt())
            setPadding(32, 32, 32, 32)
        }
        val inputValues = mutableMapOf<String, EditText>()
        schema.elements.forEach { element ->
            when (element) {
                is ToolUiElement.Text -> {
                    val tv = TextView(context).apply {
                        text = element.value
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 16f
                        setPadding(0, 8, 0, 8)
                    }
                    container.addView(tv)
                }
                is ToolUiElement.Image -> {
                    val iv = ImageView(context)
                    // Support data URI (base64) or http(s) in future
                    if (element.src.startsWith("data:image/")) {
                        val base64 = element.src.substringAfter(",", "")
                        try {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            iv.setImageBitmap(bmp)
                        } catch (_: Exception) {}
                    }
                    val params = android.widget.LinearLayout.LayoutParams(200, 200)
                    params.setMargins(0, 8, 0, 8)
                    iv.layoutParams = params
                    container.addView(iv)
                }
                is ToolUiElement.Button -> {
                    val btn = Button(context).apply {
                        text = element.label
                        setOnClickListener {
                            // Collect input values if any
                            val result = inputValues.mapValues { it.value.text.toString() }.toMutableMap()
                            result["action"] = element.action
                            hideOverlay()
                            onResult(result)
                        }
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(btn)
                }
                is ToolUiElement.InputField -> {
                    val et = EditText(context).apply {
                        hint = element.hint ?: element.label
                        setText(element.default ?: "")
                        setTextColor(0xFFFFFFFF.toInt())
                        setHintTextColor(0xFFCCCCCC.toInt())
                    }
                    val label = TextView(context).apply {
                        text = element.label
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 14f
                    }
                    container.addView(label)
                    container.addView(et)
                    inputValues[element.id] = et
                }
                is ToolUiElement.DismissButton -> {
                    // No-op for OverlayManager (dismiss_button is only for canvas overlays)
                }
                else -> {}
            }
        }
        // Add to window
        overlayView = container
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 200
        windowManager?.addView(overlayView, params)
    }
} 