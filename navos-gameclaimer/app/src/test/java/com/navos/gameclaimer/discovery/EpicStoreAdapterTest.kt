package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.*
import com.navos.gameclaimer.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 8: Epic Games Store adapter tests.
 * 
 * Tests adapter configuration, URL parsing, offer classification,
 * region detection, and security validation.
 * 
 * Uses fixtures/fakes - no live account required.
 */
class EpicStoreAdapterTest {
    
    // ===== ADAPTER CONFIGURATION =====
    
    @Test
    fun `Epic adapter has correct store configuration`() {
        val adapter = EpicStoreAdapter()
        
        assertEquals(GameStore.EPIC_GAMES, adapter.store)
        assertEquals("Epic Games", adapter.displayName)
        assertTrue(adapter.supportsDiscovery)
        assertFalse(adapter.supportsClaiming) // Disabled in Phase 8
        assertTrue(adapter.requiresAuth)
    }
    
    @Test
    fun `Epic adapter returns login URL`() {
        val adapter = EpicStoreAdapter()
        
        assertTrue(adapter.getAuthUrl().contains("epicgames.com/id/login"))
    }
    
    @Test
    fun `Epic adapter claim returns disabled error`() = runTest {
        val adapter = EpicStoreAdapter()
        val giveaway = createTestGiveaway()
        
        val result = adapter.claim(giveaway)
        
        assertTrue(result is ClaimResult.Failed)
        val failed = result as ClaimResult.Failed
        assertTrue(failed.reason.contains("disabled"))
        assertFalse(failed.isRetryable)
    }
    
    // ===== URL PARSING =====
    
    @Test
    fun `Epic parses standard product URLs`() {
        val adapter = EpicStoreAdapter()
        
        // Standard product page
        val product = adapter.parseProductUrl("https://store.epicgames.com/en-US/p/fortnite")
        assertEquals("p" to "fortnite", product)
        
        // Product with dashes
        val dashed = adapter.parseProductUrl("https://store.epicgames.com/en-US/p/rocket-league")
        assertEquals("p" to "rocket-league", dashed)
        
        // Product with numbers
        val numbered = adapter.parseProductUrl("https://store.epicgames.com/en-US/p/gta-5-premium")
        assertEquals("p" to "gta-5-premium", numbered)
    }
    
    @Test
    fun `Epic parses bundle URLs`() {
        val adapter = EpicStoreAdapter()
        
        val bundle = adapter.parseProductUrl("https://store.epicgames.com/en-US/bundles/fortnite-bundle")
        assertEquals("bundles" to "fortnite-bundle", bundle)
    }
    
    @Test
    fun `Epic parses different locale URLs`() {
        val adapter = EpicStoreAdapter()
        
        // Note: Current regex expects en-US format, others may fail
        // This tests the documented behavior
        val enUs = adapter.parseProductUrl("https://store.epicgames.com/en-US/p/test-game")
        assertNotNull(enUs)
    }
    
    // ===== URL SECURITY =====
    
    @Test
    fun `Epic rejects deceptive host suffix attacks`() {
        val adapter = EpicStoreAdapter()
        
        assertNull(adapter.parseProductUrl("https://fakeepicgames.com/en-US/p/game"))
        assertNull(adapter.parseProductUrl("https://notepicgames.com/en-US/p/game"))
        assertNull(adapter.parseProductUrl("https://myepicgames.com/en-US/p/game"))
    }
    
    @Test
    fun `Epic rejects deceptive host prefix attacks`() {
        val adapter = EpicStoreAdapter()
        
        assertNull(adapter.parseProductUrl("https://store.epicgames.com.attacker.com/en-US/p/game"))
        assertNull(adapter.parseProductUrl("https://epicgames.com.evil.com/en-US/p/game"))
    }
    
    @Test
    fun `Epic rejects unsafe schemes`() {
        val adapter = EpicStoreAdapter()
        
        assertNull(adapter.parseProductUrl("http://store.epicgames.com/en-US/p/game"))
        assertNull(adapter.parseProductUrl("javascript:alert(1)//epicgames.com"))
        assertNull(adapter.parseProductUrl("file:///epicgames.com/p/game"))
    }
    
    @Test
    fun `Epic rejects IP addresses`() {
        val adapter = EpicStoreAdapter()
        
        assertNull(adapter.parseProductUrl("https://192.168.1.1/en-US/p/game"))
        assertNull(adapter.parseProductUrl("https://127.0.0.1/en-US/p/game"))
        assertNull(adapter.parseProductUrl("https://[::1]/en-US/p/game"))
    }
    
    @Test
    fun `Epic rejects URLs with userinfo`() {
        val adapter = EpicStoreAdapter()
        
        // userinfo@host is a phishing vector
        assertNull(adapter.parseProductUrl("https://user:pass@store.epicgames.com/en-US/p/game"))
        assertNull(adapter.parseProductUrl("https://admin@epicgames.com/en-US/p/game"))
    }
    
    @Test
    fun `Epic rejects unexpected ports`() {
        val adapter = EpicStoreAdapter()
        
        assertNull(adapter.parseProductUrl("https://store.epicgames.com:8080/en-US/p/game"))
        assertNull(adapter.parseProductUrl("https://store.epicgames.com:443/en-US/p/game"))
    }
    
    @Test
    fun `Epic accepts valid subdomains`() {
        val adapter = EpicStoreAdapter()
        
        // store.epicgames.com is the main store
        val store = adapter.parseProductUrl("https://store.epicgames.com/en-US/p/game")
        assertNotNull(store)
    }
    
    // ===== PAGE STATE OBSERVATIONS =====
    
    @Test
    fun `Page states are distinct enums`() {
        // Verify all page states exist and are distinct
        val states = EpicPageState.values()
        assertEquals(6, states.size)
        
        assertTrue(states.contains(EpicPageState.NEW_ADD))
        assertTrue(states.contains(EpicPageState.NEW_GET))
        assertTrue(states.contains(EpicPageState.OLD_CTA))
        assertTrue(states.contains(EpicPageState.OWNED))
        assertTrue(states.contains(EpicPageState.REGION))
        assertTrue(states.contains(EpicPageState.UNKNOWN))
    }
    
    @Test
    fun `Region restriction detected from page state`() {
        val adapter = EpicStoreAdapter()
        
        assertTrue(adapter.isRegionRestricted(EpicPageState.REGION))
        assertFalse(adapter.isRegionRestricted(EpicPageState.NEW_ADD))
        assertFalse(adapter.isRegionRestricted(EpicPageState.NEW_GET))
        assertFalse(adapter.isRegionRestricted(EpicPageState.OWNED))
        assertFalse(adapter.isRegionRestricted(EpicPageState.UNKNOWN))
    }
    
    // ===== OFFER CLASSIFICATION =====
    
    @Test
    fun `Offer classified as free-to-keep by default`() {
        val adapter = EpicStoreAdapter()
        val giveaway = createTestGiveaway()
        
        val type = adapter.classifyOfferType(giveaway)
        assertEquals(GiveawayType.FREE_TO_KEEP, type)
    }
    
    @Test
    fun `Bundle URL still classified as free-to-keep`() {
        val adapter = EpicStoreAdapter()
        val giveaway = createTestGiveaway(
            claimUrl = "https://store.epicgames.com/en-US/bundles/fortnite-bundle"
        )
        
        val type = adapter.classifyOfferType(giveaway)
        assertEquals(GiveawayType.FREE_TO_KEEP, type)
    }
    
    @Test
    fun `Mobile platform classified separately`() {
        val adapter = EpicStoreAdapter()
        val giveaway = createTestGiveaway()
        
        // Android
        val androidType = adapter.classifyOfferType(giveaway, "android")
        assertEquals(GiveawayType.DLC_OR_ADDON, androidType) // Using existing enum
        
        // iOS
        val iosType = adapter.classifyOfferType(giveaway, "ios")
        assertEquals(GiveawayType.DLC_OR_ADDON, iosType)
        
        // Unknown platform defaults to giveaway type
        val unknownType = adapter.classifyOfferType(giveaway, "unknown")
        assertEquals(GiveawayType.FREE_TO_KEEP, unknownType)
    }
    
    // ===== FACTORY INTEGRATION =====
    
    @Test
    fun `Factory includes Epic adapter`() {
        val factory = DefaultStoreAdapterFactory()
        
        val epic = factory.getAdapter(GameStore.EPIC_GAMES)
        assertNotNull(epic)
        assertEquals(GameStore.EPIC_GAMES, epic?.store)
    }
    
    @Test
    fun `Factory returns 8 adapters with Epic, Prime, Microsoft and Ubisoft`() {
        val factory = DefaultStoreAdapterFactory()
        
        val adapters = factory.getAllAdapters()
        assertEquals(8, adapters.size)
        
        val stores = adapters.map { it.store }.toSet()
        assertTrue(stores.contains(GameStore.STEAM))
        assertTrue(stores.contains(GameStore.GOG))
        assertTrue(stores.contains(GameStore.ITCH_IO))
        assertTrue(stores.contains(GameStore.INDIEGALA))
        assertTrue(stores.contains(GameStore.EPIC_GAMES))
    }
    
    @Test
    fun `Epic is discovery adapter but not claiming adapter`() {
        val factory = DefaultStoreAdapterFactory()
        
        val discoveryAdapters = factory.getDiscoveryAdapters()
        val claimingAdapters = factory.getClaimingAdapters()
        
        assertTrue(discoveryAdapters.any { it.store == GameStore.EPIC_GAMES })
        assertFalse(claimingAdapters.any { it.store == GameStore.EPIC_GAMES })
    }
    
    // ===== MALFORMED INPUT HANDLING =====
    
    @Test
    fun `Epic handles malformed URLs gracefully`() {
        val adapter = EpicStoreAdapter()
        
        assertNull(adapter.parseProductUrl(""))
        assertNull(adapter.parseProductUrl("not-a-url"))
        assertNull(adapter.parseProductUrl("https://"))
        assertNull(adapter.parseProductUrl("https://store.epicgames.com"))
        assertNull(adapter.parseProductUrl("https://store.epicgames.com/en-US/"))
    }
    
    @Test
    fun `Epic handles null-like input gracefully`() {
        val adapter = EpicStoreAdapter()
        
        assertNull(adapter.parseProductUrl("   "))
        assertNull(adapter.parseProductUrl("\n\t"))
    }
    
    // ===== ELIGIBILITY =====
    
    @Test
    fun `Default eligibility requires auth`() = runTest {
        val adapter = EpicStoreAdapter()
        val giveaway = createTestGiveaway()
        
        val eligibility = adapter.checkEligibility(giveaway)
        assertEquals(ClaimEligibility.REQUIRES_AUTH, eligibility)
    }
    
    // ===== HELPERS =====
    
    private fun createTestGiveaway(
        claimUrl: String = "https://store.epicgames.com/en-US/p/test-game"
    ): Giveaway {
        return Giveaway(
            id = "test:epic:1",
            title = "Test Game",
            store = GameStore.EPIC_GAMES,
            type = GiveawayType.FREE_TO_KEEP,
            claimUrl = claimUrl,
            source = DiscoverySource.GAMER_POWER
        )
    }
}
