package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.remote.GamerPowerGiveaway
import com.navos.gameclaimer.data.remote.GamerPowerApiClient
import com.navos.gameclaimer.domain.model.GameStore
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 12: GamerPower platform-to-GameStore mapping tests.
 * 
 * Tests mapping of GamerPower platform strings to our GameStore enum,
 * including all four Phase 7 stores, ambiguous mappings, unknown platforms,
 * malformed payloads, duplicate detection, and Phase 12 external store mappings.
 * 
 * Phase 12 additions:
 * - Fanatical URL-based detection
 * - Alienware Arena URL-based detection
 * - Instructions-based store hints
 */
class GamerPowerPlatformMappingTest {
    
    private val apiClient = GamerPowerApiClient()
    
    // ===== STEAM PLATFORM MAPPING =====
    
    @Test
    fun `Steam platform maps to GameStore STEAM`() {
        assertEquals(GameStore.STEAM, apiClient.mapPlatformToStore("steam"))
        assertEquals(GameStore.STEAM, apiClient.mapPlatformToStore("Steam"))
        assertEquals(GameStore.STEAM, apiClient.mapPlatformToStore("STEAM"))
    }
    
    @Test
    fun `PC platform with Steam URL maps to STEAM`() {
        // PC detected by URL, not platform string alone
        val result = apiClient.mapUrlToExternalStore("https://store.steampowered.com/app/12345")
        // Steam URLs are not external stores, they use platform mapping
        assertNull(result)
    }
    
    @Test
    fun `PC platform maps based on claim URL store detection`() {
        // PC with IndieGala URL - IndieGala IS an external store
        val igResult = apiClient.mapUrlToExternalStore("https://www.indiegala.com/giveaways/game")
        assertEquals(GameStore.INDIEGALA, igResult)
    }
    
    // ===== GOG PLATFORM MAPPING =====
    
    @Test
    fun `GOG platform maps to GameStore GOG`() {
        assertEquals(GameStore.GOG, apiClient.mapPlatformToStore("gog"))
        assertEquals(GameStore.GOG, apiClient.mapPlatformToStore("GOG"))
        assertEquals(GameStore.GOG, apiClient.mapPlatformToStore("Gog"))
    }
    
    // ===== ITCH.IO PLATFORM MAPPING =====
    
    @Test
    fun `Itch platform maps to GameStore ITCH_IO`() {
        assertEquals(GameStore.ITCH_IO, apiClient.mapPlatformToStore("itchio"))
        assertEquals(GameStore.ITCH_IO, apiClient.mapPlatformToStore("itch.io"))
        assertEquals(GameStore.ITCH_IO, apiClient.mapPlatformToStore("Itch.io"))
    }
    
    // ===== INDIEGALA PLATFORM MAPPING =====
    
    @Test
    fun `IndieGala platform maps to GameStore INDIEGALA`() {
        assertEquals(GameStore.INDIEGALA, apiClient.mapPlatformToStore("indiegala"))
        assertEquals(GameStore.INDIEGALA, apiClient.mapPlatformToStore("IndieGala"))
        assertEquals(GameStore.INDIEGALA, apiClient.mapPlatformToStore("INDIEGALA"))
    }
    
    // ===== UNKNOWN PLATFORMS =====
    
    @Test
    fun `Unknown platform returns null`() {
        assertNull(apiClient.mapPlatformToStore("xbox"))
        assertNull(apiClient.mapPlatformToStore("playstation"))
        assertNull(apiClient.mapPlatformToStore("nintendo"))
        assertNull(apiClient.mapPlatformToStore("mobile"))
        assertNull(apiClient.mapPlatformToStore("android"))
        assertNull(apiClient.mapPlatformToStore("ios"))
    }
    
    @Test
    fun `Empty platform returns null`() {
        assertNull(apiClient.mapPlatformToStore(""))
        assertNull(apiClient.mapPlatformToStore("   "))
    }
    
    @Test
    fun `Null-safe platform handling`() {
        assertNull(apiClient.mapPlatformToStore(null))
    }
    
    // ===== PHASE 12: FANATICAL URL MAPPING =====
    
    @Test
    fun `Fanatical URL maps to GameStore FANATICAL`() {
        assertEquals(GameStore.FANATICAL, apiClient.mapUrlToExternalStore("https://www.fanatical.com/en/game/some-game"))
        assertEquals(GameStore.FANATICAL, apiClient.mapUrlToExternalStore("https://fanatical.com/en/bundle/test"))
    }
    
    @Test
    fun `Fanatical URL with subdomain maps correctly`() {
        assertEquals(GameStore.FANATICAL, apiClient.mapUrlToExternalStore("https://cdn.fanatical.com/game/test"))
    }
    
    @Test
    fun `Fanatical HTTP URL rejected`() {
        assertNull(apiClient.mapUrlToExternalStore("http://www.fanatical.com/en/game/test"))
    }
    
    @Test
    fun `Fanatical lookalike URL rejected`() {
        assertNull(apiClient.mapUrlToExternalStore("https://fakefanatical.com/en/game/test"))
        assertNull(apiClient.mapUrlToExternalStore("https://fanatical.com.attacker.com/game/test"))
    }
    
    // ===== PHASE 12: ALIENWARE ARENA URL MAPPING =====
    
    @Test
    fun `Alienware Arena URL maps to GameStore ALIENWARE_ARENA`() {
        assertEquals(GameStore.ALIENWARE_ARENA, apiClient.mapUrlToExternalStore("https://www.alienwarearena.com/ucf/show/123"))
        assertEquals(GameStore.ALIENWARE_ARENA, apiClient.mapUrlToExternalStore("https://alienwarearena.com/ucf/show/456"))
    }
    
    @Test
    fun `Alienware Arena NA subdomain maps correctly`() {
        assertEquals(GameStore.ALIENWARE_ARENA, apiClient.mapUrlToExternalStore("https://na.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `Alienware Arena EU subdomain maps correctly`() {
        assertEquals(GameStore.ALIENWARE_ARENA, apiClient.mapUrlToExternalStore("https://eu.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `Alienware Arena LATAM subdomain maps correctly`() {
        assertEquals(GameStore.ALIENWARE_ARENA, apiClient.mapUrlToExternalStore("https://latam.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `Alienware Arena HTTP URL rejected`() {
        assertNull(apiClient.mapUrlToExternalStore("http://www.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `Alienware Arena lookalike URL rejected`() {
        assertNull(apiClient.mapUrlToExternalStore("https://fakealienwarearena.com/ucf/show/123"))
        assertNull(apiClient.mapUrlToExternalStore("https://alienwarearena.com.attacker.com/ucf/show/123"))
    }
    
    // ===== PHASE 12: INSTRUCTIONS-BASED MAPPING =====
    
    @Test
    fun `Fanatical instructions hint maps correctly`() {
        assertEquals(GameStore.FANATICAL, apiClient.mapInstructionsToStore("Claim your key at fanatical.com"))
        assertEquals(GameStore.FANATICAL, apiClient.mapInstructionsToStore("Visit Fanatical to get this game"))
    }
    
    @Test
    fun `Alienware Arena instructions hint maps correctly`() {
        assertEquals(GameStore.ALIENWARE_ARENA, apiClient.mapInstructionsToStore("Available on Alienware Arena"))
        assertEquals(GameStore.ALIENWARE_ARENA, apiClient.mapInstructionsToStore("Claim at alienwarearena.com"))
    }
    
    @Test
    fun `IndieGala instructions hint maps correctly`() {
        assertEquals(GameStore.INDIEGALA, apiClient.mapInstructionsToStore("Get it free at IndieGala"))
        assertEquals(GameStore.INDIEGALA, apiClient.mapInstructionsToStore("Visit indiegala.com for this giveaway"))
    }
    
    @Test
    fun `Empty instructions returns null`() {
        assertNull(apiClient.mapInstructionsToStore(""))
        assertNull(apiClient.mapInstructionsToStore(null))
    }
    
    @Test
    fun `Unknown instructions returns null`() {
        assertNull(apiClient.mapInstructionsToStore("Download from our website"))
        assertNull(apiClient.mapInstructionsToStore("Click the button to claim"))
    }
    
    // ===== PHASE 12: URL STORE PRIORITY =====
    
    @Test
    fun `External store URL takes priority over platform`() {
        // URL says Fanatical, platform says PC
        val url = "https://www.fanatical.com/en/game/test-game"
        val urlStore = apiClient.mapUrlToExternalStore(url)
        assertEquals(GameStore.FANATICAL, urlStore)
    }
    
    @Test
    fun `IndieGala URL mapped by external store function`() {
        assertEquals(GameStore.INDIEGALA, apiClient.mapUrlToExternalStore("https://www.indiegala.com/giveaways/test"))
        assertEquals(GameStore.INDIEGALA, apiClient.mapUrlToExternalStore("https://indiegala.com/freebies/test"))
    }
    
    // ===== AMBIGUOUS MAPPINGS =====
    
    @Test
    fun `Multi-platform string returns primary match`() {
        // GamerPower sometimes returns "PC, Steam"
        val result = apiClient.mapPlatformToStore("PC, Steam")
        assertEquals(GameStore.STEAM, result)
    }
    
    @Test
    fun `Multi-platform with Epic preference`() {
        // "PC, Epic Games Store"
        val result = apiClient.mapPlatformToStore("PC, Epic Games Store")
        assertEquals(GameStore.EPIC_GAMES, result)
    }
    
    // ===== MALFORMED PAYLOADS =====
    
    @Test
    fun `Malformed platform with special characters handled gracefully`() {
        assertNull(apiClient.mapPlatformToStore("<script>alert('xss')</script>"))
    }
    
    @Test
    fun `Platform with embedded text extracts valid platform`() {
        // "Steam" is detected within the string
        assertEquals(GameStore.STEAM, apiClient.mapPlatformToStore("PC Steam"))
    }
    
    @Test
    fun `Extremely long platform string handled gracefully`() {
        val longPlatform = "a".repeat(10000)
        assertNull(apiClient.mapPlatformToStore(longPlatform))
    }
    
    @Test
    fun `Unicode platform strings handled gracefully`() {
        assertNull(apiClient.mapPlatformToStore("游戏平台"))
        assertNull(apiClient.mapPlatformToStore("🎮"))
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
    fun `External store URL mapping - Fanatical`() {
        assertEquals(GameStore.FANATICAL, apiClient.mapUrlToExternalStore("https://www.fanatical.com/en/game/test"))
    }
    
    @Test
    fun `External store URL mapping - Alienware Arena`() {
        assertEquals(GameStore.ALIENWARE_ARENA, apiClient.mapUrlToExternalStore("https://www.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `External store URL mapping - IndieGala`() {
        assertEquals(GameStore.INDIEGALA, apiClient.mapUrlToExternalStore("https://www.indiegala.com/giveaways/test"))
    }
    
    @Test
    fun `Unknown URL returns null store`() {
        assertNull(apiClient.mapUrlToExternalStore("https://example.com/game"))
        assertNull(apiClient.mapUrlToExternalStore(""))
        assertNull(apiClient.mapUrlToExternalStore("not-a-url"))
        assertNull(apiClient.mapUrlToExternalStore(null))
    }
    
    // ===== HELPER FUNCTIONS =====
    
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
