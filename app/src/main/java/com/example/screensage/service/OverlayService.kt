package com.example.screensage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.screensage.MainActivity
import com.example.screensage.R
import com.example.screensage.ai.AiClient
import com.example.screensage.ai.AiRepository
import com.example.screensage.models.ChatSession
import com.example.screensage.models.OverlayState
import com.example.screensage.preferences.PreferencesManager
import com.example.screensage.storage.ChatHistoryManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var aiRepository: AiRepository
    private lateinit var overlayManager: OverlayManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentState = OverlayState.COLLAPSED

    private val textSelectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TEXT_SELECTED -> {
                    val selectedText = intent.getStringExtra(EXTRA_SELECTED_TEXT) ?: return
                    handleTextSelection(selectedText)
                }
                ACTION_RESTORE_SESSION -> {
                    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
                    restoreSession(sessionId)
                }
            }
        }
    }
    
    private fun restoreSession(sessionId: String) {
        serviceScope.launch {
            val chatHistoryManager = ChatHistoryManager(this@OverlayService)
            val session = chatHistoryManager.loadSession(sessionId)
            if (session != null) {
                overlayManager.restoreSession(session)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferencesManager = PreferencesManager(this)
        aiRepository = AiRepository(AiClient(this), preferencesManager)
        overlayManager = OverlayManager(this, windowManager, serviceScope, preferencesManager) { state ->
            currentState = state
        }

        registerBroadcastReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        overlayManager.createOverlay()
        overlayManager.setAiRepository(aiRepository)
        
        // Preload the local model in background if using local provider
        serviceScope.launch {
            try {
                android.util.Log.d("OverlayService", "Preloading local model...")
                aiRepository.preloadLocalModel()
                android.util.Log.d("OverlayService", "Local model preloaded successfully")
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "Failed to preload model", e)
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBroadcastReceiver()
        overlayManager.removeAllViews()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification() {
        val channelId = NOTIFICATION_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Sage Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TEXT_SELECTED)
            addAction(ACTION_RESTORE_SESSION)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(textSelectionReceiver, filter)
    }

    private fun unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(textSelectionReceiver)
    }

    private fun handleTextSelection(text: String) {
        overlayManager.handleTextSelection(text, aiRepository, serviceScope)
    }

    companion object {
        const val ACTION_TEXT_SELECTED = "com.screensage.TEXT_SELECTED"
        const val ACTION_RESTORE_SESSION = "com.screensage.RESTORE_SESSION"
        const val EXTRA_SELECTED_TEXT = "selected_text"
        const val EXTRA_SESSION_ID = "session_id"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "screen_sage_overlay"
    }
}