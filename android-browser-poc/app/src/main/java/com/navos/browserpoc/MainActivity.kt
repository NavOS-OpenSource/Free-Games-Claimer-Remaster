package com.navos.browserpoc

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.navos.browserpoc.ui.theme.BrowserPoCTheme

/**
 * NavOS GameClaimer - Browser Proof of Concept (Phase 2)
 * 
 * Purpose: Validate WebView capabilities for store page rendering,
 * cookie persistence, and basic page interaction on Android.
 * 
 * SECURITY NOTES:
 * - JavaScript is enabled only for store page functionality
 * - WebView debugging is disabled in release builds
 * - Navigation is restricted to HTTPS only
 * - No credentials are stored or transmitted
 * - Cookie persistence is enabled for session testing
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable cookie persistence across app restarts
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        
        setContent {
            BrowserPoCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrowserPoCScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPoCScreen() {
    // Default to Epic Games Store free games page (public, no auth required)
    var urlInput by remember { mutableStateOf("https://store.epicgames.com/en-US/free-games") }
    var currentUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("Browser PoC") }
    var loadError by remember { mutableStateOf<String?>(null) }
    var cookieInfo by remember { mutableStateOf("No cookies yet") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    
    // Handle back navigation
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = { Text(pageTitle, maxLines = 1) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        
        // URL Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                label = { Text("URL") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (isValidHttpsUrl(urlInput)) {
                        loadError = null
                        webView?.loadUrl(urlInput)
                    } else {
                        loadError = "Only HTTPS URLs allowed"
                    }
                }
            ) {
                Text("Go")
            }
        }
        
        // Error display
        loadError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        
        // Loading indicator
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        
        // Cookie info display (for testing persistence)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = "Cookie test: $cookieInfo",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // Quick action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    urlInput = "https://store.epicgames.com/en-US/free-games"
                    webView?.loadUrl(urlInput)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Epic Free", maxLines = 1)
            }
            Button(
                onClick = {
                    // Check cookies for current domain
                    val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: "none"
                    cookieInfo = if (cookies.length > 50) "${cookies.take(50)}..." else cookies
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Check Cookies", maxLines = 1)
            }
            Button(
                onClick = { webView?.reload() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reload", maxLines = 1)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // WebView
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            // Security: Only allow HTTPS navigation
                            return if (!isValidHttpsUrl(url)) {
                                loadError = "Blocked non-HTTPS URL: $url"
                                true
                            } else {
                                loadError = null
                                false
                            }
                        }
                        
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            currentUrl = url ?: ""
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            currentUrl = url ?: ""
                        }
                        
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            loadError = "Error $errorCode: $description"
                            isLoading = false
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            pageTitle = title ?: "Browser PoC"
                        }
                        
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            isLoading = newProgress < 100
                        }
                    }
                    
                    // WebView settings - security conscious
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.apply {
                        javaScriptEnabled = true // Required for store pages
                        domStorageEnabled = true // For modern web apps
                        databaseEnabled = true // For local storage
                        
                        // Security settings
                        allowFileAccess = false
                        allowContentAccess = false
                        
                        // User agent - use default Android WebView UA
                        // Do not spoof to desktop Chrome
                        
                        // Cookie settings handled by CookieManager
                    }
                    
                    // Security: No JavaScript interface exposed
                    // (We don't need JS-to-native communication for PoC)
                    
                    webView = this
                    
                    // Load initial page
                    loadUrl(urlInput)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        
        // Current URL display
        Text(
            text = "Current: $currentUrl",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Validate that URL is HTTPS only (security requirement)
 */
fun isValidHttpsUrl(url: String): Boolean {
    return try {
        val trimmed = url.trim()
        trimmed.startsWith("https://", ignoreCase = true) &&
        !trimmed.contains("localhost", ignoreCase = true) &&
        !trimmed.contains("127.0.0.1") &&
        !trimmed.contains("192.168.") &&
        !trimmed.contains("10.0.")
    } catch (e: Exception) {
        false
    }
}
