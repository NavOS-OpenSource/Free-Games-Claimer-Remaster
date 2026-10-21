package com.navos.gameclaimer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.ui.screens.BrowserAuthScreen
import com.navos.gameclaimer.ui.screens.HomeScreen
import com.navos.gameclaimer.ui.screens.SettingsScreen
import com.navos.gameclaimer.ui.screens.StoreDetailScreen

/**
 * Phase 14: Navigation routes for the app.
 * 
 * Destinations:
 * - Discover: Giveaway feed with filtering and search
 * - Library: Available/Claimed/Expired tabs
 * - Activity: Queue state and recent outcomes
 * - Needs Attention: Uncertain claims, auth requirements
 * - Settings: Automation, notifications, store accounts
 */
object Routes {
    // Main destinations (bottom nav)
    const val DISCOVER = "discover"
    const val LIBRARY = "library"
    const val ACTIVITY = "activity"
    const val NEEDS_ATTENTION = "needs_attention"
    const val SETTINGS = "settings"
    
    // Detail screens
    const val GIVEAWAY_DETAIL = "giveaway/{giveawayId}"
    const val STORE_DETAIL = "store/{storeId}"
    const val BROWSER_AUTH = "browser_auth/{storeId}"
    const val UNCERTAIN_DETAIL = "uncertain/{queueId}"
    
    // Legacy alias for compatibility
    const val HOME = DISCOVER
    
    fun giveawayDetail(giveawayId: String) = "giveaway/$giveawayId"
    fun storeDetail(storeId: String) = "store/$storeId"
    fun browserAuth(storeId: String) = "browser_auth/$storeId"
    fun uncertainDetail(queueId: String) = "uncertain/$queueId"
}

/**
 * Bottom navigation destinations.
 */
sealed class BottomNavDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Discover : BottomNavDestination(
        route = Routes.DISCOVER,
        title = "Discover",
        icon = Icons.Default.Explore
    )
    
    data object Library : BottomNavDestination(
        route = Routes.LIBRARY,
        title = "Library",
        icon = Icons.Default.LibraryBooks
    )
    
    data object Activity : BottomNavDestination(
        route = Routes.ACTIVITY,
        title = "Activity",
        icon = Icons.Default.History
    )
    
    data object NeedsAttention : BottomNavDestination(
        route = Routes.NEEDS_ATTENTION,
        title = "Attention",
        icon = Icons.Default.Warning
    )
    
    data object Settings : BottomNavDestination(
        route = Routes.SETTINGS,
        title = "Settings",
        icon = Icons.Default.Settings
    )
    
    companion object {
        val items = listOf(Discover, Library, Activity, NeedsAttention, Settings)
    }
}

/**
 * Main navigation host for the app.
 * 
 * Phase 14: Full navigation with bottom bar destinations.
 */
@Composable
fun GameClaimerNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DISCOVER
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Main destinations
        composable(Routes.DISCOVER) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToStore = { storeId -> navController.navigate(Routes.storeDetail(storeId)) }
            )
        }
        
        composable(Routes.LIBRARY) {
            // TODO: Phase 14 - LibraryScreen with tabs
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToStore = { storeId -> navController.navigate(Routes.storeDetail(storeId)) }
            )
        }
        
        composable(Routes.ACTIVITY) {
            // TODO: Phase 14 - ActivityScreen
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToStore = { storeId -> navController.navigate(Routes.storeDetail(storeId)) }
            )
        }
        
        composable(Routes.NEEDS_ATTENTION) {
            // TODO: Phase 14 - NeedsAttentionScreen
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToStore = { storeId -> navController.navigate(Routes.storeDetail(storeId)) }
            )
        }
        
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Detail screens
        composable(Routes.STORE_DETAIL) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId") ?: ""
            StoreDetailScreen(
                storeId = storeId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAuth = { navController.navigate(Routes.browserAuth(storeId)) }
            )
        }
        
        composable(Routes.BROWSER_AUTH) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId") ?: ""
            val store = GameStore.entries.find { it.name == storeId } ?: GameStore.EPIC_GAMES
            BrowserAuthScreen(
                store = store,
                onAuthComplete = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}
