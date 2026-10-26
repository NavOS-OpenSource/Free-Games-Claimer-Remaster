package com.navos.gameclaimer.ui.screens

import android.webkit.CookieManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 22: Store detail screen with brutalist UI redesign.
 * 
 * Shows store-specific settings and auth status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreDetailScreen(
    storeId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val store = remember(storeId) {
        GameStore.entries.find { it.name == storeId }
    }
    
    val scope = rememberCoroutineScope()
    
    // Auth state
    var isAuthenticated by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf<String?>(null) }
    var isLoadingName by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    
    // Fetch Steam account name from profile page
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
            // Look for: <span class="persona online">AccountName</span> or id="account_pulldown"
            val patterns = listOf(
                // Pattern 1: account_pulldown element
                Regex("""id="account_pulldown"[^>]*>([^<]+)</"""),
                // Pattern 2: persona name
                Regex("""class="persona[^"]*"[^>]*>([^<]+)</"""),
                // Pattern 3: Account dropdown text
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
    
    // Check Steam auth status
    fun checkSteamAuth() {
        val cookies = CookieManager.getInstance().getCookie("https://store.steampowered.com")
        isAuthenticated = cookies?.contains("steamLoginSecure=") == true &&
                          cookies.contains("steamLoginSecure=;").not()
    }
    
    // Disconnect Steam (clear cookies)
    fun disconnectSteam() {
        val cookieManager = CookieManager.getInstance()
        
        // Clear cookies for all Steam domains
        val steamDomains = listOf(
            "https://store.steampowered.com",
            "https://steamcommunity.com", 
            "https://steampowered.com",
            "https://help.steampowered.com",
            "https://login.steampowered.com"
        )
        
        // Note: We can only remove all cookies or set empty values
        // Setting empty values for Steam cookies
        steamDomains.forEach { domain ->
            try {
                val cookies = cookieManager.getCookie(domain)
                cookies?.split(";")?.forEach { cookie ->
                    val cookieName = cookie.split("=").firstOrNull()?.trim()
                    if (cookieName != null) {
                        // Set cookie to expire
                        cookieManager.setCookie(domain, "$cookieName=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                    }
                }
            } catch (_: Exception) { }
        }
        
        cookieManager.flush()
        isAuthenticated = false
        accountName = null
    }
    
    // Check auth on load and fetch account name
    LaunchedEffect(Unit) {
        if (store == GameStore.STEAM) {
            checkSteamAuth()
            if (isAuthenticated) {
                isLoadingName = true
                accountName = fetchSteamAccountName()
                isLoadingName = false
            }
        }
    }
    
    // Disconnect confirmation dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Account?") },
            text = { 
                Text("This will clear your Steam session. You'll need to login again to claim games.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        disconnectSteam()
                        showDisconnectDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = store?.displayName ?: "Store",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (store == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                BrutalistCard {
                    Text(
                        text = "Store not found",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(BrutalistDesign.SpacingMedium.dp),
                verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
            ) {
                // Auth Status Card - Now dynamic
                BrutalistAuthStatusCard(
                    isAuthenticated = isAuthenticated,
                    accountName = accountName,
                    isLoadingName = isLoadingName,
                    onDisconnect = { showDisconnectDialog = true }
                )
                
                // Store Info Card
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)) {
                        BrutalistSectionHeader(title = "Store Information")
                        
                        Text(
                            text = "Free games URL:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = store.freeGamesUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Connect/Disconnect Button
                if (!isAuthenticated) {
                    BrutalistButton(
                        onClick = onNavigateToAuth,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Account", fontWeight = FontWeight.Bold)
                    }
                }
                
                // Security Notice
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(BrutalistDesign.SpacingSmall.dp))
                        Column {
                            Text(
                                text = "Security Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingXSmall.dp))
                            Text(
                                text = "Authentication happens directly in ${store.displayName}'s website. " +
                                       "Your credentials are never stored in this app - only a secure session token.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrutalistAuthStatusCard(
    isAuthenticated: Boolean,
    accountName: String? = null,
    isLoadingName: Boolean = false,
    onDisconnect: () -> Unit = {}
) {
    val containerColor = if (isAuthenticated) 
        MaterialTheme.colorScheme.primaryContainer
    else 
        MaterialTheme.colorScheme.errorContainer
    
    val contentColor = if (isAuthenticated)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onErrorContainer
    
    val borderColor = if (isAuthenticated)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error
    
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        borderColor = borderColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            BrutalistContainer(
                modifier = Modifier.size(48.dp),
                containerColor = contentColor.copy(alpha = 0.2f),
                borderColor = borderColor
            ) {
                Icon(
                    imageVector = if (isAuthenticated) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = contentColor
                )
            }
            Spacer(modifier = Modifier.width(BrutalistDesign.SpacingMedium.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (isAuthenticated) {
                    // Show account name as title when connected
                    if (isLoadingName) {
                        Text(
                            text = "Loading...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    } else {
                        Text(
                            text = accountName ?: "Steam Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                    Text(
                        text = "Connected • Ready to claim",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                } else {
                    Text(
                        text = "Not Connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = "Connect to enable auto-claiming",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
            
            // Disconnect button (only when connected)
            if (isAuthenticated) {
                IconButton(onClick = onDisconnect) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Disconnect",
                        tint = contentColor
                    )
                }
            }
        }
    }
}
