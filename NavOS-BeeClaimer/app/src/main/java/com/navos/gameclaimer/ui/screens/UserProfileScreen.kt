package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.navos.gameclaimer.GameClaimerApplication
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import com.navos.gameclaimer.domain.model.ClaimQueueState
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * User tab - Original layout with Profile header, stats buttons, Library, and Quick Actions.
 * Clicking "Claimed" stat opens activity detail sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as GameClaimerApplication
    val database = remember { GameClaimerDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    // Activity/Claimed detail sheet state
    var showActivitySheet by remember { mutableStateOf(false) }
    
    // Claimed/Activity history
    val activityItems by database.claimQueueDao().getRecentActivity(100)
        .collectAsState(initial = emptyList())
    
    // Pending items (in progress)
    val pendingItems = activityItems.filter { 
        it.state == ClaimQueueState.PENDING.name || 
        it.state == ClaimQueueState.IN_PROGRESS.name 
    }
    
    // Completed claims count
    val claimedCount = activityItems.count { it.state == ClaimQueueState.COMPLETED.name }
    
    // Track connected stores
    var connectedStoresCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        var count = 0
        GameStore.entries.forEach { store ->
            scope.launch {
                val adapter = app.adapterFactory.getAdapter(store)
                if (adapter?.isAuthenticated() == true) {
                    count++
                }
            }
        }
        connectedStoresCount = count
    }
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = "Profile",
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings"
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
            // ===== PROFILE HEADER =====
            item {
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BrutalistDesign.SpacingLarge.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                        
                        Text(
                            text = "Game Claimer",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "Autoclaim your free games",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // ===== STATS BUTTONS ROW =====
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
                ) {
                    // Stores stat
                    StatButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Storefront,
                        value = "$connectedStoresCount",
                        label = "Stores",
                        onClick = { /* Could navigate to stores */ }
                    )
                    
                    // Claimed stat - opens activity sheet
                    StatButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Gamepad,
                        value = "$claimedCount",
                        label = "Claimed",
                        onClick = { showActivitySheet = true }
                    )
                    
                    // Pending stat
                    StatButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Schedule,
                        value = "${pendingItems.size}",
                        label = "Pending",
                        onClick = { showActivitySheet = true }
                    )
                }
            }
            
            // ===== LIBRARY BUTTON =====
            item {
                ProfileMenuItem(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = "Library",
                    subtitle = "View your game collection",
                    onClick = onNavigateToLibrary
                )
            }
            
            // ===== QUICK ACTIONS =====
            item {
                Spacer(Modifier.height(BrutalistDesign.SpacingSmall.dp))
                BrutalistSectionHeader(title = "Quick Actions")
            }
            
            item {
                ProfileMenuItem(
                    icon = Icons.Outlined.Settings,
                    title = "Settings",
                    subtitle = "Automation, notifications, themes",
                    onClick = onNavigateToSettings
                )
            }
            
            item {
                ProfileMenuItem(
                    icon = Icons.Outlined.Info,
                    title = "About",
                    subtitle = "Version 1.0.0",
                    onClick = { }
                )
            }
        }
    }
    
    // Activity/Claimed detail bottom sheet
    if (showActivitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showActivitySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ActivitySheetContent(
                items = activityItems,
                onDismiss = { showActivitySheet = false }
            )
        }
    }
}

/**
 * Clickable stat button
 */
@Composable
private fun StatButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    onClick: () -> Unit
) {
    BrutalistCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BrutalistDesign.SpacingMedium.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Activity sheet content - detailed claim history
 */
@Composable
private fun ActivitySheetContent(
    items: List<ClaimQueueEntity>,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BrutalistDesign.SpacingMedium.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No activity yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.queueId }) { item ->
                    ActivityItemRow(item = item, dateFormat = dateFormat)
                }
            }
        }
        
        Spacer(Modifier.height(BrutalistDesign.SpacingLarge.dp))
    }
}

/**
 * Activity item row in the sheet
 */
@Composable
private fun ActivityItemRow(
    item: ClaimQueueEntity,
    dateFormat: SimpleDateFormat
) {
    val (icon, tint) = when (item.state) {
        ClaimQueueState.COMPLETED.name -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        ClaimQueueState.FAILED.name -> Icons.Default.Error to MaterialTheme.colorScheme.error
        ClaimQueueState.IN_PROGRESS.name -> Icons.Default.Schedule to MaterialTheme.colorScheme.tertiary
        ClaimQueueState.BLOCKED_USER_ACTION.name -> Icons.Default.Warning to MaterialTheme.colorScheme.error
        else -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.gameTitle.ifEmpty { "Unknown Game" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.store} • ${item.state.replace("_", " ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        item.lastAttemptAt?.let { timestamp ->
            Text(
                text = dateFormat.format(Date(timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
 * Library item card (horizontal scroll)
 */
@Composable
private fun LibraryItemCard(item: GiveawayEntity) {
    BrutalistCard(
        modifier = Modifier.width(140.dp)
    ) {
        Column(
            modifier = Modifier.padding(BrutalistDesign.SpacingSmall.dp)
        ) {
            // Thumbnail
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                item.imageUrl?.let { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.store,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.isClaimed) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Claimed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * "More items" card at end of horizontal scroll
 */
@Composable
private fun MoreItemsCard(count: Int) {
    BrutalistCard(
        modifier = Modifier
            .width(100.dp)
            .height(120.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "+$count",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Profile menu item
 */
@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(BrutalistDesign.SpacingMedium.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
