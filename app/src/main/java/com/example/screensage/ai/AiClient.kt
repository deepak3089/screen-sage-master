package com.example.screensage.ai

import android.content.Context
import android.util.Log
import com.example.screensage.models.AiResponse
import com.example.screensage.models.ChatMessage
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Gemini API
interface GeminiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

// ChatGPT API
interface ChatGPTService {
    @POST("v1/chat/completions")
    suspend fun createCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatGPTRequest
    ): ChatGPTResponse
}

data class ChatGPTRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<ChatGPTMessage>
)

data class ChatGPTMessage(
    val role: String,
    val content: String
)

data class ChatGPTResponse(
    val choices: List<ChatGPTChoice>?
)

data class ChatGPTChoice(
    val message: ChatGPTMessage?
)

// Claude API
interface ClaudeService {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: ClaudeRequest
    ): ClaudeResponse
}

data class ClaudeRequest(
    val model: String = "claude-3-sonnet-20240229",
    val max_tokens: Int = 1024,
    val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    val role: String,
    val content: String
)

data class ClaudeResponse(
    val content: List<ClaudeContent>?
)

data class ClaudeContent(
    val text: String?
)

class AiClient(private val context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val geminiRetrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(httpClient)
        .build()

    private val chatGptRetrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(httpClient)
        .build()

    private val claudeRetrofit = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(httpClient)
        .build()

    private val geminiService = geminiRetrofit.create(GeminiService::class.java)
    private val chatGptService = chatGptRetrofit.create(ChatGPTService::class.java)
    private val claudeService = claudeRetrofit.create(ClaudeService::class.java)
    
    // Local model manager (lazy initialization)
    private val localModelManager by lazy { LocalModelManager(context) }
    
    // Track last request time for debouncing
    private var lastRequestTime = 0L
    private val minRequestInterval = 2000L // 2 seconds between requests for local model (increased from 500ms)
    
    // Track if a local request is in progress
    @Volatile
    private var isLocalRequestInProgress = false
    
    // Lock for thread-safe request guard
    private val requestLock = Any()

    suspend fun requestQuery(
        query: String,
        apiKey: String,
        provider: String = "gemini",
        model: String? = null,
        systemPrompt: String = "You are a helpful AI assistant.",
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        try {
            val response = when (provider.lowercase()) {
                "local" -> requestLocal(query, systemPrompt, conversationHistory)
                "gemini" -> requestGemini(query, apiKey, model, systemPrompt, conversationHistory)
                "chatgpt", "openai" -> requestChatGPT(query, apiKey, model, systemPrompt, conversationHistory)
                "claude" -> requestClaude(query, apiKey, model, systemPrompt, conversationHistory)
                else -> requestGemini(query, apiKey, model, systemPrompt, conversationHistory)
            }
            response
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun requestLocal(
        query: String,
        systemPrompt: String = "You are a helpful AI assistant.",
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<AiResponse> {
        return try {
            // Check if a request is already in progress (thread-safe)
            synchronized(requestLock) {
                if (isLocalRequestInProgress) {
                    Log.d("AiClient", "Request rejected: Another request is in progress")
                    return Result.failure(Exception("Please wait for the current request to complete."))
                }
                isLocalRequestInProgress = true
            }
            
            // Debounce: prevent rapid-fire requests
            val currentTime = System.currentTimeMillis()
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < minRequestInterval) {
                Log.d("AiClient", "Request too soon after last request (${timeSinceLastRequest}ms < ${minRequestInterval}ms)")
                kotlinx.coroutines.delay(minRequestInterval - timeSinceLastRequest)
            }
            lastRequestTime = System.currentTimeMillis()
            
            try {
                // Build conversation context
                val contextBuilder = StringBuilder()
                
                if (conversationHistory.isNotEmpty()) {
                    conversationHistory.takeLast(3).forEach { msg ->  // Reduced from 5 to 3 for faster processing
                        val role = if (msg.role == "user") "User" else "Assistant"
                        contextBuilder.append("$role: ${msg.content}\n")
                    }
                }
                
                contextBuilder.append("User: $query")
                
                Log.d("AiClient", "Starting local model request")
                val result = localModelManager.generateResponse(contextBuilder.toString(), systemPrompt)
                
                result.fold(
                    onSuccess = { response ->
                        Log.d("AiClient", "Local model request completed successfully")
                        Result.success(
                            AiResponse(
                                explanation = response.trim(),
                                confidence = 0.9,
                                timestamp = System.currentTimeMillis().toString()
                            )
                        )
                    },
                    onFailure = { exception ->
                        Log.d("AiClient", "Local model request failed: ${exception.message}")
                        Result.failure(exception)
                    }
                )
            } finally {
                // Always mark request as complete (thread-safe)
                synchronized(requestLock) {
                    isLocalRequestInProgress = false
                    Log.d("AiClient", "Request in-progress flag cleared")
                }
            }
        } catch (e: Exception) {
            synchronized(requestLock) {
                isLocalRequestInProgress = false
            }
            Log.e("AiClient", "Unexpected error in requestLocal", e)
            Result.failure(e)
        }
    }

    private suspend fun requestGemini(
        query: String, 
        apiKey: String, 
        model: String? = null,
        systemPrompt: String = "You are a helpful AI assistant.",
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<AiResponse> {
        return try {
            val modelName = model ?: "gemini-3-flash-preview"
            
            // Build conversation context for Gemini
            val contents = mutableListOf<GeminiContent>()
            
            // Add system prompt and conversation history as a single user message
            val contextBuilder = StringBuilder()
            contextBuilder.append(systemPrompt)
            
            if (conversationHistory.isNotEmpty()) {
                contextBuilder.append("\n\nConversation history:\n")
                conversationHistory.forEach { msg ->
                    val role = if (msg.role == "user") "User" else "Assistant"
                    contextBuilder.append("$role: ${msg.content}\n")
                }
            }
            
            contextBuilder.append("\nUser: $query")
            
            contents.add(GeminiContent(parts = listOf(GeminiPart(text = contextBuilder.toString()))))
            
            val request = GeminiRequest(contents = contents)
            val dynamicService = geminiRetrofit.create(GeminiService::class.java)
            val response = dynamicService.generateContent(modelName, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response"
            Result.success(
                AiResponse(
                    explanation = text,
                    confidence = 0.9,
                    timestamp = System.currentTimeMillis().toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun requestChatGPT(
        query: String, 
        apiKey: String, 
        model: String? = null,
        systemPrompt: String = "You are a helpful AI assistant.",
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<AiResponse> {
        return try {
            val modelName = model ?: "gpt-5.2"
            
            // Build messages list with system prompt and conversation history
            val messages = mutableListOf<ChatGPTMessage>()
            messages.add(ChatGPTMessage(role = "system", content = systemPrompt))
            
            // Add conversation history
            conversationHistory.forEach { msg ->
                messages.add(ChatGPTMessage(role = msg.role, content = msg.content))
            }
            
            // Add current query
            messages.add(ChatGPTMessage(role = "user", content = query))
            
            val request = ChatGPTRequest(model = modelName, messages = messages)
            val response = chatGptService.createCompletion("Bearer $apiKey", request)
            val text = response.choices?.firstOrNull()?.message?.content
                ?: "No response"
            Result.success(
                AiResponse(
                    explanation = text,
                    confidence = 0.9,
                    timestamp = System.currentTimeMillis().toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun requestClaude(
        query: String, 
        apiKey: String, 
        model: String? = null,
        systemPrompt: String = "You are a helpful AI assistant.",
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<AiResponse> {
        return try {
            val modelName = model ?: "claude-3-5-sonnet-20241022"
            
            // Build messages list with conversation history
            val messages = mutableListOf<ClaudeMessage>()
            
            // Build context with system prompt and history
            val contextBuilder = StringBuilder()
            contextBuilder.append(systemPrompt)
            
            if (conversationHistory.isNotEmpty()) {
                contextBuilder.append("\n\nConversation history:\n")
                conversationHistory.forEach { msg ->
                    val role = if (msg.role == "user") "User" else "Assistant"
                    contextBuilder.append("$role: ${msg.content}\n")
                }
            }
            
            contextBuilder.append("\nUser: $query")
            
            messages.add(ClaudeMessage(role = "user", content = contextBuilder.toString()))
            
            val request = ClaudeRequest(model = modelName, messages = messages)
            val response = claudeService.createMessage(apiKey, request = request)
            val text = response.content?.firstOrNull()?.text
                ?: "No response"
            Result.success(
                AiResponse(
                    explanation = text,
                    confidence = 0.9,
                    timestamp = System.currentTimeMillis().toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestExplanation(
        text: String,
        apiKey: String,
        provider: String = "gemini",
        model: String? = null,
        systemPrompt: String = "You are a helpful AI assistant.",
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<AiResponse> = requestQuery(text, apiKey, provider, model, systemPrompt, conversationHistory)
    
    /**
     * Access local model manager for model management operations
     */
    val modelManager: LocalModelManager
        get() = localModelManager
    
    /**
     * Unload local model to free memory
     */
    fun unloadLocalModel() {
        localModelManager.unloadModel()
    }
}
