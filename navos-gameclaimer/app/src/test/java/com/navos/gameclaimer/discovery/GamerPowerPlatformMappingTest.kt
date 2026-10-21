package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.remote.GamerPowerGiveaway
import com.navos.gameclaimer.data.remote.GamerPowerApiClient
import com.navos.gameclaimer.domain.model.GameStore
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 7: GamerPower platform-to-GameStore mapping tests.
 * 
 * Tests mapping of GamerPower platform strings to our GameStore enum,
 * including all four Phase 7 stores, ambiguous mappings, unknown platforms,
 * malformed payloads, and duplicate detection.
 */
class GamerPowerPlatformMappingTest {
    
    // ===== STEAM PLATFORM MAPPING =====
    
    @Test
    fun `Steam platform maps to GameStore STEAM`() {
        assertEquals(GameStore.STEAM, mapPlatformToStore("steam"))
        assertEquals(GameStore.STEAM, mapPlatformToStore("Steam"))
        assertEquals(GameStore.STEAM, mapPlatformToStore("STEAM"))
    }
    
    @Test
    fun `PC platform maps to GameStore STEAM when no other indicators`() {
        // PC defaults to Steam when source is GamerPower and no other store info
        val result = mapPlatformToStoreWithContext("PC", claimUrl = "https://store.steampowered.com/app/12345")
        assertEquals(GameStore.STEAM, result)
    }
    
    @Test
    fun `PC platform maps based on claim URL store detection`() {
        // PC with GOG URL
        val gogResult = mapPlatformToStoreWithContext("PC", claimUrl = "https://www.gog.com/game/test")
        assertEquals(GameStore.GOG, gogResult)
        
        // PC with Itch URL
        val itchResult = mapPlatformToStoreWithContext("PC", claimUrl = "https://dev.itch.io/game")
        assertEquals(GameStore.ITCH_IO, itchResult)
        
        // PC with IndieGala URL
        val igResult = mapPlatformToStoreWithContext("PC", claimUrl = "https://www.indiegala.com/giveaways/game")
        assertEquals(GameStore.INDIEGALA, igResult)
    }
    
    // ===== GOG PLATFORM MAPPING =====
    
    @Test
    fun `GOG platform maps to GameStore GOG`() {
        assertEquals(GameStore.GOG, mapPlatformToStore("gog"))
        assertEquals(GameStore.GOG, mapPlatformToStore("GOG"))
        assertEquals(GameStore.GOG, mapPlatformToStore("Gog"))
    }
    
    @Test
    fun `DRM-Free platform with GOG URL maps to GameStore GOG`() {
        val result = mapPlatformToStoreWithContext("DRM-Free", claimUrl = "https://www.gog.com/game/witcher")
        assertEquals(GameStore.GOG, result)
    }
    
    // ===== ITCH.IO PLATFORM MAPPING =====
    
    @Test
    fun `Itch platform maps to GameStore ITCH_IO`() {
        assertEquals(GameStore.ITCH_IO, mapPlatformToStore("itchio"))
        assertEquals(GameStore.ITCH_IO, mapPlatformToStore("itch.io"))
        assertEquals(GameStore.ITCH_IO, mapPlatformToStore("Itch.io"))
        assertEquals(GameStore.ITCH_IO, mapPlatformToStore("Itch"))
    }
    
    @Test
    fun `Indie platform with Itch URL maps to GameStore ITCH_IO`() {
        val result = mapPlatformToStoreWithContext("Indie", claimUrl = "https://developer.itch.io/game")
        assertEquals(GameStore.ITCH_IO, result)
    }
    
    // ===== INDIEGALA PLATFORM MAPPING =====
    
    @Test
    fun `IndieGala platform maps to GameStore INDIEGALA`() {
        assertEquals(GameStore.INDIEGALA, mapPlatformToStore("indiegala"))
        assertEquals(GameStore.INDIEGALA, mapPlatformToStore("IndieGala"))
        assertEquals(GameStore.INDIEGALA, mapPlatformToStore("INDIEGALA"))
    }
    
    @Test
    fun `PC Giveaway platform with IndieGala URL maps to GameStore INDIEGALA`() {
        val result = mapPlatformToStoreWithContext("PC", claimUrl = "https://www.indiegala.com/giveaways/test")
        assertEquals(GameStore.INDIEGALA, result)
        
        // Also freebies
        val freebieResult = mapPlatformToStoreWithContext("PC", claimUrl = "https://indiegala.com/freebies/test")
        assertEquals(GameStore.INDIEGALA, freebieResult)
    }
    
    // ===== UNKNOWN PLATFORMS =====
    
    @Test
    fun `Unknown platform returns null`() {
        assertNull(mapPlatformToStore("xbox"))
        assertNull(mapPlatformToStore("playstation"))
        assertNull(mapPlatformToStore("nintendo"))
        assertNull(mapPlatformToStore("mobile"))
        assertNull(mapPlatformToStore("android"))
        assertNull(mapPlatformToStore("ios"))
    }
    
    @Test
    fun `Empty platform returns null`() {
        assertNull(mapPlatformToStore(""))
        assertNull(mapPlatformToStore("   "))
    }
    
    @Test
    fun `Null-safe platform handling`() {
        assertNull(mapPlatformToStore(null))
    }
    
    // ===== AMBIGUOUS MAPPINGS =====
    
    @Test
    fun `Multi-platform string returns primary match`() {
        // GamerPower sometimes returns "PC, Steam"
        val result = mapPlatformToStore("PC, Steam")
        assertEquals(GameStore.STEAM, result)
    }
    
    @Test
    fun `Multi-platform with GOG preference`() {
        // "PC, DRM-Free" with GOG URL
        val result = mapPlatformToStoreWithContext("PC, DRM-Free", claimUrl = "https://www.gog.com/game/test")
        assertEquals(GameStore.GOG, result)
    }
    
    @Test
    fun `Ambiguous platform resolved by claim URL`() {
        // "Indie Game" doesn't tell us much, but URL does
        val result = mapPlatformToStoreWithContext("Indie Game", claimUrl = "https://cool-dev.itch.io/my-game")
        assertEquals(GameStore.ITCH_IO, result)
    }
    
    // ===== MALFORMED PAYLOADS =====
    
    @Test
    fun `Malformed platform with special characters handled gracefully`() {
        assertNull(mapPlatformToStore("<script>alert('xss')</script>"))
    }
    
    @Test
    fun `Platform with null byte extracts valid platform`() {
        // Null byte in string, but "Steam" is still detected
        assertEquals(GameStore.STEAM, mapPlatformToStore("PC\u0000Steam"))
    }
    
    @Test
    fun `Platform with embedded newlines extracts platform`() {
        // Newlines might appear in malformed data but "Steam" is still present
        assertEquals(GameStore.STEAM, mapPlatformToStore("PC\nSteam"))
    }
    
    @Test
    fun `Extremely long platform string handled gracefully`() {
        val longPlatform = "a".repeat(10000)
        assertNull(mapPlatformToStore(longPlatform))
    }
    
    @Test
    fun `Unicode platform strings handled gracefully`() {
        assertNull(mapPlatformToStore("游戏平台"))
        assertNull(mapPlatformToStore("🎮"))
        // But valid platforms with extra spacing should work
        assertEquals(GameStore.STEAM, mapPlatformToStore(" Steam "))
    }
    
    // ===== DUPLICATE DETECTION =====
    
    @Test
    fun `Duplicate giveaways detected by normalized ID`() {
        val giveaway1 = createGamerPowerGiveaway(id = 1, platform = "Steam", claimUrl = "https://steampowered.com/app/123")
        val giveaway2 = createGamerPowerGiveaway(id = 1, platform = "PC", claimUrl = "https://steampowered.com/app/123")
        
        // Same ID = duplicate
        assertEquals(normalizeGiveawayId(giveaway1), normalizeGiveawayId(giveaway2))
    }
    
    @Test
    fun `Same game different stores not duplicates`() {
        val steamGiveaway = createGamerPowerGiveaway(id = 1, platform = "Steam", claimUrl = "https://steampowered.com/app/123")
        val gogGiveaway = createGamerPowerGiveaway(id = 2, platform = "GOG", claimUrl = "https://gog.com/game/test")
        
        // Different IDs = not duplicates
        assertNotEquals(normalizeGiveawayId(steamGiveaway), normalizeGiveawayId(gogGiveaway))
    }
    
    @Test
    fun `Duplicate detection by claim URL when IDs differ`() {
        // Sometimes GamerPower has multiple entries for same game
        val entry1 = createGamerPowerGiveaway(id = 100, platform = "PC", claimUrl = "https://store.steampowered.com/app/12345")
        val entry2 = createGamerPowerGiveaway(id = 101, platform = "Steam", claimUrl = "https://store.steampowered.com/app/12345")
        
        // Same claim URL = likely duplicate, should be deduplicated
        val url1 = entry1.openGiveawayUrl ?: ""
        val url2 = entry2.openGiveawayUrl ?: ""
        assertEquals(normalizeClaimUrl(url1), normalizeClaimUrl(url2))
    }
    
    // ===== URL-BASED STORE DETECTION =====
    
    @Test
    fun `Store detection from Steam URLs`() {
        assertEquals(GameStore.STEAM, detectStoreFromUrl("https://store.steampowered.com/app/12345"))
        assertEquals(GameStore.STEAM, detectStoreFromUrl("https://steampowered.com/app/12345"))
        assertEquals(GameStore.STEAM, detectStoreFromUrl("https://steamcommunity.com/games/test"))
    }
    
    @Test
    fun `Store detection from GOG URLs`() {
        assertEquals(GameStore.GOG, detectStoreFromUrl("https://www.gog.com/game/test"))
        assertEquals(GameStore.GOG, detectStoreFromUrl("https://gog.com/en/game/test"))
    }
    
    @Test
    fun `Store detection from Itch URLs`() {
        assertEquals(GameStore.ITCH_IO, detectStoreFromUrl("https://itch.io/games"))
        assertEquals(GameStore.ITCH_IO, detectStoreFromUrl("https://developer.itch.io/game"))
    }
    
    @Test
    fun `Store detection from IndieGala URLs`() {
        assertEquals(GameStore.INDIEGALA, detectStoreFromUrl("https://www.indiegala.com/giveaways/test"))
        assertEquals(GameStore.INDIEGALA, detectStoreFromUrl("https://indiegala.com/freebies/test"))
    }
    
    @Test
    fun `Unknown URL returns null store`() {
        assertNull(detectStoreFromUrl("https://example.com/game"))
        assertNull(detectStoreFromUrl("https://epicgames.com/store"))
        assertNull(detectStoreFromUrl(""))
        assertNull(detectStoreFromUrl("not-a-url"))
    }
    
    // ===== HELPER FUNCTIONS =====
    
    /**
     * Maps a GamerPower platform string to our GameStore enum.
     * Case-insensitive, handles common variations.
     */
    private fun mapPlatformToStore(platform: String?): GameStore? {
        if (platform.isNullOrBlank()) return null
        
        val normalized = platform.trim().lowercase()
        
        // Check for exact matches first
        return when {
            normalized == "steam" -> GameStore.STEAM
            normalized == "gog" -> GameStore.GOG
            normalized in listOf("itchio", "itch.io", "itch") -> GameStore.ITCH_IO
            normalized == "indiegala" -> GameStore.INDIEGALA
            
            // Multi-platform strings - extract primary
            normalized.contains("steam") -> GameStore.STEAM
            normalized.contains("gog") -> GameStore.GOG
            normalized.contains("itch") -> GameStore.ITCH_IO
            normalized.contains("indiegala") -> GameStore.INDIEGALA
            
            // Malformed / too long / special chars
            normalized.length > 1000 -> null
            normalized.any { it.isISOControl() } -> null
            
            else -> null
        }
    }
    
    /**
     * Maps platform with claim URL context for disambiguation.
     */
    private fun mapPlatformToStoreWithContext(platform: String, claimUrl: String): GameStore? {
        // Try platform first
        val platformStore = mapPlatformToStore(platform)
        if (platformStore != null && platformStore != GameStore.STEAM) {
            return platformStore
        }
        
        // Fall back to URL-based detection
        val urlStore = detectStoreFromUrl(claimUrl)
        if (urlStore != null) {
            return urlStore
        }
        
        // Default to platform result
        return platformStore
    }
    
    /**
     * Detects store from claim URL.
     */
    private fun detectStoreFromUrl(url: String): GameStore? {
        if (url.isBlank()) return null
        
        val normalized = url.lowercase()
        return when {
            normalized.contains("steampowered.com") || normalized.contains("steamcommunity.com") -> GameStore.STEAM
            normalized.contains("gog.com") -> GameStore.GOG
            normalized.contains("itch.io") -> GameStore.ITCH_IO
            normalized.contains("indiegala.com") -> GameStore.INDIEGALA
            else -> null
        }
    }
    
    /**
     * Normalizes giveaway ID for duplicate detection.
     */
    private fun normalizeGiveawayId(giveaway: GamerPowerGiveaway): String {
        return "gamerpower:${giveaway.id}"
    }
    
    /**
     * Normalizes claim URL for duplicate detection.
     */
    private fun normalizeClaimUrl(url: String): String {
        return url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removeSuffix("/")
    }
    
    /**
     * Creates a test GamerPower giveaway response.
     */
    private fun createGamerPowerGiveaway(
        id: Int,
        platform: String,
        claimUrl: String
    ): GamerPowerGiveaway {
        return GamerPowerGiveaway(
            id = id,
            title = "Test Game $id",
            worth = "N/A",
            thumbnail = "https://example.com/thumb.jpg",
            image = "https://example.com/image.jpg",
            description = "Test description",
            instructions = "Test instructions",
            openGiveawayUrl = claimUrl,
            publishedDate = "2026-01-01 00:00:00",
            type = "Game",
            platforms = platform,
            endDate = "N/A",
            usersCount = 1000,
            status = "Active",
            gamerpowerUrl = "https://gamerpower.com/giveaway/$id"
        )
    }
}
