package com.example.screensage.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.screensage.R
import com.example.screensage.ai.AiRepository
import com.example.screensage.models.ChatMessage
import com.example.screensage.models.ChatSession
import com.example.screensage.models.OverlayState
import com.example.screensage.preferences.PreferencesManager
import com.example.screensage.storage.ChatHistoryManager
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

class OverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val serviceScope: CoroutineScope,
    private val preferencesManager: PreferencesManager,
    private val onStateChanged: (OverlayState) -> Unit
) {
    private var overlayView: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentState = OverlayState.COLLAPSED
    private var aiRepository: AiRepository? = null
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var isOnRightSide = true
    private var loadingMessageView: TextView? = null
    private var hasInitializedAi = false
    private var lastQuery: String? = null
    private var currentMessagesContainer: LinearLayout? = null
    
    // Icon position memory
    private var lastIconX = -1
    private var lastIconY = -1
    
    // Velocity tracking for physics-based throwing
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastMoveTime = 0L
    
    // Chat context memory
    private var currentSession: ChatSession? = null
    private val conversationHistory = mutableListOf<ChatMessage>()
    private val chatHistoryManager = ChatHistoryManager(context)
    
    // Markwon instance for markdown rendering
    private val markwon: Markwon by lazy {
        Markwon.create(context)
    }

    companion object {
        private const val TAG = "OverlayManager"
    }

    private fun getOverlayBackgroundColor(): Int {
        // Translucent glassy background for chat box
        return when (preferencesManager.getOverlayColorSync()) {
            PreferencesManager.COLOR_PINK -> 0xF0FFE4F1.toInt()  // Light pink with high transparency
            PreferencesManager.COLOR_BLUE -> 0xF0E3F2FD.toInt()  // Light blue with high transparency
            PreferencesManager.COLOR_PURPLE -> 0xF0F3E5F5.toInt() // Light purple with high transparency
            PreferencesManager.COLOR_GREEN -> 0xF0E8F5E9.toInt()  // Light green with high transparency
            else -> 0xF0FFE4F1.toInt()
        }
    }

    private fun getWidgetBackgroundColor(): Int {
        // Solid color for widget and user bubbles
        return when (preferencesManager.getOverlayColorSync()) {
            PreferencesManager.COLOR_PINK -> 0xFFFF69B4.toInt()
            PreferencesManager.COLOR_BLUE -> 0xFF1E90FF.toInt()
            PreferencesManager.COLOR_PURPLE -> 0xFFDA70D6.toInt()
            PreferencesManager.COLOR_GREEN -> 0xFF00FA9A.toInt()
            else -> 0xFFFF69B4.toInt()
        }
    }
    
    private fun getSendButtonColor(): Int {
        // Solid color for send button
        return when (preferencesManager.getOverlayColorSync()) {
            PreferencesManager.COLOR_PINK -> 0xFFFF1493.toInt()  // Deep pink
            PreferencesManager.COLOR_BLUE -> 0xFF2196F3.toInt()  // Material blue
            PreferencesManager.COLOR_PURPLE -> 0xFF9C27B0.toInt() // Material purple
            PreferencesManager.COLOR_GREEN -> 0xFF4CAF50.toInt()  // Material green
            else -> 0xFFFF1493.toInt()
        }
    }
    
    private fun getUserBubbleColor(): Int {
        // Solid color for user chat bubbles
        return getWidgetBackgroundColor()
    }

    private fun createRoundedDrawable(color: Int, cornerRadius: Float = 24f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            setCornerRadius(cornerRadius)
        }
    }

    private fun createChatBubbleDrawable(color: Int, isRightSide: Boolean = true): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            // Set individual corner radii based on which side
            cornerRadii = if (isRightSide) {
                floatArrayOf(
                    24f, 24f,  // top-left
                    24f, 24f,  // top-right
                    0f, 0f,    // bottom-right (pointy)
                    24f, 24f   // bottom-left
                )
            } else {
                floatArrayOf(
                    24f, 24f,  // top-left
                    24f, 24f,  // top-right
                    24f, 24f,  // bottom-right
                    0f, 0f     // bottom-left (pointy)
                )
            }
        }
    }

    fun createOverlay() {
        if (overlayView != null) return

        overlayView = FrameLayout(context)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        overlayParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = 100
            height = 100
            gravity = Gravity.TOP or Gravity.START
            // Use last position if available, otherwise start at top-right
            x = if (lastIconX >= 0) lastIconX else screenWidth - 120
            y = if (lastIconY >= 0) lastIconY else 100  // Changed from bottom to top
        }

        windowManager.addView(overlayView, overlayParams)
        updateCollapsedState()
    }

    fun setAiRepository(repository: AiRepository) {
        this.aiRepository = repository
    }

    private fun updateCollapsedState() {
        overlayView?.removeAllViews()
        overlayView?.clipChildren = false
        overlayView?.clipToPadding = false

        val widget = FrameLayout(context).apply {
            setBackground(createChatBubbleDrawable(getWidgetBackgroundColor(), isOnRightSide))
            layoutParams = FrameLayout.LayoutParams(100, 100)
            clipChildren = false
            clipToPadding = false
        }

        val iconImage = ImageView(context).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(0, 0, 0, 0)
            setBackground(createChatBubbleDrawable(0x00000000, isOnRightSide))
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        widget.addView(iconImage)

        setupWidgetTouchListener(widget)
        overlayView?.addView(widget)
        updateState(OverlayState.COLLAPSED)
    }

    private fun setupWidgetTouchListener(widget: FrameLayout) {
        widget.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - lastX
                    val deltaY = event.rawY - lastY

                    if (!isDragging && (Math.abs(event.rawX - dragStartX) > 10 || Math.abs(event.rawY - dragStartY) > 10)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        overlayParams?.apply {
                            x = (x + deltaX.toInt()).coerceIn(0, context.resources.displayMetrics.widthPixels - 100)
                            y = (y + deltaY.toInt()).coerceIn(0, context.resources.displayMetrics.heightPixels - 100)
                        }
                        windowManager.updateViewLayout(overlayView, overlayParams)
                    }

                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        snapToEdge()
                    } else {
                        expandOverlay()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        overlayParams?.let { params ->
            val screenWidth = context.resources.displayMetrics.widthPixels
            val centerX = params.x + 50
            val edgePadding = 16

            val targetX = if (centerX < screenWidth / 2) {
                isOnRightSide = false
                edgePadding
            } else {
                isOnRightSide = true
                screenWidth - 100 - edgePadding
            }

            val animator = ValueAnimator.ofInt(params.x, targetX)
            animator.duration = 200
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                windowManager.updateViewLayout(overlayView, params)
            }
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    updateCollapsedState()
                }
            })
            animator.start()
        }
    }
    
    private fun setupHeaderDragListener(header: FrameLayout) {
        var headerDragStartX = 0f
        var headerDragStartY = 0f
        var headerLastX = 0f
        var headerLastY = 0f
        var isHeaderDragging = false
        var dragStartTime = 0L
        val dragDelayMs = 150L  // Delay before drag starts
        
        header.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    headerDragStartX = event.rawX
                    headerDragStartY = event.rawY
                    headerLastX = event.rawX
                    headerLastY = event.rawY
                    isHeaderDragging = false
                    dragStartTime = System.currentTimeMillis()
                    lastMoveTime = System.currentTimeMillis()
                    velocityX = 0f
                    velocityY = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - headerLastX
                    val deltaY = event.rawY - headerLastY
                    val timeSinceStart = System.currentTimeMillis() - dragStartTime
                    val timeDelta = System.currentTimeMillis() - lastMoveTime

                    // Only start dragging after delay and sufficient movement
                    if (!isHeaderDragging && timeSinceStart > dragDelayMs && 
                        (Math.abs(event.rawX - headerDragStartX) > 20 || Math.abs(event.rawY - headerDragStartY) > 20)) {
                        isHeaderDragging = true
                    }

                    if (isHeaderDragging) {
                        // Calculate velocity for physics-based throwing
                        if (timeDelta > 0) {
                            velocityX = deltaX / timeDelta * 1000  // pixels per second
                            velocityY = deltaY / timeDelta * 1000
                        }
                        
                        overlayParams?.apply {
                            // Allow dragging outside screen bounds
                            x = (x + deltaX.toInt())
                            y = (y + deltaY.toInt())
                        }
                        windowManager.updateViewLayout(overlayView, overlayParams)
                    }

                    headerLastX = event.rawX
                    headerLastY = event.rawY
                    lastMoveTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isHeaderDragging) {
                        checkIfOffScreenWithPhysics()
                    }
                    isHeaderDragging = false
                    true
                }
                else -> false
            }
        }
    }
    
    private fun checkIfOffScreenWithPhysics() {
        overlayParams?.let { params ->
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels
            
            // Get the panel dimensions
            val panelWidth = params.width
            val panelHeight = params.height
            
            // Calculate how much of the panel is off-screen
            val leftEdge = params.x
            val rightEdge = params.x + panelWidth
            val topEdge = params.y
            val bottomEdge = params.y + panelHeight
            
            // Check if thrown with significant velocity
            val throwVelocityThreshold = 500f  // pixels per second
            val isThrowing = Math.abs(velocityX) > throwVelocityThreshold || Math.abs(velocityY) > throwVelocityThreshold
            
            // If more than 40% is off-screen OR thrown with velocity, collapse
            val offScreenThreshold = if (isThrowing) 0.3f else 0.4f
            
            val offScreenLeft = if (leftEdge < 0) Math.abs(leftEdge).toFloat() / panelWidth else 0f
            val offScreenRight = if (rightEdge > screenWidth) (rightEdge - screenWidth).toFloat() / panelWidth else 0f
            val offScreenTop = if (topEdge < 0) Math.abs(topEdge).toFloat() / panelHeight else 0f
            val offScreenBottom = if (bottomEdge > screenHeight) (bottomEdge - screenHeight).toFloat() / panelHeight else 0f
            
            if (offScreenLeft > offScreenThreshold || offScreenRight > offScreenThreshold || 
                offScreenTop > offScreenThreshold || offScreenBottom > offScreenThreshold || isThrowing) {
                // Simple fade and collapse
                animateSimpleCollapse()
            }
        }
    }
    
    private fun animateSimpleCollapse() {
        // Simple fade out animation
        val fadeAnimator = ValueAnimator.ofFloat(1f, 0f)
        fadeAnimator.duration = 200
        fadeAnimator.addUpdateListener { animation ->
            overlayView?.alpha = animation.animatedValue as Float
        }
        fadeAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                overlayView?.alpha = 1f
                collapseOverlay()
            }
        })
        fadeAnimator.start()
    }

    private fun expandOverlay() {
        if (currentState != OverlayState.COLLAPSED) return
        
        // Only start a new session if we don't have one (not restoring)
        if (currentSession == null) {
            startNewSession()
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val panelWidth = (screenWidth * 0.9).toInt()
        val panelHeight = (screenHeight * 0.3).toInt().coerceAtLeast(250)  // Reduced from 0.4 to 0.3 (25% reduction)

        overlayView?.removeAllViews()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackground(createRoundedDrawable(getOverlayBackgroundColor(), 24f))
            layoutParams = FrameLayout.LayoutParams(panelWidth, panelHeight)
            setPadding(0, 0, 0, 0)
            clipChildren = false
            clipToPadding = false
        }

        // Header
        val header = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(24, 20, 24, 16)
        }
        
        // Add drag functionality to header
        setupHeaderDragListener(header)

        val titleText = TextView(context).apply {
            text = currentSession?.title ?: "Screen Sage"
            textSize = 20f
            setTextColor(0xFF000000.toInt())
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            includeFontPadding = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 70
                gravity = Gravity.CENTER_VERTICAL
                topMargin = 4
                bottomMargin = 4
            }
            
            // Make title clickable to edit, but only on tap (not drag)
            var titleClickStartX = 0f
            var titleClickStartY = 0f
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        titleClickStartX = event.rawX
                        titleClickStartY = event.rawY
                        false  // Don't consume, let header drag work
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = Math.abs(event.rawX - titleClickStartX)
                        val deltaY = Math.abs(event.rawY - titleClickStartY)
                        // Only trigger edit if it was a tap (not a drag)
                        if (deltaX < 20 && deltaY < 20) {
                            showTitleEditDialog()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }
        header.addView(titleText)

        val closeButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            layoutParams = FrameLayout.LayoutParams(56, 56, Gravity.END or Gravity.CENTER_VERTICAL)
            setBackground(createRoundedDrawable(0x30000000.toInt(), 28f))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(12, 12, 12, 12)
            setColorFilter(0xFF000000.toInt())
            elevation = 4f
            setOnClickListener { collapseOverlay() }
        }
        header.addView(closeButton)
        container.addView(header)

        // Messages area
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(16, 8, 16, 8)
            clipToPadding = false
        }

        val messagesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 8)
        }
        
        // Store reference to messages container
        currentMessagesContainer = messagesContainer
        
        // Restore messages if this is an existing session
        if (conversationHistory.isNotEmpty()) {
            conversationHistory.forEach { msg ->
                when (msg.role) {
                    "user" -> {
                        val messageView = TextView(context).apply {
                            text = msg.content
                            textSize = 14f
                            setTextColor(0xFFFFFFFF.toInt())
                            setPadding(16, 12, 16, 12)
                            setBackground(createRoundedDrawable(getUserBubbleColor(), 16f))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(64, 8, 0, 8)
                                gravity = Gravity.END
                            }
                        }
                        messagesContainer.addView(messageView)
                    }
                    "assistant" -> {
                        val messageView = TextView(context).apply {
                            markwon.setMarkdown(this, msg.content)
                            textSize = 14f
                            setTextColor(0xFF000000.toInt())
                            setPadding(16, 12, 16, 12)
                            setBackground(createRoundedDrawable(0xFFF0F0F0.toInt(), 16f))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 8, 64, 8)
                            }
                            setTextIsSelectable(true)
                        }
                        messagesContainer.addView(messageView)
                    }
                }
            }
        }

        scrollView.addView(messagesContainer)
        container.addView(scrollView)

        // Input area
        val inputContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 8, 16, 20)
            gravity = Gravity.CENTER_VERTICAL
        }

        val inputField = EditText(context).apply {
            hint = "Ask me anything..."
            textSize = 15f
            setTextColor(0xFF000000.toInt())
            setHintTextColor(0x80000000.toInt())
            minHeight = 56
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(24, 16, 24, 16)
            setBackground(createRoundedDrawable(0xFFF5F5F5.toInt(), 28f))
            setSingleLine(true)
            maxLines = 1
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            
            // Explicitly set IME options to prevent keyboard action submissions
            imeOptions = EditorInfo.IME_ACTION_NONE
            inputType = InputType.TYPE_CLASS_TEXT
            
            // Prevent Enter key from triggering any action - only send button should work
            setOnEditorActionListener { _, actionId, event ->
                Log.d(TAG, "Editor action received: $actionId")
                // Consume all editor actions to prevent automatic submission
                true
            }
            
            // Add text watcher for debugging only (no submission logic)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    Log.d(TAG, "Text changed: ${s?.length ?: 0} characters")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        inputContainer.addView(inputField)

        val sendButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_send)
            layoutParams = LinearLayout.LayoutParams(72, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(12, 0, 0, 0)
            }
            setBackground(createRoundedDrawable(getSendButtonColor(), 28f))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(18, 18, 18, 18)
            setColorFilter(0xFFFFFFFF.toInt())
            elevation = 2f
            setOnClickListener {
                val query = inputField.text.toString()
                Log.d(TAG, "Send button clicked with query: '$query' (${query.length} chars)")
                if (query.isNotEmpty()) {
                    addUserMessage(messagesContainer, query)
                    aiRepository?.let { submitQuery(query, it, serviceScope, messagesContainer) }
                    inputField.text.clear()
                    scrollView.post {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                } else {
                    Log.d(TAG, "Send button clicked but query is empty, ignoring")
                }
            }
        }
        inputContainer.addView(sendButton)
        container.addView(inputContainer)

        // Set up outside touch listener to collapse overlay
        container.setOnTouchListener { _, event ->
            // Don't consume the event - let it pass through
            false
        }
        
        overlayView?.addView(container)
        
        // Set up overlay view to detect outside touches
        overlayView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                collapseOverlay()
                true
            } else {
                false
            }
        }

        // Position at top of screen
        overlayParams?.apply {
            width = panelWidth
            height = panelHeight
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 50  // Small margin from top
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        windowManager.updateViewLayout(overlayView, overlayParams)

        updateState(OverlayState.EXPANDED_IDLE)
        
        // Show a welcome message for new sessions (like ChatGPT/Claude)
        if (conversationHistory.isEmpty()) {
            val welcomeView = TextView(context).apply {
                text = "👋 Hi! I'm Screen Sage. How can I help you today?"
                textSize = 14f
                setTextColor(0xFF666666.toInt())
                setPadding(16, 12, 16, 12)
                setBackground(createRoundedDrawable(0xFFF0F0F0.toInt(), 16f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 64, 8)
                }
                gravity = Gravity.CENTER
            }
            messagesContainer.addView(welcomeView)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun addUserMessage(container: LinearLayout, message: String) {
        // Add to conversation history
        addMessageToHistory("user", message)
        
        val messageView = TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(16, 12, 16, 12)
            setBackground(createRoundedDrawable(getUserBubbleColor(), 16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(64, 8, 0, 8)
                gravity = Gravity.END
            }
        }
        container.addView(messageView)
    }

    private fun addAiMessage(container: LinearLayout, message: String) {
        // Add to conversation history
        addMessageToHistory("assistant", message)
        
        // Generate title after first exchange completes (user message + AI response)
        if (conversationHistory.size == 2) {
            val firstUserMessage = conversationHistory.firstOrNull { it.role == "user" }?.content
            if (firstUserMessage != null) {
                generateChatTitle(firstUserMessage)
            }
        }
        
        val messageView = TextView(context).apply {
            // Use Markwon to set markdown text
            markwon.setMarkdown(this, message)
            textSize = 14f
            setTextColor(0xFF000000.toInt())
            setPadding(16, 12, 16, 12)
            setBackground(createRoundedDrawable(0xFFF0F0F0.toInt(), 16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 64, 8)
            }
            // Allow text selection for copying
            setTextIsSelectable(true)
        }
        container.addView(messageView)
    }

    private fun addThinkingMessage(container: LinearLayout) {
        val thinkingView = TextView(context).apply {
            text = "💭 Thinking"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setPadding(16, 12, 16, 12)
            setBackground(createRoundedDrawable(0xFFF0F0F0.toInt(), 16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 64, 8)
            }
            maxLines = 10
            isVerticalScrollBarEnabled = false
            tag = "thinking_message"
        }
        container.addView(thinkingView)
        
        // Add typing animation with dots
        val animator = android.animation.ValueAnimator.ofInt(0, 3)
        animator.duration = 1200
        animator.repeatCount = android.animation.ValueAnimator.INFINITE
        animator.addUpdateListener { animation ->
            val dots = ".".repeat((animation.animatedValue as Int))
            thinkingView.text = "💭 Thinking$dots"
        }
        animator.start()
    }

    private fun addErrorMessage(container: LinearLayout, errorTitle: String, errorMessage: String, isRetryable: Boolean = true) {
        // Create error message container
        val errorContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackground(createRoundedDrawable(0xFFFFEBEE.toInt(), 16f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 64, 8)
            }
        }

        // Error text in a scrollable view
        val errorScrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                300 // Max height for error message
            )
        }
        
        val errorView = TextView(context).apply {
            text = "⚠️ $errorTitle\n\n$errorMessage"
            textSize = 11f
            setTextColor(0xFFD32F2F.toInt())
            setPadding(0, 0, 0, 8)
        }
        errorScrollView.addView(errorView)
        errorContainer.addView(errorScrollView)

        // Button container
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 0)
            }
        }

        // Retry button (if retryable)
        if (isRetryable) {
            val retryButton = android.widget.Button(context).apply {
                text = "Retry"
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF2196F3.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
                setOnClickListener {
                    // Remove error message
                    container.removeView(errorContainer)
                    
                    // Retry the last query
                    val query = lastQuery
                    val repo = aiRepository
                    val msgContainer = currentMessagesContainer
                    
                    if (query != null && repo != null && msgContainer != null) {
                        submitQuery(query, repo, serviceScope, msgContainer)
                    }
                }
            }
            buttonContainer.addView(retryButton)
        }

        // Settings button (if not retryable - configuration error)
        if (!isRetryable) {
            val settingsButton = android.widget.Button(context).apply {
                text = "Open Settings"
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF4CAF50.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
                setOnClickListener {
                    // Open MainActivity settings
                    val intent = android.content.Intent(context, com.example.screensage.MainActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            }
            buttonContainer.addView(settingsButton)
        }

        errorContainer.addView(buttonContainer)
        container.addView(errorContainer)
    }

    private fun collapseOverlay() {
        if (currentState == OverlayState.COLLAPSED) return
        
        // Save session to history (but DON'T clear it - keep it for reopening)
        currentSession?.let { session ->
            if (session.messages.isNotEmpty()) {
                serviceScope.launch {
                    chatHistoryManager.saveSession(session)
                }
            }
        }
        
        // DON'T clear conversation history - keep it so we can restore when reopening
        // clearConversationHistory()

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        overlayParams?.apply {
            // Remember the current position before collapsing
            lastIconX = x
            lastIconY = y
            
            width = 100
            height = 100
            gravity = Gravity.TOP or Gravity.START
            // Keep the same position where it was
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(overlayView, overlayParams)

        updateCollapsedState()
    }

    private fun submitQuery(query: String, aiRepository: AiRepository, scope: CoroutineScope, messagesContainer: LinearLayout) {
        if (query.isBlank()) {
            Log.d(TAG, "submitQuery called with blank query, ignoring")
            return
        }
        
        Log.d(TAG, "submitQuery: Starting query submission - '$query'")
        
        // Store the last query for retry
        lastQuery = query
        
        // Add thinking message
        addThinkingMessage(messagesContainer)
        val scrollView = messagesContainer.parent as? ScrollView
        scrollView?.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
        
        updateState(OverlayState.EXPANDED_LOADING)
        scope.launch {
            try {
                Log.d(TAG, "Submitting query: $query")
                val result = aiRepository.submitQuery(query, conversationHistory)
                Log.d(TAG, "Query result: $result")
                
                // Remove thinking message
                val thinkingMsg = messagesContainer.findViewWithTag<TextView>("thinking_message")
                if (thinkingMsg != null) {
                    messagesContainer.removeView(thinkingMsg)
                }
                
                result.onSuccess { response ->
                    Log.d(TAG, "Query successful: ${response.explanation}")
                    addAiMessage(messagesContainer, response.explanation)
                    updateState(OverlayState.EXPANDED_RESPONSE)
                    scrollView?.post {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }.onFailure { exception ->
                    Log.e(TAG, "Query failed", exception)
                    val errorMsg = aiRepository.handleError(exception as Exception)
                    addErrorMessage(messagesContainer, errorMsg.title, errorMsg.message, errorMsg.isRetryable)
                    updateState(OverlayState.EXPANDED_ERROR)
                    scrollView?.post {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in submitQuery", e)
                val thinkingMsg = messagesContainer.findViewWithTag<TextView>("thinking_message")
                if (thinkingMsg != null) {
                    messagesContainer.removeView(thinkingMsg)
                }
                addErrorMessage(messagesContainer, "Unexpected Error", "Exception: ${e.javaClass.simpleName}\nMessage: ${e.message}\nStack: ${e.stackTraceToString().take(500)}", true)
                updateState(OverlayState.EXPANDED_ERROR)
                scrollView?.post {
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }

    private fun updateState(newState: OverlayState) {
        currentState = newState
        onStateChanged(newState)
    }

    fun handleTextSelection(text: String, aiRepository: AiRepository, scope: CoroutineScope) {
        this.aiRepository = aiRepository
        if (currentState == OverlayState.COLLAPSED) {
            expandOverlay()
        }
        updateState(OverlayState.EXPANDED_LOADING)
        scope.launch {
            val result = aiRepository.explainText(text)
            result.onSuccess { response ->
                updateState(OverlayState.EXPANDED_RESPONSE)
            }.onFailure { exception ->
                updateState(OverlayState.EXPANDED_ERROR)
            }
        }
    }

    fun removeAllViews() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }
    
    // Chat context management methods
    private fun startNewSession() {
        val sessionId = UUID.randomUUID().toString()
        currentSession = ChatSession(
            id = sessionId,
            title = "New Chat",
            messages = mutableListOf(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversationHistory.clear()
    }
    
    private fun addMessageToHistory(role: String, content: String) {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        conversationHistory.add(message)
        currentSession?.messages?.add(message)
        currentSession = currentSession?.copy(updatedAt = System.currentTimeMillis())
    }
    
    fun getConversationHistory(): List<ChatMessage> {
        return conversationHistory.toList()
    }
    
    fun clearConversationHistory() {
        conversationHistory.clear()
        currentSession = null
    }
    
    fun getCurrentSession(): ChatSession? {
        return currentSession
    }
    
    fun restoreSession(session: ChatSession) {
        currentSession = session
        conversationHistory.clear()
        conversationHistory.addAll(session.messages)
        
        // If overlay is collapsed, expand it to show the restored session
        if (currentState == OverlayState.COLLAPSED) {
            expandOverlay()
        }
    }
    
    fun updateSessionTitle(newTitle: String) {
        currentSession = currentSession?.copy(title = newTitle)
        // Save the updated session
        currentSession?.let { session ->
            serviceScope.launch {
                chatHistoryManager.saveSession(session)
            }
        }
    }
    
    private fun showTitleEditDialog() {
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        
        val titleLabel = TextView(context).apply {
            text = "Edit Chat Title"
            textSize = 18f
            setTextColor(0xFF000000.toInt())
            setPadding(0, 0, 0, 16)
        }
        dialogLayout.addView(titleLabel)
        
        val titleInput = EditText(context).apply {
            setText(currentSession?.title ?: "")
            textSize = 16f
            setTextColor(0xFF000000.toInt())
            setHintTextColor(0x80000000.toInt())
            hint = "Enter chat title"
            setSingleLine(true)
            setPadding(24, 16, 24, 16)
            setBackground(createRoundedDrawable(0xFFF5F5F5.toInt(), 12f))
        }
        dialogLayout.addView(titleInput)
        
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 24, 0, 0)
            }
        }
        
        val cancelButton = android.widget.Button(context).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFFEEEEEE.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, 8, 0)
            }
        }
        
        val saveButton = android.widget.Button(context).apply {
            text = "Save"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackground(createRoundedDrawable(getSendButtonColor(), 8f))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(8, 0, 0, 0)
            }
        }
        
        buttonContainer.addView(cancelButton)
        buttonContainer.addView(saveButton)
        dialogLayout.addView(buttonContainer)
        
        // Create dialog window
        val dialogParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            width = (context.resources.displayMetrics.widthPixels * 0.8).toInt()
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.CENTER
        }
        
        windowManager.addView(dialogLayout, dialogParams)
        
        // Request focus for the input field
        titleInput.requestFocus()
        titleInput.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(titleInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        
        cancelButton.setOnClickListener {
            windowManager.removeView(dialogLayout)
        }
        
        saveButton.setOnClickListener {
            val newTitle = titleInput.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                updateSessionTitle(newTitle)
                // Refresh the overlay to show new title
                if (currentState != OverlayState.COLLAPSED) {
                    expandOverlay()
                }
            }
            windowManager.removeView(dialogLayout)
        }
    }
    
    private fun generateChatTitle(firstMessage: String) {
        serviceScope.launch {
            try {
                val titlePrompt = "Generate a concise 3-5 word title for a conversation that starts with: \"$firstMessage\". Only respond with the title, nothing else."
                val result = aiRepository?.submitQuery(titlePrompt, emptyList())
                
                result?.onSuccess { response ->
                    val title = response.explanation.trim().take(50) // Limit to 50 chars
                    currentSession = currentSession?.copy(title = title)
                }
            } catch (e: Exception) {
                // If title generation fails, keep default title
                e.printStackTrace()
            }
        }
    }
}
