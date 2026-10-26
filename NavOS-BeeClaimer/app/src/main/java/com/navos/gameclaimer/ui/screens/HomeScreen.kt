package com.navos.gameclaimer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.GameClaimerApplication
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import kotlinx.coroutines.launch

/**
 * Home screen showing available game stores and recent free games.
 * 
 * Phase 22: Brutalist UI redesign with strong borders and boxy appearance
 * Phase 5+: Shows actual free games from store APIs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToStore: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as GameClaimerApplication
    val scope = rememberCoroutineScope()
    
    // Track auth status for each store
    var authStatus by remember { mutableStateOf<Map<GameStore, Boolean>>(emptyMap()) }
    
    // Check auth status on launch and when returning to screen
    LaunchedEffect(Unit) {
        GameStore.entries.forEach { store ->
            scope.launch {
                val adapter = app.adapterFactory.getAdapter(store)
                val isAuth = adapter?.isAuthenticated() ?: false
                authStatus = authStatus + (store to isAuth)
            }
        }
    }
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = "GameClaimer",
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
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
            // Game Stores Section
            item {
                BrutalistSectionHeader(title = "Game Stores")
            }
            
            items(GameStore.entries) { store ->
                BrutalistStoreCard(
                    store = store,
                    isAuthenticated = authStatus[store] ?: false,
                    onClick = { onNavigateToStore(store.name) }
                )
            }
            
            // Free Games Section
            item {
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                BrutalistSectionHeader(title = "Free Games")
            }
            
            item {
                // Placeholder for when we fetch actual games
                BrutalistCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                        Text(
                            text = "Connect a store to see free games",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingLarge.dp))
            }
        }
    }
}

/**
 * Brutalist-styled store card
 */
@Composable
private fun BrutalistStoreCard(
    store: GameStore,
    isAuthenticated: Boolean,
    onClick: () -> Unit
) {
    BrutalistIconCard(
        title = store.displayName,
        subtitle = if (isAuthenticated) "Connected" else "Tap to connect",
        icon = Icons.Default.Storefront,
        onClick = onClick,
        trailingContent = {
            if (isAuthenticated) {
                BrutalistBadge(
                    text = "Active",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    )
}
