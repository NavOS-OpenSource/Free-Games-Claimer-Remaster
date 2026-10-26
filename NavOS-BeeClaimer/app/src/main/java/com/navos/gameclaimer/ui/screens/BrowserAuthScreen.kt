package com.navos.gameclaimer.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.AndroidView
import com.navos.gameclaimer.browser.SecureBrowserWrapper
import com.navos.gameclaimer.domain.model.BrowserAuthEvent
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.MfaType
import kotlinx.coroutines.flow.collectLatest

/**
 * Phase 5: Browser Authentication Screen
 * 
 * Provides in-app browser for store authentication.
 * Users manually complete login, MFA, CAPTCHA challenges.
 * 
 * SECURITY FEATURES:
 * - HTTPS-only navigation
 * - SSL errors never bypassed
 * - Domain restriction to store's allowed domains
 * - No credential logging
 * - Clear visual indicators for security state
 * 
 * USER EXPERIENCE:
 * - Shows loading progress
 * - Indicates when MFA is required
 * - Provides back navigation
 * - Shows current URL (sanitized)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserAuthScreen(
    store: GameStore,
    onAuthComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    
    // Create secure browser wrapper
    val browserWrapper = remember { SecureBrowserWrapper.forStore(context, store) }
    
    // UI State
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("Login to ${store.displayName}") }
    var showMfaIndicator by remember { mutableStateOf(false) }
    var mfaType by remember { mutableStateOf<MfaType?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var authCompleted by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var showUrlBar by remember { mutableStateOf(false) }
    
    // Snackbar for warnings
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle browser events
    LaunchedEffect(browserWrapper) {
        browserWrapper.events.collectLatest { event ->
            when (event) {
                is BrowserAuthEvent.PageStarted -> {
                    isLoading = true
                    errorMessage = null
                }
                
                is BrowserAuthEvent.PageFinished -> {
                    isLoading = false
                    pageTitle = event.title ?: "Login to ${store.displayName}"
                }
                
                is BrowserAuthEvent.MfaChallengeDetected -> {
                    showMfaIndicator = true
                    mfaType = event.mfaType
                }
                
                is BrowserAuthEvent.AuthenticationComplete -> {
                    authCompleted = true
                    // Give user a moment to see the success state
                    kotlinx.coroutines.delay(1000)
                    onAuthComplete()
                }
                
                is BrowserAuthEvent.NavigationBlocked -> {
                    errorMessage = "Navigation blocked: ${event.reason}"
                }
                
                is BrowserAuthEvent.NavigationWarning -> {
                    // Show warning toast but allow navigation
                    warningMessage = "Navigating to: ${event.domain}"
                }
                
                is BrowserAuthEvent.SslError -> {
                    errorMessage = "Security error: Connection not secure"
                }
                
                is BrowserAuthEvent.LoadError -> {
                    isLoading = false
                    errorMessage = "Load error: ${event.description ?: "Unknown error"}"
                }
                
                is BrowserAuthEvent.LoginFormDetected -> {
                    showMfaIndicator = false
                    mfaType = null
                }
                
                is BrowserAuthEvent.OwnershipDetected -> {
                    // Phase 17: Log ownership detection (already logged in SecureBrowserWrapper)
                    // Future: Could display in UI if needed
                }
                
                is BrowserAuthEvent.DetectionCompleted -> {
                    // Phase 18D: Generic detection result (already logged in SecureBrowserWrapper)
                    // Future: Could display detection status in UI
                }
            }
        }
    }
    
    // Show warning snackbar
    LaunchedEffect(warningMessage) {
        warningMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            warningMessage = null
        }
    }
    
    // Handle back button
    BackHandler {
        if (!browserWrapper.goBack()) {
            onCancel()
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(browserWrapper) {
        onDispose {
            browserWrapper.destroy()
        }
    }
    
    // Determine neutral colors based on dark/light mode
    val isDarkMode = !MaterialTheme.colorScheme.background.luminance().let { it > 0.5f }
    val browserToolbarColor = if (isDarkMode) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
    val browserToolbarContentColor = if (isDarkMode) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(), // Handle status bar for edge-to-edge
                title = {
                    Row(
                        modifier = Modifier.clickable {
                            showUrlBar = !showUrlBar
                            if (showUrlBar) {
                                urlInput = browserWrapper.currentUrl
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showUrlBar) "Enter URL" else pageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!browserWrapper.goBack()) {
                            onCancel()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Show URL domain
                    Text(
                        text = browserWrapper.currentUrl.take(30).ifEmpty { "" },
                        style = MaterialTheme.typography.bodySmall,
                        color = browserToolbarContentColor.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Open in external browser (for sites with WebView issues)
                    IconButton(onClick = {
                        val customTabsIntent = CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                        customTabsIntent.launchUrl(context, Uri.parse(browserWrapper.currentUrl.ifEmpty { browserWrapper.config.loginUrl }))
                    }) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open in Browser"
                        )
                    }
                    // Reload button
                    IconButton(onClick = { browserWrapper.reload() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = browserToolbarColor,
                    titleContentColor = browserToolbarContentColor,
                    navigationIconContentColor = browserToolbarContentColor,
                    actionIconContentColor = browserToolbarContentColor
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading indicator
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            // URL input bar
            if (showUrlBar) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Enter URL") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val url = if (urlInput.startsWith("http://") || urlInput.startsWith("https://")) {
                                        urlInput
                                    } else {
                                        "https://$urlInput"
                                    }
                                    browserWrapper.loadUrl(url)
                                    showUrlBar = false
                                }
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val url = if (urlInput.startsWith("http://") || urlInput.startsWith("https://")) {
                                    urlInput
                                } else {
                                    "https://$urlInput"
                                }
                                browserWrapper.loadUrl(url)
                                showUrlBar = false
                            }
                        ) {
                            Text("Go")
                        }
                    }
                }
            }
            
            // MFA indicator
            if (showMfaIndicator && mfaType != null) {
                MfaIndicatorCard(mfaType = mfaType!!)
            }
            
            // Error message
            errorMessage?.let { error ->
                ErrorCard(message = error, onDismiss = { errorMessage = null })
            }
            
            // Auth complete indicator
            if (authCompleted) {
                AuthCompleteCard()
            }
            
            // WebView
            AndroidView(
                factory = { browserWrapper.createWebView() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                update = { webView ->
                    // Load login page on first display
                    if (webView.url.isNullOrEmpty()) {
                        browserWrapper.loadLoginPage()
                    }
                }
            )
            
            // Security footer
            SecurityFooter()
        }
    }
}

@Composable
private fun MfaIndicatorCard(mfaType: MfaType) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = getMfaTitle(mfaType),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = getMfaDescription(mfaType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun AuthCompleteCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Authentication successful! Returning...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SecurityFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Secure connection • Your credentials are only sent to the store",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getMfaTitle(mfaType: MfaType): String = when (mfaType) {
    MfaType.TOTP -> "Authenticator Code Required"
    MfaType.STEAM_GUARD_EMAIL -> "Steam Guard Email Code"
    MfaType.STEAM_GUARD_MOBILE -> "Steam Guard Mobile"
    MfaType.SMS -> "SMS Verification"
    MfaType.EMAIL_CODE -> "Email Verification"
    MfaType.CAPTCHA -> "CAPTCHA Challenge"
    MfaType.DEVICE_VERIFICATION -> "Device Verification"
    MfaType.OTHER -> "Additional Verification Required"
}

private fun getMfaDescription(mfaType: MfaType): String = when (mfaType) {
    MfaType.TOTP -> "Enter the code from your authenticator app"
    MfaType.STEAM_GUARD_EMAIL -> "Check your email for the Steam Guard code"
    MfaType.STEAM_GUARD_MOBILE -> "Approve the login from your Steam mobile app"
    MfaType.SMS -> "Enter the code sent to your phone"
    MfaType.EMAIL_CODE -> "Enter the code sent to your email"
    MfaType.CAPTCHA -> "Complete the CAPTCHA to continue"
    MfaType.DEVICE_VERIFICATION -> "Verify this device to continue"
    MfaType.OTHER -> "Complete the verification step to continue"
}
