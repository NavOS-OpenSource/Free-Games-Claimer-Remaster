package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.*
import com.navos.gameclaimer.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 7: Unit tests for store adapters.
 * 
 * Tests adapter configuration, URL parsing, and discovery-only behavior.
 * No live API calls - uses fixtures and stubs.
 */
class StoreAdapterTest {
    
    // ===== Steam Adapter Tests =====
    
    @Test
    fun `Steam adapter has correct store configuration`() {
        val adapter = SteamStoreAdapter()
        
        assertEquals(GameStore.STEAM, adapter.store)
        assertEquals("Steam", adapter.displayName)
        assertTrue(adapter.supportsDiscovery)
        assertFalse(adapter.supportsClaiming) // Disabled in Phase 7
        assertTrue(adapter.requiresAuth)
    }
    
    @Test
    fun `Steam adapter returns login URL`() {
        val adapter = SteamStoreAdapter()
        
        assertEquals("https://store.steampowered.com/login/", adapter.getAuthUrl())
    }
    
    @Test
    fun `Steam adapter parses app URLs correctly`() {
        val adapter = SteamStoreAdapter()
        
        // Standard app URL
        val app = adapter.parseItemId("https://store.steampowered.com/app/12345")
        assertEquals("app" to "12345", app)
        
        // Package (sub) URL
        val sub = adapter.parseItemId("https://store.steampowered.com/sub/67890")
        assertEquals("sub" to "67890", sub)
        
        // Bundle URL
        val bundle = adapter.parseItemId("https://store.steampowered.com/bundle/11111")
        assertEquals("bundle" to "11111", bundle)
        
        // Age-gated URL
        val ageCheck = adapter.parseItemId("https://store.steampowered.com/agecheck/app/99999")
        assertEquals("app" to "99999", ageCheck)
        
        // Invalid URL
        assertNull(adapter.parseItemId("https://example.com/app/123"))
    }
    
    @Test
    fun `Steam adapter claim returns disabled error`() = runTest {
        val adapter = SteamStoreAdapter()
        val giveaway = createTestGiveaway(GameStore.STEAM)
        
        val result = adapter.claim(giveaway)
        
        assertTrue(result is ClaimResult.Failed)
        val failed = result as ClaimResult.Failed
        assertTrue(failed.reason.contains("disabled"))
        assertFalse(failed.isRetryable)
    }
    
    // ===== GOG Adapter Tests =====
    
    @Test
    fun `GOG adapter has correct store configuration`() {
        val adapter = GogStoreAdapter()
        
        assertEquals(GameStore.GOG, adapter.store)
        assertEquals("GOG", adapter.displayName)
        assertTrue(adapter.supportsDiscovery)
        assertFalse(adapter.supportsClaiming) // Disabled in Phase 7
        assertTrue(adapter.requiresAuth)
    }
    
    @Test
    fun `GOG adapter returns login URL`() {
        val adapter = GogStoreAdapter()
        
        assertEquals("https://login.gog.com/login_form", adapter.getAuthUrl())
    }
    
    @Test
    fun `GOG adapter parses game URLs correctly`() {
        val adapter = GogStoreAdapter()
        
        // Standard game URL
        val game = adapter.parseGameSlug("https://www.gog.com/en/game/cyberpunk-2077")
        assertEquals("cyberpunk-2077", game)
        
        // Without /en/
        val game2 = adapter.parseGameSlug("https://www.gog.com/game/witcher_3")
        assertEquals("witcher_3", game2)
        
        // Invalid URL
        assertNull(adapter.parseGameSlug("https://example.com/game/test"))
    }
    
    @Test
    fun `GOG adapter has cookie consent JavaScript`() {
        val adapter = GogStoreAdapter()
        
        assertTrue(adapter.COOKIE_CONSENT_JS.contains("CybotCookiebotDialog"))
    }
    
    // ===== Itch.io Adapter Tests =====
    
    @Test
    fun `Itch adapter has correct store configuration`() {
        val adapter = ItchioStoreAdapter()
        
        assertEquals(GameStore.ITCH_IO, adapter.store)
        assertEquals("Itch.io", adapter.displayName)
        assertTrue(adapter.supportsDiscovery)
        assertFalse(adapter.supportsClaiming) // Disabled in Phase 7
        assertTrue(adapter.requiresAuth)
    }
    
    @Test
    fun `Itch adapter returns login URL`() {
        val adapter = ItchioStoreAdapter()
        
        assertEquals("https://itch.io/login", adapter.getAuthUrl())
    }
    
    @Test
    fun `Itch adapter parses game URLs correctly`() {
        val adapter = ItchioStoreAdapter()
        
        // Standard game URL: developer.itch.io/game
        val game = adapter.parseGameUrl("https://cool-dev.itch.io/awesome-game")
        assertEquals("cool-dev" to "awesome-game", game)
        
        // Another format
        val game2 = adapter.parseGameUrl("https://indie-studio.itch.io/puzzle-game-2")
        assertEquals("indie-studio" to "puzzle-game-2", game2)
        
        // Invalid URL
        assertNull(adapter.parseGameUrl("https://itch.io/games"))
    }
    
    @Test
    fun `Itch adapter validates subdomain hosts`() {
        val adapter = ItchioStoreAdapter()
        
        assertTrue(adapter.isValidItchioHost("itch.io"))
        assertTrue(adapter.isValidItchioHost("developer.itch.io"))
        assertTrue(adapter.isValidItchioHost("cool-studio.itch.io"))
        
        // Attack attempts
        assertFalse(adapter.isValidItchioHost("itch.io.attacker.com"))
        assertFalse(adapter.isValidItchioHost("fakeitch.io"))
        assertFalse(adapter.isValidItchioHost("notitch.io"))
    }
    
    // ===== IndieGala Adapter Tests =====
    
    @Test
    fun `IndieGala adapter has correct store configuration`() {
        val adapter = IndieGalaStoreAdapter()
        
        assertEquals(GameStore.INDIEGALA, adapter.store)
        assertEquals("IndieGala", adapter.displayName)
        assertTrue(adapter.supportsDiscovery)
        assertFalse(adapter.supportsClaiming) // Disabled in Phase 7
        assertTrue(adapter.requiresAuth)
    }
    
    @Test
    fun `IndieGala adapter returns login URL`() {
        val adapter = IndieGalaStoreAdapter()
        
        assertEquals("https://www.indiegala.com/login", adapter.getAuthUrl())
    }
    
    @Test
    fun `IndieGala adapter parses giveaway URLs correctly`() {
        val adapter = IndieGalaStoreAdapter()
        
        // Standard giveaway URL
        val giveaway = adapter.parseGiveawaySlug("https://www.indiegala.com/giveaways/cool-game")
        assertEquals("cool-game", giveaway)
        
        // Freebies URL
        val freebie = adapter.parseGiveawaySlug("https://indiegala.com/freebies/awesome-game")
        assertEquals("awesome-game", freebie)
        
        // Invalid URL
        assertNull(adapter.parseGiveawaySlug("https://example.com/giveaways/test"))
    }
    
    // ===== Factory Tests =====
    
    @Test
    fun `DefaultStoreAdapterFactory returns all Phase 9 adapters`() {
        val factory = DefaultStoreAdapterFactory()
        
        val adapters = factory.getAllAdapters()
        
        // Phase 9: Now includes Prime Gaming (6 total)
        assertEquals(6, adapters.size)
        assertTrue(adapters.any { it.store == GameStore.STEAM })
        assertTrue(adapters.any { it.store == GameStore.GOG })
        assertTrue(adapters.any { it.store == GameStore.ITCH_IO })
        assertTrue(adapters.any { it.store == GameStore.INDIEGALA })
        assertTrue(adapters.any { it.store == GameStore.EPIC_GAMES })
        assertTrue(adapters.any { it.store == GameStore.AMAZON_PRIME })
    }
    
    @Test
    fun `DefaultStoreAdapterFactory returns adapter by store`() {
        val factory = DefaultStoreAdapterFactory()
        
        val steam = factory.getAdapter(GameStore.STEAM)
        assertNotNull(steam)
        assertEquals(GameStore.STEAM, steam?.store)
        
        val gog = factory.getAdapter(GameStore.GOG)
        assertNotNull(gog)
        assertEquals(GameStore.GOG, gog?.store)
        
        // Phase 8: Epic implemented
        val epic = factory.getAdapter(GameStore.EPIC_GAMES)
        assertNotNull(epic)
        assertEquals(GameStore.EPIC_GAMES, epic?.store)
        
        // Phase 9: Prime Gaming implemented
        val prime = factory.getAdapter(GameStore.AMAZON_PRIME)
        assertNotNull(prime)
        assertEquals(GameStore.AMAZON_PRIME, prime?.store)
    }
    
    @Test
    fun `DefaultStoreAdapterFactory returns discovery adapters only`() {
        val factory = DefaultStoreAdapterFactory()
        
        val discoveryAdapters = factory.getDiscoveryAdapters()
        
        // Phase 9: All 6 adapters support discovery
        assertEquals(6, discoveryAdapters.size)
        assertTrue(discoveryAdapters.all { it.supportsDiscovery })
    }
    
    @Test
    fun `DefaultStoreAdapterFactory returns no claiming adapters in Phase 7`() {
        val factory = DefaultStoreAdapterFactory()
        
        val claimingAdapters = factory.getClaimingAdapters()
        
        // All claiming disabled through Phase 8
        assertEquals(0, claimingAdapters.size)
    }
    
    @Test
    fun `DefaultStoreAdapterFactory reports supported stores`() {
        val factory = DefaultStoreAdapterFactory()
        
        assertTrue(factory.isStoreSupported(GameStore.STEAM))
        assertTrue(factory.isStoreSupported(GameStore.GOG))
        assertTrue(factory.isStoreSupported(GameStore.ITCH_IO))
        assertTrue(factory.isStoreSupported(GameStore.INDIEGALA))
        assertTrue(factory.isStoreSupported(GameStore.EPIC_GAMES)) // Phase 8
        assertTrue(factory.isStoreSupported(GameStore.AMAZON_PRIME)) // Phase 9
    }
    
    // ===== Helper Functions =====
    
    private fun createTestGiveaway(store: GameStore): Giveaway {
        return Giveaway(
            id = "test:${store.name.lowercase()}:1",
            title = "Test Game",
            store = store,
            type = GiveawayType.FREE_TO_KEEP,
            claimUrl = "https://example.com/game",
            source = DiscoverySource.GAMER_POWER
        )
    }
}
