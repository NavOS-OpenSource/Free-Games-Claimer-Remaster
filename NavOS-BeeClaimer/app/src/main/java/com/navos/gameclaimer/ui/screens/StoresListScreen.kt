package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.navos.gameclaimer.GameClaimerApplication
import com.navos.gameclaimer.R
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.domain.model.ClaimQueueState
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import com.navos.gameclaimer.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

/**
 * Stores tab - scrollable layout with Attention, Connected, and All Stores sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoresListScreen(
    onNavigateToStore: (String) -> Unit,
    onNavigateToAutomation: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as GameClaimerApplication
    val scope = rememberCoroutineScope()
    val database = remember { GameClaimerDatabase.getInstance(context) }
    
    // Track auth status for each store
    var authStatus by remember { mutableStateOf<Map<GameStore, Boolean>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Get attention items (blocked + uncertain)
    val blockedItems by database.claimQueueDao().getItemsNeedingUserAction()
        .collectAsState(initial = emptyList())
    val uncertainItems by database.claimQueueDao().getUncertainItems()
        .collectAsState(initial = emptyList())
    val attentionItems = blockedItems + uncertainItems
    
    // Check auth status on launch - Steam only for now
    val enabledStores = listOf(GameStore.STEAM)
    
    LaunchedEffect(Unit) {
        isLoading = true
        enabledStores.forEach { store ->
            scope.launch {
                val adapter = app.adapterFactory.getAdapter(store)
                val isAuth = adapter?.isAuthenticated() ?: false
                authStatus = authStatus + (store to isAuth)
            }
        }
        isLoading = false
    }
    
    val connectedStores = enabledStores.filter { authStatus[it] == true }
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = "Stores",
                actions = {
                    IconButton(onClick = onNavigateToAutomation) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Open Automation Browser",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(BrutalistDesign.SpacingMedium.dp),
            verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
        ) {
            // ===== ATTENTION SECTION =====
            if (attentionItems.isNotEmpty()) {
                item {
                    BrutalistSectionHeader(
                        title = "Attention (${attentionItems.size})"
                    )
                }
                
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
                    ) {
                        items(attentionItems, key = { it.queueId }) { item ->
                            AttentionItemCard(
                                item = item,
                                onClick = { onNavigateToStore(item.store) }
                            )
                        }
                    }
                }
            }
            
            // ===== CONNECTED STORES SECTION =====
            item {
                Spacer(Modifier.height(BrutalistDesign.SpacingSmall.dp))
                BrutalistSectionHeader(
                    title = if (connectedStores.isNotEmpty()) "Connected (${connectedStores.size})" else "Connected"
                )
            }
            
            if (connectedStores.isEmpty() && !isLoading) {
                item {
                    EmptySectionCard(
                        icon = Icons.Outlined.Storefront,
                        message = "No stores connected",
                        hint = "Connect stores below to enable auto-claim"
                    )
                }
            } else if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                // Use same full-width cards as All Stores
                items(connectedStores) { store ->
                    StoreListItem(
                        store = store,
                        isAuthenticated = true,
                        onClick = { onNavigateToStore(store.name) }
                    )
                }
            }
            
            // ===== ALL STORES SECTION (only non-connected stores) =====
            val notConnectedStores = enabledStores.filter { authStatus[it] != true }
            
            if (notConnectedStores.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(BrutalistDesign.SpacingSmall.dp))
                    BrutalistSectionHeader(title = "All Stores")
                }
                
                items(notConnectedStores) { store ->
                    StoreListItem(
                        store = store,
                        isAuthenticated = false,
                        onClick = { onNavigateToStore(store.name) }
                    )
                }
            }
        }
    }
}

/**
 * Empty section placeholder card
 */
@Composable
private fun EmptySectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    hint: String
) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BrutalistDesign.SpacingMedium.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Attention item card (horizontal scroll)
 */
@Composable
private fun AttentionItemCard(
    item: ClaimQueueEntity,
    onClick: () -> Unit
) {
    BrutalistCard(
        onClick = onClick,
        modifier = Modifier.width(180.dp)
    ) {
        Column(
            modifier = Modifier.padding(BrutalistDesign.SpacingSmall.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (item.state) {
                        ClaimQueueState.BLOCKED_USER_ACTION.name -> Icons.Filled.Warning
                        else -> Icons.Outlined.Warning
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = item.store,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = item.gameTitle.ifEmpty { "Unknown Game" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            
            Text(
                text = item.state.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Connected store card (horizontal scroll)
 */
@Composable
private fun ConnectedStoreCard(
    store: GameStore,
    onClick: () -> Unit
) {
    BrutalistCard(
        onClick = onClick,
        modifier = Modifier.width(120.dp)
    ) {
        Column(
            modifier = Modifier.padding(BrutalistDesign.SpacingSmall.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Store icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    var showFallback by remember { mutableStateOf(false) }
                    
                    if (!showFallback) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(store.iconUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            onError = { showFallback = true }
                        )
                    }
                    
                    if (showFallback) {
                        Text(
                            text = store.displayName.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = store.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Connected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Individual store list item with background image
 * Image fills card, text below, yellow line on top
 */
@Composable
private fun StoreListItem(
    store: GameStore,
    isAuthenticated: Boolean,
    onClick: () -> Unit
) {
    // Get dynamic corner radius from theme
    val themeState = LocalThemeState.current
    val cornerRadius = themeState.cornerRadius.dp
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${store.displayName}, ${if (isAuthenticated) "Connected" else "Not connected"}" },
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0d0d0d)
        ),
        border = null
    ) {
        Column {
            // Background image for store (with rounded top corners)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                // Use local drawable for Steam, fallback for others
                when (store) {
                    GameStore.STEAM -> {
                        Image(
                            painter = painterResource(id = R.drawable.steam_store_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        // Fallback gradient for other stores
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF1a1a2e),
                                            Color(0xFF16213e)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = store.displayName.take(2).uppercase(),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
            
            // Yellow line between image and text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            // Text section below image with ambient gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1a3a5c).copy(alpha = 0.6f), // Steam blue-ish ambient
                                Color(0xFF0d0d0d)
                            )
                        )
                    )
                    .padding(BrutalistDesign.SpacingMedium.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Store name on left
                    Text(
                        text = store.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Status with colored dot on right
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status dot indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isAuthenticated) Color(0xFF4CAF50) else Color(0xFFE53935),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAuthenticated) "Connected" else "Not connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAuthenticated) 
                                Color(0xFF4CAF50)
                            else 
                                Color(0xFFE53935)
                        )
                    }
                }
            }
        }
    }
}
