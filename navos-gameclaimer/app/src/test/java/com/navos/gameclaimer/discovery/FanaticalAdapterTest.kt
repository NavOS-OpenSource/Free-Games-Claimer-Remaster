package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.FanaticalAdapter
import com.navos.gameclaimer.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 12: Fanatical Adapter Tests
 * 
 * Tests for the Fanatical store adapter focusing on:
 * 1. URL validation and security
 * 2. Product slug parsing
 * 3. Claim behavior (disabled)
 * 4. Host validation against lookalikes
 * 5. Store configuration
 * 
 * SECURITY TESTS:
 * - HTTPS enforcement
 * - Host validation (no lookalikes)
 * - Userinfo rejection
 * - Port validation
 * - IP literal rejection
 * 
 * NOTE: Fanatical distributes keys for Steam/Epic/GOG, not direct game ownership.
 */
class FanaticalAdapterTest {
    
    private lateinit var adapter: FanaticalAdapter
    
    @Before
    fun setup() {
        adapter = FanaticalAdapter()
    }
    
    // ==================== Store Configuration Tests ====================
    
    @Test
    fun `adapter has correct store type`() {
        assertEquals(GameStore.FANATICAL, adapter.store)
    }
    
    @Test
    fun `adapter has correct display name`() {
        assertEquals("Fanatical", adapter.displayName)
    }
    
    @Test
    fun `adapter supports discovery`() {
        assertTrue(adapter.supportsDiscovery)
    }
    
    @Test
    fun `adapter does NOT support claiming - key redemption is external`() {
        assertFalse(adapter.supportsClaiming)
    }
    
    @Test
    fun `adapter requires authentication`() {
        assertTrue(adapter.requiresAuth)
    }
    
    @Test
    fun `auth URL returns English homepage for modal login`() {
        assertEquals("https://www.fanatical.com/en", adapter.getAuthUrl())
    }
    
    // ==================== URL Validation Tests ====================
    
    @Test
    fun `valid Fanatical game URL is accepted`() {
        assertTrue(adapter.isValidFanaticalUrl("https://www.fanatical.com/en/game/some-game"))
    }
    
    @Test
    fun `valid Fanatical bundle URL is accepted`() {
        assertTrue(adapter.isValidFanaticalUrl("https://www.fanatical.com/en/bundle/some-bundle"))
    }
    
    @Test
    fun `valid Fanatical pick-and-mix URL is accepted`() {
        assertTrue(adapter.isValidFanaticalUrl("https://www.fanatical.com/en/pick-and-mix/some-mix"))
    }
    
    @Test
    fun `valid Fanatical DLC URL is accepted`() {
        assertTrue(adapter.isValidFanaticalUrl("https://www.fanatical.com/en/dlc/some-dlc"))
    }
    
    @Test
    fun `bare fanatical domain is accepted`() {
        assertTrue(adapter.isValidFanaticalUrl("https://fanatical.com/en/game/test"))
    }
    
    @Test
    fun `www subdomain is accepted`() {
        assertTrue(adapter.isValidFanaticalUrl("https://www.fanatical.com/en/game/test"))
    }
    
    @Test
    fun `other subdomains are accepted`() {
        // Fanatical may use regional or CDN subdomains
        assertTrue(adapter.isValidFanaticalUrl("https://cdn.fanatical.com/something"))
    }
    
    // ==================== HTTPS Security Tests ====================
    
    @Test
    fun `HTTP URLs are rejected - HTTPS required`() {
        assertFalse(adapter.isValidFanaticalUrl("http://www.fanatical.com/en/game/test"))
    }
    
    @Test
    fun `FTP URLs are rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("ftp://www.fanatical.com/en/game/test"))
    }
    
    @Test
    fun `javascript URLs are rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("javascript:alert('xss')"))
    }
    
    @Test
    fun `data URLs are rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("data:text/html,<script>alert('xss')</script>"))
    }
    
    @Test
    fun `file URLs are rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("file:///etc/passwd"))
    }
    
    // ==================== Host Validation Tests ====================
    
    @Test
    fun `lookalike domain fakefanatical is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://fakefanatical.com/en/game/test"))
    }
    
    @Test
    fun `lookalike domain fanatical-com is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://fanatical-com.com/en/game/test"))
    }
    
    @Test
    fun `suffix attack fanatical-com-attacker is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://fanatical.com.attacker.com/en/game/test"))
    }
    
    @Test
    fun `subdomain attack www-fanatical-com is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://www-fanatical-com.attacker.com/en/game/test"))
    }
    
    @Test
    fun `similar domain fanatical-store is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://fanatical-store.com/en/game/test"))
    }
    
    @Test
    fun `typosquat fanaticall is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://fanaticall.com/en/game/test"))
    }
    
    @Test
    fun `typosquat fanatikal is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://fanatikal.com/en/game/test"))
    }
    
    // ==================== IP Literal Rejection Tests ====================
    
    @Test
    fun `IPv4 literal is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://192.168.1.1/en/game/test"))
    }
    
    @Test
    fun `localhost is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://localhost/en/game/test"))
    }
    
    @Test
    fun `127-0-0-1 is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://127.0.0.1/en/game/test"))
    }
    
    @Test
    fun `IPv6 literal is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://[::1]/en/game/test"))
    }
    
    // ==================== Userinfo Rejection Tests ====================
    
    @Test
    fun `URL with userinfo credentials is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://user:pass@www.fanatical.com/en/game/test"))
    }
    
    @Test
    fun `URL with username only is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://admin@www.fanatical.com/en/game/test"))
    }
    
    // ==================== Port Validation Tests ====================
    
    @Test
    fun `default port 443 is accepted`() {
        assertTrue(adapter.isValidFanaticalUrl("https://www.fanatical.com:443/en/game/test"))
    }
    
    @Test
    fun `non-standard port is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://www.fanatical.com:8080/en/game/test"))
    }
    
    @Test
    fun `HTTP port 80 is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://www.fanatical.com:80/en/game/test"))
    }
    
    // ==================== Malformed URL Tests ====================
    
    @Test
    fun `malformed URL is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("not-a-url"))
    }
    
    @Test
    fun `empty string is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl(""))
    }
    
    @Test
    fun `URL with spaces is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("https://www.fanatical.com/en/game/test game"))
    }
    
    @Test
    fun `null-like URL is rejected`() {
        assertFalse(adapter.isValidFanaticalUrl("null"))
    }
    
    // ==================== Product Slug Parsing Tests ====================
    
    @Test
    fun `parse game slug from valid URL`() {
        val result = adapter.parseProductSlug("https://www.fanatical.com/en/game/some-awesome-game")
        assertNotNull(result)
        assertEquals("game", result?.first)
        assertEquals("some-awesome-game", result?.second)
    }
    
    @Test
    fun `parse bundle slug from valid URL`() {
        val result = adapter.parseProductSlug("https://www.fanatical.com/en/bundle/platinum-collection")
        assertNotNull(result)
        assertEquals("bundle", result?.first)
        assertEquals("platinum-collection", result?.second)
    }
    
    @Test
    fun `parse pick-and-mix slug from valid URL`() {
        val result = adapter.parseProductSlug("https://www.fanatical.com/en/pick-and-mix/build-your-own")
        assertNotNull(result)
        assertEquals("pick-and-mix", result?.first)
        assertEquals("build-your-own", result?.second)
    }
    
    @Test
    fun `parse DLC slug from valid URL`() {
        val result = adapter.parseProductSlug("https://www.fanatical.com/en/dlc/game-expansion-pack")
        assertNotNull(result)
        assertEquals("dlc", result?.first)
        assertEquals("game-expansion-pack", result?.second)
    }
    
    @Test
    fun `parse slug with numbers`() {
        val result = adapter.parseProductSlug("https://www.fanatical.com/en/game/game-2024-edition")
        assertNotNull(result)
        assertEquals("game-2024-edition", result?.second)
    }
    
    @Test
    fun `parse slug fails for invalid host`() {
        val result = adapter.parseProductSlug("https://fake-fanatical.com/en/game/test")
        assertNull(result)
    }
    
    @Test
    fun `parse slug fails for HTTP`() {
        val result = adapter.parseProductSlug("http://www.fanatical.com/en/game/test")
        assertNull(result)
    }
    
    @Test
    fun `parse slug fails for non-product URL`() {
        val result = adapter.parseProductSlug("https://www.fanatical.com/en/about")
        assertNull(result)
    }
    
    @Test
    fun `parse slug from different locale`() {
        val result = adapter.parseProductSlug("https://www.fanatical.com/de/game/ein-spiel")
        assertNotNull(result)
        assertEquals("game", result?.first)
        assertEquals("ein-spiel", result?.second)
    }
    
    // ==================== Claim Behavior Tests ====================
    
    @Test
    fun `claim returns failure with key redemption message`() = runBlocking {
        val giveaway = createTestGiveaway("https://www.fanatical.com/en/game/test-game")
        val result = adapter.claim(giveaway)
        
        assertTrue(result is ClaimResult.Failed)
        val failed = result as ClaimResult.Failed
        assertTrue(failed.reason.contains("key", ignoreCase = true))
        assertTrue(failed.reason.contains("manual", ignoreCase = true))
        assertFalse(failed.isRetryable)
    }
    
    @Test
    fun `check eligibility requires auth`() = runBlocking {
        val giveaway = createTestGiveaway("https://www.fanatical.com/en/game/test-game")
        val result = adapter.checkEligibility(giveaway)
        
        assertEquals(ClaimEligibility.REQUIRES_AUTH, result)
    }
    
    @Test
    fun `check eligibility returns unknown for invalid URL`() = runBlocking {
        val giveaway = createTestGiveaway("http://fake-fanatical.com/game/test")
        val result = adapter.checkEligibility(giveaway)
        
        assertEquals(ClaimEligibility.UNKNOWN, result)
    }
    
    // ==================== Discovery Tests ====================
    
    @Test
    fun `discover giveaways returns empty - uses GamerPower`() = runBlocking {
        val result = adapter.discoverGiveaways(5000)
        
        assertTrue(result is DiscoveryResult.Empty)
        assertEquals(DiscoverySource.STORE_DIRECT, (result as DiscoveryResult.Empty).source)
    }
    
    // ==================== Verification Tests ====================
    
    @Test
    fun `verify giveaway passes through valid giveaway`() = runBlocking {
        val giveaway = createTestGiveaway("https://www.fanatical.com/en/game/test-game")
        val result = adapter.verifyGiveaway(giveaway)
        
        assertNotNull(result)
        assertEquals(giveaway.id, result?.id)
    }
    
    @Test
    fun `verify giveaway returns null for invalid URL`() = runBlocking {
        val giveaway = createTestGiveaway("http://fake-fanatical.com/game/test")
        val result = adapter.verifyGiveaway(giveaway)
        
        assertNull(result)
    }
    
    // ==================== Authentication Tests ====================
    
    @Test
    fun `isAuthenticated returns false by default`() = runBlocking {
        assertFalse(adapter.isAuthenticated())
    }
    
    // ==================== Key Redemption Platform Tests ====================
    
    @Test
    fun `get key redemption platform returns null - needs page parsing`() {
        val giveaway = createTestGiveaway("https://www.fanatical.com/en/game/test-game")
        val result = adapter.getKeyRedemptionPlatform(giveaway)
        
        assertNull(result)
    }
    
    @Test
    fun `key redemption platforms include Steam`() {
        assertTrue(GameStore.STEAM in FanaticalAdapter.KEY_REDEMPTION_PLATFORMS)
    }
    
    @Test
    fun `key redemption platforms include Epic`() {
        assertTrue(GameStore.EPIC_GAMES in FanaticalAdapter.KEY_REDEMPTION_PLATFORMS)
    }
    
    @Test
    fun `key redemption platforms include GOG`() {
        assertTrue(GameStore.GOG in FanaticalAdapter.KEY_REDEMPTION_PLATFORMS)
    }
    
    @Test
    fun `key redemption platforms include Ubisoft`() {
        assertTrue(GameStore.UBISOFT in FanaticalAdapter.KEY_REDEMPTION_PLATFORMS)
    }
    
    // ==================== Companion Object Tests ====================
    
    @Test
    fun `trusted domains include fanatical-com`() {
        assertTrue("fanatical.com" in FanaticalAdapter.TRUSTED_DOMAINS)
    }
    
    @Test
    fun `trusted domains include www-fanatical-com`() {
        assertTrue("www.fanatical.com" in FanaticalAdapter.TRUSTED_DOMAINS)
    }
    
    @Test
    fun `URL home constant is correct`() {
        assertEquals("https://www.fanatical.com", FanaticalAdapter.URL_HOME)
    }
    
    @Test
    fun `URL EN home constant is correct`() {
        assertEquals("https://www.fanatical.com/en", FanaticalAdapter.URL_EN_HOME)
    }
    
    // ==================== Regex Tests ====================
    
    @Test
    fun `game regex matches valid game URL`() {
        val match = FanaticalAdapter.FANATICAL_GAME_REGEX.find("https://www.fanatical.com/en/game/test-game-123")
        assertNotNull(match)
        assertEquals("test-game-123", match?.groupValues?.get(1))
    }
    
    @Test
    fun `bundle regex matches valid bundle URL`() {
        val match = FanaticalAdapter.FANATICAL_BUNDLE_REGEX.find("https://www.fanatical.com/en/bundle/platinum-collection")
        assertNotNull(match)
        assertEquals("platinum-collection", match?.groupValues?.get(1))
    }
    
    @Test
    fun `pick-and-mix regex matches valid URL`() {
        val match = FanaticalAdapter.FANATICAL_PICK_MIX_REGEX.find("https://www.fanatical.com/en/pick-and-mix/build-your-own")
        assertNotNull(match)
        assertEquals("build-your-own", match?.groupValues?.get(1))
    }
    
    @Test
    fun `product regex captures type and slug`() {
        val match = FanaticalAdapter.FANATICAL_PRODUCT_REGEX.find("https://www.fanatical.com/de/game/test-game")
        assertNotNull(match)
        assertEquals("game", match?.groupValues?.get(1))
        assertEquals("test-game", match?.groupValues?.get(2))
    }
    
    // ==================== Adapter Count Verification ====================
    
    @Test
    fun `adapter factory has correct number of adapters`() {
        val factory = com.navos.gameclaimer.data.adapter.DefaultStoreAdapterFactory()
        // Phase 12: 12 adapters total
        assertEquals(12, factory.getAllAdapters().size)
    }
    
    @Test
    fun `all adapters support discovery`() {
        val factory = com.navos.gameclaimer.data.adapter.DefaultStoreAdapterFactory()
        val discoveryAdapters = factory.getDiscoveryAdapters()
        // All 12 adapters support discovery
        assertEquals(12, discoveryAdapters.size)
    }
    
    // ==================== Helper Functions ====================
    
    private fun createTestGiveaway(claimUrl: String): Giveaway {
        return Giveaway(
            id = "test:fanatical:123",
            title = "Test Fanatical Giveaway",
            store = GameStore.FANATICAL,
            type = GiveawayType.FREE_TO_KEEP,
            claimUrl = claimUrl,
            source = DiscoverySource.GAMER_POWER
        )
    }
}
