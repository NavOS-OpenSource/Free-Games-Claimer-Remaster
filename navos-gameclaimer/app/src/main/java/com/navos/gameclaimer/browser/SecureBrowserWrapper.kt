package com.navos.gameclaimer.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.navos.gameclaimer.domain.model.BrowserAuthEvent
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
    private val config: StoreAuthConfig
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
    
    // ===== URL Validation =====
    
    data class UrlValidation(
        val isValid: Boolean,
        val reason: String = ""
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
            return UrlValidation(false, "Domain not allowed for ${config.store.displayName}: $host")
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
    
    companion object {
        /**
         * Create a SecureBrowserWrapper for a store.
         */
        fun forStore(context: Context, store: GameStore): SecureBrowserWrapper {
            return SecureBrowserWrapper(context, StoreAuthConfig.forStore(store))
        }
    }
}
