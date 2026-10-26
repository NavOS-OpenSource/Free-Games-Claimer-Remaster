package com.navos.gameclaimer.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.ui.screens.*
import com.navos.gameclaimer.ui.viewmodel.NeedsAttentionViewModel

/**
 * Phase 23: Navigation redesign with three primary tabs and swipe support
 * 
 * Bottom Navigation:
 * - Stores: Browse game stores, authentication, browser
 * - Games: Discover giveaways, library, activity
 * - User: Settings, profile, preferences
 * 
 * Swipe navigation between main tabs.
 * Back behavior preserves tab state when switching.
 */
object Routes {
    // Splash screen
    const val SPLASH = "splash"
    
    // Primary tab roots
    const val STORES_ROOT = "stores"
    const val GAMES_ROOT = "games"
    const val USER_ROOT = "user"
    
    // Main tabs pager (single route that handles all 3 main tabs with swipe)
    const val MAIN_TABS = "main_tabs/{initialTab}"
    
    // Stores tab screens
    const val STORES_LIST = "stores/list"
    const val STORE_DETAIL = "stores/detail/{storeId}"
    const val STORE_BROWSER = "stores/browser/{storeId}"
    const val AUTOMATION_BROWSER = "stores/automation"
    
    // Games tab screens
    const val GAMES_DISCOVER = "games/discover"
    const val GAMES_LIBRARY = "user/library"
    const val GAMES_ACTIVITY = "games/activity"
    const val GAMES_ATTENTION = "games/attention"
    const val GIVEAWAY_DETAIL = "games/giveaway/{giveawayId}"
    const val STEAM_CLAIM = "games/steam-claim"
    
    // User tab screens
    const val USER_PROFILE = "user/profile"
    const val USER_SETTINGS = "user/settings"
    
    // Debug screens (prototype)
    const val MULTI_ACCOUNT_EXPERIMENT = "debug/multi-account"
    const val MANUAL_CLAIM = "debug/manual-claim"
    
    // Route builders
    fun mainTabs(initialTab: Int = 1) = "main_tabs/$initialTab"
    fun storeDetail(storeId: String) = "stores/detail/$storeId"
    fun storeBrowser(storeId: String) = "stores/browser/$storeId"
    fun giveawayDetail(giveawayId: String) = "games/giveaway/$giveawayId"
    
    // Legacy compatibility
    const val HOME = GAMES_DISCOVER
    const val SETTINGS = USER_SETTINGS
    const val DISCOVER = GAMES_DISCOVER
    const val LIBRARY = GAMES_LIBRARY
    const val ACTIVITY = GAMES_ACTIVITY
    const val NEEDS_ATTENTION = GAMES_ATTENTION
    const val BROWSER_AUTH = STORE_BROWSER
}

/**
 * Phase 23: Bottom navigation with three primary destinations
 */
sealed class BottomNavDestination(
    val route: String,
    val tabIndex: Int,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Stores : BottomNavDestination(
        route = Routes.STORES_LIST,
        tabIndex = 0,
        title = "Stores",
        selectedIcon = Icons.Filled.Storefront,
        unselectedIcon = Icons.Outlined.Storefront
    )
    
    data object Games : BottomNavDestination(
        route = Routes.GAMES_DISCOVER,
        tabIndex = 1,
        title = "Games",
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports
    )
    
    data object User : BottomNavDestination(
        route = Routes.USER_PROFILE,
        tabIndex = 2,
        title = "User",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
    
    companion object {
        val items: List<BottomNavDestination> = listOf(Stores, Games, User)
    }
}

/**
 * Phase 23: Main app scaffold with bottom navigation and swipe support
 */
@Composable
fun GameClaimerNavHost(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    
    // Track which main tab we're on (persists across navigation)
    var currentMainTabIndex by remember { mutableIntStateOf(1) } // Default to Games
    
    // Update tab index when on main tabs
    LaunchedEffect(currentRoute) {
        if (currentRoute?.startsWith("main_tabs/") == true) {
            val tabArg = navBackStackEntry?.arguments?.getInt("initialTab") ?: 1
            currentMainTabIndex = tabArg
        }
    }
    
    // Show bottom nav except on splash and detail screens (routes with parameters other than main_tabs)
    val showBottomNav = remember(currentRoute) {
        when {
            currentRoute == null -> false
            currentRoute == Routes.SPLASH -> false
            currentRoute.contains("{") && !currentRoute.startsWith("main_tabs") -> false
            else -> true
        }
    }
    
    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                BrutalistBottomNavigation(
                    destinations = BottomNavDestination.items,
                    currentIndex = currentMainTabIndex,
                    onNavigate = { index ->
                        currentMainTabIndex = index
                        // Only navigate if not already on main tabs - the pager handles tab switching
                        if (currentRoute?.startsWith("main_tabs") != true) {
                            navController.navigate(Routes.mainTabs(index)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            // ========== SPLASH SCREEN ==========
            composable(Routes.SPLASH) {
                SplashScreen(
                    onSplashComplete = {
                        navController.navigate(Routes.mainTabs(1)) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            
            // ========== MAIN TABS (swipeable pager) ==========
            composable(
                route = Routes.MAIN_TABS,
                arguments = listOf(navArgument("initialTab") { 
                    type = NavType.IntType
                    defaultValue = 1
                })
            ) { backStackEntry ->
                // Use currentMainTabIndex (reactive) instead of route arg (only read at nav time)
                MainTabPager(
                    navController = navController,
                    targetPage = currentMainTabIndex,
                    onPageChanged = { newIndex ->
                        currentMainTabIndex = newIndex
                    }
                )
            }
            
            // ========== STORES DETAIL SCREENS ==========
            composable(Routes.AUTOMATION_BROWSER) {
                SteamClaimScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(
                route = Routes.STORE_DETAIL,
                arguments = listOf(navArgument("storeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val storeId = backStackEntry.arguments?.getString("storeId") ?: ""
                StoreDetailScreen(
                    storeId = storeId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAuth = { navController.navigate(Routes.storeBrowser(storeId)) }
                )
            }
            
            composable(
                route = Routes.STORE_BROWSER,
                arguments = listOf(navArgument("storeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val storeId = backStackEntry.arguments?.getString("storeId") ?: ""
                val store = GameStore.entries.find { it.name == storeId } ?: GameStore.EPIC_GAMES
                BrowserAuthScreen(
                    store = store,
                    onAuthComplete = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            
            // ========== GAMES DETAIL SCREENS ==========
            composable(Routes.STEAM_CLAIM) {
                SteamClaimScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(Routes.GAMES_LIBRARY) {
                LibraryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(Routes.GAMES_ACTIVITY) {
                val context = LocalContext.current
                val database = remember { GameClaimerDatabase.getInstance(context) }
                ActivityScreen(
                    claimQueueDao = database.claimQueueDao(),
                    onUncertainClick = { queueId ->
                        // TODO: Navigate to uncertain detail
                    }
                )
            }
            
            composable(Routes.GAMES_ATTENTION) {
                val context = LocalContext.current
                val database = remember { GameClaimerDatabase.getInstance(context) }
                val viewModel = remember {
                    NeedsAttentionViewModel(
                        claimQueueDao = database.claimQueueDao(),
                        giveawayDao = database.giveawayDao()
                    )
                }
                NeedsAttentionScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenStore = { storeId ->
                        navController.navigate(Routes.storeDetail(storeId))
                    }
                )
            }
            
            composable(
                route = Routes.GIVEAWAY_DETAIL,
                arguments = listOf(navArgument("giveawayId") { type = NavType.StringType })
            ) { backStackEntry ->
                val giveawayId = backStackEntry.arguments?.getString("giveawayId") ?: ""
                GiveawayDetailScreen(
                    giveawayId = giveawayId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            // ========== USER DETAIL SCREENS ==========
            composable(Routes.USER_SETTINGS) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMultiAccountExperiment = {
                        navController.navigate(Routes.MULTI_ACCOUNT_EXPERIMENT)
                    },
                    onNavigateToManualClaim = {
                        navController.navigate(Routes.MANUAL_CLAIM)
                    }
                )
            }
            
            // ========== DEBUG SCREENS ==========
            composable(Routes.MULTI_ACCOUNT_EXPERIMENT) {
                MultiAccountExperimentScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(Routes.MANUAL_CLAIM) {
                ManualClaimScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Swipeable main tab pager - allows swiping between Stores, Games, and User tabs
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MainTabPager(
    navController: NavHostController,
    targetPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = targetPage,
        pageCount = { 3 }
    )
    
    // Scroll to target page when bottom nav is tapped
    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    
    // Notify parent when page changes via swipe
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondBoundsPageCount = 1
    ) { page ->
        when (page) {
            0 -> StoresListScreen(
                onNavigateToStore = { storeId ->
                    navController.navigate(Routes.storeDetail(storeId))
                },
                onNavigateToAutomation = {
                    navController.navigate(Routes.AUTOMATION_BROWSER)
                }
            )
            1 -> GamesDiscoverScreen(
                onNavigateToGiveaway = { giveawayId ->
                    navController.navigate(Routes.giveawayDetail(giveawayId))
                },
                onNavigateToSteamClaim = {
                    navController.navigate(Routes.STEAM_CLAIM)
                }
            )
            2 -> UserProfileScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.USER_SETTINGS)
                },
                onNavigateToLibrary = {
                    navController.navigate(Routes.GAMES_LIBRARY)
                }
            )
        }
    }
}

/**
 * Phase 23: Brutalist bottom navigation bar with index-based selection
 */
@Composable
fun BrutalistBottomNavigation(
    destinations: List<BottomNavDestination>,
    currentIndex: Int,
    onNavigate: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        destinations.forEachIndexed { index, destination ->
            val selected = currentIndex == index
            
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(index) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.title
                    )
                },
                label = {
                    Text(
                        text = destination.title,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
