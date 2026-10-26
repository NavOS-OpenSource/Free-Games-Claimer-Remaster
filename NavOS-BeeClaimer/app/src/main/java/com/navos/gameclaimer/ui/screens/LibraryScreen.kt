package com.navos.gameclaimer.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.navos.gameclaimer.GameClaimerApplication
import com.navos.gameclaimer.data.local.dao.GiveawayDao
import com.navos.gameclaimer.data.remote.SteamLibraryApi
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import com.navos.gameclaimer.ui.theme.LocalThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "LibraryScreen"

/**
 * Steam game from library
 */
data class SteamGame(
    val appId: Int,
    val name: String,
    val playtimeHours: Float
) {
    val headerImageUrl: String
        get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
    
    val libraryCoverUrl: String
        get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
    
    val playtimeFormatted: String
        get() = when {
            playtimeHours == 0f -> "Never played"
            playtimeHours < 1f -> "${(playtimeHours * 60).toInt()}m"
            else -> "%.1fh".format(playtimeHours)
        }
}

/**
 * Library screen - fetches games via hidden WebView to use session cookies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    giveawayDao: GiveawayDao? = null,
    onGiveawayClick: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as GameClaimerApplication
    val scope = rememberCoroutineScope()
    
    // State
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var libraryGames by remember { mutableStateOf<List<SteamGame>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Alphabetical") }
    var showSortMenu by remember { mutableStateOf(false) }
    var isSteamConnected by remember { mutableStateOf(false) }
    var playtimeFilter by remember { mutableStateOf("All") }
    
    // UI toggles
    var showSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf<SteamGame?>(null) }
    
    // WebView for fetching (hidden)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var shouldFetch by remember { mutableStateOf(true) }
    
    // Check Steam connection
    LaunchedEffect(Unit) {
        val adapter = app.adapterFactory.getAdapter(GameStore.STEAM)
        isSteamConnected = adapter?.isAuthenticated() ?: false
        if (!isSteamConnected) {
            isLoading = false
        }
    }
    
    // Filter and sort - properly track all state dependencies
    val displayedGames = remember(libraryGames, searchQuery, playtimeFilter, sortBy) {
        var filtered = if (searchQuery.isBlank()) libraryGames
        else libraryGames.filter { it.name.contains(searchQuery, ignoreCase = true) }
        
        // Apply playtime filter
        filtered = when (playtimeFilter) {
            "Never Played" -> filtered.filter { it.playtimeHours == 0f }
            "<1h" -> filtered.filter { it.playtimeHours > 0f && it.playtimeHours < 1f }
            "1-10h" -> filtered.filter { it.playtimeHours >= 1f && it.playtimeHours < 10f }
            "10-100h" -> filtered.filter { it.playtimeHours >= 10f && it.playtimeHours < 100f }
            "100h+" -> filtered.filter { it.playtimeHours >= 100f }
            else -> filtered // "All"
        }
        
        when (sortBy) {
            "Alphabetical" -> filtered.sortedBy { it.name.lowercase() }
            "Recent" -> filtered.sortedByDescending { it.appId }
            else -> filtered
        }
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("LIBRARY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSteamConnected && !isLoading) {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        IconButton(
                            onClick = {
                                shouldFetch = true
                                isRefreshing = true
                                webView?.loadUrl(SteamLibraryApi.LIBRARY_URL)
                            },
                            enabled = !isRefreshing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Hidden WebView for fetching
            if (isSteamConnected) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            @SuppressLint("SetJavaScriptEnabled")
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    Log.d(TAG, "Loading: $url")
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    Log.d(TAG, "Finished: $url")
                                    if (!shouldFetch) return
                                    
                                    // Check page type first
                                    evaluateJavascript(SteamLibraryApi.JS_CHECK_PAGE) { pageType ->
                                        val type = pageType.trim('"')
                                        Log.d(TAG, "Page type: $type")
                                        
                                        when (type) {
                                            "login" -> {
                                                shouldFetch = false
                                                errorMessage = "Session expired. Please log in to Steam again."
                                                isLoading = false
                                                isRefreshing = false
                                            }
                                            "userdata" -> {
                                                // New: Extract from userdata JSON endpoint
                                                evaluateJavascript(SteamLibraryApi.JS_EXTRACT_GAMES) { result ->
                                                    shouldFetch = false
                                                    Log.d(TAG, "Userdata result: ${result.take(500)}")
                                                    parseGamesResult(result)?.let { games ->
                                                        Log.d(TAG, "Parsed ${games.size} games from userdata")
                                                        // Show games immediately with placeholder names
                                                        libraryGames = games
                                                        errorMessage = null
                                                        isLoading = false
                                                        isRefreshing = false
                                                        
                                                        // Enrich game names in background with progressive updates
                                                        scope.launch {
                                                            try {
                                                                enrichGameNames(games) { progressGames ->
                                                                    libraryGames = progressGames
                                                                }
                                                                Log.d(TAG, "Enriched all games with names")
                                                            } catch (e: Exception) {
                                                                Log.e(TAG, "Enrichment failed", e)
                                                                // Keep original games with placeholder names
                                                            }
                                                        }
                                                    } ?: run {
                                                        // Check if error is not_logged_in
                                                        if (result.contains("not_logged_in")) {
                                                            errorMessage = "Session expired. Please log in to Steam again."
                                                        } else {
                                                            errorMessage = "Failed to parse games list"
                                                        }
                                                        isLoading = false
                                                        isRefreshing = false
                                                    }
                                                }
                                            }
                                            "games" -> {
                                                // Legacy: Community games page (if redirected)
                                                evaluateJavascript(SteamLibraryApi.JS_EXTRACT_COMMUNITY_GAMES) { result ->
                                                    shouldFetch = false
                                                    parseGamesResult(result)?.let { games ->
                                                        libraryGames = games
                                                        errorMessage = null
                                                    } ?: run {
                                                        errorMessage = "Failed to parse games list"
                                                    }
                                                    isLoading = false
                                                    isRefreshing = false
                                                }
                                            }
                                            "private" -> {
                                                shouldFetch = false
                                                errorMessage = "Profile is private"
                                                isLoading = false
                                                isRefreshing = false
                                            }
                                            else -> {
                                                // For userdata endpoint, try extracting directly
                                                view?.postDelayed({
                                                    view.evaluateJavascript(SteamLibraryApi.JS_EXTRACT_GAMES) { result ->
                                                        shouldFetch = false
                                                        Log.d(TAG, "Fallback result: ${result.take(500)}")
                                                        parseGamesResult(result)?.let { games ->
                                                            libraryGames = games
                                                            scope.launch {
                                                                enrichGameNames(games) { progressGames ->
                                                                    libraryGames = progressGames
                                                                }
                                                            }
                                                            errorMessage = null
                                                        }
                                                        isLoading = false
                                                        isRefreshing = false
                                                    }
                                                }, 500)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            webView = this
                            loadUrl(SteamLibraryApi.LIBRARY_URL)
                        }
                    },
                    modifier = Modifier.size(1.dp).alpha(0f) // Hidden
                )
            }
            
            // UI Content
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Loading library...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                !isSteamConnected -> {
                    NoConnectedStoresContent(Modifier.fillMaxSize())
                }
                errorMessage != null -> {
                    LibraryErrorContent(
                        error = errorMessage!!,
                        onRetry = {
                            shouldFetch = true
                            isRefreshing = true
                            errorMessage = null
                            webView?.loadUrl(SteamLibraryApi.LIBRARY_URL)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = BrutalistDesign.SpacingMedium.dp)) {
                        // Search bar - only show when toggled
                        if (showSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search ${libraryGames.size} games...") },
                                trailingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        // Filters - only show when toggled
                        if (showFilters) {
                            val playtimeFilters = listOf("All", "Never Played", "<1h", "1-10h", "10-100h", "100h+")
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                playtimeFilters.forEach { filter ->
                                    FilterChip(
                                        selected = playtimeFilter == filter,
                                        onClick = { playtimeFilter = filter },
                                        label = { Text(filter, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            // Sort
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("SORT:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    OutlinedButton(onClick = { showSortMenu = true }) {
                                        Text(sortBy)
                                        Text(" ▼", style = MaterialTheme.typography.bodySmall)
                                    }
                                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                        listOf("Alphabetical", "Recent").forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt) },
                                                onClick = { sortBy = opt; showSortMenu = false }
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Text("${displayedGames.size} games", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        // Games grid
                        if (displayedGames.isEmpty()) {
                            if (searchQuery.isNotBlank()) NoSearchResultsContent()
                            else LibraryEmptyState()
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(displayedGames, key = { it.appId }) { game ->
                                    SteamGameCard(game = game, onClick = { selectedGame = game })
                                }
                            }
                        }
                    }
                    
                    // Game detail dialog
                    selectedGame?.let { game ->
                        GameDetailDialog(game = game, onDismiss = { selectedGame = null })
                    }
                }
            }
        }
    }
}

private fun parseGamesResult(jsonStr: String): List<SteamGame>? {
    return try {
        val cleaned = jsonStr.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\n", "")
        val json = JSONObject(cleaned)
        if (!json.optBoolean("success", false)) return null
        
        val gamesArray = json.optJSONArray("games") ?: return emptyList()
        val games = mutableListOf<SteamGame>()
        
        for (i in 0 until gamesArray.length()) {
            val g = gamesArray.getJSONObject(i)
            games.add(SteamGame(
                appId = g.optInt("appId", 0),
                name = g.optString("name", "Unknown"),
                playtimeHours = g.optDouble("hours", 0.0).toFloat()
            ))
        }
        
        Log.d(TAG, "Parsed ${games.size} games")
        games
    } catch (e: Exception) {
        Log.e(TAG, "Parse error", e)
        null
    }
}

/**
 * Enrich game list with names from Steam API.
 * Fetches names in batches and updates state progressively.
 * Uses a simple in-memory cache to avoid re-fetching.
 */
private val gameNameCache = mutableMapOf<Int, String>()

// Well-known games that may not return from appdetails API
private val WELL_KNOWN_GAMES = mapOf(
    730 to "Counter-Strike 2",
    570 to "Dota 2",
    440 to "Team Fortress 2",
    578080 to "PUBG: BATTLEGROUNDS",
    1172470 to "Apex Legends",
    252490 to "Rust",
    271590 to "Grand Theft Auto V",
    1085660 to "Destiny 2",
    1245620 to "Elden Ring",
    892970 to "Valheim",
    1091500 to "Cyberpunk 2077",
    413150 to "Stardew Valley",
    814380 to "Sekiro: Shadows Die Twice",
    374320 to "Dark Souls III",
    1817070 to "Marvel Rivals",
    1623730 to "Palworld",
    553850 to "Helldivers 2",
    2358720 to "Black Myth: Wukong",
    1966720 to "Lethal Company",
    1203220 to "Naraka: Bladepoint"
)

private suspend fun enrichGameNames(
    games: List<SteamGame>,
    onProgress: (List<SteamGame>) -> Unit
): List<SteamGame> {
    if (games.isEmpty()) return games
    
    return withContext(Dispatchers.IO) {
        val enriched = games.toMutableList()
        val batchSize = 10
        val batches = games.chunked(batchSize)
        
        for ((batchIndex, batch) in batches.withIndex()) {
            val batchEnriched = batch.map { game ->
                // Check cache first
                val cachedName = gameNameCache[game.appId]
                if (cachedName != null) {
                    return@map SteamGame(appId = game.appId, name = cachedName, playtimeHours = game.playtimeHours)
                }
                
                // Skip if already has real name (not placeholder)
                if (!game.name.startsWith("App ")) {
                    return@map game
                }
                
                // Check well-known games fallback (some games don't return from appdetails)
                val wellKnownName = WELL_KNOWN_GAMES[game.appId]
                if (wellKnownName != null) {
                    gameNameCache[game.appId] = wellKnownName
                    return@map SteamGame(appId = game.appId, name = wellKnownName, playtimeHours = game.playtimeHours)
                }
                
                try {
                    val url = URL("https://store.steampowered.com/api/appdetails?appids=${game.appId}&l=english")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val appData = json.optJSONObject(game.appId.toString())
                    
                    if (appData?.optBoolean("success") == true) {
                        val data = appData.optJSONObject("data")
                        val name = data?.optString("name")?.takeIf { it.isNotBlank() } 
                            ?: "Unknown (${game.appId})"
                        gameNameCache[game.appId] = name
                        SteamGame(appId = game.appId, name = name, playtimeHours = game.playtimeHours)
                    } else {
                        // API returned success:false - mark as unknown but cache it
                        val fallbackName = "Unknown (${game.appId})"
                        gameNameCache[game.appId] = fallbackName
                        SteamGame(appId = game.appId, name = fallbackName, playtimeHours = game.playtimeHours)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch name for ${game.appId}: ${e.message}")
                    game
                }
            }
            
            // Update enriched list with this batch's results
            for ((i, enrichedGame) in batchEnriched.withIndex()) {
                val originalIndex = batchIndex * batchSize + i
                if (originalIndex < enriched.size) {
                    enriched[originalIndex] = enrichedGame
                }
            }
            
            // Report progress after each batch
            onProgress(enriched.toList())
            
            // Small delay between batches to avoid rate limiting
            if (batchIndex < batches.size - 1) {
                delay(100)
            }
        }
        
        enriched
    }
}

@Composable
private fun Modifier.alpha(alpha: Float) = this.then(Modifier.background(Color.Transparent))

@Composable
private fun SteamGameCard(game: SteamGame, onClick: () -> Unit) {
    val themeState = LocalThemeState.current
    val cornerRadius = themeState.cornerRadius.dp
    var useFallback by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.aspectRatio(0.67f).clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a1a))
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(if (useFallback) game.headerImageUrl else game.libraryCoverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { if (it is AsyncImagePainter.State.Error && !useFallback) useFallback = true }
            )
        }
    }
}

@Composable
private fun NoConnectedStoresContent(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(24.dp))
            Text("No stores connected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Connect Steam to see your library", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LibraryErrorContent(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Failed to load library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LibraryEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CollectionsBookmark, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text("No games found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoSearchResultsContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text("No games match your search", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GameDetailDialog(game: SteamGame, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(game.name, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                AsyncImage(
                    model = game.headerImageUrl,
                    contentDescription = game.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(460f/215f),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(16.dp))
                Text("App ID: ${game.appId}", style = MaterialTheme.typography.bodyMedium)
                Text("Playtime: ${game.playtimeFormatted}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("Store: store.steampowered.com/app/${game.appId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
