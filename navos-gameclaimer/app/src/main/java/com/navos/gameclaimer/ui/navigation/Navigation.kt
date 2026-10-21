package com.navos.gameclaimer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.ui.screens.BrowserAuthScreen
import com.navos.gameclaimer.ui.screens.HomeScreen
import com.navos.gameclaimer.ui.screens.SettingsScreen
import com.navos.gameclaimer.ui.screens.StoreDetailScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val STORE_DETAIL = "store/{storeId}"
    const val BROWSER_AUTH = "browser_auth/{storeId}"
    
    fun storeDetail(storeId: String) = "store/$storeId"
    fun browserAuth(storeId: String) = "browser_auth/$storeId"
}

/**
 * Main navigation host for the app.
 * 
 * Manages screen transitions and back stack.
 */
@Composable
fun GameClaimerNavHost() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
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
