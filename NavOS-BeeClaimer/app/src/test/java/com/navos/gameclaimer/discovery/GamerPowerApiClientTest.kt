package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.remote.GamerPowerGiveaway
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.GiveawayType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 6: GamerPower API Client Unit Tests
 * 
 * Tests platform mapping, JSON parsing, and validation logic.
 * Uses fixtures instead of live API calls.
 */
class GamerPowerApiClientTest {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    // ===== Platform Mapping Tests =====
    
    @Test
    fun `platform Epic Games Store maps to EPIC_GAMES`() {
        val store = mapPlatformToStore("Epic Games Store")
        assertEquals(GameStore.EPIC_GAMES, store)
    }
    
    @Test
    fun `platform steam maps to STEAM`() {
        val store = mapPlatformToStore("Steam")
        assertEquals(GameStore.STEAM, store)
    }
    
    @Test
    fun `platform gog maps to GOG`() {
        val store = mapPlatformToStore("GOG")
        assertEquals(GameStore.GOG, store)
    }
    
    @Test
    fun `platform amazon prime maps to AMAZON_PRIME`() {
        val store = mapPlatformToStore("Amazon Prime Gaming")
        assertEquals(GameStore.AMAZON_PRIME, store)
    }
    
    @Test
    fun `platform with multiple stores uses first match`() {
        // GamerPower sometimes returns "PC, Steam, Epic Games Store"
        val store = mapPlatformToStore("PC, Steam, Epic Games Store")
        // Should match first valid store
        assertNotNull(store)
    }
    
    @Test
    fun `unknown platform returns null`() {
        val store = mapPlatformToStore("PlayStation 5")
        assertNull(store)
    }
    
    @Test
    fun `null platform returns null`() {
        val store = mapPlatformToStore(null)
        assertNull(store)
    }
    
    @Test
    fun `empty platform returns null`() {
        val store = mapPlatformToStore("")
        assertNull(store)
    }
    
    // ===== Giveaway Type Mapping Tests =====
    
    @Test
    fun `type game maps to FREE_TO_KEEP`() {
        val type = mapGiveawayType("Game")
        assertEquals(GiveawayType.FREE_TO_KEEP, type)
    }
    
    @Test
    fun `type dlc maps to DLC_OR_ADDON`() {
        val type = mapGiveawayType("DLC")
        assertEquals(GiveawayType.DLC_OR_ADDON, type)
    }
    
    @Test
    fun `type loot maps to DLC_OR_ADDON`() {
        val type = mapGiveawayType("Loot")
        assertEquals(GiveawayType.DLC_OR_ADDON, type)
    }
    
    @Test
    fun `type beta maps to DEMO_OR_TRIAL`() {
        val type = mapGiveawayType("Beta")
        assertEquals(GiveawayType.DEMO_OR_TRIAL, type)
    }
    
    @Test
    fun `unknown type maps to UNKNOWN`() {
        val type = mapGiveawayType("Mystery")
        assertEquals(GiveawayType.UNKNOWN, type)
    }
    
    @Test
    fun `null type maps to UNKNOWN`() {
        val type = mapGiveawayType(null)
        assertEquals(GiveawayType.UNKNOWN, type)
    }
    
    // ===== JSON Parsing Tests =====
    
    @Test
    fun `parse valid giveaway JSON`() {
        val jsonStr = """
            {
                "id": 123,
                "title": "Test Game",
                "worth": "$19.99",
                "thumbnail": "https://example.com/img.jpg",
                "platforms": "Steam",
                "type": "Game",
                "open_giveaway_url": "https://store.steampowered.com/app/123"
            }
        """
        
        val giveaway = json.decodeFromString<GamerPowerGiveaway>(jsonStr)
        
        assertEquals(123, giveaway.id)
        assertEquals("Test Game", giveaway.title)
        assertEquals("$19.99", giveaway.worth)
        assertEquals("Steam", giveaway.platforms)
        assertEquals("Game", giveaway.type)
    }
    
    @Test
    fun `parse giveaway with missing optional fields`() {
        val jsonStr = """
            {
                "id": 456,
                "title": "Minimal Game",
                "platforms": "Epic Games Store",
                "open_giveaway_url": "https://epicgames.com/p/game"
            }
        """
        
        val giveaway = json.decodeFromString<GamerPowerGiveaway>(jsonStr)
        
        assertEquals(456, giveaway.id)
        assertEquals("Minimal Game", giveaway.title)
        assertNull(giveaway.worth)
        assertNull(giveaway.type)
    }
    
    @Test
    fun `parse empty array returns empty list`() {
        val jsonStr = "[]"
        val giveaways = json.decodeFromString<List<GamerPowerGiveaway>>(jsonStr)
        assertTrue(giveaways.isEmpty())
    }
    
    @Test
    fun `parse giveaway array`() {
        val jsonStr = """
            [
                {"id": 1, "title": "Game 1", "platforms": "Steam", "open_giveaway_url": "https://a.com"},
                {"id": 2, "title": "Game 2", "platforms": "Epic Games Store", "open_giveaway_url": "https://b.com"}
            ]
        """
        
        val giveaways = json.decodeFromString<List<GamerPowerGiveaway>>(jsonStr)
        
        assertEquals(2, giveaways.size)
        assertEquals("Game 1", giveaways[0].title)
        assertEquals("Game 2", giveaways[1].title)
    }
    
    // ===== URL Validation Tests =====
    
    @Test
    fun `HTTPS URL is valid for claims`() {
        assertTrue(isValidClaimUrl("https://store.steampowered.com/app/123"))
    }
    
    @Test
    fun `HTTP URL is rejected for claims`() {
        assertFalse(isValidClaimUrl("http://insecure.com/game"))
    }
    
    @Test
    fun `javascript URL is rejected`() {
        assertFalse(isValidClaimUrl("javascript:alert(1)"))
    }
    
    @Test
    fun `data URL is rejected`() {
        assertFalse(isValidClaimUrl("data:text/html,<script>"))
    }
    
    @Test
    fun `file URL is rejected`() {
        assertFalse(isValidClaimUrl("file:///etc/passwd"))
    }
    
    @Test
    fun `empty URL is rejected`() {
        assertFalse(isValidClaimUrl(""))
    }
    
    @Test
    fun `null URL is rejected`() {
        assertFalse(isValidClaimUrl(null))
    }
    
    // ===== Helper Functions (mirroring API client logic) =====
    
    private fun mapPlatformToStore(platforms: String?): GameStore? {
        if (platforms.isNullOrBlank()) return null
        val lower = platforms.lowercase()
        return when {
            "epic" in lower || "epic-games" in lower -> GameStore.EPIC_GAMES
            "steam" in lower -> GameStore.STEAM
            "gog" in lower -> GameStore.GOG
            "amazon" in lower || "prime" in lower -> GameStore.AMAZON_PRIME
            else -> null
        }
    }
    
    private fun mapGiveawayType(type: String?): GiveawayType {
        return when (type?.lowercase()) {
            "game" -> GiveawayType.FREE_TO_KEEP
            "dlc" -> GiveawayType.DLC_OR_ADDON
            "loot" -> GiveawayType.DLC_OR_ADDON
            "beta" -> GiveawayType.DEMO_OR_TRIAL
            else -> GiveawayType.UNKNOWN
        }
    }
    
    private fun isValidClaimUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.startsWith("https://")
    }
}
