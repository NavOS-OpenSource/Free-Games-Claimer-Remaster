package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navos.gameclaimer.data.local.dao.ClaimQueueDao
import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.domain.model.ClaimQueueState
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 22: Activity screen with brutalist UI redesign.
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
            BrutalistTopAppBar(title = "Activity")
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (recentActivity.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                BrutalistCard(
                    modifier = Modifier.padding(BrutalistDesign.SpacingLarge.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                        Text(
                            text = "No activity yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                        Text(
                            text = "Claim attempts will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(BrutalistDesign.SpacingMedium.dp),
                verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
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
                        BrutalistActivityDateHeader(dateMillis)
                    }
                    items(
                        items = items,
                        key = { it.queueId }
                    ) { item ->
                        BrutalistActivityItemCard(
                            item = item,
                            onUncertainClick = { onUncertainClick(item.queueId) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// DATE HEADER
// ============================================================================

@Composable
private fun BrutalistActivityDateHeader(dateMillis: Long) {
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
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BrutalistDesign.SpacingSmall.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(BrutalistDesign.SpacingSmall.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(BrutalistDesign.BorderMedium.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )
    }
}

// ============================================================================
// ACTIVITY ITEM CARD
// ============================================================================

@Composable
private fun BrutalistActivityItemCard(
    item: ClaimQueueEntity,
    onUncertainClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val state = ClaimQueueState.valueOf(item.state)
    
    val (icon, iconTint, borderColor) = getStateVisuals(state)
    
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (state == ClaimQueueState.UNCERTAIN) onUncertainClick else null,
        borderColor = borderColor
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status icon with border
            val iconShape = currentBrutalistShape()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        width = BrutalistDesign.BorderThin.dp,
                        color = borderColor,
                        shape = iconShape
                    )
                    .background(
                        color = borderColor.copy(alpha = 0.1f),
                        shape = iconShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = state.name,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(BrutalistDesign.SpacingMedium.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.gameTitle.ifEmpty { "Game #${item.giveawayId.take(8)}" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrutalistBadge(
                        text = item.storeName.ifEmpty { item.store },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stateLabel(state),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = iconTint
                    )
                }
            }
            
            // Time
            Text(
                text = timeFormat.format(Date(item.lastAttemptAt ?: item.enqueuedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// HELPERS
// ============================================================================

@Composable
private fun getStateVisuals(state: ClaimQueueState): Triple<ImageVector, Color, Color> {
    return when (state) {
        ClaimQueueState.COMPLETED -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary
        )
        ClaimQueueState.FAILED -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error
        )
        ClaimQueueState.UNCERTAIN -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiary
        )
        ClaimQueueState.IN_PROGRESS -> Triple(
            Icons.Default.Sync,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondary
        )
        ClaimQueueState.PENDING, ClaimQueueState.RETRY_SCHEDULED -> Triple(
            Icons.Default.Schedule,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
        ClaimQueueState.BLOCKED_USER_ACTION -> Triple(
            Icons.Default.PersonOff,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiary
        )
        ClaimQueueState.CANCELLED -> Triple(
            Icons.Default.Cancel,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
        ClaimQueueState.NOT_SUPPORTED -> Triple(
            Icons.Default.Block,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
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
