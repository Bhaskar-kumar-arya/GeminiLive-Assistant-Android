package com.gamesmith.assistantapp.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.graphics.BitmapFactory
import com.gamesmith.assistantapp.R
import android.text.Html
import android.util.Base64
import com.gamesmith.assistantapp.data.model.ToolUiElement
import com.gamesmith.assistantapp.data.model.ToolUiSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject
import androidx.core.content.ContextCompat
import android.graphics.drawable.GradientDrawable
import android.widget.ImageButton
import android.text.method.LinkMovementMethod

@ServiceScoped
class SystemAlertWindowToolUIManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ToolUIManager {
    private var windowManager: WindowManager? = null
    private val overlayViewsByType = mutableMapOf<String, View>()
    private val handler = Handler(Looper.getMainLooper())
    private val canvasElements = mutableListOf<ToolUiElement>()
    private var canvasCallback: ((Map<String, Any>) -> Unit)? = null

    override fun showToolUI(schema: ToolUiSchema, callback: (Map<String, Any>) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { showToolUI(schema, callback) }
            return
        }
        dismissToolUI() // Dismiss any existing UI first
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt()) // Slightly more opaque background
            setPadding(32, 32, 32, 32)
        }
        val inputValues = mutableMapOf<String, EditText>()
        val hasInputField = schema.elements.any { it is ToolUiElement.InputField }

        schema.elements.forEach { element ->
            when (element) {
                is ToolUiElement.Text -> {
                    val tv = TextView(context).apply {
                        Log.d("ToolUIManager", "Raw text from Gemini for showToolUI: ${element.value}")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            text = Html.fromHtml(element.value, Html.FROM_HTML_MODE_COMPACT)
                        } else {
                            @Suppress("DEPRECATION")
                            text = Html.fromHtml(element.value)
                        }
                        movementMethod = LinkMovementMethod.getInstance()
                        Log.d("ToolUIManager", "Formatted text for showToolUI: $text")
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 16f
                        setPadding(0, 8, 0, 8)
                    }
                    container.addView(tv)
                }
                is ToolUiElement.Image -> {
                    val iv = ImageView(context)
                    if (element.src.startsWith("data:image/")) {
                        val base64 = element.src.substringAfter(",", "")
                        try {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            iv.setImageBitmap(bmp)
                        } catch (e: Exception) {
                            Log.e("ToolUIManager", "Error decoding image: ${e.message}")
                        }
                    }
                    val params = android.widget.LinearLayout.LayoutParams(200, 200) // Consider making this configurable
                    params.setMargins(0, 8, 0, 8)
                    iv.layoutParams = params
                    container.addView(iv)
                }
                is ToolUiElement.Button -> {
                    val btn = Button(context).apply {
                        text = element.label
                        setOnClickListener {
                            val result = inputValues.mapValues { it.value.text.toString() }.toMutableMap()
                            result["action"] = element.action
                            // Hide keyboard before dismissing UI if an input field was shown
                            if (hasInputField) {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(this.windowToken, 0)
                            }
                            dismissToolUI()
                            callback(result)
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
                        isFocusable = true
                        isFocusableInTouchMode = true
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
                    // No-op for showToolUI (dismiss_button is only for canvas overlays)
                }
                else -> {}
            }
        }

        val flags = if (hasInputField) {
            // Make window focusable for input fields
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL // Allows touches outside to pass (optional)
            // No FLAG_NOT_FOCUSABLE, so it *is* focusable.
            // No FLAG_ALT_FOCUSABLE_IM, as window itself will be focusable.
        } else {
            // For status overlays without input, make them non-focusable.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

        val overlayView = container
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 200 // Consider making this configurable or dynamic

        try {
            windowManager?.addView(overlayView, params)
            if (hasInputField) {
                val firstEditText = inputValues.values.firstOrNull()
                if (firstEditText != null) {
                    handler.postDelayed({
                        firstEditText.requestFocus()
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.restartInput(firstEditText)
                        imm.showSoftInput(firstEditText, InputMethodManager.SHOW_FORCED)
                    }, 120)
                }
            }
        } catch (e: Exception) {
            Log.e("ToolUIManager", "Error adding overlay view: ${e.message}")
        }
    }

    override fun dismissToolUI() {
        overlayViewsByType.values.forEach { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e("ToolUIManager", "Error removing overlay view: ${e.message}")
            }
        }
        overlayViewsByType.clear()
    }

    override fun showStatusOverlay(text: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { showStatusOverlay(text) }
            return
        }
        dismissOverlay("status")
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val textView = TextView(context).apply {
            this.text = text
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 16, 32, 16)
            textSize = 16f
        }
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
        windowManager?.addView(textView, params)
        overlayViewsByType["status"] = textView

        // Autohide status overlay after 2 seconds
        handler.removeCallbacksAndMessages(textView) // Cancel previous hide for this view
        handler.postDelayed({
            if (overlayViewsByType["status"] == textView) { // Only dismiss if it's still the current one
                dismissOverlay("status")
            }
        }, 2000)
    }

    override fun showCanvasOverlay(elements: List<ToolUiElement>, callback: (Map<String, Any>) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { showCanvasOverlay(elements, callback) }
            return
        }
        // First clear previous canvas overlay and elements
        clearCanvasOverlay()
        appendToCanvasOverlay(elements, callback)
    }

    override fun appendToCanvasOverlay(elements: List<ToolUiElement>, callback: (Map<String, Any>) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { appendToCanvasOverlay(elements, callback) }
            return
        }
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val scrollView: android.widget.ScrollView
        val container: android.widget.LinearLayout

        val hasInputField = elements.any { it is ToolUiElement.InputField }
        val existingView = overlayViewsByType["canvas"]
        var playEntranceAnimation = false
        if (existingView is android.widget.ScrollView && existingView.getChildAt(0) is android.widget.LinearLayout) {
            scrollView = existingView
            container = existingView.getChildAt(0) as android.widget.LinearLayout
        } else {
            scrollView = android.widget.ScrollView(context)
            container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val bgDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 32f
                    setColor(ContextCompat.getColor(context, R.color.canvas_bg_dark))
                    setStroke(2, ContextCompat.getColor(context, R.color.canvas_border_dark))
                }
                background = bgDrawable
                elevation = 16f
                setPadding(40, 40, 40, 40)
                // Start hidden for entrance animation
                alpha = 0f
                scaleX = 0.92f
                scaleY = 0.92f
            }
            scrollView.addView(container)
            val flags = if (hasInputField) {
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
            val displayMetrics = context.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val overlayHeight = (screenHeight * 0.8).toInt()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            params.x = 0
            params.y = 0
            windowManager?.addView(scrollView, params)
            overlayViewsByType["canvas"] = scrollView
            playEntranceAnimation = true
        }

        canvasElements.addAll(elements)
        canvasCallback = callback
        rebuildCanvasContainer(container, canvasElements, canvasCallback, playEntranceAnimation)
        if (playEntranceAnimation) {
            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).setStartDelay(30).start()
        }
    }

    private fun rebuildCanvasContainer(
        container: android.widget.LinearLayout,
        elements: List<ToolUiElement>,
        currentCallback: ((Map<String, Any>) -> Unit)?,
        animateElements: Boolean = false
    ) {
        container.removeAllViews()
        val inputValues = mutableMapOf<String, EditText>()
        val hasInputField = elements.any { it is ToolUiElement.InputField }

        // --- Modern Dismiss Icon Button (top-right) ---
        val dismissBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_canvas_close)
            background = null
            setColorFilter(ContextCompat.getColor(context, R.color.canvas_accent_teal))
            contentDescription = context.getString(R.string.canvas_dismiss_content_desc)
            val size = (48 * context.resources.displayMetrics.density).toInt()
            val params = android.widget.LinearLayout.LayoutParams(size, size)
            params.gravity = Gravity.END
            layoutParams = params
            setOnClickListener {
                // Animate pop
                animate().scaleX(1.18f).scaleY(1.18f).setDuration(80).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction {
                        hideCanvasOverlay()
                    }.start()
                }.start()
            }
        }
        val topBar = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(android.widget.Space(context), android.widget.LinearLayout.LayoutParams(0, 0, 1f))
            addView(dismissBtn)
        }
        container.addView(topBar)

        // Animate each element in with fade+slide, staggered if animateElements is true
        var elementIndex = 0
        elements.filterNot { it is ToolUiElement.DismissButton }.forEach { element ->
            val view = when (element) {
                is ToolUiElement.Text -> {
                    TextView(context).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            text = Html.fromHtml(element.value, Html.FROM_HTML_MODE_COMPACT)
                        } else {
                            @Suppress("DEPRECATION")
                            text = Html.fromHtml(element.value)
                        }
                        movementMethod = LinkMovementMethod.getInstance()
                        setTextColor(ContextCompat.getColor(context, R.color.canvas_text_light))
                        textSize = 17f
                        setPadding(0, 12, 0, 12)
                        if (animateElements) {
                            alpha = 0f
                            translationY = 32f
                        }
                    }
                }
                is ToolUiElement.Image -> {
                    ImageView(context).apply {
                        if (element.src.startsWith("data:image/")) {
                            val base64 = element.src.substringAfter(",", "")
                            try {
                                val bytes = Base64.decode(base64, Base64.DEFAULT)
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                setImageBitmap(bmp)
                            } catch (e: Exception) {
                                Log.e("ToolUIManager", "Error decoding image: ", e)
                            }
                        }
                        val params = android.widget.LinearLayout.LayoutParams(320, 320)
                        params.setMargins(0, 12, 0, 12)
                        layoutParams = params
                        background = GradientDrawable().apply {
                            cornerRadius = 20f
                            setColor(ContextCompat.getColor(context, R.color.canvas_bg_dark))
                        }
                        if (animateElements) {
                            alpha = 0f
                            translationY = 32f
                        }
                    }
                }
                is ToolUiElement.Button -> {
                    Button(context).apply {
                        text = element.label
                        setTextColor(ContextCompat.getColor(context, android.R.color.white))
                        background = GradientDrawable().apply {
                            cornerRadius = 24f
                            setColor(ContextCompat.getColor(context, R.color.canvas_accent_teal))
                        }
                        textSize = 16f
                        setPadding(0, 24, 0, 24)
                        setOnClickListener {
                            val result = mutableMapOf<String, Any>()
                            result["action"] = element.action
                            if (hasInputField) {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(this.windowToken, 0)
                            }
                            currentCallback?.invoke(result)
                        }
                        if (animateElements) {
                            alpha = 0f
                            translationY = 32f
                        }
                    }
                }
                is ToolUiElement.InputField -> {
                    val et = EditText(context).apply {
                        hint = element.hint ?: element.label
                        setText(element.default ?: "")
                        setTextColor(ContextCompat.getColor(context, R.color.canvas_text_light))
                        setHintTextColor(ContextCompat.getColor(context, R.color.canvas_accent_teal))
                        background = GradientDrawable().apply {
                            cornerRadius = 18f
                            setStroke(2, ContextCompat.getColor(context, R.color.canvas_accent_teal))
                            setColor(ContextCompat.getColor(context, R.color.canvas_bg_dark))
                        }
                        textSize = 16f
                        setPadding(32, 24, 32, 24)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setOnFocusChangeListener { v, hasFocus ->
                            if (hasFocus) {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.restartInput(v)
                                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                        if (animateElements) {
                            alpha = 0f
                            translationY = 32f
                        }
                    }
                    val label = TextView(context).apply {
                        text = element.label
                        setTextColor(ContextCompat.getColor(context, R.color.canvas_text_light))
                        textSize = 15f
                        setPadding(0, 8, 0, 8)
                        if (animateElements) {
                            alpha = 0f
                            translationY = 32f
                        }
                    }
                    val sendBtn = Button(context).apply {
                        text = "Send"
                        setTextColor(ContextCompat.getColor(context, android.R.color.white))
                        background = GradientDrawable().apply {
                            cornerRadius = 24f
                            setColor(ContextCompat.getColor(context, R.color.canvas_accent_teal))
                        }
                        textSize = 16f
                        setPadding(0, 18, 0, 18)
                        setOnClickListener {
                            val result = mutableMapOf<String, Any>()
                            result[element.id] = et.text.toString()
                            currentCallback?.invoke(result)
                        }
                        if (animateElements) {
                            alpha = 0f
                            translationY = 32f
                        }
                    }
                    container.addView(label)
                    container.addView(et)
                    container.addView(sendBtn)
                    inputValues[element.id] = et
                    if (animateElements) {
                        handler.postDelayed({ label.animate().alpha(1f).translationY(0f).setDuration(220).start() }, 40L * elementIndex)
                        elementIndex++
                        handler.postDelayed({ et.animate().alpha(1f).translationY(0f).setDuration(220).start() }, 40L * elementIndex)
                        elementIndex++
                        handler.postDelayed({ sendBtn.animate().alpha(1f).translationY(0f).setDuration(220).start() }, 40L * elementIndex)
                        elementIndex++
                    }
                    null // Already added
                }
                is ToolUiElement.DismissButton -> null
                else -> null
            }
            if (view != null) {
                container.addView(view)
                if (animateElements) {
                    handler.postDelayed({ view.animate().alpha(1f).translationY(0f).setDuration(220).start() }, 40L * elementIndex)
                    elementIndex++
                }
            }
        }

        if (hasInputField) {
            val firstEditText = inputValues.values.firstOrNull()
            if (firstEditText != null) {
                handler.postDelayed({
                    firstEditText.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.restartInput(firstEditText)
                    imm.showSoftInput(firstEditText, InputMethodManager.SHOW_FORCED)
                }, 120)
            }
        }
    }

    override fun clearCanvasOverlay() {
        dismissOverlay("canvas")
        canvasElements.clear()
        canvasCallback = null
    }

    override fun dismissOverlay(type: String) {
        val view = overlayViewsByType.remove(type)
        view?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e("ToolUIManager", "Error removing overlay view ($type): ${e.message}")
            }
        }
    }

    // Animate exit on hide
    private fun hideCanvasOverlay() {
        val scrollView = overlayViewsByType["canvas"] as? android.widget.ScrollView
        val container = scrollView?.getChildAt(0) as? android.widget.LinearLayout
        if (container != null) {
            container.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(180).withEndAction {
                dismissOverlay("canvas")
            }.start()
        } else {
            dismissOverlay("canvas")
        }
        // Do NOT clear canvasElements or canvasCallback
    }
} 