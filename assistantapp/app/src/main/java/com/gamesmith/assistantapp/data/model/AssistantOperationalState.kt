package com.gamesmith.assistantapp.data.model

sealed class AssistantOperationalState {
    object IDLE : AssistantOperationalState()
    object LISTENING : AssistantOperationalState()
    object PROCESSING : AssistantOperationalState() // Waiting for Gemini's response
    object SPEAKING : AssistantOperationalState() // Gemini is speaking (playing audio)
    data class ERROR(val message: String) : AssistantOperationalState()
} 