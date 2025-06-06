package com.gamesmith.assistantapp.data.model

object MessageTypes {
    // Mobile App -> Server
    const val CONNECT_GEMINI = "CONNECT_GEMINI"
    const val SEND_MESSAGE = "SEND_MESSAGE"
    const val SEND_REALTIME_INPUT = "SEND_REALTIME_INPUT"
    const val SEND_TOOL_RESPONSE = "SEND_TOOL_RESPONSE"
    const val UPDATE_CONFIG = "UPDATE_CONFIG"
    const val DISCONNECT_GEMINI = "DISCONNECT_GEMINI"

    // Server -> Mobile App
    const val GEMINI_CONNECTED = "GEMINI_CONNECTED"
    const val GEMINI_DISCONNECTED = "GEMINI_DISCONNECTED"
    const val GEMINI_ERROR = "GEMINI_ERROR"
    const val CONTENT_MESSAGE = "CONTENT_MESSAGE"
    const val TOOL_CALL = "TOOL_CALL"
    const val TOOL_CALL_CANCELLATION = "TOOL_CALL_CANCELLATION"
    const val SETUP_COMPLETE = "SETUP_COMPLETE"
    const val INTERRUPTED = "INTERRUPTED"
    const val TURN_COMPLETE = "TURN_COMPLETE"
    const val AUDIO_CHUNK = "AUDIO_CHUNK"
    const val LOG_MESSAGE = "LOG_MESSAGE"

    // WebRTC Signaling (Server -> Mobile App)
    const val WEBRTC_ANSWER = "WEBRTC_ANSWER"
    const val WEBRTC_ICE_CANDIDATE = "WEBRTC_ICE_CANDIDATE"

    // Custom Gemini status
    const val ASSISTANT_SPEAKING = "ASSISTANT_SPEAKING"
} 