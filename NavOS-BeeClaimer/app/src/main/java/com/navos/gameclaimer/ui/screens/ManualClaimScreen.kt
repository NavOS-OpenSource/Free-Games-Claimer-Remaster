package com.navos.gameclaimer.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.navos.gameclaimer.data.adapter.SteamStoreAdapter
import com.navos.gameclaimer.ui.components.BrutalistTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manual Steam Claim Screen
 * 
 * Allows users to paste a Steam game URL and claim free games manually.
 * This is a standalone Developer Options screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualClaimScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // WebView state - created immediately but may be invisible
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewReady by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var isPageLoading by remember { mutableStateOf(false) }
    
    // Claim state
    var steamUrl by remember { mutableStateOf("") }
    var validationState by remember { mutableStateOf<ClaimValidationState>(ClaimValidationState.Idle) }
    var gameInfo by remember { mutableStateOf<ClaimGameInfo?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var isClaiming by remember { mutableStateOf(false) }
    
    // Auth status
    var isAuthenticated by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf<String?>(null) }
    var isLoadingName by remember { mutableStateOf(false) }
    
    // Fetch Steam account name from profile page via HTTP
    suspend fun fetchSteamAccountName(): String? = withContext(Dispatchers.IO) {
        try {
            val cookies = CookieManager.getInstance().getCookie("https://store.steampowered.com")
            if (cookies.isNullOrEmpty()) return@withContext null
            
            val url = URL("https://store.steampowered.com/")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Cookie", cookies)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            // Extract account name from the page
            val patterns = listOf(
                Regex("""id="account_pulldown"[^>]*>([^<]+)</"""),
                Regex("""class="persona[^"]*"[^>]*>([^<]+)</"""),
                Regex("""<a[^>]*account_dropdown[^>]*>([^<]+)</""")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(response)
                if (match != null) {
                    val name = match.groupValues[1].trim()
                    if (name.isNotEmpty() && name != "Account" && !name.contains("Login")) {
                        return@withContext name
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    // Check auth status
    fun checkAuthStatus() {
        val cookies = CookieManager.getInstance().getCookie("https://store.steampowered.com")
        isAuthenticated = cookies?.contains("steamLoginSecure=") == true &&
                          cookies.contains("steamLoginSecure=;").not()
    }
    
    // Check auth and fetch account name on load
    LaunchedEffect(Unit) {
        checkAuthStatus()
        if (isAuthenticated) {
            isLoadingName = true
            accountName = fetchSteamAccountName()
            isLoadingName = false
        }
    }
    
    // Tab state
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Claim", "Browser")
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = "Manual Steam Claim",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            
            // Shared WebView - always present but visibility controlled
            Box(modifier = Modifier.fillMaxSize()) {
                // WebView layer - always created
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                @Suppress("DEPRECATION")
                                databaseEnabled = true
                            }
                            
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                            
                            webViewClient = object : WebViewClient() {
                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    // Intercept steam:// URLs and open via Intent
                                    if (url != null && url.startsWith("steam://")) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            ctx.startActivity(intent)
                                            // Signal success via a post to main thread
                                            view?.post {
                                                validationState = ClaimValidationState.F2PAdded
                                                isClaiming = false
                                            }
                                            return true
                                        } catch (e: Exception) {
                                            // Steam app not installed
                                            view?.post {
                                                validationState = ClaimValidationState.Invalid("Steam app not installed")
                                                isClaiming = false
                                            }
                                            return true
                                        }
                                    }
                                    return false
                                }
                                
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    url?.let { currentUrl = it }
                                    isPageLoading = true
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    url?.let { currentUrl = it }
                                    isPageLoading = false
                                    webViewReady = true
                                    CookieManager.getInstance().flush()
                                    // Update auth status after page load
                                    checkAuthStatus()
                                    // Try to get account name from page
                                    if (isAuthenticated && accountName == null) {
                                        evaluateJavascript("""
                                            (function() {
                                                var el = document.querySelector('#account_pulldown');
                                                return el ? el.textContent.trim() : null;
                                            })();
                                        """.trimIndent()) { result ->
                                            val name = result?.trim('"')
                                            if (name != null && name != "null" && name.isNotEmpty()) {
                                                accountName = name
                                            }
                                        }
                                    }
                                }
                            }
                            
                            webView = this
                            webViewReady = true
                            // Load Steam store by default
                            loadUrl("https://store.steampowered.com/")
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (selectedTab == 1) it else it.size(1.dp) } // Hide when on Claim tab
                )
                
                // Content layer based on selected tab
                if (selectedTab == 0) {
                    ClaimTab(
                        steamUrl = steamUrl,
                        onSteamUrlChange = { 
                            steamUrl = it
                            validationState = ClaimValidationState.Idle
                            gameInfo = null
                        },
                        validationState = validationState,
                        gameInfo = gameInfo,
                        isValidating = isValidating,
                        isClaiming = isClaiming,
                        isAuthenticated = isAuthenticated,
                        accountName = accountName,
                        isLoadingName = isLoadingName,
                        webViewReady = webViewReady,
                        onVerify = {
                            scope.launch {
                                isValidating = true
                                validationState = ClaimValidationState.Checking
                                
                                // Parse and validate URL
                                val parsedUrl = parseAndValidateSteamUrl(steamUrl)
                                if (parsedUrl == null) {
                                    validationState = ClaimValidationState.Invalid("Invalid Steam URL format")
                                    isValidating = false
                                    return@launch
                                }
                                
                                // Navigate to the game page
                                webView?.loadUrl(parsedUrl)
                                
                                // Wait for page to actually load (not just fixed delay)
                                var waitCount = 0
                                while (isPageLoading && waitCount < 30) {
                                    delay(200)
                                    waitCount++
                                }
                                // Extra delay for React/JS content to render
                                delay(1500)
                            
                            // Check page state using JS - with retry for title
                            var finalPageTitle = "Unknown Game"
                            
                            // First try to get page title from DOM
                            webView?.evaluateJavascript("""
                                (function() {
                                    var el = document.querySelector('#appHubAppName, .apphub_AppName');
                                    return el ? el.innerText.trim() : null;
                                })();
                            """.trimIndent()) { titleResult ->
                                val title = titleResult?.trim('"')
                                if (title != null && title != "null" && title.isNotBlank()) {
                                    finalPageTitle = title
                                }
                            }
                            
                            // Small delay for title fetch
                            delay(300)
                            
                            // Fallback: If title still unknown, try Steam API
                            val appId = extractAppId(parsedUrl)
                            if (finalPageTitle == "Unknown Game" && appId != null) {
                                try {
                                    val apiUrl = "https://store.steampowered.com/api/appdetails?appids=$appId&l=english"
                                    val connection = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
                                    connection.connectTimeout = 3000
                                    connection.readTimeout = 3000
                                    val apiResponse = connection.inputStream.bufferedReader().readText()
                                    val apiJson = JSONObject(apiResponse)
                                    val appData = apiJson.optJSONObject(appId)
                                    if (appData?.optBoolean("success") == true) {
                                        val data = appData.optJSONObject("data")
                                        val apiName = data?.optString("name")
                                        if (!apiName.isNullOrBlank()) {
                                            finalPageTitle = apiName
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("ManualClaim", "API title fetch failed: ${e.message}")
                                }
                            }
                            
                            webView?.evaluateJavascript(SteamStoreAdapter.JS_DETECT_OWNERSHIP) { result ->
                                scope.launch {
                                    try {
                                        val cleaned = result?.trim('"')?.replace("\\\"", "\"")
                                            ?.replace("\\n", "")
                                        if (cleaned == null || cleaned == "null") {
                                            validationState = ClaimValidationState.Invalid("Could not analyze page")
                                            isValidating = false
                                            return@launch
                                        }
                                        
                                        val json = JSONObject(cleaned)
                                        
                                        // Check for error - but only if it's actually a non-null string
                                        if (json.has("error") && !json.isNull("error")) {
                                            val errorMsg = json.optString("error", "")
                                            if (errorMsg.isNotBlank()) {
                                                validationState = ClaimValidationState.Invalid(errorMsg)
                                                isValidating = false
                                                return@launch
                                            }
                                        }
                                        
                                        val pageTitle = if (json.isNull("pageTitle")) finalPageTitle 
                                            else json.optString("pageTitle", finalPageTitle).let { 
                                                if (it == "null" || it.isBlank()) finalPageTitle else it 
                                            }
                                        val owned = json.optBoolean("owned", false)
                                        val notOwned = json.optBoolean("notOwned", false)
                                        val requiresLogin = json.optBoolean("requiresLogin", false)
                                        val requiresAgeGate = json.optBoolean("requiresAgeGate", false)
                                        val isFreeToPlay = json.optBoolean("isFreeToPlay", false)
                                        val isDemo = json.optBoolean("isDemo", false)
                                        val requiresBaseGame = json.optBoolean("requiresBaseGame", false)
                                        val hasDiscount100 = json.optBoolean("hasDiscount100", false)
                                        val action = json.optString("action", "").let { 
                                            if (it == "null" || it.isBlank()) null else it 
                                        }
                                        val priceFinal = json.optString("priceFinal", "").let { 
                                            if (it == "null" || it.isBlank()) null else it 
                                        }
                                        
                                        gameInfo = ClaimGameInfo(
                                            title = pageTitle,
                                            url = parsedUrl,
                                            appId = extractAppId(parsedUrl) ?: "unknown"
                                        )
                                        
                                        validationState = when {
                                            owned -> ClaimValidationState.AlreadyOwned
                                            requiresLogin -> ClaimValidationState.RequiresLogin
                                            requiresAgeGate -> ClaimValidationState.RequiresAgeVerification
                                            isDemo -> ClaimValidationState.IsDemo
                                            isFreeToPlay && !hasDiscount100 -> ClaimValidationState.IsFreeToPlay
                                            requiresBaseGame -> ClaimValidationState.RequiresBaseGame
                                            notOwned && (hasDiscount100 || action != null) -> 
                                                ClaimValidationState.Claimable(action ?: "unknown", hasDiscount100)
                                            else -> ClaimValidationState.NotClaimable(
                                                reason = "No free claim available",
                                                price = priceFinal
                                            )
                                        }
                                    } catch (e: Exception) {
                                        validationState = ClaimValidationState.Invalid("Error: ${e.message ?: "Unknown error"}")
                                    }
                                    isValidating = false
                                }
                            }
                        }
                    },
                    onClaim = {
                        scope.launch {
                            isClaiming = true
                            
                            // Choose JS based on game type
                            val jsToRun = if (validationState is ClaimValidationState.IsFreeToPlay) {
                                SteamStoreAdapter.JS_ADD_F2P_GAME
                            } else {
                                SteamStoreAdapter.JS_CLICK_CLAIM
                            }
                            
                            webView?.evaluateJavascript(jsToRun) { result ->
                                scope.launch {
                                    try {
                                        val cleaned = result?.trim('"')?.replace("\\\"", "\"")
                                        if (cleaned != null && cleaned != "null") {
                                            val json = JSONObject(cleaned)
                                            val success = json.optBoolean("success", false)
                                            val method = json.optString("method", "")
                                            
                                            // Handle need_steam_app - F2P game needs Steam app to add
                                            if (!success && method == "need_steam_app") {
                                                val steamUrl = json.optString("steamUrl", "")
                                                val gameTitle = json.optString("gameTitle", gameInfo?.title ?: "Game")
                                                if (steamUrl.isNotEmpty()) {
                                                    validationState = ClaimValidationState.RequiresSteamApp(steamUrl, gameTitle)
                                                }
                                                isClaiming = false
                                                return@launch
                                            }
                                            
                                            if (success) {
                                                // For F2P, AddFreeLicense worked directly via web
                                                if (validationState is ClaimValidationState.IsFreeToPlay) {
                                                    validationState = ClaimValidationState.F2PAdded
                                                    isClaiming = false
                                                    return@launch
                                                }
                                                
                                                delay(2000)
                                                
                                                webView?.evaluateJavascript(SteamStoreAdapter.JS_VERIFY_OWNED) { verifyResult ->
                                                    scope.launch {
                                                        val isOwned = verifyResult?.trim('"') == "true"
                                                        if (isOwned) {
                                                            validationState = ClaimValidationState.AlreadyOwned
                                                        } else {
                                                            webView?.reload()
                                                            delay(2000)
                                                            webView?.evaluateJavascript(SteamStoreAdapter.JS_VERIFY_OWNED) { r2 ->
                                                                scope.launch {
                                                                    if (r2?.trim('"') == "true") {
                                                                        validationState = ClaimValidationState.AlreadyOwned
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
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Error during claim
                                    }
                                    isClaiming = false
                                }
                            }
                        }
                    },
                    onLoginClick = { selectedTab = 1 },
                    onViewInBrowser = { 
                        gameInfo?.url?.let { 
                            webView?.loadUrl(it)
                            selectedTab = 1
                        }
                    }
                )
                }
                
                // Browser tab shows the WebView which is already created above
                // No additional content needed as WebView handles it
            }
        }
    }
}

@Composable
private fun ClaimTab(
    steamUrl: String,
    onSteamUrlChange: (String) -> Unit,
    validationState: ClaimValidationState,
    gameInfo: ClaimGameInfo?,
    isValidating: Boolean,
    isClaiming: Boolean,
    isAuthenticated: Boolean,
    accountName: String?,
    isLoadingName: Boolean,
    webViewReady: Boolean,
    onVerify: () -> Unit,
    onClaim: () -> Unit,
    onLoginClick: () -> Unit,
    onViewInBrowser: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Auth status card
        if (!isAuthenticated) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                onClick = onLoginClick
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Not logged in",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            "Tap to login to Steam →",
                            fontSize = 13.sp,
                            color = Color(0xFFE65100).copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = Color(0xFFE65100)
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isLoadingName) "Loading..." else (accountName ?: "Steam Account"),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            "Connected • Ready to claim",
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "How to use",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. Make sure you're logged into Steam\n" +
                    "2. Paste a free game URL below\n" +
                    "3. Click Verify → then Claim",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // URL Input
        OutlinedTextField(
            value = steamUrl,
            onValueChange = onSteamUrlChange,
            label = { Text("Steam Game URL") },
            placeholder = { Text("https://store.steampowered.com/app/123456") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isValidating && !isClaiming,
            trailingIcon = {
                if (steamUrl.isNotEmpty()) {
                    IconButton(onClick = { onSteamUrlChange("") }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            }
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            "Examples: /app/123456, just the ID, or full URL",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Verify button
        Button(
            onClick = onVerify,
            enabled = steamUrl.isNotBlank() && !isValidating && !isClaiming && webViewReady,
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
        ValidationResultDisplay(
            state = validationState,
            gameInfo = gameInfo,
            isClaiming = isClaiming,
            onClaim = onClaim,
            onViewInBrowser = onViewInBrowser,
            webViewReady = webViewReady
        )
    }
}

@Composable
private fun ValidationResultDisplay(
    state: ClaimValidationState,
    gameInfo: ClaimGameInfo?,
    isClaiming: Boolean,
    onClaim: () -> Unit,
    onViewInBrowser: () -> Unit,
    webViewReady: Boolean
) {
    when (state) {
        is ClaimValidationState.Idle -> { }
        is ClaimValidationState.Checking -> {
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
        is ClaimValidationState.Invalid -> {
            ResultCard(Icons.Default.Error, "Invalid", state.reason, 
                MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        }
        is ClaimValidationState.RequiresLogin -> {
            ResultCard(Icons.AutoMirrored.Filled.Login, "Login Required", 
                "Please login to Steam first (Browser tab)", 
                Color(0xFFFFF3E0), Color(0xFFE65100))
        }
        is ClaimValidationState.RequiresAgeVerification -> {
            ResultCard(Icons.Default.Warning, "Age Verification", 
                "Go to Browser tab and verify your age", 
                Color(0xFFFFF3E0), Color(0xFFE65100))
        }
        is ClaimValidationState.AlreadyOwned -> {
            ResultCard(Icons.Default.CheckCircle, gameInfo?.title ?: "Game", 
                "✓ Already in your library!", 
                Color(0xFFE8F5E9), Color(0xFF2E7D32))
        }
        is ClaimValidationState.IsDemo -> {
            ResultCard(Icons.Default.Info, gameInfo?.title ?: "Demo", 
                "This is a demo, not the full game", 
                MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is ClaimValidationState.IsFreeToPlay -> {
            // F2P games get an "Add to Account" button
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SportsEsports,
                            null,
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                gameInfo?.title ?: "Game",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                            Text(
                                "Free-to-Play game",
                                fontSize = 13.sp,
                                color = Color(0xFF1565C0).copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onClaim,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isClaiming && webViewReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1565C0)
                        )
                    ) {
                        if (isClaiming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Adding...")
                        } else {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add to Account")
                        }
                    }
                }
            }
        }
        is ClaimValidationState.F2PAdded -> {
            // Success state for F2P games - added to library via AddFreeLicense
            ResultCard(
                Icons.Default.CheckCircle, 
                gameInfo?.title ?: "Game", 
                "Added to your Steam Library!", 
                Color(0xFFE8F5E9), 
                Color(0xFF2E7D32)
            )
        }
        is ClaimValidationState.RequiresSteamApp -> {
            // F2P game needs Steam app to add to library
            val context = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SportsEsports,
                            null,
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                state.gameTitle,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                            Text(
                                "Free-to-Play - Requires Steam app",
                                fontSize = 13.sp,
                                color = Color(0xFF1565C0).copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "F2P games can only be added via the Steam app. Click the button below to open Steam.",
                        fontSize = 13.sp,
                        color = Color(0xFF1565C0).copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.steamUrl))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Steam app not installed - open Play Store
                                try {
                                    val playStoreIntent = Intent(Intent.ACTION_VIEW, 
                                        Uri.parse("market://details?id=com.valvesoftware.android.steam.community"))
                                    context.startActivity(playStoreIntent)
                                } catch (e2: Exception) {
                                    // Play Store not available - open web
                                    val webIntent = Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/apps/details?id=com.valvesoftware.android.steam.community"))
                                    context.startActivity(webIntent)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open in Steam App")
                    }
                }
            }
        }
        is ClaimValidationState.RequiresBaseGame -> {
            ResultCard(Icons.Default.Extension, gameInfo?.title ?: "DLC", 
                "This DLC requires the base game", 
                Color(0xFFFFF3E0), Color(0xFFE65100))
        }
        is ClaimValidationState.NotClaimable -> {
            ResultCard(Icons.Default.MoneyOff, gameInfo?.title ?: "Game", 
                "${state.reason}${state.price?.let { " ($it)" } ?: ""}", 
                MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is ClaimValidationState.Claimable -> {
            ClaimableCard(
                gameInfo = gameInfo,
                isClaiming = isClaiming,
                onClaim = onClaim,
                onViewInBrowser = onViewInBrowser,
                webViewReady = webViewReady
            )
        }
    }
}

@Composable
private fun ResultCard(
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
                Text(message, fontSize = 13.sp, color = contentColor.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ClaimableCard(
    gameInfo: ClaimGameInfo?,
    isClaiming: Boolean,
    onClaim: () -> Unit,
    onViewInBrowser: () -> Unit,
    webViewReady: Boolean
) {
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
            
            Button(
                onClick = onClaim,
                enabled = !isClaiming && webViewReady,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
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
            
            OutlinedButton(
                onClick = onViewInBrowser,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInBrowser, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("View in Browser")
            }
        }
    }
}

// Validation states
private sealed class ClaimValidationState {
    object Idle : ClaimValidationState()
    object Checking : ClaimValidationState()
    data class Invalid(val reason: String) : ClaimValidationState()
    object RequiresLogin : ClaimValidationState()
    object RequiresAgeVerification : ClaimValidationState()
    object AlreadyOwned : ClaimValidationState()
    object IsDemo : ClaimValidationState()
    object IsFreeToPlay : ClaimValidationState()
    object F2PAdded : ClaimValidationState()  // Successfully triggered Steam for F2P game
    data class RequiresSteamApp(val steamUrl: String, val gameTitle: String) : ClaimValidationState()  // F2P needs Steam app
    object RequiresBaseGame : ClaimValidationState()
    data class NotClaimable(val reason: String, val price: String?) : ClaimValidationState()
    data class Claimable(val action: String, val isFree: Boolean) : ClaimValidationState()
}

private data class ClaimGameInfo(
    val title: String,
    val url: String,
    val appId: String
)

// URL parsing helpers
private fun parseAndValidateSteamUrl(input: String): String? {
    val trimmed = input.trim()
    
    if (trimmed.startsWith("https://store.steampowered.com/")) {
        return if (SteamStoreAdapter.STEAM_ITEM_REGEX.containsMatchIn(trimmed)) trimmed else null
    }
    
    val patterns = listOf(
        Regex("""^/?(?:agecheck/)?(app|sub|bundle)/(\d+)"""),
        Regex("""^(\d{3,})$""")
    )
    
    for (pattern in patterns) {
        val match = pattern.find(trimmed)
        if (match != null) {
            return when {
                match.groupValues.size == 3 -> 
                    "https://store.steampowered.com/${match.groupValues[1]}/${match.groupValues[2]}"
                match.groupValues.size == 2 && match.groupValues[1].all { it.isDigit() } -> 
                    "https://store.steampowered.com/app/${match.groupValues[1]}"
                else -> null
            }
        }
    }
    return null
}

private fun extractAppId(url: String): String? {
    val match = SteamStoreAdapter.STEAM_ITEM_REGEX.find(url)
    return match?.groupValues?.getOrNull(2)
}
