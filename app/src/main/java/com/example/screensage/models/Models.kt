package com.example.screensage.models

enum class AiProvider {
    GEMINI,
    CHATGPT,
    CLAUDE
}

data class AiResponse(
    val explanation: String,
    val confidence: Double,
    val timestamp: String
)

data class ErrorMessage(
    val title: String,
    val message: String,
    val isRetryable: Boolean
)

enum class OverlayState {
    COLLAPSED,
    EXPANDED_IDLE,
    EXPANDED_LOADING,
    EXPANDED_RESPONSE,
    EXPANDED_ERROR
}

data class ChatMessage(
    val id: String,
    val role: String, // "user", "assistant", or "system"
    val content: String,
    val timestamp: Long
)

data class ChatSession(
    val id: String,
    val title: String,
    val messages: MutableList<ChatMessage>,
    val createdAt: Long,
    val updatedAt: Long
)
