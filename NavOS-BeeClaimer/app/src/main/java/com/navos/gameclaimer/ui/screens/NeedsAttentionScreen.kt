package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import com.navos.gameclaimer.ui.viewmodel.NeedsAttentionUiState
import com.navos.gameclaimer.ui.viewmodel.NeedsAttentionViewModel
import com.navos.gameclaimer.ui.viewmodel.UncertainItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 22: Needs Attention screen with brutalist UI redesign.
 * 
 * SAFETY CRITICAL:
 * - Shows claims in UNCERTAIN state that need manual verification
 * - User must verify in their account whether claim succeeded
 * - Uses optimistic locking via resolveUncertainItem
 * - NEVER silently requeues or marks as completed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedsAttentionScreen(
    viewModel: NeedsAttentionViewModel,
    onNavigateBack: () -> Unit,
    onOpenStore: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val uncertainItems by viewModel.uncertainItems.collectAsState()
    val blockedItems by viewModel.blockedItems.collectAsState()
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = "Needs Attention",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val state = uiState) {
            is NeedsAttentionUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    BrutalistCard {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                            Text("Loading items...", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            
            is NeedsAttentionUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    BrutalistCard(
                        modifier = Modifier.padding(BrutalistDesign.SpacingLarge.dp),
                        borderColor = MaterialTheme.colorScheme.primary
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                            Text(
                                text = "All clear!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                            Text(
                                text = "No items need your attention right now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            is NeedsAttentionUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    BrutalistCard(
                        modifier = Modifier.padding(BrutalistDesign.SpacingLarge.dp),
                        borderColor = MaterialTheme.colorScheme.error
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Something went wrong",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                            BrutalistButton(onClick = { viewModel.clearError() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
            
            is NeedsAttentionUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(BrutalistDesign.SpacingMedium.dp),
                    verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingMedium.dp)
                ) {
                    // Info card at top
                    item {
                        BrutalistCard(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(BrutalistDesign.SpacingSmall.dp))
                                Text(
                                    text = "These claims were interrupted and need verification. " +
                                           "Please check your store account to see if the game was claimed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    
                    // Uncertain items section
                    if (uncertainItems.isNotEmpty()) {
                        item {
                            BrutalistSectionHeader(
                                title = "Uncertain Claims (${uncertainItems.size})"
                            )
                        }
                        
                        items(
                            items = uncertainItems,
                            key = { it.queueId }
                        ) { item ->
                            BrutalistUncertainItemCard(
                                item = item,
                                onVerifyClaimed = { viewModel.markAsOwned(item.queueId, item.version) },
                                onAllowRetry = { viewModel.markAsNotOwnedAndRetry(item.queueId, item.version) },
                                onOpenStore = { onOpenStore(item.store.name) }
                            )
                        }
                    }
                    
                    // Blocked items section
                    if (blockedItems.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                            BrutalistSectionHeader(
                                title = "Requires Action (${blockedItems.size})"
                            )
                        }
                        
                        items(
                            items = blockedItems,
                            key = { it.queueId }
                        ) { item ->
                            BrutalistBlockedItemCard(
                                item = item,
                                onOpenStore = { onOpenStore(item.store.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// UNCERTAIN ITEM CARD
// ============================================================================

@Composable
private fun BrutalistUncertainItemCard(
    item: UncertainItem,
    onVerifyClaimed: () -> Unit,
    onAllowRetry: () -> Unit,
    onOpenStore: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.error
    ) {
        Column {
            // Title and store
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingXSmall.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrutalistBadge(
                    text = item.store.displayName,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                BrutalistBadge(
                    text = "Uncertain",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            // Attempt time
            item.attemptedAt?.let { attemptTime ->
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                Text(
                    text = "Last attempt: ${dateFormat.format(Date(attemptTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
            BrutalistDivider()
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
            
            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)) {
                // Open store to verify
                BrutalistOutlinedButton(
                    onClick = onOpenStore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open ${item.store.displayName} to Check")
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
                ) {
                    // Mark as verified claimed
                    BrutalistButton(
                        onClick = onVerifyClaimed,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("I Own It", fontWeight = FontWeight.Bold)
                    }
                    
                    // Allow retry
                    BrutalistOutlinedButton(
                        onClick = onAllowRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retry Claim")
                    }
                }
            }
        }
    }
}

// ============================================================================
// BLOCKED ITEM CARD
// ============================================================================

@Composable
private fun BrutalistBlockedItemCard(
    item: com.navos.gameclaimer.ui.viewmodel.BlockedItem,
    onOpenStore: () -> Unit
) {
    BrutalistCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.tertiary
    ) {
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingXSmall.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrutalistBadge(
                    text = item.store.displayName,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                BrutalistBadge(
                    text = "Blocked",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
            
            // Reason
            val reasonText = when (item.reason) {
                com.navos.gameclaimer.ui.viewmodel.BlockedReason.RequiresAuth -> 
                    "Requires sign-in to your account"
                com.navos.gameclaimer.ui.viewmodel.BlockedReason.RequiresManualAction -> 
                    "Requires manual action on the store"
                com.navos.gameclaimer.ui.viewmodel.BlockedReason.Captcha -> 
                    "Requires CAPTCHA verification"
                com.navos.gameclaimer.ui.viewmodel.BlockedReason.TwoFactorAuth -> 
                    "Requires two-factor authentication"
                com.navos.gameclaimer.ui.viewmodel.BlockedReason.Unknown -> 
                    "Requires manual action"
            }
            Text(
                text = reasonText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
            
            BrutalistButton(
                onClick = onOpenStore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open ${item.store.displayName}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
