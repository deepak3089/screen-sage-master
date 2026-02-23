package com.example.screensage.ai

import com.example.screensage.models.AiResponse
import com.example.screensage.models.ChatMessage
import com.example.screensage.models.ErrorMessage
import com.example.screensage.preferences.PreferencesManager
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiRepository(
    private val aiClient: AiClient,
    private val preferencesManager: PreferencesManager
) {
    suspend fun preloadLocalModel(): Result<Unit> {
        val provider = preferencesManager.getProvider()
        if (provider.lowercase() != "local") {
            return Result.success(Unit)
        }
        
        return try {
            aiClient.modelManager.loadModel()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun explainText(text: String, conversationHistory: List<ChatMessage> = emptyList()): Result<AiResponse> {
        val apiKey = preferencesManager.getApiKey()
        val provider = preferencesManager.getProvider()
        
        // Local provider doesn't need API key
        if (provider.lowercase() != "local" && apiKey.isNullOrEmpty()) {
            return Result.failure(
                Exception("API key not configured")
            )
        }
        val model = preferencesManager.getModel()
        val systemPrompt = preferencesManager.getSystemPrompt()
        
        return try {
            // Increased timeout for local model (first inference can take 30+ seconds)
            val result = withTimeoutOrNull(60000) {
                aiClient.requestExplanation(text, apiKey ?: "", provider, model, systemPrompt, conversationHistory)
            }
            result ?: Result.failure(Exception("Request timed out"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitQuery(query: String, conversationHistory: List<ChatMessage> = emptyList()): Result<AiResponse> {
        val apiKey = preferencesManager.getApiKey()
        val provider = preferencesManager.getProvider()
        
        // Local provider doesn't need API key
        if (provider.lowercase() != "local" && apiKey.isNullOrEmpty()) {
            return Result.failure(
                Exception("API key not configured")
            )
        }
        val model = preferencesManager.getModel()
        val systemPrompt = preferencesManager.getSystemPrompt()
        
        return try {
            // Increased timeout for local model (first inference can take 30+ seconds)
            val result = withTimeoutOrNull(60000) {
                aiClient.requestQuery(query, apiKey ?: "", provider, model, systemPrompt, conversationHistory)
            }
            result ?: Result.failure(Exception("Request timed out"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatPrompt(input: String): String {
        return "Explain the following text concisely:\n\n$input"
    }

    fun handleError(exception: Exception): ErrorMessage {
        // Get full error details for debugging
        val fullError = buildString {
            append("Exception: ${exception.javaClass.simpleName}\n")
            append("Message: ${exception.message ?: "No message"}\n")
            exception.cause?.let {
                append("Cause: ${it.javaClass.simpleName}\n")
                append("Cause Message: ${it.message ?: "No message"}\n")
            }
        }
        
        return when (exception) {
            is SocketTimeoutException -> ErrorMessage(
                title = "Request Timeout",
                message = "The AI service is taking too long to respond.\n\nDetails:\n$fullError",
                isRetryable = true
            )
            is UnknownHostException -> ErrorMessage(
                title = "Connection Error",
                message = "Unable to reach AI service. Check your internet connection.\n\nDetails:\n$fullError",
                isRetryable = true
            )
            else -> {
                val errorMsg = exception.message ?: ""
                when {
                    errorMsg.contains("API key", ignoreCase = true) || 
                    errorMsg.contains("401", ignoreCase = true) ||
                    errorMsg.contains("unauthorized", ignoreCase = true) -> ErrorMessage(
                        title = "Invalid API Key",
                        message = "Your API key is invalid or expired.\n\nDetails:\n$fullError",
                        isRetryable = false
                    )
                    errorMsg.contains("not configured", ignoreCase = true) -> ErrorMessage(
                        title = "API Key Required",
                        message = "API key not configured. Please add your API key in settings.\n\nDetails:\n$fullError",
                        isRetryable = false
                    )
                    errorMsg.contains("rate limit", ignoreCase = true) ||
                    errorMsg.contains("429", ignoreCase = true) -> ErrorMessage(
                        title = "Rate Limit Exceeded",
                        message = "You've exceeded the API rate limit.\n\nDetails:\n$fullError",
                        isRetryable = true
                    )
                    errorMsg.contains("quota", ignoreCase = true) ||
                    errorMsg.contains("403", ignoreCase = true) -> ErrorMessage(
                        title = "Quota Exceeded",
                        message = "Your API quota has been exceeded.\n\nDetails:\n$fullError",
                        isRetryable = false
                    )
                    errorMsg.contains("timeout", ignoreCase = true) -> ErrorMessage(
                        title = "Request Timeout",
                        message = "The request timed out.\n\nDetails:\n$fullError",
                        isRetryable = true
                    )
                    errorMsg.contains("network", ignoreCase = true) ||
                    errorMsg.contains("connection", ignoreCase = true) -> ErrorMessage(
                        title = "Network Error",
                        message = "Network connection failed.\n\nDetails:\n$fullError",
                        isRetryable = true
                    )
                    else -> ErrorMessage(
                        title = "Error",
                        message = "An unexpected error occurred.\n\nFull Details:\n$fullError",
                        isRetryable = true
                    )
                }
            }
        }
    }
}
