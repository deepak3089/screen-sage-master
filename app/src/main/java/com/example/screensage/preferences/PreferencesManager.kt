package com.example.screensage.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreferencesManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedSharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.getString(KEY_API_KEY, null)
    }

    suspend fun setApiKey(key: String) = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.edit().putString(KEY_API_KEY, key).apply()
    }

    suspend fun getTheme(): String = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    suspend fun setTheme(theme: String) = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.edit().putString(KEY_THEME, theme).apply()
    }

    suspend fun getModel(): String? = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.getString(KEY_MODEL, null)
    }

    suspend fun setModel(model: String) = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.edit().putString(KEY_MODEL, model).apply()
    }

    suspend fun getProvider(): String = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.getString(KEY_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
    }

    suspend fun setProvider(provider: String) = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.edit().putString(KEY_PROVIDER, provider).apply()
    }

    fun getProviderSync(): String {
        return encryptedSharedPreferences.getString(KEY_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
    }

    suspend fun getOverlayColor(): String = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.getString(KEY_OVERLAY_COLOR, COLOR_PINK) ?: COLOR_PINK
    }

    fun getOverlayColorSync(): String {
        return encryptedSharedPreferences.getString(KEY_OVERLAY_COLOR, COLOR_PINK) ?: COLOR_PINK
    }

    suspend fun setOverlayColor(color: String) = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.edit().putString(KEY_OVERLAY_COLOR, color).apply()
    }

    suspend fun getSystemPromptPreset(): String = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.getString(KEY_SYSTEM_PROMPT_PRESET, PRESET_CONCISE) ?: PRESET_CONCISE
    }

    suspend fun setSystemPromptPreset(preset: String) = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.edit().putString(KEY_SYSTEM_PROMPT_PRESET, preset).apply()
    }

    suspend fun getCustomSystemPrompt(): String = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.getString(KEY_CUSTOM_SYSTEM_PROMPT, "") ?: ""
    }

    suspend fun setCustomSystemPrompt(prompt: String) = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.edit().putString(KEY_CUSTOM_SYSTEM_PROMPT, prompt).apply()
    }

    suspend fun getSystemPrompt(): String = withContext(Dispatchers.IO) {
        val preset = getSystemPromptPreset()
        when (preset.lowercase()) {
            PRESET_CONCISE -> "You are a helpful AI assistant. Provide brief, to-the-point explanations. Be concise and clear."
            PRESET_DETAILED -> "You are a helpful AI assistant. Provide comprehensive and thorough explanations. Include relevant details and context."
            PRESET_SIMPLE -> "You are a helpful AI assistant. Explain things in simple, easy-to-understand language. Avoid technical jargon and use beginner-friendly terms."
            PRESET_TECHNICAL -> "You are a helpful AI assistant. Provide technical explanations using advanced terminology. Assume the user has technical knowledge."
            PRESET_CREATIVE -> "You are a helpful AI assistant. Respond in an engaging, conversational tone. Be creative and personable in your explanations."
            PRESET_CUSTOM -> getCustomSystemPrompt().ifEmpty { "You are a helpful AI assistant." }
            else -> "You are a helpful AI assistant."
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        encryptedSharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "screen_sage_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_THEME = "theme"
        private const val KEY_MODEL = "model"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_OVERLAY_COLOR = "overlay_color"
        private const val KEY_SYSTEM_PROMPT_PRESET = "system_prompt_preset"
        private const val KEY_CUSTOM_SYSTEM_PROMPT = "custom_system_prompt"

        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"

        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_CHATGPT = "chatgpt"
        const val PROVIDER_CLAUDE = "claude"
        const val PROVIDER_LOCAL = "local"

        const val COLOR_PINK = "pink"
        const val COLOR_BLUE = "blue"
        const val COLOR_PURPLE = "purple"
        const val COLOR_GREEN = "green"

        const val PRESET_CONCISE = "concise"
        const val PRESET_DETAILED = "detailed"
        const val PRESET_SIMPLE = "simple"
        const val PRESET_TECHNICAL = "technical"
        const val PRESET_CREATIVE = "creative"
        const val PRESET_CUSTOM = "custom"
    }
}
