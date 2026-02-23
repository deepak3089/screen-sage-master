package com.example.screensage.storage

import android.content.Context
import com.example.screensage.models.ChatSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ChatHistoryManager(private val context: Context) {
    private val gson = Gson()
    private val historyFile = File(context.filesDir, "chat_history.json")
    private val maxSessions = 50

    suspend fun saveSession(session: ChatSession) = withContext(Dispatchers.IO) {
        try {
            val sessions = getAllSessions().toMutableList()
            
            // Remove existing session with same ID if it exists
            sessions.removeAll { it.id == session.id }
            
            // Add new session at the beginning
            sessions.add(0, session)
            
            // Keep only the last maxSessions
            val trimmedSessions = sessions.take(maxSessions)
            
            // Write to file
            historyFile.writeText(gson.toJson(trimmedSessions))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadSession(sessionId: String): ChatSession? = withContext(Dispatchers.IO) {
        try {
            getAllSessions().find { it.id == sessionId }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getAllSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        try {
            if (!historyFile.exists()) {
                return@withContext emptyList()
            }
            
            val json = historyFile.readText()
            if (json.isBlank()) {
                return@withContext emptyList()
            }
            
            val type = object : TypeToken<List<ChatSession>>() {}.type
            gson.fromJson<List<ChatSession>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val sessions = getAllSessions().toMutableList()
            sessions.removeAll { it.id == sessionId }
            historyFile.writeText(gson.toJson(sessions))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        try {
            if (historyFile.exists()) {
                historyFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
