package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.ui.viewmodel.NeedsAttentionUiState
import com.navos.gameclaimer.ui.viewmodel.NeedsAttentionViewModel
import com.navos.gameclaimer.ui.viewmodel.UncertainItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 14: Needs Attention screen for uncertain claims.
 * 
 * SAFETY CRITICAL:
 * - Shows claims in UNCERTAIN state that need manual verification
 * - User must verify in their account whether claim succeeded
 * - Uses optimistic locking via resolveUncertainItem
 * - NEVER silently requeues or marks as completed
 * 
 * STATES: Loading, Empty, Error, Success (with counts)
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
    
    var isRefreshing by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Needs Attention") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is NeedsAttentionUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading items...")
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "All clear!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No items need your attention right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
            
            is NeedsAttentionUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Info card at top
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
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
                            Text(
                                text = "Uncertain Claims (${uncertainItems.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        items(
                            items = uncertainItems,
                            key = { it.queueId }
                        ) { item ->
                            UncertainItemCard(
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
                            Text(
                                text = "Requires Action (${blockedItems.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                        
                        items(
                            items = blockedItems,
                            key = { it.queueId }
                        ) { item ->
                            BlockedItemCard(
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

@Composable
private fun UncertainItemCard(
    item: UncertainItem,
    onVerifyClaimed: () -> Unit,
    onAllowRetry: () -> Unit,
    onOpenStore: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title and store
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.store.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Attempt time
            item.attemptedAt?.let { attemptTime ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last attempt: ${dateFormat.format(Date(attemptTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Open store to verify
                OutlinedButton(
                    onClick = onOpenStore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open ${item.store.displayName} to Check")
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mark as verified claimed
                    Button(
                        onClick = onVerifyClaimed,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("I Own It")
                    }
                    
                    // Allow retry
                    OutlinedButton(
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

@Composable
private fun BlockedItemCard(
    item: com.navos.gameclaimer.ui.viewmodel.BlockedItem,
    onOpenStore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.store.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onOpenStore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open ${item.store.displayName}")
            }
        }
    }
}
