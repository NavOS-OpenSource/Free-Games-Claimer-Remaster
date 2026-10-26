package com.navos.gameclaimer.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import com.navos.gameclaimer.browser.SecureBrowserWrapper
import com.navos.gameclaimer.data.adapter.SteamStoreAdapter
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import com.navos.gameclaimer.domain.automation.SteamClaimFlowManager
import com.navos.gameclaimer.domain.automation.SteamClaimQueueItem
import com.navos.gameclaimer.domain.automation.SteamClaimQueueManager
import com.navos.gameclaimer.domain.automation.SteamClaimStatus
import com.navos.gameclaimer.domain.automation.SteamClaimSummary
import com.navos.gameclaimer.domain.model.BrowserAuthEvent
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.Giveaway
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Steam Claim Screen
 * 
 * Provides the UI for automated Steam game claiming:
 * 1. Shows the claim queue with status for each game
 * 2. Displays WebView for claim operations
 * 3. Handles login, age gates, and claim verification
 * 
 * SECURITY:
 * - User must initiate the claim process
 * - Shows all operations in real-time
 * - User can stop at any time
 * - Only claims items verified as -100% discount
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamClaimScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Load Steam giveaways from database
    val database = remember { GameClaimerDatabase.getInstance(context) }
    val giveawayEntities by database.giveawayDao().getActiveGiveawaysForStore(GameStore.STEAM.name)
        .collectAsState(initial = emptyList())
    val steamGiveaways = remember(giveawayEntities) {
        giveawayEntities.map { it.toDomain() }
    }
    
    // Managers
    val queueManager = remember { SteamClaimQueueManager() }
    val flowManager = remember { SteamClaimFlowManager() }
    val adapter = remember { SteamStoreAdapter() }
    
    // Browser wrapper for WebView
    val browserWrapper = remember { SecureBrowserWrapper.forStore(context, GameStore.STEAM) }
    
    // State
    val queue by queueManager.queue.collectAsState()
    val currentIndex by queueManager.currentIndex.collectAsState()
    val isProcessing by queueManager.isProcessing.collectAsState()
    val flowState by flowManager.state.collectAsState()
    
    var showWebView by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to start") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var needsLogin by remember { mutableStateOf(false) }
    
    // Initialize queue
    LaunchedEffect(steamGiveaways) {
        queueManager.enqueue(steamGiveaways)
    }
    
    // Handle browser events
    LaunchedEffect(browserWrapper) {
        browserWrapper.events.collectLatest { event ->
            when (event) {
                is BrowserAuthEvent.PageStarted -> {
                    isLoading = true
                }
                is BrowserAuthEvent.PageFinished -> {
                    isLoading = false
                    // Process the page after load
                    if (isProcessing && showWebView) {
                        val js = flowManager.onPageLoaded(browserWrapper.getWebView()!!, event.url)
                        if (js != null) {
                            delay(500) // Small delay for page stability
                            executeJs(browserWrapper, js) { result ->
                                handleJsResult(flowManager, queueManager, result)
                            }
                        }
                    }
                }
                is BrowserAuthEvent.AuthenticationComplete -> {
                    needsLogin = false
                    statusMessage = "Logged in! Continuing..."
                }
                is BrowserAuthEvent.SslError -> {
                    errorMessage = "SSL security error - stopping"
                    queueManager.stopProcessing()
                }
                else -> {}
            }
        }
    }
    
    // Handle flow state changes
    LaunchedEffect(flowState) {
        when (val state = flowState) {
            is SteamClaimFlowManager.ClaimFlowState.Loading -> {
                statusMessage = "Loading: ${state.gameTitle ?: "game"}..."
            }
            is SteamClaimFlowManager.ClaimFlowState.DetectingOwnership -> {
                statusMessage = "Checking ownership..."
            }
            is SteamClaimFlowManager.ClaimFlowState.AlreadyOwned -> {
                statusMessage = "Already owned!"
                queueManager.markComplete(SteamClaimStatus.ALREADY_OWNED)
                delay(1000)
                processNextItem(queueManager, flowManager, browserWrapper) { showWebView = it }
            }
            is SteamClaimFlowManager.ClaimFlowState.ReadyToClaim -> {
                statusMessage = "Claiming ${state.title}..."
            }
            is SteamClaimFlowManager.ClaimFlowState.VerifyingClaim -> {
                statusMessage = "Verifying claim..."
                delay(5000) // Wait for Steam to process
                executeJs(browserWrapper, SteamStoreAdapter.JS_VERIFY_OWNED) { result ->
                    flowManager.onVerificationComplete(state.appId, result)
                }
            }
            is SteamClaimFlowManager.ClaimFlowState.ClaimSuccess -> {
                statusMessage = "✓ Claimed: ${state.title}"
                queueManager.markComplete(SteamClaimStatus.SUCCESS, state.method)
                delay(1500)
                processNextItem(queueManager, flowManager, browserWrapper) { showWebView = it }
            }
            is SteamClaimFlowManager.ClaimFlowState.ClaimFailed -> {
                statusMessage = "Failed: ${state.reason}"
                queueManager.markComplete(SteamClaimStatus.FAILED, state.reason)
                delay(1500)
                processNextItem(queueManager, flowManager, browserWrapper) { showWebView = it }
            }
            is SteamClaimFlowManager.ClaimFlowState.RequiresLogin -> {
                statusMessage = "Login required"
                needsLogin = true
                queueManager.markComplete(SteamClaimStatus.REQUIRES_LOGIN)
            }
            is SteamClaimFlowManager.ClaimFlowState.RequiresAgeVerification -> {
                statusMessage = "Handling age verification..."
                // Age gate JS was returned, wait for reload
            }
            is SteamClaimFlowManager.ClaimFlowState.Skipped -> {
                statusMessage = "Skipped: ${state.reason}"
                queueManager.markComplete(SteamClaimStatus.SKIPPED, state.reason)
                delay(1000)
                processNextItem(queueManager, flowManager, browserWrapper) { showWebView = it }
            }
            is SteamClaimFlowManager.ClaimFlowState.Error -> {
                errorMessage = state.message
                queueManager.markComplete(SteamClaimStatus.FAILED, state.message)
                delay(1500)
                processNextItem(queueManager, flowManager, browserWrapper) { showWebView = it }
            }
            else -> {}
        }
    }
    
    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Steam Auto-Claim",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        queueManager.stopProcessing()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Progress indicator
                    if (isProcessing && queue.isNotEmpty()) {
                        Text(
                            "${currentIndex + 1}/${queue.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 16.dp)
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
            // Status bar
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading || isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            // Error message
            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            // Login prompt
            if (needsLogin && !isProcessing) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Steam Login Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Please log in to Steam in the browser below to continue claiming.")
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showWebView = true
                                browserWrapper.loadLoginPage()
                            }
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Steam Login")
                        }
                    }
                }
            }
            
            // Main content: Queue list and WebView
            if (showWebView) {
                // WebView takes half the screen when visible
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { browserWrapper.createWebView() },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Loading overlay
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
                
                HorizontalDivider()
            }
            
            // Queue list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (showWebView) 0.5f else 1f)
            ) {
                // Summary bar
                val summary = queueManager.getSummary()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryChip("✓ ${summary.success}", Color(0xFF4CAF50))
                    SummaryChip("○ ${summary.alreadyOwned}", Color(0xFF2196F3))
                    SummaryChip("⊘ ${summary.skipped}", Color(0xFF9E9E9E))
                    SummaryChip("✗ ${summary.failed}", Color(0xFFF44336))
                }
                
                // Queue list
                val listState = rememberLazyListState()
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(queue) { item ->
                        ClaimQueueItemRow(
                            item = item,
                            isCurrent = queue.indexOf(item) == currentIndex && isProcessing
                        )
                    }
                    
                    // Empty state
                    if (queue.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No Steam games in queue",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // Auto-scroll to current item
                LaunchedEffect(currentIndex) {
                    if (currentIndex < queue.size) {
                        listState.animateScrollToItem(currentIndex)
                    }
                }
            }
            
            // Control buttons
            Surface(
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Start/Stop button
                    if (!isProcessing && queue.any { it.status == SteamClaimStatus.PENDING }) {
                        Button(
                            onClick = {
                                queueManager.startProcessing()
                                showWebView = true
                                statusMessage = "Starting claim process..."
                                processNextItem(queueManager, flowManager, browserWrapper) { showWebView = it }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Claiming")
                        }
                    } else if (isProcessing) {
                        OutlinedButton(
                            onClick = {
                                queueManager.stopProcessing()
                                statusMessage = "Stopped"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop")
                        }
                    }
                    
                    // Done button (show when complete)
                    if (!isProcessing && queue.isNotEmpty() && queue.none { it.status == SteamClaimStatus.PENDING }) {
                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            browserWrapper.destroy()
        }
    }
}

@Composable
private fun SummaryChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun ClaimQueueItemRow(
    item: SteamClaimQueueItem,
    isCurrent: Boolean
) {
    val backgroundColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    
    val statusColor = when (item.status) {
        SteamClaimStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        SteamClaimStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        SteamClaimStatus.SUCCESS -> Color(0xFF4CAF50)
        SteamClaimStatus.ALREADY_OWNED -> Color(0xFF2196F3)
        SteamClaimStatus.SKIPPED -> Color(0xFF9E9E9E)
        SteamClaimStatus.FAILED -> Color(0xFFF44336)
        SteamClaimStatus.REQUIRES_LOGIN -> Color(0xFFFF9800)
        SteamClaimStatus.REQUIRES_MANUAL_ACTION -> Color(0xFFFF9800)
    }
    
    val statusIcon = when (item.status) {
        SteamClaimStatus.PENDING -> Icons.Default.Schedule
        SteamClaimStatus.IN_PROGRESS -> Icons.Default.Sync
        SteamClaimStatus.SUCCESS -> Icons.Default.CheckCircle
        SteamClaimStatus.ALREADY_OWNED -> Icons.Default.Inventory
        SteamClaimStatus.SKIPPED -> Icons.Default.SkipNext
        SteamClaimStatus.FAILED -> Icons.Default.Error
        SteamClaimStatus.REQUIRES_LOGIN -> Icons.Default.Login
        SteamClaimStatus.REQUIRES_MANUAL_ACTION -> Icons.Default.TouchApp
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.giveaway.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (item.resultMessage != null) {
                Text(
                    text = item.resultMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Processing indicator
        if (isCurrent && item.status == SteamClaimStatus.IN_PROGRESS) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        }
    }
    
    HorizontalDivider()
}

private fun executeJs(
    browserWrapper: SecureBrowserWrapper,
    script: String,
    onResult: (String) -> Unit
) {
    browserWrapper.getWebView()?.post {
        browserWrapper.evaluateJavascript(script) { result ->
            val cleaned = cleanJsResult(result)
            onResult(cleaned)
        }
    }
}

private fun cleanJsResult(result: String?): String {
    if (result == null || result == "null") return ""
    var cleaned = result
    if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
        cleaned = cleaned.substring(1, cleaned.length - 1)
    }
    return cleaned.replace("\\\"", "\"").replace("\\\\", "\\")
}

private fun handleJsResult(
    flowManager: SteamClaimFlowManager,
    queueManager: SteamClaimQueueManager,
    result: String
) {
    val state = flowManager.state.value
    val currentItem = queueManager.getNextItem()
    val appId = currentItem?.giveaway?.externalId ?: return
    
    when (state) {
        is SteamClaimFlowManager.ClaimFlowState.DetectingOwnership -> {
            val nextJs = flowManager.onOwnershipDetected(appId, result)
            // If claim JS was returned, execute it
            if (nextJs == SteamStoreAdapter.JS_CLICK_CLAIM) {
                // The flow state will update to ReadyToClaim, then we execute
            }
        }
        is SteamClaimFlowManager.ClaimFlowState.ReadyToClaim -> {
            // Execute claim click
            flowManager.onClaimClicked(appId, result)
        }
        else -> {}
    }
}

private fun processNextItem(
    queueManager: SteamClaimQueueManager,
    flowManager: SteamClaimFlowManager,
    browserWrapper: SecureBrowserWrapper,
    setShowWebView: (Boolean) -> Unit
) {
    val nextItem = queueManager.getNextItem()
    
    if (nextItem == null) {
        // Queue complete
        queueManager.stopProcessing()
        setShowWebView(false)
        flowManager.reset()
        return
    }
    
    // Start claim flow for next item
    flowManager.startClaimFlow(nextItem.giveaway)
    
    // Mark as in progress
    val items = queueManager.queue.value.toMutableList()
    val index = queueManager.currentIndex.value
    if (index < items.size) {
        items[index] = items[index].copy(status = SteamClaimStatus.IN_PROGRESS)
        // Note: Need to update queue through proper mechanism
    }
    
    // Navigate to the game URL
    browserWrapper.loadUrl(nextItem.giveaway.claimUrl)
}
