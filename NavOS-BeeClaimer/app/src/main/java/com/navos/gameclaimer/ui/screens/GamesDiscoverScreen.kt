package com.navos.gameclaimer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.Giveaway
import com.navos.gameclaimer.domain.model.GiveawayType
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import com.navos.gameclaimer.ui.theme.LocalThemeState
import com.navos.gameclaimer.ui.viewmodel.DiscoverUiState
import com.navos.gameclaimer.ui.viewmodel.DiscoverViewModel
import com.navos.gameclaimer.worker.DiscoveryWorker
import java.util.concurrent.TimeUnit

/**
 * Phase 23: Games tab - discover free giveaways and browse library.
 * 
 * REAL DATA INTEGRATION:
 * - Uses GamerPower API via DiscoveryWorker
 * - Renders actual Giveaway domain objects from DiscoverViewModel
 * - Shows real titles, stores, thumbnails, and expiry information
 * - Calculates counts from actual filtered dataset
 * 
 * Sub-tabs within Games:
 * - Discover: Find new free giveaways (powered by GamerPower)
 * - Library: View claimed/available games
 * - Activity: Recent claim activity
 * - Attention: Items needing action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesDiscoverScreen(
    onNavigateToGiveaway: (String) -> Unit,
    onNavigateToSteamClaim: () -> Unit = {}
) {
    // State for search
    var showSearch by rememberSaveable { mutableStateOf(false) }
    
    // Get database and ViewModel
    val context = LocalContext.current
    val database = remember { GameClaimerDatabase.getInstance(context) }
    
    // Create ViewModel with proper factory
    val viewModel: DiscoverViewModel = viewModel(
        factory = DiscoverViewModel.Factory(database.giveawayDao())
    )
    
    // Collect state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val activeGiveaways by viewModel.activeGiveaways.collectAsState()
    val availableCount by viewModel.availableCount.collectAsState()
    val selectedStore by viewModel.selectedStore.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val storeCounts by viewModel.storeCountsFlow.collectAsState()
    
    // Refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Trigger initial discovery on first launch
    LaunchedEffect(Unit) {
        if (uiState is DiscoverUiState.Empty || uiState is DiscoverUiState.Loading) {
            DiscoveryWorker.enqueueOneTime(context)
        }
    }
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = "Games",
                actions = {
                    IconButton(
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier.semantics { 
                            contentDescription = if (showSearch) "Close search" else "Open search"
                        }
                    ) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = null
                        )
                    }
                    IconButton(
                        onClick = { 
                            isRefreshing = true
                            viewModel.refresh()
                            DiscoveryWorker.enqueueOneTime(context)
                            isRefreshing = false
                        },
                        modifier = Modifier.semantics { 
                            contentDescription = "Refresh giveaways"
                        }
                    ) {
                        if (isRefreshing || uiState is DiscoverUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Show Steam claim FAB when there are Steam games
            val steamCount = storeCounts[GameStore.STEAM] ?: 0
            if (steamCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToSteamClaim,
                    icon = {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    },
                    text = { Text("Claim ($steamCount)") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        DiscoverContent(
            modifier = Modifier.padding(paddingValues),
            showSearch = showSearch,
            searchQuery = searchQuery,
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            selectedStore = selectedStore,
            onStoreFilterChange = { viewModel.setStoreFilter(it) },
            giveaways = activeGiveaways,
            availableCount = availableCount,
            storeCounts = storeCounts,
            uiState = uiState,
            onGiveawayClick = onNavigateToGiveaway,
            onRefresh = {
                isRefreshing = true
                viewModel.refresh()
                DiscoveryWorker.enqueueOneTime(context)
            }
        )
    }
}

/**
 * Discover sub-tab content with real GamerPower data
 */
@Composable
private fun DiscoverContent(
    modifier: Modifier = Modifier,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStore: GameStore?,
    onStoreFilterChange: (GameStore?) -> Unit,
    giveaways: List<Giveaway>,
    availableCount: Int,
    storeCounts: Map<GameStore, Int>,
    uiState: DiscoverUiState,
    onGiveawayClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    
    Column(modifier = modifier.fillMaxSize()) {
        // Search field with animation
        AnimatedVisibility(
            visible = showSearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            BrutalistSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClear = { onSearchQueryChange("") },
                placeholder = "Search giveaways..."
            )
        }
        
        // Quick filters - only show stores that have giveaways
        // Currently testing Steam only
        val storeOrder = mapOf(
            GameStore.STEAM to 0
        )
        val availableStores = storeCounts.keys
            .filter { it == GameStore.STEAM }  // Only Steam for now
            .sortedBy { storeOrder[it] ?: 50 }
        
        // All available stores for filter (even if not in current results)
        val allStores = listOf(
            GameStore.STEAM,
            GameStore.EPIC_GAMES,
            GameStore.GOG,
            GameStore.AMAZON_PRIME,
            GameStore.ITCH_IO
        )
        
        // Filter state
        var showFilterMenu by remember { mutableStateOf(false) }
        var showDlcOnly by remember { mutableStateOf(false) }
        var showGamesOnly by remember { mutableStateOf(false) }
        var hideExpiringSoon by remember { mutableStateOf(false) }
        var selectedStoresFilter by remember { mutableStateOf<Set<GameStore>>(emptySet()) }
        
        // Check if any filter is active
        val hasActiveFilters = showDlcOnly || showGamesOnly || hideExpiringSoon || selectedStoresFilter.isNotEmpty()
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = BrutalistDesign.SpacingMedium.dp,
                    vertical = BrutalistDesign.SpacingSmall.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
            ) {
                item {
                    BrutalistFilterChip(
                        selected = selectedStore == null && !hasActiveFilters,
                        onClick = { 
                            onStoreFilterChange(null)
                            // Clear custom filters when clicking All
                            showDlcOnly = false
                            showGamesOnly = false
                            hideExpiringSoon = false
                            selectedStoresFilter = emptySet()
                        },
                        label = "All (${availableCount})"
                    )
                }
                items(availableStores) { store ->
                    val count = storeCounts[store] ?: 0
                    BrutalistFilterChip(
                        selected = selectedStore == store,
                        onClick = { onStoreFilterChange(if (selectedStore == store) null else store) },
                        label = "${store.displayName} ($count)"
                    )
                }
            }
            
            // Filter button
            Box {
                IconButton(
                    onClick = { showFilterMenu = true }
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter options",
                        tint = if (hasActiveFilters) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    // Type filters header
                    Text(
                        "Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = showGamesOnly,
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Games only")
                            }
                        },
                        onClick = { 
                            showGamesOnly = !showGamesOnly
                            if (showGamesOnly) showDlcOnly = false
                        }
                    )
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = showDlcOnly,
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("DLC only")
                            }
                        },
                        onClick = { 
                            showDlcOnly = !showDlcOnly
                            if (showDlcOnly) showGamesOnly = false
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Store filters header
                    Text(
                        "Stores",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    allStores.forEach { store ->
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedStoresFilter.contains(store),
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(store.displayName)
                                }
                            },
                            onClick = { 
                                selectedStoresFilter = if (selectedStoresFilter.contains(store)) {
                                    selectedStoresFilter - store
                                } else {
                                    selectedStoresFilter + store
                                }
                            }
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Other filters
                    Text(
                        "Other",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = hideExpiringSoon,
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Hide expiring soon")
                            }
                        },
                        onClick = { hideExpiringSoon = !hideExpiringSoon }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    DropdownMenuItem(
                        text = { 
                            Text(
                                "Clear all filters",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { 
                            showDlcOnly = false
                            showGamesOnly = false
                            hideExpiringSoon = false
                            selectedStoresFilter = emptySet()
                            showFilterMenu = false
                        }
                    )
                }
            }
        }
        
        BrutalistDivider()
        
        // Content based on UI state
        when (uiState) {
            is DiscoverUiState.Loading -> {
                LoadingContent()
            }
            is DiscoverUiState.Error -> {
                ErrorContent(
                    message = uiState.message,
                    onRetry = onRefresh
                )
            }
            is DiscoverUiState.Empty -> {
                EmptyDiscoverContent(onRefresh = onRefresh)
            }
            is DiscoverUiState.Success -> {
                // Giveaways list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(BrutalistDesign.SpacingMedium.dp),
                    verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
                ) {
                    // Active giveaways section header
                    item {
                        BrutalistSectionHeader(
                            title = "Active Giveaways",
                            subtitle = "$availableCount free ${if (availableCount == 1) "game" else "games"} available"
                        )
                    }
                    
                    // Filter by search query and custom filters
                    val displayedGiveaways = giveaways.filter { giveaway ->
                        // Search filter
                        val matchesSearch = searchQuery.isEmpty() || 
                            giveaway.title.contains(searchQuery, ignoreCase = true) ||
                            giveaway.store.displayName.contains(searchQuery, ignoreCase = true)
                        
                        // Type filter (DLC only / Games only)
                        val matchesType = when {
                            showDlcOnly -> giveaway.type == GiveawayType.DLC_OR_ADDON
                            showGamesOnly -> giveaway.type == GiveawayType.FREE_TO_KEEP || 
                                           giveaway.type == GiveawayType.FREE_TO_PLAY
                            else -> true
                        }
                        
                        // Store filter from custom filter menu
                        val matchesStoreFilter = selectedStoresFilter.isEmpty() || 
                            selectedStoresFilter.contains(giveaway.store)
                        
                        // Expiry filter (hide if expires within 24 hours)
                        val matchesExpiry = if (hideExpiringSoon && giveaway.endTime != null) {
                            val hoursUntilExpiry = (giveaway.endTime - System.currentTimeMillis()) / (1000 * 60 * 60)
                            hoursUntilExpiry > 24
                        } else {
                            true
                        }
                        
                        matchesSearch && matchesType && matchesStoreFilter && matchesExpiry
                    }
                    
                    if (displayedGiveaways.isEmpty() && searchQuery.isNotEmpty()) {
                        item {
                            NoSearchResultsCard(query = searchQuery)
                        }
                    } else {
                        items(displayedGiveaways, key = { it.id }) { giveaway ->
                            RealGiveawayCard(
                                giveaway = giveaway,
                                onClick = { onGiveawayClick(giveaway.id) },
                                onOpenInBrowser = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(giveaway.claimUrl))
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                    
                    // Attribution footer
                    item {
                        GamerPowerAttribution()
                    }
                }
            }
        }
    }
}

/**
 * Real giveaway card - full image with text overlay
 * Image fills entire card, text overlaid with gradient for readability
 */
@Composable
private fun RealGiveawayCard(
    giveaway: Giveaway,
    onClick: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    val context = LocalContext.current
    
    // Dynamic ambient color extracted from image
    var dominantColor by remember { mutableStateOf(Color(0xFF1a1a1a)) }
    
    // Get dynamic corner radius from theme
    val themeState = LocalThemeState.current
    val cornerRadius = themeState.cornerRadius.dp
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { 
                contentDescription = "${giveaway.title} from ${giveaway.store.displayName}, ${formatTimeRemaining(giveaway.endTime)}"
            },
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0d0d0d)
        ),
        border = null
    ) {
        Column {
            // Image section - full width, natural aspect ratio (with rounded top corners)
            if (giveaway.imageUrl != null) {
                val fullImageUrl = giveaway.imageUrl.replace("/offers/1/", "/offers/1b/")
                
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(fullImageUrl)
                        .crossfade(true)
                        .allowHardware(false) // Needed for Palette
                        .listener(
                            onSuccess = { _, result ->
                                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                bitmap?.let {
                                    try {
                                        androidx.palette.graphics.Palette.from(it).generate { palette ->
                                            palette?.let { p ->
                                                val extracted = p.darkMutedSwatch?.rgb
                                                    ?: p.mutedSwatch?.rgb
                                                    ?: p.dominantSwatch?.rgb
                                                extracted?.let { color ->
                                                    dominantColor = Color(color)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) { }
                                }
                            }
                        )
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentScale = ContentScale.FillWidth,
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFF1a1a1a)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.SportsEsports,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color(0xFF1a1a1a)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Yellow line between image and text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            // Text area below image with ambient gradient from image color
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                dominantColor.copy(alpha = 0.6f),
                                Color(0xFF0d0d0d)
                            )
                        )
                    )
                    .padding(BrutalistDesign.SpacingMedium.dp)
            ) {
                Column {
                    // Title
                    Text(
                        text = giveaway.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Store name
                    Text(
                        text = giveaway.store.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                        
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Bottom row: Type badge left, Expiry right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Type badge
                        BrutalistBadge(
                            text = getTypeLabel(giveaway.type),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        // Expiry
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatTimeRemaining(giveaway.endTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format time remaining from end timestamp
 */
private fun formatTimeRemaining(endTime: Long?): String {
    if (endTime == null) return "No expiry date"
    
    val now = System.currentTimeMillis()
    val remaining = endTime - now
    
    return when {
        remaining < 0 -> "Expired"
        remaining < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
            "Ends in $minutes ${if (minutes == 1L) "minute" else "minutes"}"
        }
        remaining < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(remaining)
            "Ends in $hours ${if (hours == 1L) "hour" else "hours"}"
        }
        remaining < TimeUnit.DAYS.toMillis(2) -> "Ends tomorrow"
        remaining < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(remaining)
            "Ends in $days days"
        }
        remaining < TimeUnit.DAYS.toMillis(30) -> {
            val weeks = TimeUnit.MILLISECONDS.toDays(remaining) / 7
            "Ends in $weeks ${if (weeks == 1L) "week" else "weeks"}"
        }
        else -> {
            val months = TimeUnit.MILLISECONDS.toDays(remaining) / 30
            "Ends in $months ${if (months == 1L) "month" else "months"}"
        }
    }
}

/**
 * Get color for time remaining based on urgency
 */
@Composable
private fun getTimeRemainingColor(endTime: Long?): Color {
    if (endTime == null) return MaterialTheme.colorScheme.onSurfaceVariant
    
    val now = System.currentTimeMillis()
    val remaining = endTime - now
    
    return when {
        remaining < 0 -> MaterialTheme.colorScheme.error
        remaining < TimeUnit.DAYS.toMillis(1) -> MaterialTheme.colorScheme.error
        remaining < TimeUnit.DAYS.toMillis(3) -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Get display label for giveaway type
 */
private fun getTypeLabel(type: GiveawayType): String {
    return when (type) {
        GiveawayType.FREE_TO_KEEP -> "FREE"
        GiveawayType.FREE_WEEKEND -> "FREE WEEKEND"
        GiveawayType.DEMO_OR_TRIAL -> "DEMO"
        GiveawayType.FREE_TO_PLAY -> "F2P"
        GiveawayType.DLC_OR_ADDON -> "DLC"
        GiveawayType.SUBSCRIPTION_PERK -> "PERK"
        GiveawayType.UNKNOWN -> "FREE"
    }
}

/**
 * Loading state content
 */
@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Discovering free games...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state content
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(BrutalistDesign.SpacingLarge.dp),
        contentAlignment = Alignment.Center
    ) {
        BrutalistCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BrutalistDesign.SpacingLarge.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Unable to Load Giveaways",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BrutalistButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * Empty state for Discover tab
 */
@Composable
private fun EmptyDiscoverContent(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(BrutalistDesign.SpacingLarge.dp),
        contentAlignment = Alignment.Center
    ) {
        BrutalistCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(BrutalistDesign.SpacingLarge.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
            ) {
                Icon(
                    Icons.Outlined.CardGiftcard,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No Active Giveaways",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pull to refresh or tap below to check for new offers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BrutalistButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for Giveaways")
                }
            }
        }
    }
}

/**
 * No search results card
 */
@Composable
private fun NoSearchResultsCard(query: String) {
    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BrutalistDesign.SpacingLarge.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
            Text(
                text = "No Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "No giveaways found for \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * GamerPower attribution footer (required for API usage)
 */
@Composable
private fun GamerPowerAttribution() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BrutalistDesign.SpacingMedium.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Giveaway data powered by ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "GamerPower",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Library sub-tab content
 */
@Composable
private fun LibraryContent(
    modifier: Modifier = Modifier,
    giveawayDao: com.navos.gameclaimer.data.local.dao.GiveawayDao
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val availableGiveaways by giveawayDao.getUnclaimedGiveaways(now).collectAsState(initial = emptyList())
    val allGiveaways by giveawayDao.getActiveGiveaways(0).collectAsState(initial = emptyList())
    
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Available", "Claimed", "All")
    
    Column(modifier = modifier.fillMaxSize()) {
        // Library tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(BrutalistDesign.SpacingMedium.dp),
            verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
        ) {
            val displayedGiveaways = when (selectedTab) {
                0 -> availableGiveaways
                1 -> allGiveaways.filter { it.isClaimed }
                else -> allGiveaways
            }
            
            if (displayedGiveaways.isEmpty()) {
                item {
                    BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(BrutalistDesign.SpacingLarge.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.LibraryBooks,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                            Text(
                                text = "No Games Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Games will appear here as they're discovered",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(displayedGiveaways.size) { index ->
                    val giveaway = displayedGiveaways[index]
                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            // Open claim URL in browser
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(giveaway.claimUrl))
                            context.startActivity(intent)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(BrutalistDesign.SpacingMedium.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = giveaway.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = giveaway.store,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (giveaway.isClaimed) {
                                BrutalistBadge(text = "Claimed")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Activity sub-tab content
 */
@Composable
private fun ActivityContent(
    modifier: Modifier = Modifier,
    claimQueueDao: com.navos.gameclaimer.data.local.dao.ClaimQueueDao
) {
    val recentActivity by claimQueueDao.getRecentActivity(limit = 50).collectAsState(initial = emptyList())
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(BrutalistDesign.SpacingMedium.dp),
        verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
    ) {
        item {
            BrutalistSectionHeader(
                title = "Recent Activity",
                subtitle = "${recentActivity.size} items"
            )
        }
        
        if (recentActivity.isEmpty()) {
            item {
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BrutalistDesign.SpacingLarge.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                        Text(
                            text = "No Activity Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Claim activity will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentActivity.size) { index ->
                val item = recentActivity[index]
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BrutalistDesign.SpacingMedium.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.gameTitle.ifEmpty { "Unknown Game" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.state,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BrutalistBadge(text = item.state.take(8))
                    }
                }
            }
        }
    }
}

/**
 * Attention sub-tab content
 */
@Composable
private fun AttentionContent(
    modifier: Modifier = Modifier,
    database: GameClaimerDatabase
) {
    val uncertainItems by database.claimQueueDao().getUncertainItems().collectAsState(initial = emptyList())
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(BrutalistDesign.SpacingMedium.dp),
        verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
    ) {
        item {
            BrutalistSectionHeader(
                title = "Needs Attention",
                subtitle = "${uncertainItems.size} items"
            )
        }
        
        if (uncertainItems.isEmpty()) {
            item {
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BrutalistDesign.SpacingLarge.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                        Text(
                            text = "All Clear!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No items need your attention",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uncertainItems.size) { index ->
                val item = uncertainItems[index]
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BrutalistDesign.SpacingMedium.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(BrutalistDesign.SpacingMedium.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.gameTitle.ifEmpty { "Unknown Game" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Needs verification",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
