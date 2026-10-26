package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.AlienwareArenaAdapter
import com.navos.gameclaimer.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 12: Alienware Arena Adapter Tests
 * 
 * Tests for the Alienware Arena store adapter focusing on:
 * 1. URL validation and security
 * 2. UCF show ID parsing
 * 3. Region extraction
 * 4. Claim behavior (disabled)
 * 5. Host validation against lookalikes
 * 6. Regional subdomain handling
 * 
 * SECURITY TESTS:
 * - HTTPS enforcement
 * - Host validation (no lookalikes)
 * - Regional subdomain validation
 * - Userinfo rejection
 * - Port validation
 * - IP literal rejection
 * 
 * NOTE: Alienware Arena distributes keys for Steam/Epic/GOG with account level gating.
 */
class AlienwareArenaAdapterTest {
    
    private lateinit var adapter: AlienwareArenaAdapter
    
    @Before
    fun setup() {
        adapter = AlienwareArenaAdapter()
    }
    
    // ==================== Store Configuration Tests ====================
    
    @Test
    fun `adapter has correct store type`() {
        assertEquals(GameStore.ALIENWARE_ARENA, adapter.store)
    }
    
    @Test
    fun `adapter has correct display name`() {
        assertEquals("Alienware Arena", adapter.displayName)
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
    fun `auth URL returns login page`() {
        assertEquals("https://www.alienwarearena.com/login", adapter.getAuthUrl())
    }
    
    // ==================== URL Validation Tests ====================
    
    @Test
    fun `valid Alienware giveaway URL is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://www.alienwarearena.com/ucf/show/2156789/boards/contest-and-giveaways-702/Giveaway/game-key-giveaway"))
    }
    
    @Test
    fun `valid Alienware member URL is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://www.alienwarearena.com/member/123456"))
    }
    
    @Test
    fun `valid Alienware giveaways listing is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://www.alienwarearena.com/ucf/show/giveaways"))
    }
    
    @Test
    fun `bare alienwarearena domain is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `www subdomain is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://www.alienwarearena.com/ucf/show/123"))
    }
    
    // ==================== Regional Subdomain Tests ====================
    
    @Test
    fun `NA regional subdomain is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://na.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `EU regional subdomain is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://eu.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `LATAM regional subdomain is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://latam.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `extract region from NA URL`() {
        val region = adapter.extractRegion("https://na.alienwarearena.com/ucf/show/123")
        assertEquals("na", region)
    }
    
    @Test
    fun `extract region from EU URL`() {
        val region = adapter.extractRegion("https://eu.alienwarearena.com/ucf/show/123")
        assertEquals("eu", region)
    }
    
    @Test
    fun `extract region from LATAM URL`() {
        val region = adapter.extractRegion("https://latam.alienwarearena.com/ucf/show/123")
        assertEquals("latam", region)
    }
    
    @Test
    fun `extract region returns null for www`() {
        val region = adapter.extractRegion("https://www.alienwarearena.com/ucf/show/123")
        assertNull(region)
    }
    
    @Test
    fun `extract region returns null for bare domain`() {
        val region = adapter.extractRegion("https://alienwarearena.com/ucf/show/123")
        assertNull(region)
    }
    
    // ==================== HTTPS Security Tests ====================
    
    @Test
    fun `HTTP URLs are rejected - HTTPS required`() {
        assertFalse(adapter.isValidAlienwareUrl("http://www.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `FTP URLs are rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("ftp://www.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `javascript URLs are rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("javascript:alert('xss')"))
    }
    
    @Test
    fun `data URLs are rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("data:text/html,<script>alert('xss')</script>"))
    }
    
    @Test
    fun `file URLs are rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("file:///etc/passwd"))
    }
    
    // ==================== Host Validation Tests ====================
    
    @Test
    fun `lookalike domain fakealienwarearena is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://fakealienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `lookalike domain alienwarearena-com is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://alienwarearena-com.com/ucf/show/123"))
    }
    
    @Test
    fun `suffix attack alienwarearena-com-attacker is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://alienwarearena.com.attacker.com/ucf/show/123"))
    }
    
    @Test
    fun `subdomain attack www-alienwarearena-com is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://www-alienwarearena-com.attacker.com/ucf/show/123"))
    }
    
    @Test
    fun `similar domain alienware-arena is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://alienware-arena.com/ucf/show/123"))
    }
    
    @Test
    fun `typosquat alienwarearenna is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://alienwarearenna.com/ucf/show/123"))
    }
    
    @Test
    fun `typosquat allienwarearena is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://allienwarearena.com/ucf/show/123"))
    }
    
    // ==================== IP Literal Rejection Tests ====================
    
    @Test
    fun `IPv4 literal is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://192.168.1.1/ucf/show/123"))
    }
    
    @Test
    fun `localhost is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://localhost/ucf/show/123"))
    }
    
    @Test
    fun `127-0-0-1 is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://127.0.0.1/ucf/show/123"))
    }
    
    @Test
    fun `IPv6 literal is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://[::1]/ucf/show/123"))
    }
    
    // ==================== Userinfo Rejection Tests ====================
    
    @Test
    fun `URL with userinfo credentials is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://user:pass@www.alienwarearena.com/ucf/show/123"))
    }
    
    @Test
    fun `URL with username only is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://admin@www.alienwarearena.com/ucf/show/123"))
    }
    
    // ==================== Port Validation Tests ====================
    
    @Test
    fun `default port 443 is accepted`() {
        assertTrue(adapter.isValidAlienwareUrl("https://www.alienwarearena.com:443/ucf/show/123"))
    }
    
    @Test
    fun `non-standard port is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://www.alienwarearena.com:8080/ucf/show/123"))
    }
    
    @Test
    fun `HTTP port 80 is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://www.alienwarearena.com:80/ucf/show/123"))
    }
    
    // ==================== Malformed URL Tests ====================
    
    @Test
    fun `malformed URL is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("not-a-url"))
    }
    
    @Test
    fun `empty string is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl(""))
    }
    
    @Test
    fun `URL with spaces is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("https://www.alienwarearena.com/ucf/show/test giveaway"))
    }
    
    @Test
    fun `null-like URL is rejected`() {
        assertFalse(adapter.isValidAlienwareUrl("null"))
    }
    
    // ==================== UCF Show ID Parsing Tests ====================
    
    @Test
    fun `parse UCF show ID from valid URL`() {
        val result = adapter.parseUcfShowId("https://www.alienwarearena.com/ucf/show/2156789/boards/contest-and-giveaways-702/Giveaway/game-key-giveaway")
        assertEquals("2156789", result)
    }
    
    @Test
    fun `parse UCF show ID from minimal URL`() {
        val result = adapter.parseUcfShowId("https://www.alienwarearena.com/ucf/show/123456")
        assertEquals("123456", result)
    }
    
    @Test
    fun `parse UCF show ID from NA regional URL`() {
        val result = adapter.parseUcfShowId("https://na.alienwarearena.com/ucf/show/999888")
        assertEquals("999888", result)
    }
    
    @Test
    fun `parse UCF show ID from EU regional URL`() {
        val result = adapter.parseUcfShowId("https://eu.alienwarearena.com/ucf/show/777666")
        assertEquals("777666", result)
    }
    
    @Test
    fun `parse UCF show ID fails for invalid host`() {
        val result = adapter.parseUcfShowId("https://fake-alienware.com/ucf/show/123")
        assertNull(result)
    }
    
    @Test
    fun `parse UCF show ID fails for HTTP`() {
        val result = adapter.parseUcfShowId("http://www.alienwarearena.com/ucf/show/123")
        assertNull(result)
    }
    
    @Test
    fun `parse UCF show ID fails for non-UCF URL`() {
        val result = adapter.parseUcfShowId("https://www.alienwarearena.com/member/123")
        assertNull(result)
    }
    
    @Test
    fun `parse UCF show ID fails for giveaways listing`() {
        val result = adapter.parseUcfShowId("https://www.alienwarearena.com/ucf/show/giveaways")
        assertNull(result)
    }
    
    // ==================== Claim Behavior Tests ====================
    
    @Test
    fun `claim returns failure with key redemption message`() = runBlocking {
        val giveaway = createTestGiveaway("https://www.alienwarearena.com/ucf/show/123456")
        val result = adapter.claim(giveaway)
        
        assertTrue(result is ClaimResult.Failed)
        val failed = result as ClaimResult.Failed
        assertTrue(failed.reason.contains("key", ignoreCase = true))
        assertTrue(failed.reason.contains("manual", ignoreCase = true))
        assertFalse(failed.isRetryable)
    }
    
    @Test
    fun `check eligibility requires auth`() = runBlocking {
        val giveaway = createTestGiveaway("https://www.alienwarearena.com/ucf/show/123456")
        val result = adapter.checkEligibility(giveaway)
        
        assertEquals(ClaimEligibility.REQUIRES_AUTH, result)
    }
    
    @Test
    fun `check eligibility returns unknown for invalid URL`() = runBlocking {
        val giveaway = createTestGiveaway("http://fake-alienware.com/ucf/show/123")
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
        val giveaway = createTestGiveaway("https://www.alienwarearena.com/ucf/show/123456")
        val result = adapter.verifyGiveaway(giveaway)
        
        assertNotNull(result)
        assertEquals(giveaway.id, result?.id)
    }
    
    @Test
    fun `verify giveaway returns null for invalid URL`() = runBlocking {
        val giveaway = createTestGiveaway("http://fake-alienware.com/ucf/show/123")
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
        val giveaway = createTestGiveaway("https://www.alienwarearena.com/ucf/show/123456")
        val result = adapter.getKeyRedemptionPlatform(giveaway)
        
        assertNull(result)
    }
    
    @Test
    fun `key redemption platforms include Steam`() {
        assertTrue(GameStore.STEAM in AlienwareArenaAdapter.KEY_REDEMPTION_PLATFORMS)
    }
    
    @Test
    fun `key redemption platforms include Epic`() {
        assertTrue(GameStore.EPIC_GAMES in AlienwareArenaAdapter.KEY_REDEMPTION_PLATFORMS)
    }
    
    @Test
    fun `key redemption platforms include GOG`() {
        assertTrue(GameStore.GOG in AlienwareArenaAdapter.KEY_REDEMPTION_PLATFORMS)
    }
    
    @Test
    fun `key redemption platforms include Ubisoft`() {
        assertTrue(GameStore.UBISOFT in AlienwareArenaAdapter.KEY_REDEMPTION_PLATFORMS)
    }
    
    // ==================== Companion Object Tests ====================
    
    @Test
    fun `trusted domains include alienwarearena-com`() {
        assertTrue("alienwarearena.com" in AlienwareArenaAdapter.TRUSTED_DOMAINS)
    }
    
    @Test
    fun `trusted domains include www-alienwarearena-com`() {
        assertTrue("www.alienwarearena.com" in AlienwareArenaAdapter.TRUSTED_DOMAINS)
    }
    
    @Test
    fun `trusted domains include NA subdomain`() {
        assertTrue("na.alienwarearena.com" in AlienwareArenaAdapter.TRUSTED_DOMAINS)
    }
    
    @Test
    fun `trusted domains include EU subdomain`() {
        assertTrue("eu.alienwarearena.com" in AlienwareArenaAdapter.TRUSTED_DOMAINS)
    }
    
    @Test
    fun `trusted domains include LATAM subdomain`() {
        assertTrue("latam.alienwarearena.com" in AlienwareArenaAdapter.TRUSTED_DOMAINS)
    }
    
    @Test
    fun `URL home constant is correct`() {
        assertEquals("https://www.alienwarearena.com", AlienwareArenaAdapter.URL_HOME)
    }
    
    @Test
    fun `URL login constant is correct`() {
        assertEquals("https://www.alienwarearena.com/login", AlienwareArenaAdapter.URL_LOGIN)
    }
    
    @Test
    fun `URL giveaways constant is correct`() {
        assertEquals("https://www.alienwarearena.com/ucf/show/giveaways", AlienwareArenaAdapter.URL_GIVEAWAYS)
    }
    
    // ==================== Regional Subdomains Tests ====================
    
    @Test
    fun `regional subdomains include NA`() {
        assertTrue("na" in AlienwareArenaAdapter.REGIONAL_SUBDOMAINS)
    }
    
    @Test
    fun `regional subdomains include EU`() {
        assertTrue("eu" in AlienwareArenaAdapter.REGIONAL_SUBDOMAINS)
    }
    
    @Test
    fun `regional subdomains include LATAM`() {
        assertTrue("latam" in AlienwareArenaAdapter.REGIONAL_SUBDOMAINS)
    }
    
    @Test
    fun `regional subdomains include www`() {
        assertTrue("www" in AlienwareArenaAdapter.REGIONAL_SUBDOMAINS)
    }
    
    // ==================== Regex Tests ====================
    
    @Test
    fun `giveaway regex matches valid giveaway URL`() {
        val match = AlienwareArenaAdapter.ALIENWARE_GIVEAWAY_REGEX.find(
            "https://www.alienwarearena.com/ucf/show/2156789/boards/contest-and-giveaways-702/Giveaway/game-key-giveaway"
        )
        assertNotNull(match)
    }
    
    @Test
    fun `UCF ID regex extracts numeric ID`() {
        val match = AlienwareArenaAdapter.ALIENWARE_UCF_ID_REGEX.find(
            "https://na.alienwarearena.com/ucf/show/1234567/boards/test"
        )
        assertNotNull(match)
        assertEquals("1234567", match?.groupValues?.get(1))
    }
    
    @Test
    fun `member regex matches valid member URL`() {
        val match = AlienwareArenaAdapter.ALIENWARE_MEMBER_REGEX.find(
            "https://www.alienwarearena.com/member/98765/profile"
        )
        assertNotNull(match)
        assertEquals("98765", match?.groupValues?.get(1))
    }
    
    // ==================== Helper Functions ====================
    
    private fun createTestGiveaway(claimUrl: String): Giveaway {
        return Giveaway(
            id = "test:alienware_arena:123",
            title = "Test Alienware Arena Giveaway",
            store = GameStore.ALIENWARE_ARENA,
            type = GiveawayType.FREE_TO_KEEP,
            claimUrl = claimUrl,
            source = DiscoverySource.GAMER_POWER
        )
    }
}
