package com.navos.gameclaimer.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.navos.gameclaimer.data.adapter.SteamOwnershipState
import com.navos.gameclaimer.data.adapter.SteamStoreAdapter
import com.navos.gameclaimer.data.adapter.EpicStoreAdapter
import com.navos.gameclaimer.data.adapter.EpicPageState
import com.navos.gameclaimer.data.adapter.GogStoreAdapter
import com.navos.gameclaimer.data.adapter.ItchioStoreAdapter
import com.navos.gameclaimer.data.adapter.IndieGalaStoreAdapter
import com.navos.gameclaimer.data.adapter.FanaticalAdapter
import com.navos.gameclaimer.data.adapter.MicrosoftStoreAdapter
import com.navos.gameclaimer.data.adapter.UbisoftAdapter
import com.navos.gameclaimer.data.adapter.PrimeGamingAdapter
import com.navos.gameclaimer.data.adapter.AlienwareArenaAdapter
import com.navos.gameclaimer.data.adapter.FabAdapter
import com.navos.gameclaimer.data.adapter.UnityAssetStoreAdapter
import com.navos.gameclaimer.domain.model.BrowserAuthEvent
import com.navos.gameclaimer.domain.model.DetectionResult
import com.navos.gameclaimer.domain.model.DetectionState
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.MfaType
import com.navos.gameclaimer.domain.model.StoreAuthConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Phase 5: Secure Browser Wrapper
 * 
 * Wraps Android WebView with security hardening and authentication event detection.
 * 
 * SECURITY REQUIREMENTS (per GPT spec):
 * - HTTPS only - HTTP blocked
 * - SSL errors NEVER bypassed
 * - JavaScript enabled (required for store pages) but no JS interface exposed
 * - File/content access disabled
 * - WebView debugging disabled in release
 * - Cookies managed by system CookieManager
 * - No credentials logged
 * - URL validation before navigation
 * 
 * COOKIE SECURITY NOTE:
 * WebView cookies are stored by Android's system CookieManager in a WebView-specific
 * database. This is SEPARATE from app-managed tokens and cannot be encrypted by
 * Android Keystore. The WebView cookie database is protected by Android's app sandbox
 * and file permissions, but is not encrypted at rest on non-encrypted devices.
 * 
 * This is a platform limitation - we document it but cannot change it.
 */
class SecureBrowserWrapper(
    private val context: Context,
    val config: StoreAuthConfig  // Made public for external browser fallback
) {
    private var webView: WebView? = null
    private val eventChannel = Channel<BrowserAuthEvent>(Channel.BUFFERED)
    
    /** Flow of authentication events */
    val events: Flow<BrowserAuthEvent> = eventChannel.receiveAsFlow()
    
    /** Current URL (for state checking) */
    var currentUrl: String = ""
        private set
    
    /** Page title */
    var pageTitle: String = ""
        private set
    
    /** Loading state */
    var isLoading: Boolean = false
        private set
    
    /**
     * Create and configure the WebView with security hardening.
     * Call this from Composable AndroidView factory.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(): WebView {
        return WebView(context).apply {
            // Configure security settings BEFORE any navigation
            configureSecuritySettings(this)
            
            // Use software layer to fix dropdown rendering issues
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            
            // Set up WebViewClient for navigation and security
            webViewClient = createSecureWebViewClient()
            
            // Set up WebChromeClient for progress/title
            webChromeClient = createWebChromeClient()
            
            // SECURITY: Block all downloads
            setDownloadListener { url, _, _, _, _ ->
                eventChannel.trySend(
                    BrowserAuthEvent.NavigationBlocked(url, "Downloads are not allowed")
                )
            }
            
            webView = this
        }
    }
    
    /**
     * Configure WebView security settings.
     * 
     * SECURITY: These settings are carefully chosen to balance
     * functionality (store pages need JS) with security.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSecuritySettings(webView: WebView) {
        webView.settings.apply {
            // JavaScript: Required for modern store pages
            // SECURITY: No JS interface exposed - JS cannot call native code
            javaScriptEnabled = true
            
            // DOM/Local Storage: Required for modern web apps
            domStorageEnabled = true
            databaseEnabled = true
            
            // SECURITY: Disable file/content access
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            
            // SECURITY: Use safe browsing if available
            safeBrowsingEnabled = true
            
            // SECURITY: Block mixed content (HTTP on HTTPS page)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            
            // Use default WebView user agent
            // Do NOT spoof as desktop Chrome - stores may behave differently
            
            // Disable geolocation - not needed for game claiming
            setGeolocationEnabled(false)
            
            // Media settings - allow for store videos/previews
            mediaPlaybackRequiresUserGesture = true
            
            // Zoom controls - enabled for accessibility
            builtInZoomControls = true
            displayZoomControls = false
            
            // Text zoom - allow for accessibility
            textZoom = 100
            
            // SECURITY: Disable support for multiple windows/popups
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        
        // SECURITY: Disable WebView debugging in release builds
        WebView.setWebContentsDebuggingEnabled(false)
        
        // Enable third-party cookies (required for auth redirects)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }
    
    /**
     * Create WebViewClient with security checks.
     */
    private fun createSecureWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                
                // SECURITY: Validate URL before allowing navigation
                val validationResult = validateUrl(url)
                if (!validationResult.isValid) {
                    eventChannel.trySend(
                        BrowserAuthEvent.NavigationBlocked(url, validationResult.reason)
                    )
                    // Log sanitized info - never log full URL which may contain tokens
                    logSafe("Navigation blocked", mapOf(
                        "reason" to validationResult.reason,
                        "domain" to extractDomain(url)
                    ))
                    return true // Block navigation
                }
                
                // Warn but allow for unknown domains (SSO providers, etc.)
                if (validationResult.shouldWarn && validationResult.warnDomain != null) {
                    eventChannel.trySend(
                        BrowserAuthEvent.NavigationWarning(url, validationResult.warnDomain)
                    )
                }
                
                return false // Allow navigation
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    currentUrl = it
                    isLoading = true
                    eventChannel.trySend(BrowserAuthEvent.PageStarted(it))
                    
                    // Check for auth state patterns
                    checkAuthPatterns(it)
                }
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    currentUrl = it
                    isLoading = false
                    eventChannel.trySend(BrowserAuthEvent.PageFinished(it, pageTitle))
                    
                    // Flush cookies to persistent storage
                    CookieManager.getInstance().flush()
                    
                    // Phase 18D: Run ownership detection for applicable stores
                    runOwnershipDetection(view, it)
                }
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Only report main frame errors
                if (request?.isForMainFrame == true) {
                    val errorCode = error?.errorCode ?: -1
                    val description = error?.description?.toString()
                    eventChannel.trySend(BrowserAuthEvent.LoadError(errorCode, description))
                    isLoading = false
                }
            }
            
            /**
             * SECURITY CRITICAL: Never bypass SSL errors.
             * 
             * This is a hard security requirement. SSL errors indicate:
             * - Man-in-the-middle attack
             * - Expired/invalid certificate
             * - Domain mismatch
             * 
             * Any of these should abort the connection.
             */
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                // NEVER call handler.proceed() - always cancel
                handler?.cancel()
                
                val url = error?.url ?: "unknown"
                eventChannel.trySend(BrowserAuthEvent.SslError(url))
                
                logSafe("SSL error - connection blocked", mapOf(
                    "domain" to extractDomain(url),
                    "primaryError" to (error?.primaryError?.toString() ?: "unknown")
                ))
            }
        }
    }
    
    /**
     * Create WebChromeClient for title/progress with popup blocking.
     */
    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                pageTitle = title ?: ""
            }
            
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                isLoading = newProgress < 100
            }
            
            /**
             * SECURITY: Block all popup/new window requests.
             */
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // Return false to block popup creation
                eventChannel.trySend(
                    BrowserAuthEvent.NavigationBlocked("popup", "Popup windows are not allowed")
                )
                return false
            }
        }
    }
    
    /**
     * Check URL against auth state patterns.
     */
    private fun checkAuthPatterns(url: String) {
        // Check for MFA patterns
        for (pattern in config.mfaUrlPatterns) {
            if (url.startsWith(pattern) || url.contains(pattern)) {
                val mfaType = detectMfaType(url)
                eventChannel.trySend(BrowserAuthEvent.MfaChallengeDetected(mfaType))
                return
            }
        }
        
        // Check for success patterns
        for (pattern in config.successUrlPatterns) {
            if (url.startsWith(pattern) || url.contains(pattern)) {
                eventChannel.trySend(BrowserAuthEvent.AuthenticationComplete(url))
                return
            }
        }
        
        // Check for login form (generic detection)
        if (url.contains("/login") || url.contains("/signin") || url.contains("/auth")) {
            eventChannel.trySend(BrowserAuthEvent.LoginFormDetected(url))
        }
    }
    
    /**
     * Detect MFA type from URL patterns.
     */
    private fun detectMfaType(url: String): MfaType {
        return when {
            url.contains("steamguard") -> MfaType.STEAM_GUARD_EMAIL
            url.contains("twofactor") -> MfaType.TOTP
            url.contains("mfa") -> MfaType.TOTP
            url.contains("cvf") -> MfaType.DEVICE_VERIFICATION
            url.contains("captcha") -> MfaType.CAPTCHA
            else -> MfaType.OTHER
        }
    }
    
    /**
     * Navigate to a URL after validation.
     */
    fun loadUrl(url: String): Boolean {
        val validation = validateUrl(url)
        if (!validation.isValid) {
            eventChannel.trySend(BrowserAuthEvent.NavigationBlocked(url, validation.reason))
            return false
        }
        webView?.loadUrl(url)
        return true
    }
    
    /**
     * Navigate to the store's login page.
     */
    fun loadLoginPage() {
        loadUrl(config.loginUrl)
    }
    
    /**
     * Navigate back if possible.
     */
    fun goBack(): Boolean {
        return if (webView?.canGoBack() == true) {
            webView?.goBack()
            true
        } else {
            false
        }
    }
    
    /**
     * Reload current page.
     */
    fun reload() {
        webView?.reload()
    }
    
    /**
     * Clear session data (logout).
     * 
     * Note: This clears cookies for ALL domains in WebView.
     * Per-store logout should navigate to store's logout URL first.
     */
    fun clearSessionData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        webView?.clearCache(true)
        webView?.clearHistory()
    }
    
    /**
     * Get current cookies for the store's domain (for session check).
     * Returns presence indicator, NOT the actual cookie values.
     */
    fun hasSessionCookies(): Boolean {
        val cookies = CookieManager.getInstance().getCookie(config.store.baseUrl)
        return !cookies.isNullOrEmpty()
    }
    
    /**
     * Clean up WebView resources.
     */
    fun destroy() {
        webView?.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        eventChannel.close()
    }
    
    /**
     * Get the underlying WebView for Compose integration.
     */
    fun getWebView(): WebView? = webView
    
    /**
     * Execute JavaScript on the current page and return result.
     * 
     * SECURITY: Only use for read-only detection scripts.
     * Never execute scripts that modify page state.
     * 
     * @param script JavaScript to execute
     * @param callback Callback with result (or null on error)
     */
    fun evaluateJavascript(script: String, callback: (String?) -> Unit) {
        webView?.evaluateJavascript(script) { result ->
            // Result comes wrapped in quotes as JSON string
            val cleaned = if (result != null && result != "null") {
                // Remove outer quotes and unescape
                result.trim('"').replace("\\\"", "\"").replace("\\n", "\n")
            } else {
                null
            }
            callback(cleaned)
        } ?: callback(null)
    }
    
    // ===== URL Validation =====
    
    data class UrlValidation(
        val isValid: Boolean,
        val reason: String = "",
        val shouldWarn: Boolean = false,  // True if domain is unknown but allowed
        val warnDomain: String? = null    // Domain to warn about
    )
    
    /**
     * Validate URL for security.
     * 
     * SECURITY: This is a critical security gate.
     * Uses parsed URL components, not string matching.
     */
    private fun validateUrl(url: String): UrlValidation {
        val trimmed = url.trim()
        
        // Parse URL to extract components safely
        val parsedUrl = try {
            android.net.Uri.parse(trimmed)
        } catch (e: Exception) {
            return UrlValidation(false, "Invalid URL format")
        }
        
        val scheme = parsedUrl.scheme?.lowercase() ?: ""
        val host = parsedUrl.host?.lowercase() ?: ""
        val port = parsedUrl.port
        val userInfo = parsedUrl.userInfo
        
        // SECURITY: Only HTTPS allowed
        if (scheme != "https") {
            return UrlValidation(false, "Only HTTPS URLs allowed (scheme: $scheme)")
        }
        
        // SECURITY: Block dangerous schemes (defense in depth)
        val blockedSchemes = listOf("javascript", "file", "content", "intent", "data", "blob")
        if (blockedSchemes.contains(scheme)) {
            return UrlValidation(false, "Blocked URL scheme: $scheme")
        }
        
        // SECURITY: Reject URLs with user-info (user:pass@host) - potential phishing
        if (!userInfo.isNullOrEmpty()) {
            return UrlValidation(false, "URLs with credentials are not allowed")
        }
        
        // SECURITY: Block non-standard ports (except 443 for HTTPS)
        if (port != -1 && port != 443) {
            return UrlValidation(false, "Non-standard port not allowed: $port")
        }
        
        // SECURITY: Host must not be empty
        if (host.isEmpty()) {
            return UrlValidation(false, "Missing hostname")
        }
        
        // SECURITY: Block localhost and private IPs
        val blockedHosts = listOf(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "[::1]"
        )
        if (blockedHosts.contains(host)) {
            return UrlValidation(false, "Local addresses blocked")
        }
        
        // SECURITY: Block private IP ranges
        val privatePatterns = listOf(
            Regex("^192\\.168\\..*"),
            Regex("^10\\..*"),
            Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"),
            Regex("^169\\.254\\..*"), // Link-local
            Regex("^\\[fc.*"), // IPv6 private
            Regex("^\\[fd.*")  // IPv6 private
        )
        for (pattern in privatePatterns) {
            if (pattern.matches(host)) {
                return UrlValidation(false, "Private/local addresses blocked")
            }
        }
        
        // SECURITY: Check if normalized hostname is in allowed list
        val isAllowed = config.allowedDomains.any { allowed ->
            val normalizedAllowed = allowed.lowercase()
            host == normalizedAllowed || 
            host.endsWith(".$normalizedAllowed")
        }
        
        if (!isAllowed) {
            // Warn but allow - user takes responsibility for unknown domains
            return UrlValidation(
                isValid = true, 
                shouldWarn = true, 
                warnDomain = host
            )
        }
        
        return UrlValidation(true)
    }
    
    /**
     * Extract domain from URL (for safe logging).
     */
    private fun extractDomain(url: String): String {
        return try {
            android.net.Uri.parse(url).host ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Safe logging - never log sensitive data.
     * 
     * SECURITY: This replaces direct Log calls to ensure
     * URLs, cookies, and tokens are never logged.
     */
    private fun logSafe(message: String, data: Map<String, String?> = emptyMap()) {
        // In production, this could go to a secure logging system
        // For now, we just skip logging sensitive URLs entirely
        // The data map only contains pre-sanitized values
        
        // Example: Log.d("SecureBrowser", "$message: $data")
        // Currently disabled to avoid any accidental logging
    }
    
    /**
     * Phase 18D: Run ownership/availability detection based on current URL.
     * 
     * SECURITY: This is READ-ONLY detection - only observes page state.
     * Results are logged via Logcat and emitted as events.
     * 
     * Detection is delayed to allow dynamic content (React, Angular, etc.) to load.
     * 
     * @param view The WebView that finished loading
     * @param url The URL that was loaded
     */
    private fun runOwnershipDetection(view: WebView?, url: String) {
        if (view == null) return
        
        // Determine which store this URL belongs to and run appropriate detection
        when {
            // Existing stores (Phase 18)
            url.contains("store.steampowered.com/app/") -> {
                runSteamDetection(view, url)
            }
            url.contains("store.epicgames.com/") && (url.contains("/p/") || url.contains("/bundles/")) -> {
                runEpicDetection(view, url)
            }
            url.contains("gog.com/") && url.contains("/game/") -> {
                runGogDetection(view, url)
            }
            // Phase 19 P1 stores
            url.contains(".itch.io/") -> {
                runItchioDetection(view, url)
            }
            url.contains("indiegala.com/") && (url.contains("/giveaways/") || url.contains("/freebies/")) -> {
                runIndieGalaDetection(view, url)
            }
            url.contains("fanatical.com/") && (url.contains("/game/") || url.contains("/bundle/")) -> {
                runFanaticalDetection(view, url)
            }
            // Phase 19 P2+ stores
            url.contains("xbox.com/") && url.contains("/games/store/") -> {
                runMicrosoftDetection(view, url)
            }
            (url.contains("ubisoft.com/") && url.contains("/games/free")) ||
                url.contains("register.ubisoft.com/") -> {
                runUbisoftDetection(view, url)
            }
            url.contains("luna.amazon.com/claims") || url.contains("gaming.amazon.com") -> {
                runPrimeGamingDetection(view, url)
            }
            url.contains("alienwarearena.com/ucf/show/") -> {
                runAlienwareArenaDetection(view, url)
            }
            url.contains("fab.com/listings/") -> {
                runFabDetection(view, url)
            }
            url.contains("assetstore.unity.com/") && url.contains("/packages/") -> {
                runUnityAssetStoreDetection(view, url)
            }
        }
    }
    
    // ===== Steam Detection =====
    
    /**
     * Run ownership detection for Steam app pages.
     * Uses bounded readiness polling instead of fixed delay.
     */
    private fun runSteamDetection(view: WebView, url: String) {
        // Extract app ID from URL
        val appIdMatch = Regex("""/app/(\d+)""").find(url)
        val appId = appIdMatch?.groupValues?.get(1) ?: return
        
        Log.d(TAG_DETECTION, "Steam: Detecting ownership for app $appId (polling for readiness...)")
        
        // Poll for Steam page readiness with bounded timeout
        pollForReadiness(
            view = view,
            readinessCheck = JS_STEAM_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runSteamDetectionJs(view, appId) },
            onTimeout = {
                Log.w(TAG_DETECTION, "Steam: Readiness timeout for app $appId, attempting detection anyway")
                runSteamDetectionJs(view, appId)
            }
        )
    }
    
    private fun runSteamDetectionJs(view: WebView, appId: String) {
        Log.d(TAG_DETECTION, "Steam: Running detection JS for app $appId")
        
        view.evaluateJavascript(SteamStoreAdapter.JS_DETECT_OWNERSHIP) { result ->
            Log.d(TAG_DETECTION, "Steam: Raw JS result: ${result?.take(200) ?: "NULL"}")
            
            if (result != null && result != "null" && result.isNotBlank()) {
                val cleaned = cleanJsResult(result)
                Log.d(TAG_DETECTION, "Steam: Cleaned result: ${cleaned.take(200)}")
                
                val steamAdapter = SteamStoreAdapter()
                val ownershipState = steamAdapter.parseOwnershipResult(appId, cleaned)
                
                // Convert to generic state and log
                val genericState = when (ownershipState) {
                    is SteamOwnershipState.Owned -> {
                        Log.i(TAG_DETECTION, "Steam: OWNED - App $appId via ${ownershipState.detectionMethod}")
                        DetectionState.OWNED
                    }
                    is SteamOwnershipState.NotOwned -> {
                        Log.i(TAG_DETECTION, "Steam: AVAILABLE - App $appId, action: ${ownershipState.availableAction}")
                        DetectionState.AVAILABLE
                    }
                    is SteamOwnershipState.RequiresLogin -> {
                        Log.i(TAG_DETECTION, "Steam: AUTH_REQUIRED - App $appId, reason: ${ownershipState.reason}")
                        DetectionState.AUTH_REQUIRED
                    }
                    is SteamOwnershipState.RequiresAgeVerification -> {
                        Log.i(TAG_DETECTION, "Steam: AGE_GATE - App $appId")
                        DetectionState.UNKNOWN  // Will be handled by age gate JS
                    }
                    is SteamOwnershipState.FreeToPlay -> {
                        Log.i(TAG_DETECTION, "Steam: F2P (no claim needed) - App $appId")
                        DetectionState.NOT_ELIGIBLE  // No claim button available
                    }
                    is SteamOwnershipState.Demo -> {
                        Log.i(TAG_DETECTION, "Steam: DEMO (skip) - App $appId")
                        DetectionState.NOT_ELIGIBLE  // Demo, not full game
                    }
                    is SteamOwnershipState.RequiresBaseGame -> {
                        Log.i(TAG_DETECTION, "Steam: DLC requires base game - App $appId")
                        DetectionState.NOT_ELIGIBLE  // Can't claim without base game
                    }
                    is SteamOwnershipState.Uncertain -> {
                        Log.w(TAG_DETECTION, "Steam: UNKNOWN - App $appId, reason: ${ownershipState.reason}")
                        DetectionState.UNKNOWN
                    }
                    is SteamOwnershipState.Error -> {
                        Log.e(TAG_DETECTION, "Steam: ERROR - App $appId, error: ${ownershipState.error}")
                        DetectionState.ERROR
                    }
                }
                
                // Emit events
                eventChannel.trySend(BrowserAuthEvent.OwnershipDetected(appId, ownershipState, genericState))
                eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                    store = GameStore.STEAM,
                    itemId = appId,
                    result = DetectionResult(
                        state = genericState,
                        storeSpecificState = ownershipState,
                        rawResult = cleaned.take(500)
                    )
                ))
            } else {
                Log.e(TAG_DETECTION, "Steam: Detection failed for app $appId - result was null")
                eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                    store = GameStore.STEAM,
                    itemId = appId,
                    result = DetectionResult.error("JS result was null or empty")
                ))
            }
        }
    }
    
    // ===== Epic Detection =====
    
    /**
     * Run ownership detection for Epic Games product pages.
     * Uses bounded readiness polling instead of fixed delay.
     */
    private fun runEpicDetection(view: WebView, url: String) {
        // Extract product slug from URL
        val slugMatch = Regex("""/(p|bundles)/([a-z0-9-]+)""").find(url)
        val slug = slugMatch?.groupValues?.get(2) ?: return
        
        Log.d(TAG_DETECTION, "Epic: Detecting state for product $slug (polling for readiness...)")
        
        // Poll for Epic page readiness with bounded timeout
        pollForReadiness(
            view = view,
            readinessCheck = JS_EPIC_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runEpicDetectionJs(view, slug) },
            onTimeout = {
                Log.w(TAG_DETECTION, "Epic: Readiness timeout for product $slug, attempting detection anyway")
                runEpicDetectionJs(view, slug)
            }
        )
    }
    
    private fun runEpicDetectionJs(view: WebView, slug: String) {
        Log.d(TAG_DETECTION, "Epic: Running detection JS for product $slug")
        
        view.evaluateJavascript(EpicStoreAdapter.JS_DETECT_PAGE_STATE) { result ->
            Log.d(TAG_DETECTION, "Epic: Raw JS result: ${result?.take(200) ?: "NULL"}")
            
            if (result != null && result != "null" && result.isNotBlank()) {
                val cleaned = cleanJsResult(result)
                Log.d(TAG_DETECTION, "Epic: Cleaned result: ${cleaned.take(200)}")
                
                val epicAdapter = EpicStoreAdapter()
                val (pageState, buttonText) = epicAdapter.parsePageState(cleaned)
                
                // Convert to generic state
                val genericState = when (pageState) {
                    EpicPageState.OWNED -> {
                        Log.i(TAG_DETECTION, "Epic: OWNED - Product $slug")
                        DetectionState.OWNED
                    }
                    EpicPageState.NEW_ADD, EpicPageState.NEW_GET, EpicPageState.OLD_CTA -> {
                        Log.i(TAG_DETECTION, "Epic: AVAILABLE - Product $slug, flow: $pageState, text: $buttonText")
                        DetectionState.AVAILABLE
                    }
                    EpicPageState.REGION -> {
                        Log.i(TAG_DETECTION, "Epic: REGION_RESTRICTED - Product $slug")
                        DetectionState.REGION_RESTRICTED
                    }
                    EpicPageState.UNKNOWN -> {
                        Log.w(TAG_DETECTION, "Epic: UNKNOWN - Product $slug")
                        DetectionState.UNKNOWN
                    }
                }
                
                // Emit event
                eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                    store = GameStore.EPIC_GAMES,
                    itemId = slug,
                    result = DetectionResult(
                        state = genericState,
                        storeSpecificState = pageState,
                        detectionMethod = pageState.name,
                        rawResult = cleaned.take(500)
                    )
                ))
            } else {
                Log.e(TAG_DETECTION, "Epic: Detection failed for product $slug - result was null")
                eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                    store = GameStore.EPIC_GAMES,
                    itemId = slug,
                    result = DetectionResult.error("JS result was null or empty")
                ))
            }
        }
    }
    
    // ===== GOG Detection =====
    
    /**
     * Run detection for GOG game pages.
     * Uses bounded readiness polling instead of fixed delay.
     */
    private fun runGogDetection(view: WebView, url: String) {
        // Extract game slug from URL
        val slugMatch = Regex("""/game/([a-z0-9_-]+)""", RegexOption.IGNORE_CASE).find(url)
        val slug = slugMatch?.groupValues?.get(1) ?: return
        
        Log.d(TAG_DETECTION, "GOG: Detecting state for game $slug (polling for readiness...)")
        
        // Poll for GOG page readiness with bounded timeout
        pollForReadiness(
            view = view,
            readinessCheck = JS_GOG_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runGogDetectionJs(view, slug) },
            onTimeout = {
                Log.w(TAG_DETECTION, "GOG: Readiness timeout for game $slug, attempting detection anyway")
                runGogDetectionJs(view, slug)
            }
        )
    }
    
    private fun runGogDetectionJs(view: WebView, slug: String) {
        Log.d(TAG_DETECTION, "GOG: Running detection JS for game $slug")
        
        // First check for cookie consent overlay
        view.evaluateJavascript(GogStoreAdapter.JS_COOKIE_CONSENT) { consentResult ->
            val hasConsent = consentResult?.contains("true") == true
            if (hasConsent) {
                Log.i(TAG_DETECTION, "GOG: CONSENT_REQUIRED - Cookie banner detected for $slug")
                eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                    store = GameStore.GOG,
                    itemId = slug,
                    result = DetectionResult.consentRequired()
                ))
                return@evaluateJavascript
            }
            
            // Check for login requirement
            view.evaluateJavascript(GogStoreAdapter.JS_LOGIN_FIELDS) { loginResult ->
                val needsLogin = loginResult?.contains("true") == true
                if (needsLogin) {
                    Log.i(TAG_DETECTION, "GOG: AUTH_REQUIRED - Login form detected for $slug")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.GOG,
                        itemId = slug,
                        result = DetectionResult.authRequired("Login form detected")
                    ))
                    return@evaluateJavascript
                }
                
                // Run giveaway detection
                view.evaluateJavascript(GogStoreAdapter.JS_FIND_GIVEAWAY) { result ->
                    Log.d(TAG_DETECTION, "GOG: Raw JS result: ${result?.take(200) ?: "NULL"}")
                    
                    if (result != null && result != "null" && result.isNotBlank()) {
                        val cleaned = cleanJsResult(result)
                        Log.d(TAG_DETECTION, "GOG: Cleaned result: ${cleaned.take(200)}")
                        
                        val gogAdapter = GogStoreAdapter()
                        val (state, message) = gogAdapter.parseGiveawayResult(cleaned)
                        
                        Log.i(TAG_DETECTION, "GOG: $state - Game $slug, message: $message")
                        
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.GOG,
                            itemId = slug,
                            result = DetectionResult(
                                state = state,
                                detectionMethod = message,
                                rawResult = cleaned.take(500)
                            )
                        ))
                    } else {
                        Log.e(TAG_DETECTION, "GOG: Detection failed for game $slug - result was null")
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.GOG,
                            itemId = slug,
                            result = DetectionResult.error("JS result was null or empty")
                        ))
                    }
                }
            }
        }
    }
    
    // ===== Phase 19 P1: Itch.io Detection =====
    
    /**
     * Run ownership detection for itch.io game pages.
     */
    private fun runItchioDetection(view: WebView, url: String) {
        // Extract developer and game slug from URL
        val match = ItchioStoreAdapter.ITCHIO_GAME_REGEX.find(url)
        val developer = match?.groupValues?.get(1) ?: return
        val gameSlug = match?.groupValues?.get(2) ?: return
        val gameId = "$developer/$gameSlug"
        
        Log.d(TAG_DETECTION, "Itch.io: Detecting state for game $gameId (polling for readiness...)")
        
        pollForReadiness(
            view = view,
            readinessCheck = ItchioStoreAdapter.JS_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runItchioDetectionJs(view, gameId) },
            onTimeout = {
                Log.w(TAG_DETECTION, "Itch.io: Readiness timeout for game $gameId, attempting detection anyway")
                runItchioDetectionJs(view, gameId)
            }
        )
    }
    
    private fun runItchioDetectionJs(view: WebView, gameId: String) {
        Log.d(TAG_DETECTION, "Itch.io: Running detection JS for game $gameId")
        
        view.evaluateJavascript(ItchioStoreAdapter.JS_OWNED) { result ->
            Log.d(TAG_DETECTION, "Itch.io: Raw JS result: ${result?.take(200) ?: "NULL"}")
            
            if (result != null && result != "null" && result.isNotBlank()) {
                val cleaned = cleanJsResult(result)
                Log.d(TAG_DETECTION, "Itch.io: Cleaned result: ${cleaned.take(200)}")
                
                try {
                    val json = org.json.JSONObject(cleaned)
                    val owned = json.optBoolean("owned", false)
                    
                    val state = if (owned) DetectionState.OWNED else DetectionState.AVAILABLE
                    Log.i(TAG_DETECTION, "Itch.io: $state - Game $gameId")
                    
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.ITCH_IO,
                        itemId = gameId,
                        result = DetectionResult(
                            state = state,
                            detectionMethod = "purchase_banner",
                            rawResult = cleaned.take(500)
                        )
                    ))
                } catch (e: Exception) {
                    Log.e(TAG_DETECTION, "Itch.io: JSON parse error for $gameId: ${e.message}")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.ITCH_IO,
                        itemId = gameId,
                        result = DetectionResult.error("JSON parse error: ${e.message}")
                    ))
                }
            } else {
                Log.e(TAG_DETECTION, "Itch.io: Detection failed for game $gameId - result was null")
                eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                    store = GameStore.ITCH_IO,
                    itemId = gameId,
                    result = DetectionResult.error("JS result was null or empty")
                ))
            }
        }
    }
    
    // ===== Phase 19 P1: IndieGala Detection =====
    
    /**
     * Run detection for IndieGala giveaway pages.
     */
    private fun runIndieGalaDetection(view: WebView, url: String) {
        // Extract giveaway/freebie slug from URL
        var match = IndieGalaStoreAdapter.INDIEGALA_GIVEAWAY_REGEX.find(url)
        if (match == null) {
            match = IndieGalaStoreAdapter.INDIEGALA_FREEBIES_REGEX.find(url)
        }
        val slug = match?.groupValues?.get(1) ?: return
        
        Log.d(TAG_DETECTION, "IndieGala: Detecting state for giveaway $slug (polling for readiness...)")
        
        // First dismiss any overlays
        view.evaluateJavascript(IndieGalaStoreAdapter.JS_DISMISS_OVERLAYS) { dismissResult ->
            Log.d(TAG_DETECTION, "IndieGala: Dismiss overlays result: ${dismissResult?.take(100)}")
            
            pollForReadiness(
                view = view,
                readinessCheck = IndieGalaStoreAdapter.JS_READINESS,
                maxAttempts = MAX_POLL_ATTEMPTS,
                pollIntervalMs = POLL_INTERVAL_MS,
                onReady = { runIndieGalaDetectionJs(view, slug) },
                onTimeout = {
                    Log.w(TAG_DETECTION, "IndieGala: Readiness timeout for giveaway $slug, attempting detection anyway")
                    runIndieGalaDetectionJs(view, slug)
                }
            )
        }
    }
    
    private fun runIndieGalaDetectionJs(view: WebView, slug: String) {
        Log.d(TAG_DETECTION, "IndieGala: Running detection JS for giveaway $slug")
        
        // First check login state
        view.evaluateJavascript(IndieGalaStoreAdapter.JS_LOGIN_STATE) { loginResult ->
            Log.d(TAG_DETECTION, "IndieGala: Login state: ${loginResult?.take(100)}")
            
            try {
                val loginJson = org.json.JSONObject(cleanJsResult(loginResult ?: "{}"))
                if (loginJson.optBoolean("hasLoginForm", false) && !loginJson.optBoolean("isLoggedIn", false)) {
                    Log.i(TAG_DETECTION, "IndieGala: AUTH_REQUIRED - Login form detected for $slug")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.INDIEGALA,
                        itemId = slug,
                        result = DetectionResult.authRequired("Login form detected")
                    ))
                    return@evaluateJavascript
                }
            } catch (e: Exception) {
                Log.d(TAG_DETECTION, "IndieGala: Login state parse error: ${e.message}")
            }
            
            // Check captcha
            view.evaluateJavascript(IndieGalaStoreAdapter.JS_CAPTCHA_UNSOLVED) { captchaResult ->
                try {
                    val captchaJson = org.json.JSONObject(cleanJsResult(captchaResult ?: "{}"))
                    if (captchaJson.optBoolean("unsolved", false)) {
                        Log.i(TAG_DETECTION, "IndieGala: CAPTCHA_REQUIRED - Unsolved captcha for $slug")
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.INDIEGALA,
                            itemId = slug,
                            result = DetectionResult(
                                state = DetectionState.CAPTCHA_REQUIRED,
                                detectionMethod = "captcha_unsolved"
                            )
                        ))
                        return@evaluateJavascript
                    }
                } catch (e: Exception) {
                    Log.d(TAG_DETECTION, "IndieGala: Captcha parse error: ${e.message}")
                }
                
                // Check ownership
                view.evaluateJavascript(IndieGalaStoreAdapter.JS_OWNED) { result ->
                    Log.d(TAG_DETECTION, "IndieGala: Raw JS result: ${result?.take(200) ?: "NULL"}")
                    
                    if (result != null && result != "null" && result.isNotBlank()) {
                        val cleaned = cleanJsResult(result)
                        
                        try {
                            val json = org.json.JSONObject(cleaned)
                            val owned = json.optBoolean("owned", false)
                            val hasAddButton = json.optBoolean("hasAddButton", false)
                            
                            val state = when {
                                owned -> DetectionState.OWNED
                                hasAddButton -> DetectionState.AVAILABLE
                                else -> DetectionState.UNKNOWN
                            }
                            
                            Log.i(TAG_DETECTION, "IndieGala: $state - Giveaway $slug (owned=$owned, hasAdd=$hasAddButton)")
                            
                            eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                                store = GameStore.INDIEGALA,
                                itemId = slug,
                                result = DetectionResult(
                                    state = state,
                                    detectionMethod = "add_button_check",
                                    rawResult = cleaned.take(500)
                                )
                            ))
                        } catch (e: Exception) {
                            Log.e(TAG_DETECTION, "IndieGala: JSON parse error for $slug: ${e.message}")
                            eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                                store = GameStore.INDIEGALA,
                                itemId = slug,
                                result = DetectionResult.error("JSON parse error: ${e.message}")
                            ))
                        }
                    } else {
                        Log.e(TAG_DETECTION, "IndieGala: Detection failed for giveaway $slug - result was null")
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.INDIEGALA,
                            itemId = slug,
                            result = DetectionResult.error("JS result was null or empty")
                        ))
                    }
                }
            }
        }
    }
    
    // ===== Phase 19 P1: Fanatical Detection =====
    
    /**
     * Run detection for Fanatical game/bundle pages.
     */
    private fun runFanaticalDetection(view: WebView, url: String) {
        // Extract product type and slug from URL
        val match = FanaticalAdapter.FANATICAL_PRODUCT_REGEX.find(url)
        val productType = match?.groupValues?.get(1) ?: "game"
        val slug = match?.groupValues?.get(2) ?: return
        val productId = "$productType/$slug"
        
        Log.d(TAG_DETECTION, "Fanatical: Detecting state for $productId (polling for readiness...)")
        
        pollForReadiness(
            view = view,
            readinessCheck = FanaticalAdapter.JS_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runFanaticalDetectionJs(view, productId) },
            onTimeout = {
                Log.w(TAG_DETECTION, "Fanatical: Readiness timeout for $productId, attempting detection anyway")
                runFanaticalDetectionJs(view, productId)
            }
        )
    }
    
    private fun runFanaticalDetectionJs(view: WebView, productId: String) {
        Log.d(TAG_DETECTION, "Fanatical: Running detection JS for $productId")
        
        // First check sign-in state
        view.evaluateJavascript(FanaticalAdapter.JS_SIGNED_OUT) { signedOutResult ->
            Log.d(TAG_DETECTION, "Fanatical: Sign-out state: ${signedOutResult?.take(100)}")
            
            try {
                val signedOutJson = org.json.JSONObject(cleanJsResult(signedOutResult ?: "{}"))
                if (signedOutJson.optBoolean("isSignedOut", true)) {
                    Log.i(TAG_DETECTION, "Fanatical: AUTH_REQUIRED - User not signed in for $productId")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.FANATICAL,
                        itemId = productId,
                        result = DetectionResult.authRequired("User not signed in")
                    ))
                    return@evaluateJavascript
                }
            } catch (e: Exception) {
                Log.d(TAG_DETECTION, "Fanatical: Sign-out state parse error: ${e.message}")
            }
            
            // Check product availability
            view.evaluateJavascript(FanaticalAdapter.JS_PRODUCT_STATE) { result ->
                Log.d(TAG_DETECTION, "Fanatical: Raw JS result: ${result?.take(200) ?: "NULL"}")
                
                if (result != null && result != "null" && result.isNotBlank()) {
                    val cleaned = cleanJsResult(result)
                    
                    try {
                        val json = org.json.JSONObject(cleaned)
                        val available = json.optBoolean("available", false)
                        val outOfStock = json.optBoolean("outOfStock", false)
                        val alreadyClaimed = json.optBoolean("alreadyClaimed", false)
                        
                        val state = when {
                            alreadyClaimed -> DetectionState.OWNED
                            outOfStock -> DetectionState.NOT_FOUND
                            available -> DetectionState.AVAILABLE
                            else -> DetectionState.UNKNOWN
                        }
                        
                        Log.i(TAG_DETECTION, "Fanatical: $state - Product $productId " +
                            "(available=$available, outOfStock=$outOfStock, claimed=$alreadyClaimed)")
                        
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.FANATICAL,
                            itemId = productId,
                            result = DetectionResult(
                                state = state,
                                detectionMethod = "product_state_check",
                                rawResult = cleaned.take(500)
                            )
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG_DETECTION, "Fanatical: JSON parse error for $productId: ${e.message}")
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.FANATICAL,
                            itemId = productId,
                            result = DetectionResult.error("JSON parse error: ${e.message}")
                        ))
                    }
                } else {
                    Log.e(TAG_DETECTION, "Fanatical: Detection failed for $productId - result was null")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.FANATICAL,
                        itemId = productId,
                        result = DetectionResult.error("JS result was null or empty")
                    ))
                }
            }
        }
    }
    
    // ===== Phase 19 P2+: Microsoft Store Detection =====
    
    /**
     * Run detection for Microsoft Store (Xbox) game pages.
     */
    private fun runMicrosoftDetection(view: WebView, url: String) {
        // Extract product ID from URL (format: .../game/product-name/XXXXXXXXXXXX)
        val productIdMatch = Regex("""/games/store/[^/]+/([A-Z0-9]{12})""", RegexOption.IGNORE_CASE).find(url)
        val productId = productIdMatch?.groupValues?.get(1) ?: return
        
        Log.d(TAG_DETECTION, "Microsoft: Detecting state for product $productId (polling for readiness...)")
        
        pollForReadiness(
            view = view,
            readinessCheck = MicrosoftStoreAdapter.JS_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runMicrosoftDetectionJs(view, productId) },
            onTimeout = {
                Log.w(TAG_DETECTION, "Microsoft: Readiness timeout for $productId, attempting detection anyway")
                runMicrosoftDetectionJs(view, productId)
            }
        )
    }
    
    private fun runMicrosoftDetectionJs(view: WebView, productId: String) {
        Log.d(TAG_DETECTION, "Microsoft: Running detection JS for product $productId")
        
        // Check login state first
        view.evaluateJavascript(MicrosoftStoreAdapter.JS_LOGIN_STATE) { loginResult ->
            Log.d(TAG_DETECTION, "Microsoft: Login state: ${loginResult?.take(100)}")
            
            try {
                val loginJson = org.json.JSONObject(cleanJsResult(loginResult ?: "{}"))
                if (!loginJson.optBoolean("isLoggedIn", false) && loginJson.optBoolean("hasSignInButton", false)) {
                    Log.i(TAG_DETECTION, "Microsoft: AUTH_REQUIRED - Not signed in for $productId")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.MICROSOFT_STORE,
                        itemId = productId,
                        result = DetectionResult.authRequired("User not signed in")
                    ))
                    return@evaluateJavascript
                }
            } catch (e: Exception) {
                Log.d(TAG_DETECTION, "Microsoft: Login state parse error: ${e.message}")
            }
            
            // Check ownership
            view.evaluateJavascript(MicrosoftStoreAdapter.JS_PAGE_OWNED) { result ->
                Log.d(TAG_DETECTION, "Microsoft: Raw JS result: ${result?.take(200) ?: "NULL"}")
                
                if (result != null && result != "null" && result.isNotBlank()) {
                    val cleaned = cleanJsResult(result)
                    
                    try {
                        val json = org.json.JSONObject(cleaned)
                        val owned = json.optBoolean("owned", false)
                        val hasGetButton = json.optBoolean("hasGetButton", false)
                        val unavailable = json.optBoolean("unavailable", false)
                        
                        val state = when {
                            owned -> DetectionState.OWNED
                            unavailable -> DetectionState.NOT_FOUND
                            hasGetButton -> DetectionState.AVAILABLE
                            else -> DetectionState.UNKNOWN
                        }
                        
                        Log.i(TAG_DETECTION, "Microsoft: $state - Product $productId")
                        
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.MICROSOFT_STORE,
                            itemId = productId,
                            result = DetectionResult(
                                state = state,
                                detectionMethod = "page_state_check",
                                rawResult = cleaned.take(500)
                            )
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG_DETECTION, "Microsoft: JSON parse error for $productId: ${e.message}")
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.MICROSOFT_STORE,
                            itemId = productId,
                            result = DetectionResult.error("JSON parse error: ${e.message}")
                        ))
                    }
                } else {
                    Log.e(TAG_DETECTION, "Microsoft: Detection failed for $productId - result was null")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.MICROSOFT_STORE,
                        itemId = productId,
                        result = DetectionResult.error("JS result was null or empty")
                    ))
                }
            }
        }
    }
    
    // ===== Phase 19 P2+: Ubisoft Detection =====
    
    /**
     * Run detection for Ubisoft free game/registration pages.
     */
    private fun runUbisoftDetection(view: WebView, url: String) {
        // Extract game slug from URL
        val slugMatch = UbisoftAdapter.CLAIM_URL_REGEX.find(url)
        val slug = slugMatch?.groupValues?.get(1) ?: url.substringAfterLast("/").take(50)
        
        Log.d(TAG_DETECTION, "Ubisoft: Detecting state for $slug (polling for readiness...)")
        
        pollForReadiness(
            view = view,
            readinessCheck = UbisoftAdapter.JS_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runUbisoftDetectionJs(view, slug) },
            onTimeout = {
                Log.w(TAG_DETECTION, "Ubisoft: Readiness timeout for $slug, attempting detection anyway")
                runUbisoftDetectionJs(view, slug)
            }
        )
    }
    
    private fun runUbisoftDetectionJs(view: WebView, slug: String) {
        Log.d(TAG_DETECTION, "Ubisoft: Running detection JS for $slug")
        
        // Check login state
        view.evaluateJavascript(UbisoftAdapter.JS_LOGIN_STATE) { result ->
            Log.d(TAG_DETECTION, "Ubisoft: Login state: ${result?.take(200) ?: "NULL"}")
            
            if (result != null && result != "null" && result.isNotBlank()) {
                val cleaned = cleanJsResult(result)
                
                try {
                    val json = org.json.JSONObject(cleaned)
                    val isLoggedIn = json.optBoolean("isLoggedIn", false)
                    val hasOverlay = json.optBoolean("hasOverlay", false)
                    val hasWelcomeBack = json.optBoolean("hasWelcomeBack", false)
                    
                    val state = when {
                        !isLoggedIn && hasOverlay -> DetectionState.AUTH_REQUIRED
                        hasWelcomeBack -> DetectionState.OWNED  // Already registered/claimed
                        isLoggedIn -> DetectionState.AVAILABLE
                        else -> DetectionState.UNKNOWN
                    }
                    
                    Log.i(TAG_DETECTION, "Ubisoft: $state - Game $slug (loggedIn=$isLoggedIn, overlay=$hasOverlay)")
                    
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.UBISOFT,
                        itemId = slug,
                        result = DetectionResult(
                            state = state,
                            detectionMethod = "login_state_check",
                            rawResult = cleaned.take(500)
                        )
                    ))
                } catch (e: Exception) {
                    Log.e(TAG_DETECTION, "Ubisoft: JSON parse error for $slug: ${e.message}")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.UBISOFT,
                        itemId = slug,
                        result = DetectionResult.error("JSON parse error: ${e.message}")
                    ))
                }
            } else {
                Log.e(TAG_DETECTION, "Ubisoft: Detection failed for $slug - result was null")
                eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                    store = GameStore.UBISOFT,
                    itemId = slug,
                    result = DetectionResult.error("JS result was null or empty")
                ))
            }
        }
    }
    
    // ===== Phase 19 P2+: Prime Gaming Detection =====
    
    /**
     * Run detection for Prime Gaming claims page.
     */
    private fun runPrimeGamingDetection(view: WebView, url: String) {
        Log.d(TAG_DETECTION, "PrimeGaming: Detecting state (polling for readiness...)")
        
        pollForReadiness(
            view = view,
            readinessCheck = PrimeGamingAdapter.JS_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runPrimeGamingDetectionJs(view) },
            onTimeout = {
                Log.w(TAG_DETECTION, "PrimeGaming: Readiness timeout, attempting detection anyway")
                runPrimeGamingDetectionJs(view)
            }
        )
    }
    
    private fun runPrimeGamingDetectionJs(view: WebView) {
        Log.d(TAG_DETECTION, "PrimeGaming: Running detection JS")
        
        // Check login state
        view.evaluateJavascript(PrimeGamingAdapter.JS_LOGIN_STATE) { result ->
            Log.d(TAG_DETECTION, "PrimeGaming: Login state: ${result?.take(200) ?: "NULL"}")
            
            if (result != null && result != "null" && result.isNotBlank()) {
                val cleaned = cleanJsResult(result)
                
                try {
                    val json = org.json.JSONObject(cleaned)
                    val isLoggedIn = json.optBoolean("isLoggedIn", false)
                    val hasMfaPrompt = json.optBoolean("hasMfaPrompt", false)
                    val isPrimeSubscriber = json.optBoolean("isPrimeSubscriber", false)
                    
                    val state = when {
                        hasMfaPrompt -> DetectionState.MFA_REQUIRED
                        !isLoggedIn -> DetectionState.AUTH_REQUIRED
                        !isPrimeSubscriber -> DetectionState.NOT_ELIGIBLE
                        else -> DetectionState.AVAILABLE
                    }
                    
                    Log.i(TAG_DETECTION, "PrimeGaming: $state (loggedIn=$isLoggedIn, mfa=$hasMfaPrompt, prime=$isPrimeSubscriber)")
                    
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.AMAZON_PRIME,
                        itemId = "claims_page",
                        result = DetectionResult(
                            state = state,
                            detectionMethod = "login_state_check",
                            rawResult = cleaned.take(500)
                        )
                    ))
                } catch (e: Exception) {
                    Log.e(TAG_DETECTION, "PrimeGaming: JSON parse error: ${e.message}")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.AMAZON_PRIME,
                        itemId = "claims_page",
                        result = DetectionResult.error("JSON parse error: ${e.message}")
                    ))
                }
            } else {
                Log.e(TAG_DETECTION, "PrimeGaming: Detection failed - result was null")
                eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                    store = GameStore.AMAZON_PRIME,
                    itemId = "claims_page",
                    result = DetectionResult.error("JS result was null or empty")
                ))
            }
        }
    }
    
    // ===== Phase 19 P2+: Alienware Arena Detection =====
    
    /**
     * Run detection for Alienware Arena giveaway pages.
     */
    private fun runAlienwareArenaDetection(view: WebView, url: String) {
        // Extract giveaway ID from URL
        val idMatch = AlienwareArenaAdapter.ALIENWARE_UCF_ID_REGEX.find(url)
        val giveawayId = idMatch?.groupValues?.get(1) ?: url.substringAfterLast("/").take(20)
        
        Log.d(TAG_DETECTION, "AlienwareArena: Detecting state for giveaway $giveawayId (polling for readiness...)")
        
        pollForReadiness(
            view = view,
            readinessCheck = AlienwareArenaAdapter.JS_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runAlienwareArenaDetectionJs(view, giveawayId) },
            onTimeout = {
                Log.w(TAG_DETECTION, "AlienwareArena: Readiness timeout for $giveawayId, attempting detection anyway")
                runAlienwareArenaDetectionJs(view, giveawayId)
            }
        )
    }
    
    private fun runAlienwareArenaDetectionJs(view: WebView, giveawayId: String) {
        Log.d(TAG_DETECTION, "AlienwareArena: Running detection JS for giveaway $giveawayId")
        
        // Check login state first
        view.evaluateJavascript(AlienwareArenaAdapter.JS_LOGIN_STATE) { loginResult ->
            Log.d(TAG_DETECTION, "AlienwareArena: Login state: ${loginResult?.take(100)}")
            
            try {
                val loginJson = org.json.JSONObject(cleanJsResult(loginResult ?: "{}"))
                if (!loginJson.optBoolean("isLoggedIn", false) && loginJson.optBoolean("hasLoginButton", false)) {
                    Log.i(TAG_DETECTION, "AlienwareArena: AUTH_REQUIRED - Not logged in for $giveawayId")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.ALIENWARE_ARENA,
                        itemId = giveawayId,
                        result = DetectionResult.authRequired("User not logged in")
                    ))
                    return@evaluateJavascript
                }
            } catch (e: Exception) {
                Log.d(TAG_DETECTION, "AlienwareArena: Login state parse error: ${e.message}")
            }
            
            // Check giveaway state
            view.evaluateJavascript(AlienwareArenaAdapter.JS_GIVEAWAY_STATE) { result ->
                Log.d(TAG_DETECTION, "AlienwareArena: Raw JS result: ${result?.take(200) ?: "NULL"}")
                
                if (result != null && result != "null" && result.isNotBlank()) {
                    val cleaned = cleanJsResult(result)
                    
                    try {
                        val json = org.json.JSONObject(cleaned)
                        val claimed = json.optBoolean("claimed", false)
                        val keysAvailable = json.optBoolean("keysAvailable", false)
                        val ended = json.optBoolean("ended", false)
                        val levelRequired = json.optInt("levelRequired", 0)
                        val userLevel = json.optInt("userLevel", 0)
                        
                        val state = when {
                            claimed -> DetectionState.OWNED
                            ended -> DetectionState.EXPIRED
                            levelRequired > userLevel -> DetectionState.NOT_ELIGIBLE
                            keysAvailable -> DetectionState.AVAILABLE
                            !keysAvailable -> DetectionState.NOT_FOUND  // Out of keys
                            else -> DetectionState.UNKNOWN
                        }
                        
                        Log.i(TAG_DETECTION, "AlienwareArena: $state - Giveaway $giveawayId " +
                            "(claimed=$claimed, keys=$keysAvailable, level=$userLevel/$levelRequired)")
                        
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.ALIENWARE_ARENA,
                            itemId = giveawayId,
                            result = DetectionResult(
                                state = state,
                                detectionMethod = "giveaway_state_check",
                                rawResult = cleaned.take(500)
                            )
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG_DETECTION, "AlienwareArena: JSON parse error for $giveawayId: ${e.message}")
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.ALIENWARE_ARENA,
                            itemId = giveawayId,
                            result = DetectionResult.error("JSON parse error: ${e.message}")
                        ))
                    }
                } else {
                    Log.e(TAG_DETECTION, "AlienwareArena: Detection failed for $giveawayId - result was null")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.ALIENWARE_ARENA,
                        itemId = giveawayId,
                        result = DetectionResult.error("JS result was null or empty")
                    ))
                }
            }
        }
    }
    
    // ===== Phase 19 P2+: Fab Detection =====
    
    /**
     * Run detection for Fab (Epic's asset marketplace) listing pages.
     */
    private fun runFabDetection(view: WebView, url: String) {
        // Extract listing ID from URL
        val listingMatch = FabAdapter.FAB_LISTING_REGEX.find(url)
        val listingId = listingMatch?.groupValues?.get(1) ?: return
        
        Log.d(TAG_DETECTION, "Fab: Detecting state for listing $listingId (polling for readiness...)")
        
        pollForReadiness(
            view = view,
            readinessCheck = FabAdapter.JS_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runFabDetectionJs(view, listingId) },
            onTimeout = {
                Log.w(TAG_DETECTION, "Fab: Readiness timeout for $listingId, attempting detection anyway")
                runFabDetectionJs(view, listingId)
            }
        )
    }
    
    private fun runFabDetectionJs(view: WebView, listingId: String) {
        Log.d(TAG_DETECTION, "Fab: Running detection JS for listing $listingId")
        
        // Check login state first
        view.evaluateJavascript(FabAdapter.JS_LOGIN_STATE) { loginResult ->
            Log.d(TAG_DETECTION, "Fab: Login state: ${loginResult?.take(100)}")
            
            try {
                val loginJson = org.json.JSONObject(cleanJsResult(loginResult ?: "{}"))
                if (!loginJson.optBoolean("isLoggedIn", false) && loginJson.optBoolean("hasSignInButton", false)) {
                    Log.i(TAG_DETECTION, "Fab: AUTH_REQUIRED - Not signed in for $listingId")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.FAB,
                        itemId = listingId,
                        result = DetectionResult.authRequired("User not signed in")
                    ))
                    return@evaluateJavascript
                }
            } catch (e: Exception) {
                Log.d(TAG_DETECTION, "Fab: Login state parse error: ${e.message}")
            }
            
            // Check listing state
            view.evaluateJavascript(FabAdapter.JS_LISTING_STATE) { result ->
                Log.d(TAG_DETECTION, "Fab: Raw JS result: ${result?.take(200) ?: "NULL"}")
                
                if (result != null && result != "null" && result.isNotBlank()) {
                    val cleaned = cleanJsResult(result)
                    
                    try {
                        val json = org.json.JSONObject(cleaned)
                        val owned = json.optBoolean("owned", false)
                        val isFree = json.optBoolean("isFree", false)
                        val hasAddButton = json.optBoolean("hasAddButton", false)
                        
                        val state = when {
                            owned -> DetectionState.OWNED
                            isFree && hasAddButton -> DetectionState.AVAILABLE
                            !isFree -> DetectionState.NOT_FREE
                            else -> DetectionState.UNKNOWN
                        }
                        
                        Log.i(TAG_DETECTION, "Fab: $state - Listing $listingId (owned=$owned, free=$isFree)")
                        
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.FAB,
                            itemId = listingId,
                            result = DetectionResult(
                                state = state,
                                detectionMethod = "listing_state_check",
                                rawResult = cleaned.take(500)
                            )
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG_DETECTION, "Fab: JSON parse error for $listingId: ${e.message}")
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.FAB,
                            itemId = listingId,
                            result = DetectionResult.error("JSON parse error: ${e.message}")
                        ))
                    }
                } else {
                    Log.e(TAG_DETECTION, "Fab: Detection failed for $listingId - result was null")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.FAB,
                        itemId = listingId,
                        result = DetectionResult.error("JS result was null or empty")
                    ))
                }
            }
        }
    }
    
    // ===== Phase 19 P2+: Unity Asset Store Detection =====
    
    /**
     * Run detection for Unity Asset Store package pages.
     */
    private fun runUnityAssetStoreDetection(view: WebView, url: String) {
        // Extract package ID from URL (trailing number)
        val packageMatch = Regex("""/packages/[^/]+(?:/[^/]+)*/[a-z0-9-]+-(\d+)""", RegexOption.IGNORE_CASE).find(url)
        val packageId = packageMatch?.groupValues?.get(1) ?: return
        
        Log.d(TAG_DETECTION, "Unity: Detecting state for package $packageId (polling for readiness...)")
        
        pollForReadiness(
            view = view,
            readinessCheck = UnityAssetStoreAdapter.JS_READINESS,
            maxAttempts = MAX_POLL_ATTEMPTS,
            pollIntervalMs = POLL_INTERVAL_MS,
            onReady = { runUnityAssetStoreDetectionJs(view, packageId) },
            onTimeout = {
                Log.w(TAG_DETECTION, "Unity: Readiness timeout for $packageId, attempting detection anyway")
                runUnityAssetStoreDetectionJs(view, packageId)
            }
        )
    }
    
    private fun runUnityAssetStoreDetectionJs(view: WebView, packageId: String) {
        Log.d(TAG_DETECTION, "Unity: Running detection JS for package $packageId")
        
        // Check login state first
        view.evaluateJavascript(UnityAssetStoreAdapter.JS_LOGIN_STATE) { loginResult ->
            Log.d(TAG_DETECTION, "Unity: Login state: ${loginResult?.take(100)}")
            
            try {
                val loginJson = org.json.JSONObject(cleanJsResult(loginResult ?: "{}"))
                if (loginJson.optBoolean("onLoginPage", false)) {
                    Log.i(TAG_DETECTION, "Unity: AUTH_REQUIRED - On login page for $packageId")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.UNITY_ASSET_STORE,
                        itemId = packageId,
                        result = DetectionResult.authRequired("Redirected to login page")
                    ))
                    return@evaluateJavascript
                }
                
                if (!loginJson.optBoolean("isLoggedIn", false) && loginJson.optBoolean("hasLoginButton", false)) {
                    Log.i(TAG_DETECTION, "Unity: AUTH_REQUIRED - Not logged in for $packageId")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.UNITY_ASSET_STORE,
                        itemId = packageId,
                        result = DetectionResult.authRequired("User not logged in")
                    ))
                    return@evaluateJavascript
                }
            } catch (e: Exception) {
                Log.d(TAG_DETECTION, "Unity: Login state parse error: ${e.message}")
            }
            
            // Check sale state (for weekly free asset)
            view.evaluateJavascript(UnityAssetStoreAdapter.JS_SALE_STATE) { result ->
                Log.d(TAG_DETECTION, "Unity: Raw JS result: ${result?.take(200) ?: "NULL"}")
                
                if (result != null && result != "null" && result.isNotBlank()) {
                    val cleaned = cleanJsResult(result)
                    
                    try {
                        val json = org.json.JSONObject(cleaned)
                        val hasCoupon = json.optBoolean("hasCoupon", false)
                        val giftLinks = json.optJSONArray("giftLinks")
                        val hasGift = giftLinks != null && giftLinks.length() > 0
                        
                        // For Unity, we can't definitively know ownership without API
                        // Detection focuses on whether free asset is available
                        val state = when {
                            hasGift && hasCoupon -> DetectionState.AVAILABLE
                            hasGift -> DetectionState.AVAILABLE  // Gift link found
                            else -> DetectionState.UNKNOWN  // Can't determine from page
                        }
                        
                        Log.i(TAG_DETECTION, "Unity: $state - Package $packageId (hasGift=$hasGift, coupon=$hasCoupon)")
                        
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.UNITY_ASSET_STORE,
                            itemId = packageId,
                            result = DetectionResult(
                                state = state,
                                detectionMethod = "sale_state_check",
                                rawResult = cleaned.take(500)
                            )
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG_DETECTION, "Unity: JSON parse error for $packageId: ${e.message}")
                        eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                            store = GameStore.UNITY_ASSET_STORE,
                            itemId = packageId,
                            result = DetectionResult.error("JSON parse error: ${e.message}")
                        ))
                    }
                } else {
                    Log.e(TAG_DETECTION, "Unity: Detection failed for $packageId - result was null")
                    eventChannel.trySend(BrowserAuthEvent.DetectionCompleted(
                        store = GameStore.UNITY_ASSET_STORE,
                        itemId = packageId,
                        result = DetectionResult.error("JS result was null or empty")
                    ))
                }
            }
        }
    }
    
    // ===== Helper Methods =====
    
    /**
     * Clean JavaScript result from WebView (handles escaping).
     */
    private fun cleanJsResult(result: String): String {
        var cleaned = result
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        return cleaned.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
    }
    
    /**
     * Poll for page readiness with bounded timeout.
     * 
     * SECURITY: Readiness check is READ-ONLY - only queries DOM state.
     * 
     * @param view The WebView to poll
     * @param readinessCheck JS that returns "true" when page is ready
     * @param maxAttempts Maximum poll attempts before timeout
     * @param pollIntervalMs Interval between polls in milliseconds
     * @param onReady Called when page is ready
     * @param onTimeout Called when max attempts reached
     * @param attempt Current attempt number (internal)
     */
    private fun pollForReadiness(
        view: WebView,
        readinessCheck: String,
        maxAttempts: Int,
        pollIntervalMs: Long,
        onReady: () -> Unit,
        onTimeout: () -> Unit,
        attempt: Int = 0
    ) {
        if (attempt >= maxAttempts) {
            onTimeout()
            return
        }
        
        // First poll: add minimum delay for initial page load
        val initialDelay = if (attempt == 0) MIN_INITIAL_DELAY_MS else 0L
        
        view.postDelayed({
            view.evaluateJavascript(readinessCheck) { result ->
                val isReady = result?.contains("true") == true
                
                if (isReady) {
                    Log.d(TAG_DETECTION, "Page ready after ${attempt + 1} poll(s)")
                    onReady()
                } else {
                    // Schedule next poll
                    view.postDelayed({
                        pollForReadiness(
                            view, readinessCheck, maxAttempts, pollIntervalMs,
                            onReady, onTimeout, attempt + 1
                        )
                    }, pollIntervalMs)
                }
            }
        }, initialDelay)
    }
    
    companion object {
        private const val TAG_DETECTION = "StoreDetection"
        
        /** Minimum delay before first poll to allow basic page load */
        private const val MIN_INITIAL_DELAY_MS = 500L
        
        /** Interval between readiness polls */
        private const val POLL_INTERVAL_MS = 500L
        
        /** Maximum poll attempts before timeout (500ms * 10 = 5s max) */
        private const val MAX_POLL_ATTEMPTS = 10
        
        /** Legacy fixed delay (keeping for reference, no longer used) */
        @Suppress("unused")
        private const val DETECTION_DELAY_MS = 3000L
        
        // ===== Readiness Check Scripts =====
        // These scripts check if the page has loaded enough for detection
        
        /** Steam readiness: Check if React has rendered the game content */
        private const val JS_STEAM_READINESS = """
            (function() {
                // Check if main game content container exists
                var container = document.querySelector('#game_area_purchase, .game_area_purchase_game');
                var title = document.querySelector('.apphub_AppName, .game_name');
                return (container !== null || title !== null) ? 'true' : 'false';
            })()
        """
        
        /** Epic readiness: Check if React has rendered the product page */
        private const val JS_EPIC_READINESS = """
            (function() {
                // Check for purchase/ownership buttons or price container
                var buttons = document.querySelectorAll('[data-testid*="purchase"], [data-testid*="owned"], button');
                var priceContainer = document.querySelector('[data-component="PDPSidebarLayout"], .css-1h1c3y6');
                // Need some meaningful content
                return (buttons.length > 3 || priceContainer !== null) ? 'true' : 'false';
            })()
        """
        
        /** GOG readiness: Check if page has loaded product info */
        private const val JS_GOG_READINESS = """
            (function() {
                // Check for product container or price/cart area
                var product = document.querySelector('.product-title, .productcard-basics');
                var actions = document.querySelector('.product-actions, .product-actions__cart');
                return (product !== null || actions !== null) ? 'true' : 'false';
            })()
        """
        
        /**
         * Create a SecureBrowserWrapper for a store.
         */
        fun forStore(context: Context, store: GameStore): SecureBrowserWrapper {
            return SecureBrowserWrapper(context, StoreAuthConfig.forStore(store))
        }
    }
}
