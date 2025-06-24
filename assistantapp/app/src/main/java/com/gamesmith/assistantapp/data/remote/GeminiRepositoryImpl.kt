package com.gamesmith.assistantapp.data.remote

import com.gamesmith.assistantapp.data.model.ClientMessageWrapper
import com.gamesmith.assistantapp.data.model.GenerativeContentBlob
import com.gamesmith.assistantapp.data.model.LiveConfig
import com.gamesmith.assistantapp.data.model.Part
import com.gamesmith.assistantapp.data.model.SendMessagePayload
import com.gamesmith.assistantapp.data.model.SendRealtimeInputPayload
import com.gamesmith.assistantapp.data.model.ServerMessageWrapper
import com.gamesmith.assistantapp.data.model.MessageTypes
import com.gamesmith.assistantapp.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import javax.inject.Inject
import com.gamesmith.assistantapp.data.webrtc.WebRTCManager
import com.gamesmith.assistantapp.data.remote.ServerMessageEnvelope
import com.gamesmith.assistantapp.data.remote.WebRTCAnswerSdp
import com.gamesmith.assistantapp.data.remote.WebRTCIceCandidateContainer
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel // Import Channel
import com.gamesmith.assistantapp.data.model.ImageGenerationResultPayload // Import ImageGenerationResultPayload

class GeminiRepositoryImpl @Inject constructor(
    private val webSocketClient: GeminiWebSocketClient,
    private val json: Json,
    private val webRTCManager: WebRTCManager
) : GeminiRepository {

    // Channel to bridge image generation results back to the suspending function
    private val imageGenerationResultChannel = Channel<ImageGenerationResultPayload>(Channel.BUFFERED)

    override fun connect(serverUrl: String, config: LiveConfig) {
        webSocketClient.connect(serverUrl, config)
    }

    override fun disconnect() {
        webSocketClient.disconnect()
    }

    override suspend fun sendMessage(text: String) {
        val payload = SendMessagePayload(
            parts = listOf(Part(text = text)),
            turnComplete = true // Explicit text messages usually complete a turn
        )
        val wrapper = ClientMessageWrapper(
            type = "SEND_MESSAGE",
            payload = json.encodeToJsonElement(payload)
        )
        try {
            val jsonMessage = json.encodeToString(wrapper)
            webSocketClient.sendMessage(jsonMessage)
        } catch (e: Exception) {
            // Log error or handle serialization exception
            println("Error serializing SEND_MESSAGE: ${e.message}")
        }
    }

    override suspend fun sendAudioChunk(audioData: ByteArray) {
        val base64Audio = Base64.getEncoder().encodeToString(audioData)
        val payload = SendRealtimeInputPayload(
            chunks = listOf(
                GenerativeContentBlob(
                    mimeType = "audio/pcm;rate=16000", // Standard audio config
                    data = base64Audio
                )
            )
        )
        val wrapper = ClientMessageWrapper(
            type = "SEND_REALTIME_INPUT",
            payload = json.encodeToJsonElement(payload)
        )
        try {
            val jsonMessage = json.encodeToString(wrapper)
            webSocketClient.sendMessage(jsonMessage)
        } catch (e: Exception) {
            // Log error or handle serialization exception
            println("Error serializing SEND_REALTIME_INPUT: ${e.message}")
        }
    }

    override suspend fun updateConfig(config: LiveConfig) {
        val wrapper = ClientMessageWrapper(
            type = "UPDATE_CONFIG",
            payload = json.encodeToJsonElement(config) // LiveConfig directly as payload
        )
        try {
            val jsonMessage = json.encodeToString(wrapper)
            webSocketClient.sendMessage(jsonMessage)
        } catch (e: Exception) {
            println("Error serializing UPDATE_CONFIG: ${e.message}")
        }
    }

    override fun observeServerMessages(): Flow<ServerMessageWrapper> {
        return webSocketClient.serverMessages.mapNotNull { rawJson ->
            try {
                // Use ServerMessageEnvelope to parse the type and full payload first
                val envelope = json.decodeFromString(ServerMessageEnvelope.serializer(), rawJson)
                val messageType = envelope.type
                val payloadJsonElement = envelope.payload

                // Handle WebRTC messages directly
                if (messageType == MessageTypes.WEBRTC_ANSWER) {
                    payloadJsonElement?.let {
                        val answerSdp = json.decodeFromJsonElement(WebRTCAnswerSdp.serializer(), it)
                        webRTCManager.handleRemoteAnswer(answerSdp.sdp)
                    }
                    return@mapNotNull null // Consume this message, don't pass to UI
                }

                if (messageType == MessageTypes.WEBRTC_ICE_CANDIDATE) {
                    payloadJsonElement?.let {
                        val iceCandidateContainer = json.decodeFromJsonElement(WebRTCIceCandidateContainer.serializer(), it)
                        webRTCManager.addRemoteIceCandidate(iceCandidateContainer.candidate)
                    }
                    return@mapNotNull null // Consume this message, don't pass to UI
                }

                // Deserialize the payload based on the message type for other messages
                val payload: ServerMessagePayload? = when (messageType) {
                    MessageTypes.GEMINI_CONNECTED -> null // No payload
                    MessageTypes.GEMINI_DISCONNECTED -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(GeminiDisconnectedPayload.serializer(), it)
                    }
                    MessageTypes.GEMINI_ERROR -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(GeminiErrorPayload.serializer(), it)
                    }
                    MessageTypes.CONTENT_MESSAGE -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(ContentMessagePayload.serializer(), it)
                    }
                    MessageTypes.TOOL_CALL -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(ToolCallMessagePayload.serializer(), it)
                    }
                    MessageTypes.TOOL_CALL_CANCELLATION -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(ToolCallCancellationMessagePayload.serializer(), it)
                    }
                    MessageTypes.SETUP_COMPLETE -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(SetupCompleteMessagePayload.serializer(), it)
                    }
                    MessageTypes.INTERRUPTED -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(InterruptedPayload.serializer(), it)
                    }
                    MessageTypes.TURN_COMPLETE -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(TurnCompletePayload.serializer(), it)
                    }
                    MessageTypes.AUDIO_CHUNK -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(AudioChunkPayload.serializer(), it)
                    }
                    MessageTypes.LOG_MESSAGE -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(LogMessagePayload.serializer(), it)
                    }
                    MessageTypes.IMAGE_GENERATION_RESULT -> payloadJsonElement?.let {
                        json.decodeFromJsonElement(ImageGenerationResultPayload.serializer(), it)
                    }
                    else -> {
                        println("Unknown message type: $messageType")
                        null
                    }
                }

                // If the payload was successfully deserialized, emit the wrapper
                if (payload != null) {
                    ServerMessageWrapper(type = messageType, payload = payload)
                } else {
                    // If payload is null (either unknown type or deserialization failed),
                    // don't emit anything to the main flow
                    null
                }
            } catch (e: Exception) {
                // This catch block handles errors during the initial envelope parsing or other unexpected issues
                println("Error processing raw server message: ${e.message}, raw: $rawJson")
                null // Filter out messages that cause processing errors
            }
        }
    }

    override fun observeConnectionState(): StateFlow<ConnectionState> {
        return webSocketClient.connectionState
    }

    override suspend fun sendToolResponse(toolResponse: ToolResponseWrapper) {
        val payload = SendToolResponsePayload(toolResponse)
        val wrapper = ClientMessageWrapper(
            type = MessageTypes.SEND_TOOL_RESPONSE,
            payload = json.encodeToJsonElement(payload)
        )
        try {
            val jsonMessage = json.encodeToString(wrapper)
            webSocketClient.sendMessage(jsonMessage)
        } catch (e: Exception) {
            println("Error serializing SEND_TOOL_RESPONSE: "+e.message)
        }
    }

    // New: Send real-time input (text and/or audio/video)
    override suspend fun sendRealtimeInput(
        text: String?,
        audio: GenerativeContentBlob?,
        video: GenerativeContentBlob?,
        activityStart: ActivityStart?,
        activityEnd: ActivityEnd?,
        audioStreamEnd: Boolean?
    ) {
        val payload = SendRealtimeInputPayload(
            text = text,
            audio = audio,
            video = video,
            activityStart = activityStart,
            activityEnd = activityEnd,
            audioStreamEnd = audioStreamEnd
        )
        val wrapper = ClientMessageWrapper(
            type = "SEND_REALTIME_INPUT",
            payload = json.encodeToJsonElement(payload)
        )
        try {
            val jsonMessage = json.encodeToString(wrapper)
            webSocketClient.sendMessage(jsonMessage)
        } catch (e: Exception) {
            println("Error serializing SEND_REALTIME_INPUT: ${e.message}")
        }
    }

    // Convenience: Send just text as real-time input (e.g., for canvas interaction)
    override suspend fun sendRealtimeTextInput(jsonText: String) {
        sendRealtimeInput(text = jsonText, audio = null, video = null, activityStart = null, activityEnd = null, audioStreamEnd = null)
    }

    override suspend fun generateImage(text: String, imageUri: String?): Result<String> {
        val payload = GenerateImagePayload(text = text, imageUri = imageUri)
        val wrapper = ClientMessageWrapper(
            type = MessageTypes.GENERATE_IMAGE,
            payload = json.encodeToJsonElement(payload)
        )
        try {
            val jsonMessage = json.encodeToString(wrapper)
            webSocketClient.sendMessage(jsonMessage)

            // Wait for the image generation result with a timeout
            val resultPayload: ImageGenerationResultPayload? = withTimeoutOrNull(30000L) { // Timeout after 30 seconds
                imageGenerationResultChannel.receive()
            }

            return if (resultPayload != null) {
                if (resultPayload.success == true && resultPayload.imageUrl != null) { // Use safe access for success
                    Result.success(resultPayload.imageUrl)
                } else {
                    Result.failure(RuntimeException(resultPayload.error ?: "Image generation failed"))
                }
            } else {
                Result.failure(RuntimeException("Image generation request timed out or no result received."))
            }

        } catch (e: Exception) {
            println("Error sending GENERATE_IMAGE or receiving result: ${e.message}")
            return Result.failure(e)
        }
    }

    override suspend fun handleImageGenerationResult(payload: ImageGenerationResultPayload) {
        try {
            imageGenerationResultChannel.send(payload)
            println("Sent ImageGenerationResultPayload to channel from handleImageGenerationResult.")
        } catch (e: Exception) {
            println("Error sending ImageGenerationResultPayload to channel from handleImageGenerationResult: ${e.message}")
        }
    }
}