package com.example.screensage

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.screensage.preferences.PreferencesManager
import com.example.screensage.ui.theme.ScreenSageTheme
import com.example.screensage.utils.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)
        enableEdgeToEdge()
        setContent {
            val coroutineScope = rememberCoroutineScope()
            var themePreference by remember { mutableStateOf("system") }
            
            LaunchedEffect(Unit) {
                coroutineScope.launch {
                    themePreference = preferencesManager.getTheme()
                }
            }
            
            val darkTheme = when (themePreference) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            
            ScreenSageTheme(darkTheme = darkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(
                        preferencesManager = preferencesManager,
                        onStartService = { startOverlayService() },
                        onStopService = { stopOverlayService() },
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        onThemeChanged = { newTheme ->
                            themePreference = newTheme
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun startOverlayService() {
        // Check if overlay permission is granted
        if (!PermissionHelper.hasOverlayPermission(this)) {
            PermissionHelper.requestOverlayPermission(this)
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForegroundService(Intent(this, com.example.screensage.service.OverlayService::class.java))
        } else {
            startService(Intent(this, com.example.screensage.service.OverlayService::class.java))
        }
    }

    private fun stopOverlayService() {
        stopService(Intent(this, com.example.screensage.service.OverlayService::class.java))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("system") }
    var model by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("gemini") }
    var overlayColor by remember { mutableStateOf("") }
    var systemPromptPreset by remember { mutableStateOf("concise") }
    var customSystemPrompt by remember { mutableStateOf("") }
    var isServiceRunning by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            apiKey = preferencesManager.getApiKey() ?: ""
            theme = preferencesManager.getTheme()
            model = preferencesManager.getModel() ?: ""
            provider = preferencesManager.getProvider()
            overlayColor = preferencesManager.getOverlayColor()
            systemPromptPreset = preferencesManager.getSystemPromptPreset()
            customSystemPrompt = preferencesManager.getCustomSystemPrompt()
        }
    }

    if (showSettings) {
        SettingsPanel(
            apiKey = apiKey,
            onApiKeyChange = { apiKey = it },
            theme = theme,
            onThemeChange = { theme = it },
            model = model,
            onModelChange = { model = it },
            provider = provider,
            onProviderChange = { provider = it },
            overlayColor = overlayColor,
            onOverlayColorChange = { overlayColor = it },
            systemPromptPreset = systemPromptPreset,
            onSystemPromptPresetChange = { systemPromptPreset = it },
            customSystemPrompt = customSystemPrompt,
            onCustomSystemPromptChange = { customSystemPrompt = it },
            onSave = {
                coroutineScope.launch {
                    preferencesManager.setApiKey(apiKey)
                    preferencesManager.setTheme(theme)
                    if (model.isNotEmpty()) {
                        preferencesManager.setModel(model)
                    }
                    preferencesManager.setProvider(provider)
                    preferencesManager.setOverlayColor(overlayColor)
                    preferencesManager.setSystemPromptPreset(systemPromptPreset)
                    preferencesManager.setCustomSystemPrompt(customSystemPrompt)
                    onThemeChanged(theme)
                }
            },
            onStartService = onStartService,
            onStopService = onStopService,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onClose = { showSettings = false },
            modifier = modifier
        )
    } else {
        var showPermissionDialog by remember { mutableStateOf(false) }
        
        HomeScreen(
            onSettingsClick = { showSettings = true },
            onToggleService = {
                if (isServiceRunning) {
                    onStopService()
                    isServiceRunning = false
                } else {
                    // Check accessibility permission before starting
                    if (PermissionHelper.isAccessibilityServiceEnabled(context)) {
                        onStartService()
                        isServiceRunning = true
                    } else {
                        // Show permission dialog
                        showPermissionDialog = true
                    }
                }
            },
            isServiceRunning = isServiceRunning,
            modifier = modifier
        )
        
        if (showPermissionDialog) {
            PermissionDialog(
                onDismiss = { showPermissionDialog = false },
                onGrantPermission = {
                    showPermissionDialog = false
                    onOpenAccessibilitySettings()
                }
            )
        }
    }
}

@Composable
fun PermissionDialog(
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accessibility Permission Required") },
        text = {
            Column {
                Text("This service requires accessibility permission.\n")
                Text("Why?", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text("• To create an overlay on screen")
                Text("• To detect selected text beyond this app\n")
                Text("Note: All data is stored locally and never shared outside the system.", 
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onGrantPermission) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onToggleService: () -> Unit,
    isServiceRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val chatHistoryManager = remember { com.example.screensage.storage.ChatHistoryManager(context) }
    var chatSessions by remember { mutableStateOf<List<com.example.screensage.models.ChatSession>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            chatSessions = chatHistoryManager.getAllSessions()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Screen Sage",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Start/Stop button
            Button(
                onClick = onToggleService,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isServiceRunning) "Stop Overlay" else "Start Overlay")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Chat history section
            if (chatSessions.isNotEmpty()) {
                Text(
                    text = "Recent Chats",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(chatSessions.size) { index ->
                        val session = chatSessions[index]
                        ChatHistoryItem(
                            session = session,
                            onDelete = {
                                coroutineScope.launch {
                                    chatHistoryManager.deleteSession(session.id)
                                    chatSessions = chatHistoryManager.getAllSessions()
                                }
                            },
                            onClick = {
                                // Restore the chat session in the overlay
                                val intent = android.content.Intent(com.example.screensage.service.OverlayService.ACTION_RESTORE_SESSION)
                                intent.putExtra(com.example.screensage.service.OverlayService.EXTRA_SESSION_ID, session.id)
                                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No chat history yet",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start a conversation to see it here",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatHistoryItem(
    session: com.example.screensage.models.ChatSession,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${session.messages.size} messages • ${formatTimestamp(session.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete \"${session.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> "${diff / 604800000}w ago"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    theme: String,
    onThemeChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    provider: String,
    onProviderChange: (String) -> Unit,
    overlayColor: String,
    onOverlayColorChange: (String) -> Unit,
    systemPromptPreset: String,
    onSystemPromptPresetChange: (String) -> Unit,
    customSystemPrompt: String,
    onCustomSystemPromptChange: (String) -> Unit,
    onSave: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("General", "Appearance & Behavior")
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with title and back button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall
                            ) 
                        }
                    )
                }
            }
            
            // Content based on selected tab
            when (selectedTab) {
                0 -> GeneralSettingsContent(
                    apiKey = apiKey,
                    onApiKeyChange = onApiKeyChange,
                    provider = provider,
                    onProviderChange = onProviderChange,
                    model = model,
                    onModelChange = onModelChange,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onSave = onSave,
                    modifier = Modifier.weight(1f)
                )
                1 -> AppearanceSettingsContent(
                    theme = theme,
                    onThemeChange = onThemeChange,
                    overlayColor = overlayColor,
                    onOverlayColorChange = onOverlayColorChange,
                    systemPromptPreset = systemPromptPreset,
                    onSystemPromptPresetChange = onSystemPromptPresetChange,
                    customSystemPrompt = customSystemPrompt,
                    onCustomSystemPromptChange = onCustomSystemPromptChange,
                    onSave = onSave,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun GeneralSettingsContent(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    provider: String,
    onProviderChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // AI Configuration Section
        SettingsSectionHeader(title = "AI Configuration")
        Spacer(modifier = Modifier.height(12.dp))

        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // API Key field - only show for non-local providers
                if (provider.lowercase() != "local") {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { Text("Enter your AI provider API key", style = MaterialTheme.typography.bodySmall) }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Provider dropdown
                var providerExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = provider.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("AI Provider") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { providerExpanded = !providerExpanded }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Provider dropdown"
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gemini") },
                            onClick = {
                                onProviderChange("gemini")
                                onModelChange("")
                                providerExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("ChatGPT") },
                            onClick = {
                                onProviderChange("chatgpt")
                                onModelChange("")
                                providerExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Claude") },
                            onClick = {
                                onProviderChange("claude")
                                onModelChange("")
                                providerExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Local (On-Device)") },
                            onClick = {
                                onProviderChange("local")
                                onModelChange("")
                                providerExpanded = false
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Model dropdown
                var modelExpanded by remember { mutableStateOf(false) }
                val availableModels = when (provider.lowercase()) {
                    "gemini" -> listOf(
                        "gemini-3-flash-preview",
                        "gemini-1.5-flash",
                        "gemini-1.5-pro",
                        "gemini-1.0-pro"
                    )
                    "chatgpt" -> listOf(
                        "gpt-4o",
                        "gpt-4o-mini",
                        "gpt-4-turbo",
                        "gpt-4",
                        "gpt-3.5-turbo"
                    )
                    "claude" -> listOf(
                        "claude-3-5-sonnet-20241022",
                        "claude-3-5-haiku-20241022",
                        "claude-3-opus-20240229",
                        "claude-3-sonnet-20240229",
                        "claude-3-haiku-20240307"
                    )
                    "local" -> listOf("Gemma 2B (On-Device)")
                    else -> listOf()
                }
                
                // Show model dropdown only for non-local providers
                if (provider.lowercase() != "local") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = model.ifEmpty { availableModels.firstOrNull() ?: "" },
                            onValueChange = {},
                            label = { Text("Model") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { modelExpanded = !modelExpanded }) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Model dropdown"
                                    )
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            availableModels.forEach { modelName ->
                                DropdownMenuItem(
                                    text = { Text(modelName) },
                                    onClick = {
                                        onModelChange(modelName)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Show local model info
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val localModelManager = remember { com.example.screensage.ai.LocalModelManager(context) }
                        var isModelDownloaded by remember { mutableStateOf(localModelManager.isModelDownloaded()) }
                        var isDownloading by remember { mutableStateOf(false) }
                        var downloadProgress by remember { mutableStateOf(0f) }
                        var downloadedMB by remember { mutableStateOf(0L) }
                        var totalMB by remember { mutableStateOf(676L) }
                        var downloadError by remember { mutableStateOf<String?>(null) }
                        
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📱 Local Model: Gemma3-1B",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "System Requirements:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Storage: 676MB free available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "• RAM: 4GB minimum (6GB+ recommended)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "• Speed: 1-3 seconds per response",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "✓ No API key needed\n✓ Works offline\n✓ 100% private",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Download button or status
                            when {
                                isModelDownloaded -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "✓ Model Downloaded",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(onClick = {
                                            localModelManager.deleteModel()
                                            isModelDownloaded = false
                                        }) {
                                            Text("Delete")
                                        }
                                    }
                                }
                                isDownloading -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Downloading...",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "${downloadedMB}MB / ${totalMB}MB",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = downloadProgress,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = {
                                            isDownloading = true
                                            downloadError = null
                                            coroutineScope.launch {
                                                val result = localModelManager.downloadModel { progress, downloaded, total ->
                                                    downloadProgress = progress
                                                    downloadedMB = downloaded / (1024 * 1024)
                                                    totalMB = total / (1024 * 1024)
                                                }
                                                isDownloading = false
                                                result.fold(
                                                    onSuccess = {
                                                        isModelDownloaded = true
                                                    },
                                                    onFailure = { error ->
                                                        downloadError = error.message
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Download Model (676MB)")
                                    }
                                }
                            }
                            
                            // Show error if any
                            downloadError?.let { error ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Error: $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Alternative: Download manually",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "1. Visit: ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android\n2. Download gemma-2b-it-gpu-int4.bin\n3. Place in: ${localModelManager.getModelPath()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Permissions Section
        SettingsSectionHeader(title = "Permissions")
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Accessibility Service",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Required to detect text selections across all apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Accessibility Service")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Save button
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Save Settings")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AppearanceSettingsContent(
    theme: String,
    onThemeChange: (String) -> Unit,
    overlayColor: String,
    onOverlayColorChange: (String) -> Unit,
    systemPromptPreset: String,
    onSystemPromptPresetChange: (String) -> Unit,
    customSystemPrompt: String,
    onCustomSystemPromptChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Theme Section
        SettingsSectionHeader(title = "Theme")
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var themeExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = theme.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("App Theme") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { themeExpanded = !themeExpanded }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Theme dropdown"
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("System") },
                            onClick = {
                                onThemeChange("system")
                                themeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dark") },
                            onClick = {
                                onThemeChange("dark")
                                themeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Light") },
                            onClick = {
                                onThemeChange("light")
                                themeExpanded = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Overlay Appearance Section
        SettingsSectionHeader(title = "Overlay Appearance")
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var colorExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = overlayColor.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("Overlay Color") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        supportingText = { Text("Color theme for the chat overlay", style = MaterialTheme.typography.bodySmall) },
                        trailingIcon = {
                            IconButton(onClick = { colorExpanded = !colorExpanded }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Color dropdown"
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = colorExpanded,
                        onDismissRequest = { colorExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pink") },
                            onClick = {
                                onOverlayColorChange("pink")
                                colorExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Blue") },
                            onClick = {
                                onOverlayColorChange("blue")
                                colorExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Purple") },
                            onClick = {
                                onOverlayColorChange("purple")
                                colorExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Green") },
                            onClick = {
                                onOverlayColorChange("green")
                                colorExpanded = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // AI Behavior Section
        SettingsSectionHeader(title = "AI Behavior")
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Response Style",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Customize how the AI responds to your queries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                var presetExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = systemPromptPreset.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("Response Preset") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { presetExpanded = !presetExpanded }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Preset dropdown"
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Concise")
                                    Text(
                                        "Brief, to-the-point explanations",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                onSystemPromptPresetChange("concise")
                                presetExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Detailed")
                                    Text(
                                        "Comprehensive, thorough explanations",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                onSystemPromptPresetChange("detailed")
                                presetExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Simple")
                                    Text(
                                        "Easy-to-understand, beginner-friendly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                onSystemPromptPresetChange("simple")
                                presetExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Technical")
                                    Text(
                                        "Advanced, technical terminology",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                onSystemPromptPresetChange("technical")
                                presetExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Creative")
                                    Text(
                                        "Engaging, conversational tone",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                onSystemPromptPresetChange("creative")
                                presetExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Custom")
                                    Text(
                                        "Define your own prompt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            onClick = {
                                onSystemPromptPresetChange("custom")
                                presetExpanded = false
                            }
                        )
                    }
                }
                
                // Show custom prompt input when "custom" is selected
                if (systemPromptPreset.lowercase() == "custom") {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customSystemPrompt,
                        onValueChange = onCustomSystemPromptChange,
                        label = { Text("Custom System Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        supportingText = { 
                            Text(
                                "Define how the AI should respond to your queries",
                                style = MaterialTheme.typography.bodySmall
                            ) 
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Save button
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Save Settings")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}
