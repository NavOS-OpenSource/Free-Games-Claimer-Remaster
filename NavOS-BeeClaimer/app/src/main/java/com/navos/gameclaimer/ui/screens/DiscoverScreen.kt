package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.domain.model.Giveaway
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import com.navos.gameclaimer.ui.viewmodel.DiscoverUiState
import com.navos.gameclaimer.ui.viewmodel.DiscoverViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 22: Discover screen with brutalist UI redesign.
 * 
 * FEATURES:
 * - Giveaway feed with filtering by store
 * - Search functionality
 * - Refresh button
 * - Dismiss unwanted giveaways
 * 
 * STATES: Loading, Empty, Error, Success
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onGiveawayClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStore by viewModel.selectedStore.collectAsState()
    val storeCounts by viewModel.storeCountsFlow.collectAsState()
    val activeGiveaways by viewModel.activeGiveaways.collectAsState()
    
    var showSearch by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = if (showSearch) "Search" else "Discover",
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearch) "Close search" else "Search"
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search field
            if (showSearch) {
                BrutalistSearchField(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onClear = { viewModel.setSearchQuery("") }
                )
            }
            
            // Store filter chips
            if (storeCounts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(
                        horizontal = BrutalistDesign.SpacingMedium.dp,
                        vertical = BrutalistDesign.SpacingSmall.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
                ) {
                    item {
                        BrutalistFilterChip(
                            selected = selectedStore == null,
                            onClick = { viewModel.setStoreFilter(null) },
                            label = "All"
                        )
                    }
                    items(storeCounts.keys.toList()) { store ->
                        val count = storeCounts[store] ?: 0
                        BrutalistFilterChip(
                            selected = selectedStore == store,
                            onClick = { viewModel.setStoreFilter(store) },
                            label = "${store.displayName} ($count)"
                        )
                    }
                }
            }
            
            // Main content based on state
            when (val state = uiState) {
                is DiscoverUiState.Loading -> {
                    BrutalistLoadingState(message = "Loading giveaways...")
                }
                
                is DiscoverUiState.Empty -> {
                    BrutalistEmptyState(
                        title = "No giveaways found",
                        message = if (searchQuery.isNotEmpty() || selectedStore != null) {
                            "Try adjusting your filters"
                        } else {
                            "Check back later for new free games!"
                        },
                        actionLabel = "Refresh",
                        onAction = { viewModel.refresh() }
                    )
                }
                
                is DiscoverUiState.Error -> {
                    BrutalistErrorState(
                        title = "Something went wrong",
                        message = state.message,
                        actionLabel = "Try Again",
                        onAction = { viewModel.refresh() }
                    )
                }
                
                is DiscoverUiState.Success -> {
                    if (activeGiveaways.isEmpty()) {
                        BrutalistEmptyState(
                            title = "No matches",
                            message = "No giveaways match your filters"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(BrutalistDesign.SpacingMedium.dp),
                            verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
                        ) {
                            items(
                                items = activeGiveaways,
                                key = { it.id }
                            ) { giveaway ->
                                BrutalistGiveawayCard(
                                    giveaway = giveaway,
                                    onClick = { onGiveawayClick(giveaway.id) },
                                    onDismiss = { viewModel.dismissGiveaway(giveaway.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// BRUTALIST DISCOVER COMPONENTS
// ============================================================================

@Composable
private fun BrutalistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    BrutalistCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BrutalistDesign.SpacingMedium.dp,
                vertical = BrutalistDesign.SpacingSmall.dp
            )
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search giveaways...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                }
            }
        )
    }
}

@Composable
private fun BrutalistFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { 
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ) 
        },
        shape = currentBrutalistShape(),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderWidth = BrutalistDesign.BorderMedium.dp,
            selectedBorderWidth = BrutalistDesign.BorderThick.dp,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun BrutalistGiveawayCard(
    giveaway: Giveaway,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            BrutalistContainer(
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Outlined.CardGiftcard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(BrutalistDesign.SpacingMedium.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = giveaway.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrutalistBadge(
                        text = giveaway.store.displayName,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    // Original price
                    giveaway.originalPrice?.let { price ->
                        Text(
                            text = "Was $price",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                
                // Expiry
                giveaway.endTime?.let { endTime ->
                    val isExpiringSoon = endTime - System.currentTimeMillis() < 24 * 60 * 60 * 1000
                    Text(
                        text = "Ends ${dateFormat.format(Date(endTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isExpiringSoon) FontWeight.Bold else FontWeight.Normal,
                        color = if (isExpiringSoon) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                // Type badge - show only if not the default type
                if (giveaway.type != com.navos.gameclaimer.domain.model.GiveawayType.FREE_TO_KEEP) {
                    Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                    BrutalistBadge(
                        text = giveaway.type.name.replace("_", " "),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            // Dismiss button
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// STATE COMPONENTS
// ============================================================================

@Composable
private fun BrutalistLoadingState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        BrutalistCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                Text(message, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun BrutalistEmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        BrutalistCard(
            modifier = Modifier.padding(BrutalistDesign.SpacingLarge.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                    BrutalistButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrutalistErrorState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        BrutalistCard(
            modifier = Modifier.padding(BrutalistDesign.SpacingLarge.dp),
            borderColor = MaterialTheme.colorScheme.error
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                BrutalistButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
