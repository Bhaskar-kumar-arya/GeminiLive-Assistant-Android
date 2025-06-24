package com.gamesmith.assistantapp.data.remote

import com.gamesmith.assistantapp.data.model.ClientMessageWrapper
import com.gamesmith.assistantapp.data.model.ImageGenerationResultPayload
import com.gamesmith.assistantapp.data.model.LiveConfig
import com.gamesmith.assistantapp.data.model.ServerMessageWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface GeminiRepository {
    fun connect(serverUrl: String, config: LiveConfig)
    fun disconnect()
    suspend fun sendMessage(text: String)
    suspend fun sendAudioChunk(audioData: ByteArray)
    suspend fun updateConfig(config: LiveConfig)
    fun observeServerMessages(): Flow<ServerMessageWrapper>
    fun observeConnectionState(): StateFlow<ConnectionState>
    suspend fun sendToolResponse(toolResponse: com.gamesmith.assistantapp.data.model.ToolResponseWrapper)
    suspend fun sendRealtimeTextInput(jsonText: String)
    suspend fun sendRealtimeInput(
        text: String? = null,
        audio: com.gamesmith.assistantapp.data.model.GenerativeContentBlob? = null,
        video: com.gamesmith.assistantapp.data.model.GenerativeContentBlob? = null,
        activityStart: com.gamesmith.assistantapp.data.model.ActivityStart? = null,
        activityEnd: com.gamesmith.assistantapp.data.model.ActivityEnd? = null,
        audioStreamEnd: Boolean? = null
    )
    suspend fun generateImage(text: String, imageUri: String?): Result<String> // Added for image generation
    suspend fun handleImageGenerationResult(payload: ImageGenerationResultPayload)
}