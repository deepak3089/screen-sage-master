package com.example.screensage.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreenSageAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastSelectedText = ""
    private var lastSelectionTime = 0L
    private val debounceThreshold = 500L // 500ms debounce

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleTextSelection(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Optional: handle click events if needed
            }
        }
    }

    override fun onInterrupt() {
        // Handle service interruption
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service is now connected and ready to receive events
    }

    private fun handleTextSelection(event: AccessibilityEvent) {
        val source = event.source ?: return

        // Ignore events from our own app's EditText to prevent triggering AI on typing
        if (event.packageName == packageName) {
            source.recycle()
            return
        }

        // Extract selected text
        val selectedText = extractSelectedText(source)
        if (selectedText.isBlank()) {
            source.recycle()
            return
        }

        // Debounce repeated selections
        val currentTime = System.currentTimeMillis()
        if (selectedText == lastSelectedText && (currentTime - lastSelectionTime) < debounceThreshold) {
            source.recycle()
            return
        }

        lastSelectedText = selectedText
        lastSelectionTime = currentTime

        // Broadcast the selected text to OverlayService
        broadcastTextSelection(selectedText)
        source.recycle()
    }

    private fun extractSelectedText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        // Check if this is a password field
        if (node.isPassword) {
            return ""
        }

        // Try to get selected text from the node
        val text = node.text?.toString() ?: ""

        // Try to get text from parent nodes if current node is empty
        if (text.isBlank() && node.parent != null) {
            return extractSelectedText(node.parent)
        }

        return text
    }

    private fun broadcastTextSelection(text: String) {
        val intent = Intent(ACTION_TEXT_SELECTED).apply {
            putExtra(EXTRA_SELECTED_TEXT, text)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_TEXT_SELECTED = "com.screensage.TEXT_SELECTED"
        const val EXTRA_SELECTED_TEXT = "selected_text"
    }
}
