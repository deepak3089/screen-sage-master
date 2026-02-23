package com.example.screensage.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class LocalModelManager(private val context: Context) {
    private var llmInference: LlmInference? = null
    private var isModelLoaded = false
    private var isLoading = false
    
    // Mutex to ensure only one inference at a time
    private val inferenceMutex = Mutex()
    
    // Track current inference job for cancellation
    private var currentInferenceJob: Job? = null
    
    companion object {
        private const val TAG = "LocalModelManager"
        private const val MODEL_NAME = "Gemma3-1B-IT_seq128_q4_block128_ekv4096.task"
        // Direct download from GitHub Release
        private const val MODEL_URL = "https://github.com/Ixaruss/Screen-Sage/releases/download/android/Gemma3-1B-IT_seq128_q4_block128_ekv4096.task"
        
        // Resource requirements (updated for smaller Gemma3-1B model)
        const val MODEL_SIZE_MB = 676L  // 676 MB (much smaller than 2GB!)
        const val REQUIRED_RAM_MB = 4096L  // 4GB minimum
        const val ESTIMATED_RAM_USAGE_MB = 1500L  // ~1.5GB when loaded (less than Gemma-2B)
    }
    
    /**
     * Download model with progress callback
     */
    suspend fun downloadModel(onProgress: (Float, Long, Long) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== DOWNLOAD MODEL CALLED ===")
        try {
            val modelFile = File(context.filesDir, MODEL_NAME)
            Log.d(TAG, "Model file path: ${modelFile.absolutePath}")
            Log.d(TAG, "Files dir: ${context.filesDir.absolutePath}")
            
            // Delete partial download if exists
            if (modelFile.exists() && modelFile.length() < MODEL_SIZE_MB * 1024 * 1024) {
                Log.d(TAG, "Deleting partial download (size: ${modelFile.length()} bytes)")
                modelFile.delete()
            }
            
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.d(TAG, "Model already downloaded (size: ${modelFile.length()} bytes)")
                return@withContext Result.success(Unit)
            }
            
            Log.d(TAG, "Starting model download from: $MODEL_URL")
            Log.d(TAG, "Download destination: ${modelFile.absolutePath}")
            
            val url = URL(MODEL_URL)
            Log.d(TAG, "URL object created successfully")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            Log.d(TAG, "Connection object created, attempting to connect...")
            
            try {
                connection.connect()
                Log.d(TAG, "Connection established successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed with exception: ${e.javaClass.simpleName}", e)
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Exception cause: ${e.cause}")
                return@withContext Result.failure(Exception("Network connection failed: ${e.message}. Please check your internet connection."))
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "HTTP Response Code: $responseCode")
            Log.d(TAG, "HTTP Response Message: ${connection.responseMessage}")
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorMsg = when (responseCode) {
                    HttpURLConnection.HTTP_NOT_FOUND -> "Model file not found on server (404)"
                    HttpURLConnection.HTTP_FORBIDDEN -> "Access forbidden (403). Check your network settings."
                    HttpURLConnection.HTTP_UNAVAILABLE -> "Server unavailable (503). Try again later."
                    else -> "Server returned error code: $responseCode - ${connection.responseMessage}"
                }
                Log.e(TAG, "HTTP error: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }
            
            val totalSize = connection.contentLength.toLong()
            Log.d(TAG, "Content length: $totalSize bytes (${totalSize / (1024 * 1024)}MB)")
            
            if (totalSize <= 0) {
                Log.e(TAG, "Invalid content length: $totalSize")
                return@withContext Result.failure(Exception("Invalid file size from server (got $totalSize bytes)"))
            }
            
            var downloadedSize = 0L
            Log.d(TAG, "Starting download loop...")
            
            connection.inputStream.use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastLogTime = System.currentTimeMillis()
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        
                        val progress = downloadedSize.toFloat() / totalSize
                        onProgress(progress, downloadedSize, totalSize)
                        
                        // Log progress every 5 seconds
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastLogTime > 5000) {
                            Log.d(TAG, "Download progress: ${(progress * 100).toInt()}% ($downloadedSize / $totalSize bytes)")
                            lastLogTime = currentTime
                        }
                    }
                }
            }
            
            val finalSize = modelFile.length()
            Log.d(TAG, "Download complete! Final file size: $finalSize bytes (${finalSize / (1024 * 1024)}MB)")
            
            if (finalSize < totalSize) {
                Log.e(TAG, "Downloaded file is smaller than expected: $finalSize < $totalSize")
                return@withContext Result.failure(Exception("Download incomplete: got ${finalSize / (1024 * 1024)}MB of ${totalSize / (1024 * 1024)}MB"))
            }
            
            Result.success(Unit)
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Unknown host exception", e)
            Result.failure(Exception("Cannot reach download server (${e.message}). Check your internet connection."))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Socket timeout exception", e)
            Result.failure(Exception("Download timed out (${e.message}). Check your internet connection and try again."))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IO exception during download", e)
            Result.failure(Exception("Download interrupted: ${e.message ?: "IO error"}"))
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception", e)
            Result.failure(Exception("Permission denied: ${e.message}. Check app permissions."))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during download: ${e.javaClass.simpleName}", e)
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause}")
            Log.e(TAG, "Stack trace:", e)
            Result.failure(Exception("Download failed: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}"))
        }
    }
    
    /**
     * Check if device meets minimum requirements
     */
    fun checkDeviceCompatibility(): DeviceCompatibility {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)  // Convert to MB
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val availableMemory = maxMemory - (totalMemory - freeMemory)
        
        val hasEnoughRAM = maxMemory >= REQUIRED_RAM_MB
        val hasEnoughFreeRAM = availableMemory >= ESTIMATED_RAM_USAGE_MB
        
        return DeviceCompatibility(
            isCompatible = hasEnoughRAM,
            totalRAM = maxMemory,
            availableRAM = availableMemory,
            requiredRAM = REQUIRED_RAM_MB,
            estimatedUsage = ESTIMATED_RAM_USAGE_MB,
            warning = when {
                !hasEnoughRAM -> "Device has insufficient RAM (${maxMemory}MB). Minimum ${REQUIRED_RAM_MB}MB required."
                !hasEnoughFreeRAM -> "Low available memory (${availableMemory}MB). Model may run slowly."
                else -> null
            }
        )
    }
    
    /**
     * Check if model is downloaded
     */
    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.filesDir, MODEL_NAME)
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * Get model file path
     */
    fun getModelPath(): String {
        return File(context.filesDir, MODEL_NAME).absolutePath
    }
    
    /**
     * Import model from external file (for manual installation)
     */
    suspend fun importModelFromUri(uri: android.net.Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, MODEL_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.d(TAG, "Model imported successfully: ${modelFile.length()} bytes")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to import model file"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model", e)
            Result.failure(e)
        }
    }
    
    /**
     * Load model into memory (lazy loading)
     */
    suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isModelLoaded) {
            return@withContext Result.success(Unit)
        }
        
        if (isLoading) {
            return@withContext Result.failure(Exception("Model is already loading"))
        }
        
        if (!isModelDownloaded()) {
            return@withContext Result.failure(Exception("Model not downloaded. Please download the model first."))
        }
        
        try {
            isLoading = true
            Log.d(TAG, "Loading local model from: ${getModelPath()}")
            
            // Minimal options for MediaPipe 0.10.27 - only model path and max tokens
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(getModelPath())
                .setMaxTokens(512)  // Reduced from 1024 for faster responses
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            isModelLoaded = true
            isLoading = false
            
            Log.d(TAG, "Local model loaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            isLoading = false
            isModelLoaded = false
            Log.e(TAG, "Failed to load model", e)
            Result.failure(e)
        }
    }
    
    /**
     * Unload model from memory to free resources
     */
    fun unloadModel() {
        try {
            llmInference?.close()
            llmInference = null
            isModelLoaded = false
            Log.d(TAG, "Model unloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }
    
    /**
     * Generate response from local model (thread-safe with mutex and cancellation support)
     */
    suspend fun generateResponse(
        prompt: String,
        systemPrompt: String = "You are a helpful AI assistant."
    ): Result<String> {
        // Cancel any ongoing inference
        currentInferenceJob?.cancel()
        
        return inferenceMutex.withLock {
            withContext(Dispatchers.IO) {
                // Store current job for potential cancellation
                currentInferenceJob = coroutineContext[Job]
                
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "=== Starting inference request at $startTime ===")
                
                if (!isModelLoaded) {
                    Log.d(TAG, "Model not loaded, loading now...")
                    val loadResult = loadModel()
                    if (loadResult.isFailure) {
                        Log.e(TAG, "Failed to load model: ${loadResult.exceptionOrNull()?.message}")
                        return@withContext Result.failure(loadResult.exceptionOrNull() ?: Exception("Failed to load model"))
                    }
                    Log.d(TAG, "Model loaded successfully in ${System.currentTimeMillis() - startTime}ms")
                }
                
                try {
                    val inference = llmInference ?: return@withContext Result.failure(Exception("Model not initialized"))
                    
                    // Simplified prompt format for Gemma3
                    // Just send the user message directly - the model handles the rest
                    val fullPrompt = prompt
                    
                    Log.d(TAG, "Generating response for prompt: ${prompt.take(100)}...")
                    val inferenceStartTime = System.currentTimeMillis()
                    val response = inference.generateResponse(fullPrompt)
                    val inferenceTime = System.currentTimeMillis() - inferenceStartTime
                    
                    Log.d(TAG, "Response generated successfully in ${inferenceTime}ms: ${response.take(100)}...")
                    Log.d(TAG, "=== Total request time: ${System.currentTimeMillis() - startTime}ms ===")
                    Result.success(response)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Inference cancelled after ${System.currentTimeMillis() - startTime}ms")
                    Result.failure(Exception("Request cancelled"))
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating response after ${System.currentTimeMillis() - startTime}ms", e)
                    Result.failure(e)
                } finally {
                    currentInferenceJob = null
                }
            }
        }
    }
    
    /**
     * Delete model file to free storage
     */
    fun deleteModel(): Boolean {
        unloadModel()
        val modelFile = File(context.filesDir, MODEL_NAME)
        return if (modelFile.exists()) {
            modelFile.delete()
        } else {
            true
        }
    }
    
    /**
     * Get model file size
     */
    fun getModelSize(): Long {
        val modelFile = File(context.filesDir, MODEL_NAME)
        return if (modelFile.exists()) modelFile.length() else 0
    }
}

data class DeviceCompatibility(
    val isCompatible: Boolean,
    val totalRAM: Long,
    val availableRAM: Long,
    val requiredRAM: Long,
    val estimatedUsage: Long,
    val warning: String?
)
