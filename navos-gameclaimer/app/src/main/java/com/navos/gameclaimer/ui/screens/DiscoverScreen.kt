package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.Giveaway
import com.navos.gameclaimer.ui.viewmodel.DiscoverUiState
import com.navos.gameclaimer.ui.viewmodel.DiscoverViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 14: Discover screen for browsing free giveaways.
 * 
 * FEATURES:
 * - Giveaway feed with filtering by store
 * - Search functionality
 * - Pull-to-refresh (manual refresh button)
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
            TopAppBar(
                title = { 
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search giveaways...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, "Clear search")
                                    }
                                }
                            }
                        )
                    } else {
                        Text("Discover")
                    }
                },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Store filter chips
            if (storeCounts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedStore == null,
                            onClick = { viewModel.setStoreFilter(null) },
                            label = { Text("All") }
                        )
                    }
                    items(storeCounts.keys.toList()) { store ->
                        val count = storeCounts[store] ?: 0
                        FilterChip(
                            selected = selectedStore == store,
                            onClick = { viewModel.setStoreFilter(store) },
                            label = { Text("${store.displayName} ($count)") }
                        )
                    }
                }
            }
            
            // Main content based on state
            when (val state = uiState) {
                is DiscoverUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading giveaways...")
                        }
                    }
                }
                
                is DiscoverUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "No giveaways found",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty() || selectedStore != null) {
                                    "Try adjusting your filters"
                                } else {
                                    "Check back later for new free games!"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text("Refresh")
                            }
                        }
                    }
                }
                
                is DiscoverUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "Something went wrong",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text("Try Again")
                            }
                        }
                    }
                }
                
                is DiscoverUiState.Success -> {
                    if (activeGiveaways.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No giveaways match your filters")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = activeGiveaways,
                                key = { it.id }
                            ) { giveaway ->
                                GiveawayCard(
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

@Composable
private fun GiveawayCard(
    giveaway: Giveaway,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = giveaway.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = giveaway.store.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Original price
                giveaway.originalPrice?.let { price ->
                    Text(
                        text = "Was: $price",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Expiry
                giveaway.endTime?.let { endTime ->
                    val isExpiringSoon = endTime - System.currentTimeMillis() < 24 * 60 * 60 * 1000
                    Text(
                        text = "Ends ${dateFormat.format(Date(endTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isExpiringSoon) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            // Type badge - show only if not the default type
            if (giveaway.type != com.navos.gameclaimer.domain.model.GiveawayType.FREE_TO_KEEP) {
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = { },
                    label = { Text(giveaway.type.name.replace("_", " ")) }
                )
            }
        }
    }
}
