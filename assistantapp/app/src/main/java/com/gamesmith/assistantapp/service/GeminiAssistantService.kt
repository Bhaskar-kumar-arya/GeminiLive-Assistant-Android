package com.gamesmith.assistantapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gamesmith.assistantapp.MainActivity // Assuming R is in base package
import com.gamesmith.assistantapp.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.gamesmith.assistantapp.data.remote.GeminiWebSocketClient
import com.gamesmith.assistantapp.data.remote.GeminiRepository
import com.gamesmith.assistantapp.data.webrtc.WebRTCManager
import com.gamesmith.assistantapp.util.AudioPlayer
import com.gamesmith.assistantapp.data.remote.ConnectionState
import com.gamesmith.assistantapp.data.model.LiveConfig
import com.gamesmith.assistantapp.data.model.GenerationConfig
import com.gamesmith.assistantapp.data.model.MessageTypes
import com.gamesmith.assistantapp.data.model.SetupCompleteMessagePayload
import com.gamesmith.assistantapp.data.model.SpeechConfig
import com.gamesmith.assistantapp.data.model.RealtimeInputConfig
import com.gamesmith.assistantapp.data.model.AutomaticActivityDetection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.gamesmith.assistantapp.service.ToolUIManager
import android.provider.Settings
import kotlinx.coroutines.cancel
import com.gamesmith.assistantapp.domain.tool.ToolExecutor
import android.os.Handler
import kotlinx.coroutines.delay
import com.gamesmith.assistantapp.service.SystemAlertWindowToolUIManager
import com.gamesmith.assistantapp.data.model.ToolUiSchema
import com.gamesmith.assistantapp.data.model.ToolUiElement
import android.os.Looper
import com.gamesmith.assistantapp.data.model.Content
import com.gamesmith.assistantapp.data.model.FunctionDeclaration
import com.gamesmith.assistantapp.data.model.FunctionParameters
import com.gamesmith.assistantapp.data.model.ImageGenerationResultPayload
import com.gamesmith.assistantapp.data.model.Part
import com.gamesmith.assistantapp.data.model.Tool
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@AndroidEntryPoint
class GeminiAssistantService : Service(), WebRTCManager.AudioPlaybackListener {

    @Inject
    lateinit var webSocketClient: GeminiWebSocketClient // Though injected, direct use might be less common if repo handles all
    @Inject
    lateinit var geminiRepository: GeminiRepository
    @Inject
    lateinit var webRTCManager: WebRTCManager
    @Inject
    lateinit var audioPlayer: AudioPlayer
    @Inject
    lateinit var toolExecutor: com.gamesmith.assistantapp.domain.tool.ToolExecutor
    @Inject
    lateinit var toolUIManager: ToolUIManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var connectionObserverJob: Job? = null
    private var messageObserverJob: Job? = null

    private val _isVoiceSessionActive = MutableStateFlow(false)
    private val isVoiceSessionActive = _isVoiceSessionActive.asStateFlow()
    private val _isGeminiSetupComplete = MutableStateFlow(false)
    private val isGeminiSetupComplete = _isGeminiSetupComplete.asStateFlow()

    private lateinit var notificationManager: NotificationManager

    // Default configuration
    private val defaultServerUrl = "ws://192.168.134.88:3001" // User updated
    private fun getDefaultLiveConfig(): LiveConfig {
        // Dynamically generate FunctionDeclaration for all tools in toolExecutor
        val functionDeclarations = toolExecutor.tools.map { tool ->
            val parameters = if (tool.parametersJsonSchema.isNotBlank()) {
                try {
                    kotlinx.serialization.json.Json.decodeFromString<com.gamesmith.assistantapp.data.model.FunctionParameters>(tool.parametersJsonSchema)
                } catch (e: Exception) {
                    // Fallback to empty parameters if schema is invalid
                    FunctionParameters(type = "OBJECT", properties = emptyMap(), required = emptyList())
                }
            } else {
                FunctionParameters(type = "OBJECT", properties = emptyMap(), required = emptyList())
            }
            FunctionDeclaration(
                name = tool.name,
                description = tool.description,
                parameters = parameters,
                behavior = tool.defaultBehavior
            )
        }
        val toolsList = listOf(Tool(functionDeclarations = functionDeclarations))

        return LiveConfig(
           // model = "models/gemini-2.0-flash-live-001",
            model = "models/gemini-2.5-flash-preview-native-audio-dialog",
             // model = "gemini-2.5-flash-exp-native-audio-thinking-dialog",
            generationConfig = GenerationConfig(
                responseModalities = listOf("audio"),
                speechConfig = null,
            ),
            systemInstruction = Content(listOf<Part>(Part("you are a helpful mobile assistant" +
                    ".Be concise when you speak,dont ask obvious questions to the user" +
                    ". make use of the tools you have been provided to help the user." +
                    " Dont try to be over technical to the user, unless asked." +
                    "you dont need to tell the internal state of the assistant to the user, unless asked." +
                    "DONT  RUN SAME TOOL MULTIPLE TIMES UNLESS ASKED." +
                    "You are supposed to be humorous and more human like." +
                    "Realise when it is convenient for user to be given a  displayed response(using GUI) in place of an audio response " +
                    " When using create_canvas_tool or append_to_canvas_tool, the 'text' elements support HTML formatting for their 'value' field (e.g., '<i>italic</i>', '<b>bold</b>', '<a href=\\'url\\'>link</a>')."))) ,
            tools = toolsList,
            realtimeInputConfig = RealtimeInputConfig(
                 automaticActivityDetection = AutomaticActivityDetection(
                    disabled = false,
                    startOfSpeechSensitivity = "START_SENSITIVITY_HIGH",
                    endOfSpeechSensitivity = "END_SENSITIVITY_HIGH",
                    prefixPaddingMs = 20,
                    silenceDurationMs = 150
                )
            )
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "GeminiAssistantServiceChannel"
        const val ACTION_START_SESSION = "com.gamesmith.assistantapp.ACTION_START_SESSION"
        const val ACTION_STOP_SESSION = "com.gamesmith.assistantapp.ACTION_STOP_SESSION"
        const val ACTION_TOGGLE_VOICE_SESSION = "com.gamesmith.assistantapp.ACTION_TOGGLE_VOICE_SESSION"
        const val ACTION_START_SESSION_AND_VOICE = "com.gamesmith.assistantapp.ACTION_START_SESSION_AND_VOICE"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        webRTCManager.initializePeerConnectionFactory()
        webRTCManager.audioPlaybackListener = this
        Log.d("GeminiAssistantService", "Service Created. Core components injected. WebRTCManager factory initialized.")
        // Show initial status overlay as a ToolUiSchema Text element
        if (android.provider.Settings.canDrawOverlays(this)) {
            toolUIManager.showStatusOverlay("Gemini Assistant Running")
        } else {
            Log.w("GeminiAssistantService", "SYSTEM_ALERT_WINDOW permission not granted. Overlay will not be shown.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GeminiAssistantService", "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_SESSION -> {
                startGeminiSession()
            }
            ACTION_STOP_SESSION -> {
                stopGeminiSession()
                // Service is stopped via stopSelf() within stopGeminiSession or if it decides to stop
            }
            ACTION_TOGGLE_VOICE_SESSION -> {
                toggleVoiceSession()
            }
            ACTION_START_SESSION_AND_VOICE -> {
                startSessionAndVoice()
            }
            else -> {
                // Default action: ensure service is in foreground with a basic notification
                // This path might be hit if service is restarted by system
                startForeground(NOTIFICATION_ID, createNotification("Assistant Ready"))
                Log.d("GeminiAssistantService", "Service started with no specific action, showing 'Assistant Ready' notification.")
            }
        }
        return START_STICKY
    }
    private fun toggleVoiceSession() {
        // TODO: Implement proper microphone permission check.
        // For a service, this is tricky. The main app should grant it.
        // If not granted, we should not attempt to start voice, and update notification.
        // Log.d("GeminiAssistantService", "Microphone permission check needed here.")

        val wantsToEnableMic = !_isVoiceSessionActive.value

        serviceScope.launch {
            if (wantsToEnableMic) {
                if (geminiRepository.observeConnectionState().value !is ConnectionState.Connected) {
                    updateNotification("Connect to Assistant first to start voice session.")
                    Log.w("GeminiAssistantService", "Cannot start voice session, WebSocket not connected.")
                    return@launch
                }
                 if (!webRTCManager.isPeerConnectionAvailable()) {
                    Log.d("GeminiAssistantService", "PeerConnection is not available or closed, creating new one.")
                    webRTCManager.createPeerConnection()
                    webRTCManager.createDataChannel("ServiceDataChannel")
                } else {
                    Log.d("GeminiAssistantService", "PeerConnection exists and is available, re-using.")
                }

                updateNotification("Starting voice session...")
                Log.d("GeminiAssistantService", "Attempting to start voice session...")

                // Ensure PeerConnection is created before attempting to use it for offer or mic capture
                // webRTCManager.createPeerConnection() // Idempotent or creates if null
                // webRTCManager.createDataChannel("ServiceDataChannel") // Idempotent
                webRTCManager.startMicrophoneCapture() // Enables local audio track and handles audio focus

                // Create offer only if WebSocket is connected and ready for signaling
                Log.d("GeminiAssistantService", "Creating WebRTC Offer...")
                webRTCManager.createOffer()

                _isVoiceSessionActive.value = true
                updateNotification("Voice session active. Mic ON.")
                Log.i("GeminiAssistantService", "Voice session started. Mic ON.")
                updateStatusOverlay("Gemini is listening...") // Set listening state when mic is enabled

            } else { // User wants to disable the microphone (stop voice session)
                updateNotification("Stopping voice input...")
                Log.d("GeminiAssistantService", "Attempting to stop voice input...")
                webRTCManager.stopMicrophoneCapture() // Disables local audio track and restores audio settings
                _isVoiceSessionActive.value = false
                val currentStatus = getCurrentNotificationStatusText()
                updateNotification("$currentStatus Mic OFF")
                Log.i("GeminiAssistantService", "Voice input stopped. Mic OFF.")
                updateStatusOverlay("Gemini is listening...") // Set listening state when mic is disabled
            }
        }
    }
    private fun startGeminiSession() {
        Log.d("GeminiAssistantService", "Attempting to start Gemini session.")
        // Prevent multiple concurrent connection attempts or job creations
        if (connectionObserverJob?.isActive == true || messageObserverJob?.isActive == true) {
            Log.d("GeminiAssistantService", "Session start skipped, already connecting or connected.")
            return
        }

        _isGeminiSetupComplete.value = false
        updateNotification("Connecting to Assistant...")
        sendStatusBroadcast("Connecting to Assistant...")
        geminiRepository.connect(defaultServerUrl, getDefaultLiveConfig())

        connectionObserverJob = geminiRepository.observeConnectionState()
            .onEach { state ->
                Log.d("GeminiAssistantService", "ConnectionState changed: $state")
                val statusText = when (state) {
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Connected -> "Connected. Awaiting setup..."
                    is ConnectionState.Disconnected -> "Disconnected: ${state.reason ?: "Client action"}"
                    is ConnectionState.Error -> "Error: ${state.message}"
                    ConnectionState.Idle -> "Assistant Idle"
                    ConnectionState.Disconnecting -> "Disconnecting..."
                }
                updateNotification(statusText)
                sendStatusBroadcast(statusText)
                when (state) {
                    is ConnectionState.Connecting -> {}
                    is ConnectionState.Connected -> {}
                    is ConnectionState.Disconnected -> {
                        // Clean up observer jobs if disconnected unexpectedly or by server
                        messageObserverJob?.cancel()
                        connectionObserverJob?.cancel()
                        _isGeminiSetupComplete.value = false
                    }
                    is ConnectionState.Error -> {
                        messageObserverJob?.cancel()
                        connectionObserverJob?.cancel()
                        _isGeminiSetupComplete.value = false
                    }
                    else -> {}
                }
            }.launchIn(serviceScope)

        messageObserverJob = geminiRepository.observeServerMessages()
            .onEach { messageWrapper ->
                Log.d("GeminiAssistantService", "Received server message: "+messageWrapper.type)
                when (messageWrapper.type) {
                    MessageTypes.SETUP_COMPLETE -> {
                        val payload = messageWrapper.payload as? SetupCompleteMessagePayload
                        if (payload?.setupComplete?.success == true) {
                            updateNotification("Assistant Session Active")
                            _isGeminiSetupComplete.value = true
                            // Send initial greeting as real-time input
                            serviceScope.launch {
                                geminiRepository.sendRealtimeInput(text = "greet the user")
                            }
                        } else {
                            updateNotification("Setup Failed: "+(payload?.setupComplete?.error?.message ?: "Unknown reason"))
                            _isGeminiSetupComplete.value = false
                        }
                    }
                    MessageTypes.IMAGE_GENERATION_RESULT -> {
                        val payload = messageWrapper.payload as? ImageGenerationResultPayload
                        if (payload != null && payload.success == true && payload.imageData != null) {
                            Log.d("GeminiAssistantService", "Received IMAGE_GENERATION_RESULT with image data.")
                            // Save the image data to the gallery
                            serviceScope.launch(Dispatchers.IO) { // Use IO dispatcher for file operations
                                try {
                                    val imageBytes = android.util.Base64.decode(payload.imageData, android.util.Base64.NO_WRAP)
                                    val fileName = "generated-image-${System.currentTimeMillis()}.png"
                                    val mimeType = "image/png" // Assuming PNG, adjust if necessary

                                    val contentValues = android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, mimeType)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                                        }
                                    }

                                    val resolver = applicationContext.contentResolver
                                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                                    } else {
                                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    }

                                    val imageUri = resolver.insert(collection, contentValues)

                                    if (imageUri != null) {
                                        resolver.openOutputStream(imageUri)?.use { outputStream ->
                                            outputStream.write(imageBytes)
                                        }
                                        Log.d("GeminiAssistantService", "Image saved to gallery: $imageUri")

                                        // Create a new payload with the gallery URI and success status
                                        val resultPayloadWithGalleryUri = ImageGenerationResultPayload(
                                            imageUrl = imageUri.toString(), // Send the gallery URI back in the payload
                                            success = true,
                                            error = null
                                        )

                                        // Call the repository function to handle the result and send to the channel
                                        geminiRepository.handleImageGenerationResult(resultPayloadWithGalleryUri)

                                    } else {
                                        Log.e("GeminiAssistantService", "Failed to create new MediaStore entry for image.")
                                         val errorPayload = ImageGenerationResultPayload(
                                             success = false,
                                             error = "Client failed to create MediaStore entry."
                                         )
                                         geminiRepository.handleImageGenerationResult(errorPayload)
                                    }

                                } catch (e: Exception) {
                                    Log.e("GeminiAssistantService", "Error saving image to gallery: ${e.message}", e)
                                    // Send an error result back through the channel
                                    val errorPayload = ImageGenerationResultPayload(
                                        success = false,
                                        error = "Client error saving image to gallery: ${e.message}"
                                    )
                                    geminiRepository.handleImageGenerationResult(errorPayload)
                                }
                            }
                        } else if (payload != null && payload.success == false) {
                             Log.e("GeminiAssistantService", "Received IMAGE_GENERATION_RESULT with server error: ${payload.error}")
                             // Pass the server error back through the channel
                             geminiRepository.handleImageGenerationResult(payload)
                        } else {
                            Log.e("GeminiAssistantService", "Received IMAGE_GENERATION_RESULT with invalid payload.")
                             // Send a generic client error back through the channel
                             val errorPayload = ImageGenerationResultPayload(
                                 success = false,
                                 error = "Client received invalid image generation result payload."
                             )
                             geminiRepository.handleImageGenerationResult(errorPayload)
                        }
                    }
                    MessageTypes.GEMINI_CONNECTED -> {
                        updateNotification("Gemini Connected")
                    }
                    MessageTypes.ASSISTANT_SPEAKING -> {
                        updateStatusOverlay("Gemini is speaking...")
                    }
                    MessageTypes.TURN_COMPLETE, MessageTypes.INTERRUPTED -> {
                        updateStatusOverlay("Gemini is listening...")
                    }
                    MessageTypes.TOOL_CALL -> {
                        val payload = messageWrapper.payload as? com.gamesmith.assistantapp.data.model.ToolCallMessagePayload
                        payload?.toolCall?.functionCalls?.forEach { functionCall ->
                            serviceScope.launch {
                                val argsMap: Map<String, Any> = functionCall.args.entries.associate { (key, jsonElement) ->
                                    val value: Any = when (jsonElement) {
                                        is kotlinx.serialization.json.JsonPrimitive -> {
                                            if (jsonElement.isString) {
                                                jsonElement.content // Use raw string content
                                            } else {
                                                // For non-string primitives (numbers, booleans)
                                                jsonElement.longOrNull ?: jsonElement.doubleOrNull ?: jsonElement.booleanOrNull ?: jsonElement.content
                                            }
                                        }
                                        is kotlinx.serialization.json.JsonObject -> jsonElement.toString() // Or parse to Map<String, Any> if tools expect nested objects
                                        is kotlinx.serialization.json.JsonArray -> jsonElement.toString()  // Or parse to List<Any>
                                        else -> jsonElement.toString() // Fallback for other JsonElement types like JsonNull
                                    }
                                    key to value
                                }
                                val tool = toolExecutor.tools.find { it.name == functionCall.name }
                                val scheduling = tool?.defaultScheduling ?: "WHEN_IDLE"
                                val result = toolExecutor.executeTool(
                                    name = functionCall.name,
                                    args = argsMap,
                                    serviceContext = this@GeminiAssistantService,
                                    serviceScope = serviceScope
                                )
                                when (result) {
                                    is com.gamesmith.assistantapp.domain.tool.ToolExecutionResult.Success -> {
                                        val responsePart = com.gamesmith.assistantapp.data.model.FunctionResponsePart(
                                            id = functionCall.id ?: "",
                                            name = functionCall.name,
                                            response = kotlinx.serialization.json.JsonObject(result.data.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value.toString()) })
                                        )
                                        val wrapper = com.gamesmith.assistantapp.data.model.ToolResponseWrapper(
                                            functionResponses = listOf(responsePart)
                                        )
                                        geminiRepository.sendToolResponse(wrapper)
                                    }
                                    is com.gamesmith.assistantapp.domain.tool.ToolExecutionResult.Error -> {
                                        val responsePart = com.gamesmith.assistantapp.data.model.FunctionResponsePart(
                                            id = functionCall.id ?: "",
                                            name = functionCall.name,
                                            response = kotlinx.serialization.json.buildJsonObject {
                                                put("error", kotlinx.serialization.json.JsonPrimitive(result.message))
                                            }
                                        )
                                        val wrapper = com.gamesmith.assistantapp.data.model.ToolResponseWrapper(
                                            functionResponses = listOf(responsePart)
                                        )
                                        geminiRepository.sendToolResponse(wrapper)
                                    }
                                    is com.gamesmith.assistantapp.domain.tool.ToolExecutionResult.NeedsConfirmation -> {
                                        val schema = result.schema
                                        if (schema != null) {
                                            val toolName = functionCall.name
                                            val callback: (Map<String, Any>) -> Unit = { userInput ->
                                                serviceScope.launch {
                                                    val responsePart = com.gamesmith.assistantapp.data.model.FunctionResponsePart(
                                                        id = functionCall.id ?: "",
                                                        name = toolName,
                                                        response = kotlinx.serialization.json.JsonObject(userInput.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value.toString()) })
                                                    )
                                                    val wrapper = com.gamesmith.assistantapp.data.model.ToolResponseWrapper(
                                                        functionResponses = listOf(responsePart)
                                                    )
                                                    geminiRepository.sendToolResponse(wrapper)
                                                }
                                            }
                                            if (toolName == "create_canvas_tool" || toolName == "append_to_canvas_tool") {
                                                // 1. Immediately acknowledge rendering to Gemini
                                                serviceScope.launch {
                                                    val responsePart = com.gamesmith.assistantapp.data.model.FunctionResponsePart(
                                                        id = functionCall.id ?: "",
                                                        name = toolName,
                                                        response = kotlinx.serialization.json.buildJsonObject {
                                                            put("acknowledged", kotlinx.serialization.json.JsonPrimitive(true))
                                                        }
                                                    )
                                                    val wrapper = com.gamesmith.assistantapp.data.model.ToolResponseWrapper(
                                                        functionResponses = listOf(responsePart)
                                                    )
                                                    geminiRepository.sendToolResponse(wrapper)
                                                }
                                                // 2. Show the canvas overlay and handle user interaction as SEND_REALTIME_INPUT (text)
                                                val canvasCallback: (Map<String, Any>) -> Unit = { userInput: Map<String, Any> ->
                                                    serviceScope.launch {
                                                        // Send user input as JSON string via SEND_REALTIME_INPUT
                                                        val jsonText = kotlinx.serialization.json.Json.encodeToString(
                                                            kotlinx.serialization.json.JsonObject.serializer(),
                                                            kotlinx.serialization.json.JsonObject(
                                                                userInput.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value.toString()) }
                                                            )
                                                        )
                                                        geminiRepository.sendRealtimeTextInput(jsonText)
                                                    }
                                                }
                                                if (toolName == "create_canvas_tool") {
                                                    toolUIManager.showCanvasOverlay(schema.elements, canvasCallback)
                                                } else {
                                                    toolUIManager.appendToCanvasOverlay(schema.elements, canvasCallback)
                                                }
                                                // Do NOT send a tool response on user interaction for canvas tools
                                            } else {
                                                // Fallback for other tools that might use NeedsConfirmation with a schema
                                                toolUIManager.showToolUI(schema, callback)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Handle other relevant messages for notification if needed
            }.launchIn(serviceScope)
        
        startForeground(NOTIFICATION_ID, createNotification("Connecting to Assistant..."))
        // Reset voice session state when starting a new general session
        if (_isVoiceSessionActive.value) {
            webRTCManager.stopMicrophoneCapture() // Ensure mic is off
            _isVoiceSessionActive.value = false
        }
    }

    private fun stopGeminiSession() {
        Log.d("GeminiAssistantService", "Stopping Gemini session.")
        if (_isVoiceSessionActive.value) {
            webRTCManager.stopMicrophoneCapture() // Ensure microphone is stopped and audio settings are reset
            _isVoiceSessionActive.value = false
        }
        webRTCManager.close() // Fully release WebRTC and mic resources
        geminiRepository.disconnect() // This should trigger ConnectionState.Disconnected
        messageObserverJob?.cancel()
        connectionObserverJob?.cancel()
        messageObserverJob = null
        connectionObserverJob = null
        _isGeminiSetupComplete.value = false
        updateNotification("Assistant stopped. Mic OFF. Tap to start session.")
        updateStatusOverlay("Assistant stopped. Mic OFF.")
        Log.i("GeminiAssistantService", "Gemini session stopped. Service remains active.")
    }

    private fun getCurrentNotificationStatusText(): String {
        // This is a placeholder. A more robust way would be to store the last base message.
        val connState = geminiRepository.observeConnectionState().value
        return when (connState) {
            is ConnectionState.Connected -> if (_isVoiceSessionActive.value) "Voice session active." else "Assistant Session Active"
            is ConnectionState.Connecting -> "Connecting..."
            is ConnectionState.Disconnected -> "Disconnected: ${connState.reason ?: "Client action"}"
            is ConnectionState.Error -> "Error: ${connState.message}"
            else -> "Assistant Ready"
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("GeminiAssistantService", "Service Destroyed.")
        // stopGeminiSession() // stopGeminiSession calls stopSelf, which will lead to onDestroy. Redundant and can cause issues.
        
        // Explicitly stop voice session activities and release WebRTC resources
        if (_isVoiceSessionActive.value) {
            serviceScope.launch { // Use serviceScope for graceful shutdown of coroutine-based ops in WebRTCManager
                webRTCManager.stopMicrophoneCapture()
                _isVoiceSessionActive.value = false
            }
        }
        webRTCManager.close() // Close WebRTC peer connection and release all related resources
        geminiRepository.disconnect() // Ensure WebSocket is disconnected

        messageObserverJob?.cancel()
        connectionObserverJob?.cancel()
        serviceJob.cancel() // Cancel all coroutines started in serviceScope
        notificationManager.cancel(NOTIFICATION_ID) // Remove notification when service is actually destroyed
        toolUIManager.dismissToolUI()
        Log.i("GeminiAssistantService", "All resources cleaned up. Service stopped.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Gemini Assistant Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val startSessionAndVoiceIntent = Intent(this, GeminiAssistantService::class.java).apply {
            action = ACTION_START_SESSION_AND_VOICE
        }
        val startSessionAndVoicePendingIntent = PendingIntent.getService(
            this, 0, startSessionAndVoiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopSessionIntent = Intent(this, GeminiAssistantService::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val stopSessionPendingIntent = PendingIntent.getService(
            this, 0, stopSessionIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleVoiceIntent = Intent(this, GeminiAssistantService::class.java).apply {
            action = ACTION_TOGGLE_VOICE_SESSION
        }
        val toggleVoicePendingIntent = PendingIntent.getService(
            this, 0, toggleVoiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val connState = geminiRepository.observeConnectionState().value
        val isSessionActive = connState is ConnectionState.Connected
        val isMicOn = _isVoiceSessionActive.value

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gemini Assistant")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (!isSessionActive) {
            // Show only the combined action
            builder.addAction(android.R.drawable.ic_media_play, "Start Session & Voice", startSessionAndVoicePendingIntent)
        } else {
            // Show stop session and mic toggle
            builder.addAction(android.R.drawable.ic_media_pause, "Stop Session", stopSessionPendingIntent)
            if (isMicOn) {
                builder.addAction(android.R.drawable.ic_lock_silent_mode, "Turn Off Mic", toggleVoicePendingIntent)
            } else {
                builder.addAction(android.R.drawable.ic_btn_speak_now, "Turn On Mic", toggleVoicePendingIntent)
            }
        }
        return builder.build()
    }

    private fun updateNotification(contentText: String) {
        Log.d("GeminiAssistantService", "Updating notification: $contentText")
        // Re-create notification to update actions as well, if isVoiceSessionActive changed
        val notification = createNotification(contentText) 
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendStatusBroadcast(status: String) {
        val intent = Intent("com.gamesmith.assistantapp.SERVICE_STATUS")
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    private fun startSessionAndVoice() {
        // If already connected, just start voice
        val connState = geminiRepository.observeConnectionState().value
        if (connState is ConnectionState.Connected && isGeminiSetupComplete.value) {
            if (!_isVoiceSessionActive.value) {
                toggleVoiceSession()
            }
            return
        }
        // Otherwise, start session and then voice when connected and setup complete
        startGeminiSession()
        // Launch a coroutine to wait for connection and setup, then start voice
        serviceScope.launch {
            var connected = false
            geminiRepository.observeConnectionState().onEach { state ->
                if (state is ConnectionState.Connected) {
                    connected = true
                } else if (state is ConnectionState.Error || state is ConnectionState.Disconnected) {
                    this.cancel()
                }
            }.launchIn(this)
            isGeminiSetupComplete.onEach { setupComplete ->
                if (connected && setupComplete) {
                    if (!_isVoiceSessionActive.value) {
                        toggleVoiceSession()
                    }
                    this.cancel()
                }
            }.launchIn(this)
        }
    }

    private fun updateStatusOverlay(status: String) {
        toolUIManager.showStatusOverlay(status)
    }

    override fun onAudioPlaybackStarted() {
        // Do not set overlay here; rely on ASSISTANT_SPEAKING message
    }
    override fun onAudioPlaybackStopped() {
        // Do not set overlay here; rely on TURN_COMPLETE/INTERRUPTED message
    }
} 