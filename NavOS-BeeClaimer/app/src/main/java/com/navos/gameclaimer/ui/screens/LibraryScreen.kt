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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

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
    var sortBy by remember { mutableStateOf("Play Time") }
    var showSortMenu by remember { mutableStateOf(false) }
    var isSteamConnected by remember { mutableStateOf(false) }
    var playtimeFilter by remember { mutableStateOf("All") } // All, Never Played, <1h, 1-10h, 10-100h, 100h+
    
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
            "Play Time" -> filtered.sortedByDescending { it.playtimeHours }
            "Alphabetical" -> filtered.sortedBy { it.name.lowercase() }
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
                                            "games" -> {
                                                // Extract games
                                                evaluateJavascript(SteamLibraryApi.JS_EXTRACT_GAMES) { result ->
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
                                                // Wait a bit and retry
                                                view?.postDelayed({
                                                    view.evaluateJavascript(SteamLibraryApi.JS_EXTRACT_GAMES) { result ->
                                                        shouldFetch = false
                                                        parseGamesResult(result)?.let { games ->
                                                            libraryGames = games
                                                            errorMessage = null
                                                        }
                                                        isLoading = false
                                                        isRefreshing = false
                                                    }
                                                }, 1000)
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
                        // Search
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search ${libraryGames.size} games...") },
                            trailingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Playtime filter chips
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
                        
                        Spacer(Modifier.height(12.dp))
                        
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
                                    listOf("Play Time", "Alphabetical").forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt) },
                                            onClick = { sortBy = opt; showSortMenu = false }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            if (isRefreshing) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                val hasFilters = searchQuery.isNotBlank() || playtimeFilter != "All"
                                val countText = if (hasFilters && displayedGames.size != libraryGames.size) {
                                    "${displayedGames.size} of ${libraryGames.size} games"
                                } else {
                                    "${displayedGames.size} games"
                                }
                                Text(countText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        if (displayedGames.isEmpty()) {
                            if (searchQuery.isNotBlank()) NoSearchResultsContent()
                            else LibraryEmptyState()
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayedGames, key = { it.appId }) { game ->
                                    SteamGameCard(game = game, onClick = {})
                                }
                            }
                        }
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
            
            Box(
                Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            )
            
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(game.name, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (game.playtimeHours > 0) {
                    Text(game.playtimeFormatted, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
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
