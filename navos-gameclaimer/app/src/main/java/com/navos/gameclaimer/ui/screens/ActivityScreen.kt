package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.data.local.dao.ClaimQueueDao
import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.domain.model.ClaimQueueState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 14: Activity screen showing automation history.
 * 
 * Shows recent claim attempts with their outcomes:
 * - COMPLETED: Successfully claimed
 * - FAILED: Claim failed
 * - UNCERTAIN: Needs verification
 * - PENDING: Waiting to process
 * - IN_PROGRESS: Currently processing
 * - RETRY_SCHEDULED: Waiting for retry
 * - BLOCKED_USER_ACTION: Needs user action
 * - CANCELLED: User cancelled
 * - NOT_SUPPORTED: Discovery-only adapter
 * 
 * STATES: Loading, Empty, Populated
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    claimQueueDao: ClaimQueueDao,
    onUncertainClick: (String) -> Unit
) {
    // Collect recent activity (last 50 items)
    val recentActivity by claimQueueDao.getRecentActivity(limit = 50).collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") }
            )
        }
    ) { padding ->
        if (recentActivity.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No activity yet",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Claim attempts will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group by date
                val grouped = recentActivity.groupBy { item ->
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = item.lastAttemptAt ?: item.enqueuedAt
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
                
                grouped.forEach { (dateMillis, items) ->
                    item {
                        ActivityDateHeader(dateMillis)
                    }
                    items(
                        items = items,
                        key = { it.queueId }
                    ) { item ->
                        ActivityItemCard(
                            item = item,
                            onUncertainClick = { onUncertainClick(item.queueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityDateHeader(dateMillis: Long) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val today = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val yesterday = today - 24 * 60 * 60 * 1000
    
    val label = when {
        dateMillis == today -> "Today"
        dateMillis == yesterday -> "Yesterday"
        else -> dateFormat.format(Date(dateMillis))
    }
    
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ActivityItemCard(
    item: ClaimQueueEntity,
    onUncertainClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val state = ClaimQueueState.valueOf(item.state)
    
    val (icon, iconTint, containerColor) = when (state) {
        ClaimQueueState.COMPLETED -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
        ClaimQueueState.FAILED -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
        ClaimQueueState.UNCERTAIN -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
        ClaimQueueState.IN_PROGRESS -> Triple(
            Icons.Default.Sync,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
        ClaimQueueState.PENDING, ClaimQueueState.RETRY_SCHEDULED -> Triple(
            Icons.Default.Schedule,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
        ClaimQueueState.BLOCKED_USER_ACTION -> Triple(
            Icons.Default.PersonOff,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
        ClaimQueueState.CANCELLED -> Triple(
            Icons.Default.Cancel,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
        ClaimQueueState.NOT_SUPPORTED -> Triple(
            Icons.Default.Block,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = if (state == ClaimQueueState.UNCERTAIN) onUncertainClick else ({})
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = state.name,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.gameTitle.ifEmpty { "Game #${item.giveawayId.take(8)}" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.storeName.ifEmpty { item.store },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stateLabel(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = iconTint
                    )
                }
            }
            
            Text(
                text = timeFormat.format(Date(item.lastAttemptAt ?: item.enqueuedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun stateLabel(state: ClaimQueueState): String = when (state) {
    ClaimQueueState.COMPLETED -> "Claimed"
    ClaimQueueState.FAILED -> "Failed"
    ClaimQueueState.UNCERTAIN -> "Needs attention"
    ClaimQueueState.IN_PROGRESS -> "Processing"
    ClaimQueueState.PENDING -> "Pending"
    ClaimQueueState.RETRY_SCHEDULED -> "Retry scheduled"
    ClaimQueueState.BLOCKED_USER_ACTION -> "Needs action"
    ClaimQueueState.CANCELLED -> "Cancelled"
    ClaimQueueState.NOT_SUPPORTED -> "Not supported"
}
