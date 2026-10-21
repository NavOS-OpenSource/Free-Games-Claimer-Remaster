package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.data.local.dao.GiveawayDao
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 14: Library screen showing user's games.
 * 
 * TABS:
 * - Available: Active giveaways not yet claimed
 * - Claimed: Successfully claimed games
 * - Expired: Past giveaways (claimed or not)
 * 
 * STATES: Loading, Empty, Populated per tab
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    giveawayDao: GiveawayDao,
    onGiveawayClick: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Available", "Claimed", "Expired")
    
    // Collect data for each tab
    val now = System.currentTimeMillis()
    
    val availableGiveaways by giveawayDao.getUnclaimedGiveaways(now).collectAsState(initial = emptyList())
    
    // For claimed, we need a custom query - using a derived flow
    val allGiveaways by giveawayDao.getActiveGiveaways(0).collectAsState(initial = emptyList())
    val claimedGiveaways = remember(allGiveaways) {
        allGiveaways.filter { it.isClaimed }
    }
    val expiredGiveaways = remember(allGiveaways) {
        allGiveaways.filter { 
            it.endTime != null && it.endTime < now 
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    val count = when (index) {
                        0 -> availableGiveaways.size
                        1 -> claimedGiveaways.size
                        2 -> expiredGiveaways.size
                        else -> 0
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                if (count > 0) "$title ($count)" else title
                            )
                        }
                    )
                }
            }
            
            // Tab content
            when (selectedTab) {
                0 -> LibraryTabContent(
                    giveaways = availableGiveaways,
                    emptyMessage = "No available giveaways",
                    emptySubMessage = "Discovered free games will appear here",
                    showClaimedBadge = false,
                    onGiveawayClick = onGiveawayClick
                )
                1 -> LibraryTabContent(
                    giveaways = claimedGiveaways,
                    emptyMessage = "No claimed games yet",
                    emptySubMessage = "Games you claim will be saved here",
                    showClaimedBadge = true,
                    onGiveawayClick = onGiveawayClick
                )
                2 -> LibraryTabContent(
                    giveaways = expiredGiveaways,
                    emptyMessage = "No expired giveaways",
                    emptySubMessage = "Past giveaways will appear here",
                    showClaimedBadge = false,
                    showExpiredBadge = true,
                    onGiveawayClick = onGiveawayClick
                )
            }
        }
    }
}

@Composable
private fun LibraryTabContent(
    giveaways: List<GiveawayEntity>,
    emptyMessage: String,
    emptySubMessage: String,
    showClaimedBadge: Boolean = false,
    showExpiredBadge: Boolean = false,
    onGiveawayClick: (String) -> Unit
) {
    if (giveaways.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = emptySubMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = giveaways,
                key = { it.id }
            ) { giveaway ->
                LibraryItemCard(
                    giveaway = giveaway,
                    showClaimedBadge = showClaimedBadge,
                    showExpiredBadge = showExpiredBadge,
                    onClick = { onGiveawayClick(giveaway.id) }
                )
            }
        }
    }
}

@Composable
private fun LibraryItemCard(
    giveaway: GiveawayEntity,
    showClaimedBadge: Boolean,
    showExpiredBadge: Boolean = false,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    
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
                        text = giveaway.store,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Status badges
                if (showClaimedBadge && giveaway.isClaimed) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Claimed") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                if (showExpiredBadge) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Expired") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Dates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                giveaway.claimedAt?.let { claimedAt ->
                    Text(
                        text = "Claimed: ${dateFormat.format(Date(claimedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                giveaway.endTime?.let { endTime ->
                    val isExpired = endTime < System.currentTimeMillis()
                    Text(
                        text = if (isExpired) {
                            "Ended: ${dateFormat.format(Date(endTime))}"
                        } else {
                            "Ends: ${dateFormat.format(Date(endTime))}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isExpired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            // Original price if available
            giveaway.originalPrice?.let { price ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Original price: $price",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
