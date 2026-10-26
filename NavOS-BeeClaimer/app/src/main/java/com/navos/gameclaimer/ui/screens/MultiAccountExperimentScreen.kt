package com.navos.gameclaimer.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.navos.gameclaimer.browser.SessionBackupManager
import com.navos.gameclaimer.data.adapter.SteamStoreAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 1 Multi-Account Prototype: Experiment Screen
 * 
 * DEBUG-ONLY screen for testing Steam multi-account session isolation.
 * 
 * This screen provides:
 * 1. WebView for Steam authentication
 * 2. Session backup/restore controls
 * 3. Identity verification via Steam profile page
 * 4. Detailed logging of all operations
 * 
 * TEST FLOW:
 * 1. Login with Account A → Verify identity → Backup session
 * 2. Clear session → Verify logged out
 * 3. Login with Account B → Verify identity → Backup session
 * 4. Clear session → Restore Account A → Verify Account A identity
 * 5. Clear session → Restore Account B → Verify Account B identity
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiAccountExperimentScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Session manager
    val sessionManager = remember { SessionBackupManager(context) }
    
    // State
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    var accountIdInput by remember { mutableStateOf("") }
    var logEntries by remember { mutableStateOf(listOf<LogEntry>()) }
    var backups by remember { mutableStateOf(listOf<SessionBackupManager.BackupInfo>()) }
    var verificationResult by remember { mutableStateOf<String?>(null) }
    var isOperationInProgress by remember { mutableStateOf(false) }
    
    // Tab state
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Browser", "Controls", "Logs")
    
    // Helper to add log entries
    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logEntries = logEntries + LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message
        )
    }
    
    // Load backups on start
    LaunchedEffect(Unit) {
        backups = sessionManager.listBackups()
        log("Loaded ${backups.size} existing backups")
    }
    
    // Check authentication status
    fun checkAuthStatus(): String {
        val cookies = CookieManager.getInstance().getCookie("https://store.steampowered.com")
        val hasLoginSecure = cookies?.contains("steamLoginSecure=") == true &&
                            cookies.contains("steamLoginSecure=;").not()
        return if (hasLoginSecure) "AUTHENTICATED" else "NOT_AUTHENTICATED"
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multi-Account Experiment", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Show operation in progress indicator
                    if (isOperationInProgress || sessionManager.isSwitchInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            when (selectedTab) {
                0 -> BrowserTab(
                    webView = webView,
                    onWebViewCreated = { wv -> webView = wv },
                    currentUrl = currentUrl,
                    pageTitle = pageTitle,
                    isLoading = isLoading,
                    onUrlChanged = { currentUrl = it },
                    onTitleChanged = { pageTitle = it },
                    onLoadingChanged = { isLoading = it },
                    onLog = { log(it) }
                )
                
                1 -> ControlsTab(
                    accountIdInput = accountIdInput,
                    onAccountIdChanged = { accountIdInput = it },
                    backups = backups,
                    verificationResult = verificationResult,
                    isOperationInProgress = isOperationInProgress || sessionManager.isSwitchInProgress,
                    onBackup = {
                        if (accountIdInput.isBlank()) {
                            log("Account ID required for backup", LogLevel.ERROR)
                            return@ControlsTab
                        }
                        scope.launch {
                            isOperationInProgress = true
                            log("Starting backup for: ${accountIdInput.take(8)}...")
                            
                            val result = sessionManager.backupSteamSession(accountIdInput, webView)
                            
                            when (result) {
                                is SessionBackupManager.BackupResult.Success -> {
                                    log("Backup SUCCESS: ${result.cookieCount} cookies, localStorage=${result.hasLocalStorage}", LogLevel.SUCCESS)
                                    backups = sessionManager.listBackups()
                                }
                                is SessionBackupManager.BackupResult.Failed -> {
                                    log("Backup FAILED: ${result.reason}", LogLevel.ERROR)
                                }
                            }
                            isOperationInProgress = false
                        }
                    },
                    onRestore = { accountId ->
                        scope.launch {
                            isOperationInProgress = true
                            log("Starting restore for: ${accountId.take(8)}...")
                            verificationResult = null
                            
                            val result = sessionManager.restoreSteamSession(accountId, webView)
                            
                            when (result) {
                                is SessionBackupManager.RestoreResult.Success -> {
                                    log("Restore SUCCESS: ${result.cookiesRestored} cookies", LogLevel.SUCCESS)
                                    // Reload the page to verify
                                    webView?.reload()
                                }
                                is SessionBackupManager.RestoreResult.Failed -> {
                                    log("Restore FAILED: ${result.reason}", LogLevel.ERROR)
                                }
                                is SessionBackupManager.RestoreResult.NoBackupFound -> {
                                    log("Restore FAILED: No backup found", LogLevel.ERROR)
                                }
                                is SessionBackupManager.RestoreResult.BackupExpired -> {
                                    log("Restore FAILED: Backup expired", LogLevel.ERROR)
                                }
                                is SessionBackupManager.RestoreResult.BackupCorrupted -> {
                                    log("Restore FAILED: Backup corrupted", LogLevel.ERROR)
                                }
                            }
                            isOperationInProgress = false
                        }
                    },
                    onClearSession = {
                        scope.launch {
                            isOperationInProgress = true
                            log("Clearing Steam session...")
                            verificationResult = null
                            
                            val success = sessionManager.clearSteamSession()
                            
                            if (success) {
                                log("Clear SUCCESS", LogLevel.SUCCESS)
                                webView?.reload()
                            } else {
                                log("Clear FAILED", LogLevel.ERROR)
                            }
                            isOperationInProgress = false
                        }
                    },
                    onVerifyIdentity = {
                        scope.launch {
                            isOperationInProgress = true
                            log("Verifying Steam identity...")
                            
                            // Navigate to profile page
                            webView?.loadUrl("https://steamcommunity.com/my/")
                            
                            // Wait for page load and extract identity
                            kotlinx.coroutines.delay(3000)
                            
                            webView?.evaluateJavascript("""
                                (function() {
                                    // Try to get persona name
                                    var personaName = document.querySelector('.actual_persona_name')?.textContent?.trim();
                                    if (!personaName) {
                                        personaName = document.querySelector('.persona_name')?.textContent?.trim();
                                    }
                                    
                                    // Try to get Steam ID from URL
                                    var steamId = null;
                                    var profileUrl = document.querySelector('[data-miniprofile]')?.getAttribute('data-miniprofile');
                                    if (!profileUrl) {
                                        var match = window.location.href.match(/profiles\/(\d+)/);
                                        if (match) steamId = match[1];
                                    }
                                    
                                    // Check if logged in
                                    var isLoggedIn = !document.querySelector('.global_action_link[href*="login"]');
                                    
                                    return JSON.stringify({
                                        personaName: personaName,
                                        steamId: steamId || profileUrl,
                                        isLoggedIn: isLoggedIn,
                                        url: window.location.href
                                    });
                                })();
                            """.trimIndent()) { result ->
                                try {
                                    val cleaned = result?.trim('"')?.replace("\\\"", "\"")
                                    if (cleaned != null && cleaned != "null") {
                                        val json = org.json.JSONObject(cleaned)
                                        val personaName = json.optString("personaName", "Unknown")
                                        val steamId = json.optString("steamId", "Unknown")
                                        val isLoggedIn = json.optBoolean("isLoggedIn", false)
                                        
                                        if (isLoggedIn && personaName != "Unknown") {
                                            verificationResult = "✓ Logged in as: $personaName (ID: $steamId)"
                                            log("Identity verified: $personaName", LogLevel.SUCCESS)
                                        } else {
                                            verificationResult = "✗ Not logged in"
                                            log("Not logged in", LogLevel.WARN)
                                        }
                                    } else {
                                        verificationResult = "✗ Could not verify"
                                        log("Verification failed: null result", LogLevel.ERROR)
                                    }
                                } catch (e: Exception) {
                                    verificationResult = "✗ Verification error"
                                    log("Verification error: ${e.message}", LogLevel.ERROR)
                                }
                            }
                            
                            isOperationInProgress = false
                        }
                    },
                    onDeleteBackup = { accountId ->
                        if (sessionManager.deleteBackup(accountId)) {
                            log("Deleted backup for: ${accountId.take(8)}", LogLevel.SUCCESS)
                            backups = sessionManager.listBackups()
                        } else {
                            log("Failed to delete backup", LogLevel.ERROR)
                        }
                    },
                    onCheckStatus = {
                        val status = checkAuthStatus()
                        log("Auth status: $status")
                    }
                )
                
                2 -> LogsTab(logEntries = logEntries, onClear = { logEntries = emptyList() })
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserTab(
    webView: WebView?,
    onWebViewCreated: (WebView) -> Unit,
    currentUrl: String,
    pageTitle: String,
    isLoading: Boolean,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onLog: (String) -> Unit
) {
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        // URL bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                
                Text(
                    text = currentUrl.take(60) + if (currentUrl.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Quick navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { webView?.loadUrl("https://store.steampowered.com/login/") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Login", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { webView?.loadUrl("https://store.steampowered.com/") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Store", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { webView?.loadUrl("https://steamcommunity.com/my/") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Profile", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { webView?.reload() },
                modifier = Modifier.weight(0.5f)
            ) {
                Icon(Icons.Default.Refresh, "Refresh", modifier = Modifier.size(16.dp))
            }
        }
        
        // WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                    }
                    
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            url?.let { onUrlChanged(it) }
                            onLoadingChanged(true)
                            onLog("Loading: ${url?.take(50)}...")
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let { onUrlChanged(it) }
                            onLoadingChanged(false)
                            CookieManager.getInstance().flush()
                            onLog("Loaded: ${url?.take(50)}")
                        }
                        
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            view?.title?.let { onTitleChanged(it) }
                        }
                    }
                    
                    onWebViewCreated(this)
                    loadUrl("https://store.steampowered.com/login/")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}

@Composable
private fun ControlsTab(
    accountIdInput: String,
    onAccountIdChanged: (String) -> Unit,
    backups: List<SessionBackupManager.BackupInfo>,
    verificationResult: String?,
    isOperationInProgress: Boolean,
    onBackup: () -> Unit,
    onRestore: (String) -> Unit,
    onClearSession: () -> Unit,
    onVerifyIdentity: () -> Unit,
    onDeleteBackup: (String) -> Unit,
    onCheckStatus: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Account ID input
        OutlinedTextField(
            value = accountIdInput,
            onValueChange = onAccountIdChanged,
            label = { Text("Account ID (for backup)") },
            placeholder = { Text("e.g., account_a, steam_work") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isOperationInProgress
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Action buttons
        Text("Session Actions", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onBackup,
                enabled = !isOperationInProgress && accountIdInput.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Backup")
            }
            
            Button(
                onClick = onClearSession,
                enabled = !isOperationInProgress,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onVerifyIdentity,
                enabled = !isOperationInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Person, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Verify Identity")
            }
            
            OutlinedButton(
                onClick = onCheckStatus,
                enabled = !isOperationInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Check Status")
            }
        }
        
        // Verification result
        verificationResult?.let { result ->
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.startsWith("✓")) 
                        Color(0xFF1B5E20).copy(alpha = 0.2f)
                    else 
                        Color(0xFFB71C1C).copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Saved backups
        Text("Saved Backups (${backups.size})", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        if (backups.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    "No backups yet. Login to Steam and backup your session.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            backups.forEach { backup ->
                BackupCard(
                    backup = backup,
                    onRestore = { onRestore(backup.accountId) },
                    onDelete = { onDeleteBackup(backup.accountId) },
                    isOperationInProgress = isOperationInProgress
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Warning card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "⚠️ Known Limitations",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "• Clearing Steam session clears ALL store cookies\n" +
                    "• IndexedDB is not fully backed up\n" +
                    "• Backups expire after 24 hours\n" +
                    "• Some stores may have additional state",
                    fontSize = 12.sp,
                    color = Color(0xFF5D4037)
                )
            }
        }
    }
}

@Composable
private fun BackupCard(
    backup: SessionBackupManager.BackupInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    isOperationInProgress: Boolean
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    backup.accountId,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${dateFormat.format(Date(backup.timestamp))} • ${backup.sizeBytes / 1024}KB",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row {
                IconButton(
                    onClick = onRestore,
                    enabled = !isOperationInProgress
                ) {
                    Icon(
                        Icons.Default.Restore,
                        "Restore",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !isOperationInProgress
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualClaimTab(
    webView: WebView?,
    isOperationInProgress: Boolean,
    onLog: (String, LogLevel) -> Unit,
    onNavigateToGame: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // State
    var steamUrl by remember { mutableStateOf("") }
    var validationState by remember { mutableStateOf<ValidationState>(ValidationState.Idle) }
    var gameInfo by remember { mutableStateOf<GameInfo?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var isClaiming by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Instructions card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Manual Steam Game Claim",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. Paste a free Steam game URL below\n" +
                    "2. Click Verify to check if claimable\n" +
                    "3. Click Claim to add to your library",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // URL Input
        OutlinedTextField(
            value = steamUrl,
            onValueChange = { 
                steamUrl = it
                validationState = ValidationState.Idle
                gameInfo = null
            },
            label = { Text("Steam Game URL") },
            placeholder = { Text("https://store.steampowered.com/app/123456") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isValidating && !isClaiming,
            trailingIcon = {
                if (steamUrl.isNotEmpty()) {
                    IconButton(onClick = { 
                        steamUrl = ""
                        validationState = ValidationState.Idle
                        gameInfo = null
                    }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            }
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Quick examples
        Text(
            "Examples: /app/123456, /sub/12345, or full URL",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Verify button
        Button(
            onClick = {
                scope.launch {
                    isValidating = true
                    validationState = ValidationState.Checking
                    onLog("Validating URL: ${steamUrl.take(50)}...", LogLevel.INFO)
                    
                    // Parse and validate URL
                    val parsedUrl = parseAndValidateSteamUrl(steamUrl)
                    if (parsedUrl == null) {
                        validationState = ValidationState.Invalid("Invalid Steam URL format")
                        onLog("Invalid URL format", LogLevel.ERROR)
                        isValidating = false
                        return@launch
                    }
                    
                    onLog("Parsed URL: $parsedUrl", LogLevel.INFO)
                    
                    // Navigate to the game page
                    webView?.loadUrl(parsedUrl)
                    
                    // Wait for page load
                    delay(3000)
                    
                    // Check page state using JS
                    webView?.evaluateJavascript(SteamStoreAdapter.JS_DETECT_OWNERSHIP) { result ->
                        scope.launch {
                            try {
                                val cleaned = result?.trim('"')?.replace("\\\"", "\"")
                                    ?.replace("\\n", "")
                                if (cleaned == null || cleaned == "null") {
                                    validationState = ValidationState.Invalid("Could not analyze page")
                                    onLog("Page analysis failed: null result", LogLevel.ERROR)
                                    isValidating = false
                                    return@launch
                                }
                                
                                val json = JSONObject(cleaned)
                                
                                if (json.has("error")) {
                                    validationState = ValidationState.Invalid(json.getString("error"))
                                    onLog("JS Error: ${json.getString("error")}", LogLevel.ERROR)
                                    isValidating = false
                                    return@launch
                                }
                                
                                val pageTitle = json.optString("pageTitle", "Unknown Game")
                                val isLoggedIn = json.optBoolean("isLoggedIn", false)
                                val owned = json.optBoolean("owned", false)
                                val notOwned = json.optBoolean("notOwned", false)
                                val requiresLogin = json.optBoolean("requiresLogin", false)
                                val requiresAgeGate = json.optBoolean("requiresAgeGate", false)
                                val isFreeToPlay = json.optBoolean("isFreeToPlay", false)
                                val isDemo = json.optBoolean("isDemo", false)
                                val requiresBaseGame = json.optBoolean("requiresBaseGame", false)
                                val hasDiscount100 = json.optBoolean("hasDiscount100", false)
                                val action = json.optString("action", "").ifEmpty { null }
                                val priceFinal = json.optString("priceFinal", "").ifEmpty { null }
                                
                                gameInfo = GameInfo(
                                    title = pageTitle,
                                    url = parsedUrl,
                                    appId = extractAppId(parsedUrl) ?: "unknown"
                                )
                                
                                when {
                                    owned -> {
                                        validationState = ValidationState.AlreadyOwned
                                        onLog("Already owned: $pageTitle", LogLevel.SUCCESS)
                                    }
                                    requiresLogin -> {
                                        validationState = ValidationState.RequiresLogin
                                        onLog("Login required to check: $pageTitle", LogLevel.WARN)
                                    }
                                    requiresAgeGate -> {
                                        validationState = ValidationState.RequiresAgeVerification
                                        onLog("Age verification required for: $pageTitle", LogLevel.WARN)
                                    }
                                    isDemo -> {
                                        validationState = ValidationState.IsDemo
                                        onLog("This is a demo, not the full game: $pageTitle", LogLevel.WARN)
                                    }
                                    isFreeToPlay && !hasDiscount100 -> {
                                        validationState = ValidationState.IsFreeToPlay
                                        onLog("Free-to-Play game (no claim needed): $pageTitle", LogLevel.INFO)
                                    }
                                    requiresBaseGame -> {
                                        validationState = ValidationState.RequiresBaseGame
                                        onLog("DLC requires base game: $pageTitle", LogLevel.WARN)
                                    }
                                    notOwned && (hasDiscount100 || action != null) -> {
                                        validationState = ValidationState.Claimable(
                                            action = action ?: "unknown",
                                            isFree = hasDiscount100 || priceFinal?.contains("0") == true
                                        )
                                        onLog("✓ CLAIMABLE: $pageTitle (action: $action)", LogLevel.SUCCESS)
                                    }
                                    else -> {
                                        validationState = ValidationState.NotClaimable(
                                            reason = "No free claim available",
                                            price = priceFinal
                                        )
                                        onLog("Not claimable: $pageTitle (price: $priceFinal)", LogLevel.WARN)
                                    }
                                }
                            } catch (e: Exception) {
                                validationState = ValidationState.Invalid("Error: ${e.message}")
                                onLog("Validation error: ${e.message}", LogLevel.ERROR)
                            }
                            isValidating = false
                        }
                    }
                }
            },
            enabled = steamUrl.isNotBlank() && !isValidating && !isClaiming && webView != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Checking...")
            } else {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Verify Game")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Validation result
        when (val state = validationState) {
            is ValidationState.Idle -> {
                // No result yet
            }
            is ValidationState.Checking -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Analyzing game page...")
                    }
                }
            }
            is ValidationState.Invalid -> {
                ValidationResultCard(
                    icon = Icons.Default.Error,
                    title = "Invalid URL",
                    message = state.reason,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            is ValidationState.RequiresLogin -> {
                ValidationResultCard(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = "Login Required",
                    message = "Please login to Steam first (Browser tab)",
                    containerColor = Color(0xFFFFF3E0),
                    contentColor = Color(0xFFE65100)
                )
            }
            is ValidationState.RequiresAgeVerification -> {
                ValidationResultCard(
                    icon = Icons.Default.Warning,
                    title = "Age Verification Required",
                    message = "Go to Browser tab and verify your age",
                    containerColor = Color(0xFFFFF3E0),
                    contentColor = Color(0xFFE65100)
                )
            }
            is ValidationState.AlreadyOwned -> {
                ValidationResultCard(
                    icon = Icons.Default.CheckCircle,
                    title = gameInfo?.title ?: "Game",
                    message = "✓ Already in your library!",
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32)
                )
            }
            is ValidationState.IsDemo -> {
                ValidationResultCard(
                    icon = Icons.Default.Info,
                    title = gameInfo?.title ?: "Demo",
                    message = "This is a demo, not the full game",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is ValidationState.IsFreeToPlay -> {
                ValidationResultCard(
                    icon = Icons.Default.SportsEsports,
                    title = gameInfo?.title ?: "Game",
                    message = "Free-to-Play game - just download and play!",
                    containerColor = Color(0xFFE3F2FD),
                    contentColor = Color(0xFF1565C0)
                )
            }
            is ValidationState.RequiresBaseGame -> {
                ValidationResultCard(
                    icon = Icons.Default.Extension,
                    title = gameInfo?.title ?: "DLC",
                    message = "This DLC requires the base game",
                    containerColor = Color(0xFFFFF3E0),
                    contentColor = Color(0xFFE65100)
                )
            }
            is ValidationState.NotClaimable -> {
                ValidationResultCard(
                    icon = Icons.Default.MoneyOff,
                    title = gameInfo?.title ?: "Game",
                    message = "${state.reason}${state.price?.let { " (Price: $it)" } ?: ""}",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is ValidationState.Claimable -> {
                // Claimable card with Claim button
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CardGiftcard,
                                null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    gameInfo?.title ?: "Game",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "✓ FREE - Ready to claim!",
                                    fontSize = 13.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Claim button
                        Button(
                            onClick = {
                                scope.launch {
                                    isClaiming = true
                                    onLog("Attempting to claim: ${gameInfo?.title}", LogLevel.INFO)
                                    
                                    // Execute claim
                                    webView?.evaluateJavascript(SteamStoreAdapter.JS_CLICK_CLAIM) { result ->
                                        scope.launch {
                                            try {
                                                val cleaned = result?.trim('"')?.replace("\\\"", "\"")
                                                if (cleaned != null && cleaned != "null") {
                                                    val json = JSONObject(cleaned)
                                                    val success = json.optBoolean("success", false)
                                                    val method = json.optString("method", "unknown")
                                                    
                                                    if (success) {
                                                        onLog("Claim initiated via: $method", LogLevel.SUCCESS)
                                                        
                                                        // Wait for claim to process
                                                        delay(2000)
                                                        
                                                        // Verify ownership
                                                        webView?.evaluateJavascript(SteamStoreAdapter.JS_VERIFY_OWNED) { verifyResult ->
                                                            scope.launch {
                                                                val isOwned = verifyResult?.trim('"') == "true"
                                                                if (isOwned) {
                                                                    validationState = ValidationState.AlreadyOwned
                                                                    onLog("✓ CLAIM SUCCESS: ${gameInfo?.title}", LogLevel.SUCCESS)
                                                                } else {
                                                                    // Reload and check again
                                                                    webView?.reload()
                                                                    delay(2000)
                                                                    webView?.evaluateJavascript(SteamStoreAdapter.JS_VERIFY_OWNED) { r2 ->
                                                                        scope.launch {
                                                                            if (r2?.trim('"') == "true") {
                                                                                validationState = ValidationState.AlreadyOwned
                                                                                onLog("✓ CLAIM SUCCESS (verified): ${gameInfo?.title}", LogLevel.SUCCESS)
                                                                            } else {
                                                                                onLog("Claim sent - please verify in your library", LogLevel.WARN)
                                                                            }
                                                                            isClaiming = false
                                                                        }
                                                                    }
                                                                    return@launch
                                                                }
                                                                isClaiming = false
                                                            }
                                                        }
                                                        return@launch
                                                    } else {
                                                        val reason = json.optString("reason", "Unknown error")
                                                        onLog("Claim failed: $reason", LogLevel.ERROR)
                                                    }
                                                } else {
                                                    onLog("Claim failed: null result", LogLevel.ERROR)
                                                }
                                            } catch (e: Exception) {
                                                onLog("Claim error: ${e.message}", LogLevel.ERROR)
                                            }
                                            isClaiming = false
                                        }
                                    }
                                }
                            },
                            enabled = !isClaiming && webView != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32)
                            )
                        ) {
                            if (isClaiming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Claiming...")
                            } else {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Claim Game")
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // View in browser button
                        OutlinedButton(
                            onClick = { gameInfo?.url?.let { onNavigateToGame(it) } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View in Browser")
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Tips card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "💡 Tips",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "• Make sure you're logged into Steam (Browser tab)\n" +
                    "• Only works with FREE games (100% off)\n" +
                    "• Some games may require age verification\n" +
                    "• Check the Logs tab for detailed info",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ValidationResultCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    containerColor: Color,
    contentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    message,
                    fontSize = 13.sp,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// Helper function to parse and validate Steam URLs
private fun parseAndValidateSteamUrl(input: String): String? {
    val trimmed = input.trim()
    
    // Already a full URL
    if (trimmed.startsWith("https://store.steampowered.com/")) {
        return if (SteamStoreAdapter.STEAM_ITEM_REGEX.containsMatchIn(trimmed)) trimmed else null
    }
    
    // Handle short forms
    val patterns = listOf(
        // /app/123456 or app/123456
        Regex("""^/?(?:agecheck/)?(app|sub|bundle)/(\d+)"""),
        // Just the app ID
        Regex("""^(\d{3,})$""")
    )
    
    for (pattern in patterns) {
        val match = pattern.find(trimmed)
        if (match != null) {
            return when {
                match.groupValues.size == 3 -> {
                    // Type and ID
                    "https://store.steampowered.com/${match.groupValues[1]}/${match.groupValues[2]}"
                }
                match.groupValues.size == 2 && match.groupValues[1].all { it.isDigit() } -> {
                    // Just ID, assume app
                    "https://store.steampowered.com/app/${match.groupValues[1]}"
                }
                else -> null
            }
        }
    }
    
    return null
}

// Helper to extract app ID from URL
private fun extractAppId(url: String): String? {
    val match = SteamStoreAdapter.STEAM_ITEM_REGEX.find(url)
    return match?.groupValues?.getOrNull(2)
}

// Validation states
private sealed class ValidationState {
    object Idle : ValidationState()
    object Checking : ValidationState()
    data class Invalid(val reason: String) : ValidationState()
    object RequiresLogin : ValidationState()
    object RequiresAgeVerification : ValidationState()
    object AlreadyOwned : ValidationState()
    object IsDemo : ValidationState()
    object IsFreeToPlay : ValidationState()
    object RequiresBaseGame : ValidationState()
    data class NotClaimable(val reason: String, val price: String?) : ValidationState()
    data class Claimable(val action: String, val isFree: Boolean) : ValidationState()
}

// Game info holder
private data class GameInfo(
    val title: String,
    val url: String,
    val appId: String
)

@Composable
private fun LogsTab(
    logEntries: List<LogEntry>,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${logEntries.size} entries", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp),
            reverseLayout = true
        ) {
            items(logEntries.reversed()) { entry ->
                LogEntryRow(entry)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val color = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFFF6B6B)
        LogLevel.WARN -> Color(0xFFFFD93D)
        LogLevel.SUCCESS -> Color(0xFF6BCB77)
        LogLevel.INFO -> Color(0xFFAAAAAA)
    }
    
    Text(
        text = "[${timeFormat.format(Date(entry.timestamp))}] ${entry.message}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

private data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

private enum class LogLevel {
    INFO, WARN, ERROR, SUCCESS
}
