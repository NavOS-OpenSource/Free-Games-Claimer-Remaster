package com.navos.gameclaimer.data.remote

import com.navos.gameclaimer.domain.model.DiscoveryResult
import com.navos.gameclaimer.domain.model.DiscoverySource
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.Giveaway
import com.navos.gameclaimer.domain.model.GiveawayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 6: GamerPower API Client
 * 
 * Fetches giveaway data from the GamerPower public API.
 * https://www.gamerpower.com/api-read
 * 
 * SECURITY:
 * - HTTPS only
 * - All response data treated as UNTRUSTED
 * - URLs validated before use
 * - No credentials stored or transmitted
 * 
 * LICENSE:
 * GamerPower API is free for non-commercial use with attribution.
 * Commercial use requires contacting GamerPower.
 * See: https://www.gamerpower.com/api-read
 */
class GamerPowerApiClient {
    
    companion object {
        private const val BASE_URL = "https://www.gamerpower.com/api"
        private const val GIVEAWAYS_ENDPOINT = "/giveaways"
        
        // User-Agent for API requests
        private const val USER_AGENT = "NavOS-GameClaimer/1.0 (Android)"
        
        // Connection timeouts
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    /**
     * Fetch giveaways for a specific platform.
     * 
     * @param platform Platform filter (e.g., "pc", "steam", "epic-games-store")
     * @param type Type filter (e.g., "game", "loot", "beta")
     * @return DiscoveryResult with giveaways or error
     */
    suspend fun fetchGiveaways(
        platform: String? = null,
        type: String? = null
    ): DiscoveryResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Build URL with query parameters
            val urlBuilder = StringBuilder(BASE_URL + GIVEAWAYS_ENDPOINT)
            val params = mutableListOf<String>()
            platform?.let { params.add("platform=$it") }
            type?.let { params.add("type=$it") }
            if (params.isNotEmpty()) {
                urlBuilder.append("?")
                urlBuilder.append(params.joinToString("&"))
            }
            
            val url = URL(urlBuilder.toString())
            
            // Verify HTTPS
            if (url.protocol != "https") {
                return@withContext DiscoveryResult.Failed(
                    source = DiscoverySource.GAMER_POWER,
                    error = "Non-HTTPS URL rejected",
                    isRetryable = false
                )
            }
            
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                
                val responseCode = connection.responseCode
                
                when {
                    responseCode == HttpURLConnection.HTTP_OK -> {
                        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                        val apiGiveaways = parseResponse(responseBody)
                        val giveaways = apiGiveaways.mapNotNull { convertToGiveaway(it) }
                        
                        val duration = System.currentTimeMillis() - startTime
                        
                        if (giveaways.isEmpty()) {
                            DiscoveryResult.Empty(
                                source = DiscoverySource.GAMER_POWER,
                                durationMs = duration
                            )
                        } else {
                            DiscoveryResult.Success(
                                giveaways = giveaways,
                                source = DiscoverySource.GAMER_POWER,
                                durationMs = duration
                            )
                        }
                    }
                    responseCode == HttpURLConnection.HTTP_NOT_FOUND -> {
                        // 404 often means no giveaways found for filter
                        DiscoveryResult.Empty(
                            source = DiscoverySource.GAMER_POWER,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }
                    responseCode in 500..599 -> {
                        DiscoveryResult.Failed(
                            source = DiscoverySource.GAMER_POWER,
                            error = "Server error: $responseCode",
                            isRetryable = true
                        )
                    }
                    responseCode == 429 -> {
                        DiscoveryResult.Failed(
                            source = DiscoverySource.GAMER_POWER,
                            error = "Rate limited",
                            isRetryable = true
                        )
                    }
                    else -> {
                        DiscoveryResult.Failed(
                            source = DiscoverySource.GAMER_POWER,
                            error = "HTTP $responseCode",
                            isRetryable = false
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: java.net.SocketTimeoutException) {
            DiscoveryResult.Failed(
                source = DiscoverySource.GAMER_POWER,
                error = "Connection timeout",
                isRetryable = true
            )
        } catch (e: java.net.UnknownHostException) {
            DiscoveryResult.Failed(
                source = DiscoverySource.GAMER_POWER,
                error = "Network unavailable",
                isRetryable = true
            )
        } catch (e: Exception) {
            DiscoveryResult.Failed(
                source = DiscoverySource.GAMER_POWER,
                error = e.message ?: "Unknown error",
                isRetryable = true
            )
        }
    }
    
    /**
     * Parse JSON response into API models.
     * 
     * SECURITY: Treats all input as untrusted.
     */
    private fun parseResponse(body: String): List<GamerPowerGiveaway> {
        return try {
            // Handle empty response or error object
            if (body.isBlank() || body.trim().startsWith("{")) {
                emptyList()
            } else {
                json.decodeFromString<List<GamerPowerGiveaway>>(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Convert API model to domain model.
     * 
     * SECURITY:
     * - Validates all URLs
     * - Sanitizes text fields
     * - Maps to appropriate store
     * 
     * Store resolution priority:
     * 1. External store from URL host (Fanatical, Alienware Arena)
     * 2. Platform field mapping (Steam, Epic, GOG, etc.)
     * 3. Instructions text hints (fallback)
     */
    private fun convertToGiveaway(api: GamerPowerGiveaway): Giveaway? {
        // Validate required fields
        if (api.id <= 0) return null
        if (api.title.isNullOrBlank()) return null
        if (api.openGiveawayUrl.isNullOrBlank()) return null
        
        // Validate URL is HTTPS
        val claimUrl = api.openGiveawayUrl
        if (!claimUrl.startsWith("https://")) return null
        
        // Map platform to store with priority: URL host > platform > instructions
        val store = mapUrlToExternalStore(claimUrl)
            ?: mapPlatformToStore(api.platforms)
            ?: mapInstructionsToStore(api.instructions)
            ?: return null
        
        // Map type
        val type = mapGiveawayType(api.type)
        
        // Generate unique ID
        val id = "gamerpower:${store.name.lowercase()}:${api.id}"
        
        return Giveaway(
            id = id,
            title = api.title.take(200), // Limit title length
            store = store,
            type = type,
            claimUrl = claimUrl,
            originalPrice = api.worth?.takeIf { it != "N/A" },
            imageUrl = api.thumbnail?.takeIf { it.startsWith("https://") },
            description = api.description?.take(1000),
            startTime = null, // GamerPower doesn't provide exact start time
            endTime = parseEndDate(api.endDate),
            source = DiscoverySource.GAMER_POWER,
            externalId = api.id.toString()
        )
    }
    
    /**
     * Map GamerPower platform string to our GameStore enum.
     * 
     * GamerPower platforms include: PC, Steam, Epic Games Store, GOG, Playstation,
     * Xbox, Nintendo Switch, Android, iOS, DRM-Free, Itch.io, IndieGala.
     * 
     * External redemption sites (Fanatical, Alienware Arena) are identified
     * via open_giveaway_url host or instructions text, not platform field.
     */
    fun mapPlatformToStore(platforms: String?): GameStore? {
        if (platforms.isNullOrBlank()) return null
        
        val lower = platforms.lowercase()
        return when {
            "epic" in lower || "epic-games" in lower -> GameStore.EPIC_GAMES
            "steam" in lower -> GameStore.STEAM
            "gog" in lower -> GameStore.GOG
            "amazon" in lower || "prime" in lower -> GameStore.AMAZON_PRIME
            "itch.io" in lower || "itchio" in lower -> GameStore.ITCH_IO
            "indiegala" in lower -> GameStore.INDIEGALA
            else -> null // Unsupported platform
        }
    }
    
    /**
     * Identify external redemption site from giveaway URL.
     * 
     * GamerPower hosts giveaways for external sites like Fanatical and
     * Alienware Arena. These are identified by the open_giveaway_url host,
     * not the platform field.
     * 
     * SECURITY: Validates URL structure before host extraction.
     */
    fun mapUrlToExternalStore(url: String?): GameStore? {
        if (url.isNullOrBlank()) return null
        if (!url.startsWith("https://")) return null
        
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return null
            
            when {
                host == "fanatical.com" || host.endsWith(".fanatical.com") -> GameStore.FANATICAL
                host == "alienwarearena.com" || host.endsWith(".alienwarearena.com") -> GameStore.ALIENWARE_ARENA
                host == "indiegala.com" || host.endsWith(".indiegala.com") -> GameStore.INDIEGALA
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Identify store from instructions text when URL/platform are ambiguous.
     * 
     * GamerPower instructions often mention where to redeem keys.
     */
    fun mapInstructionsToStore(instructions: String?): GameStore? {
        if (instructions.isNullOrBlank()) return null
        
        val lower = instructions.lowercase()
        return when {
            "fanatical" in lower -> GameStore.FANATICAL
            "alienware" in lower -> GameStore.ALIENWARE_ARENA
            "indiegala" in lower -> GameStore.INDIEGALA
            else -> null
        }
    }
    
    /**
     * Map GamerPower type to our GiveawayType.
     */
    private fun mapGiveawayType(type: String?): GiveawayType {
        return when (type?.lowercase()) {
            "game" -> GiveawayType.FREE_TO_KEEP
            "dlc" -> GiveawayType.DLC_OR_ADDON
            "loot" -> GiveawayType.DLC_OR_ADDON
            "beta" -> GiveawayType.DEMO_OR_TRIAL
            else -> GiveawayType.UNKNOWN
        }
    }
    
    /**
     * Parse end date string to epoch millis.
     */
    private fun parseEndDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank() || dateStr == "N/A") return null
        
        return try {
            // GamerPower format: "2024-12-31 23:59:59"
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
            formatter.parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * GamerPower API response model.
 * 
 * SECURITY: All fields are nullable/have defaults because external data is untrusted.
 */
@Serializable
data class GamerPowerGiveaway(
    val id: Int = 0,
    val title: String? = null,
    val worth: String? = null,
    val thumbnail: String? = null,
    val image: String? = null,
    val description: String? = null,
    val instructions: String? = null,
    val platforms: String? = null,
    val type: String? = null,
    val status: String? = null,
    
    @SerialName("open_giveaway_url")
    val openGiveawayUrl: String? = null,
    
    @SerialName("gamerpower_url")
    val gamerpowerUrl: String? = null,
    
    @SerialName("end_date")
    val endDate: String? = null,
    
    @SerialName("published_date")
    val publishedDate: String? = null,
    
    @SerialName("users")
    val usersCount: Int = 0
)
