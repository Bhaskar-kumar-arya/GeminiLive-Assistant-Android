package com.gamesmith.assistantapp.data.remote

import com.gamesmith.assistantapp.data.model.ClientMessageWrapper
import com.gamesmith.assistantapp.data.model.ConnectGeminiPayload
import com.gamesmith.assistantapp.data.model.LiveConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

// Minimal placeholder for LiveConfig has been removed.
// ConnectGeminiPayload and ConnectGeminiMessage have been removed as they are now part of the DTOs in data/model/

@kotlinx.serialization.Serializable
data class RTCIceCandidateJson(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String?)

// DTOs for incoming WebRTC messages from the server
@kotlinx.serialization.Serializable
data class WebRTCAnswerSdp(val sdp: String)

@kotlinx.serialization.Serializable
data class WebRTCIceCandidateContainer(val candidate: RTCIceCandidateJson)

// Generic wrapper for parsing the type of incoming server messages
@kotlinx.serialization.Serializable
data class ServerMessageEnvelope(val type: String, val payload: kotlinx.serialization.json.JsonElement? = null)

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnecting : ConnectionState()
    data class Disconnected(val reason: String? = null) : ConnectionState()
    data class Error(val message: String, val cause: Throwable? = null) : ConnectionState()
}

class GeminiWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Increased buffer size to handle rapid audio chunks from Gemini
    // replay = 1 to ensure late collectors get the last message
    // extraBufferCapacity = 100 to handle bursts of audio messages
    private val _serverMessages = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val serverMessages: SharedFlow<String> = _serverMessages.asSharedFlow()

    private var currentServerUrl: String? = null
    private var currentConfig: LiveConfig? = null

    fun connect(serverUrl: String, initialConfig: LiveConfig) {
        if (_connectionState.value == ConnectionState.Connecting || _connectionState.value == ConnectionState.Connected) {
            if (this.currentServerUrl == serverUrl && this.currentConfig == initialConfig && webSocket != null) {
                // Already connected or connecting to the same server with the same config
                return
            }
            disconnectGracefully() // Disconnect previous session if different
        }

        this.currentServerUrl = serverUrl
        this.currentConfig = initialConfig
        _connectionState.value = ConnectionState.Connecting

        val request = Request.Builder().url(serverUrl).build()
        webSocket = okHttpClient.newWebSocket(request, GeminiWebSocketListener())
    }

    fun sendWebRTCOffer(sdp: String) {
        val offerMessage = ClientMessageWrapper(
            type = "WEBRTC_OFFER",
            payload = json.encodeToJsonElement(mapOf("sdp" to sdp))
        )
        sendMessage(json.encodeToString(offerMessage))
    }

    fun sendWebRTCAnswer(sdp: String) {
        val answerMessage = ClientMessageWrapper(
            type = "WEBRTC_ANSWER",
            payload = json.encodeToJsonElement(mapOf("sdp" to sdp))
        )
        sendMessage(json.encodeToString(answerMessage))
    }

    fun sendWebRTCICECandidate(candidate: RTCIceCandidateJson) {
        val iceCandidateMessage = ClientMessageWrapper(
            type = "WEBRTC_ICE_CANDIDATE",
            payload = json.encodeToJsonElement(mapOf("candidate" to candidate))
        )
        sendMessage(json.encodeToString(iceCandidateMessage))
    }

    fun sendMessage(jsonMessage: String) {
        if (_connectionState.value == ConnectionState.Connected) {
            val sent = webSocket?.send(jsonMessage)
            if (sent == false) {
                // Handle failure to send, possibly due to queue full or closed socket
                println("Error: WebSocket send queue is full or socket is closed.")
                // Consider re-evaluating connection state or notifying via error flow
            }
        } else {
            println("Error: Cannot send message, WebSocket not connected.")
            // Potentially queue message or notify via error StateFlow/SharedFlow
        }
    }

    fun disconnect() {
        disconnectGracefully()
        // Ensure state is updated if no active websocket existed
        if (_connectionState.value !is ConnectionState.Error && _connectionState.value !is ConnectionState.Disconnected) {
             _connectionState.value = ConnectionState.Disconnected("Client initiated disconnect")
        }
    }

    private fun disconnectGracefully() {
        if (webSocket != null) {
            _connectionState.value = ConnectionState.Disconnecting
            webSocket?.close(1000, "Client requested disconnect")
            // webSocket is nulled out in onClosed or onFailure
        }
        // Clear config only when disconnect is explicitly called or connection fully closes
        // currentServerUrl = null
        // currentConfig = null
    }

    private inner class GeminiWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = ConnectionState.Connected
            currentConfig?.let { config ->
                try {
                    val connectPayload = ConnectGeminiPayload(initialConfig = config)
                    val connectMessage = ClientMessageWrapper(
                        type = "CONNECT_GEMINI",
                        payload = json.encodeToJsonElement(connectPayload)
                    )
                    val jsonMessage = json.encodeToString(connectMessage)
                    webSocket.send(jsonMessage)
                } catch (e: Exception) {
                    // Log the error more formally in a real app
                    println("Error sending CONNECT_GEMINI: ${e.message}")
                    _connectionState.value = ConnectionState.Error("Failed to send CONNECT_GEMINI: ${e.message}", e)
                    webSocket.close(1001, "Failed to send initial config") // 1001 = Going Away
                }
            } ?: run {
                println("Error: Initial config not available for CONNECT_GEMINI")
                _connectionState.value = ConnectionState.Error("Initial config not available for CONNECT_GEMINI", null)
                webSocket.close(1001, "Missing initial config")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Use emit instead of tryEmit for better reliability
            // The SharedFlow now has sufficient buffer capacity and overflow handling
            try {
            if (!_serverMessages.tryEmit(text)) {
                    println("Warning: SharedFlow buffer full, message dropped. Consider increasing buffer size.")
                }
            } catch (e: Exception) {
                println("Error emitting server message: ${e.message}")
                e.printStackTrace()
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // The current plan focuses on JSON strings.
            // If binary messages become necessary, this method will need proper handling.
            println("Received binary message, currently not handled.")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = ConnectionState.Disconnecting
            // The server or client is initiating the close.
            // The actual close confirmation will come in onClosed.
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = ConnectionState.Disconnected(reason)
            this@GeminiWebSocketClient.webSocket = null
            this@GeminiWebSocketClient.currentServerUrl = null
            this@GeminiWebSocketClient.currentConfig = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val errorMessage = response?.message ?: t.message ?: "Unknown WebSocket failure"
            _connectionState.value = ConnectionState.Error(errorMessage, t)
            // Clean up WebSocket reference, as it's no longer usable
            if (this@GeminiWebSocketClient.webSocket == webSocket) {
                 this@GeminiWebSocketClient.webSocket = null
                 this@GeminiWebSocketClient.currentServerUrl = null
                 this@GeminiWebSocketClient.currentConfig = null
            }
        }
    }
} 