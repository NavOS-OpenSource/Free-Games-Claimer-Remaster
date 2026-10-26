package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.UbisoftAdapter
import com.navos.gameclaimer.data.adapter.UbisoftPageState
import com.navos.gameclaimer.data.adapter.DefaultStoreAdapterFactory
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.GiveawayType
import com.navos.gameclaimer.domain.model.DiscoverySource
import com.navos.gameclaimer.domain.model.ClaimResult
import com.navos.gameclaimer.domain.model.Giveaway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 10: Ubisoft Adapter Tests
 * 
 * Covers:
 * - Basic adapter configuration
 * - URL validation (HTTPS, trusted domains, attack prevention)
 * - Claim URL parsing
 * - Giveaway detection (vs trials/demos)
 * - Time window validation
 * - Page state detection
 * - Claim disabled verification
 */
class UbisoftAdapterTest {
    
    private lateinit var adapter: UbisoftAdapter
    
    @Before
    fun setup() {
        adapter = UbisoftAdapter()
    }
    
    // =========================================================================
    // Basic Configuration Tests
    // =========================================================================
    
    @Test
    fun `adapter has correct store type`() {
        assertEquals(GameStore.UBISOFT, adapter.store)
    }
    
    @Test
    fun `adapter has correct display name`() {
        assertEquals("Ubisoft", adapter.displayName)
    }
    
    @Test
    fun `adapter supports discovery`() {
        assertTrue(adapter.supportsDiscovery)
    }
    
    @Test
    fun `adapter does NOT support claiming`() {
        assertFalse(adapter.supportsClaiming)
    }
    
    @Test
    fun `adapter requires auth`() {
        assertTrue(adapter.requiresAuth)
    }
    
    @Test
    fun `getAuthUrl returns login URL`() {
        assertEquals("https://connect.ubisoft.com/", adapter.getAuthUrl())
    }
    
    // =========================================================================
    // URL Validation - Valid Cases
    // =========================================================================
    
    @Test
    fun `valid Ubisoft host - www ubisoft com`() {
        assertTrue(adapter.isValidUbisoftHost("https://www.ubisoft.com/en-us/games/free"))
    }
    
    @Test
    fun `valid Ubisoft host - register ubisoft com`() {
        assertTrue(adapter.isValidUbisoftHost("https://register.ubisoft.com/ghost-recon-future-soldier"))
    }
    
    @Test
    fun `valid Ubisoft host - connect ubisoft com`() {
        assertTrue(adapter.isValidUbisoftHost("https://connect.ubisoft.com/login"))
    }
    
    @Test
    fun `valid Ubisoft host - account ubisoft com`() {
        assertTrue(adapter.isValidUbisoftHost("https://account.ubisoft.com/en-US/account-information"))
    }
    
    @Test
    fun `valid Ubisoft host - ubisoft com root`() {
        assertTrue(adapter.isValidUbisoftHost("https://ubisoft.com/"))
    }
    
    // =========================================================================
    // URL Validation - Invalid Cases
    // =========================================================================
    
    @Test
    fun `invalid Ubisoft host - HTTP scheme`() {
        assertFalse(adapter.isValidUbisoftHost("http://www.ubisoft.com/"))
    }
    
    @Test
    fun `invalid Ubisoft host - fake domain suffix attack`() {
        assertFalse(adapter.isValidUbisoftHost("https://fakeubisoft.com/"))
    }
    
    @Test
    fun `invalid Ubisoft host - fake domain prefix attack`() {
        assertFalse(adapter.isValidUbisoftHost("https://ubisoft.com.attacker.com/"))
    }
    
    @Test
    fun `invalid Ubisoft host - lookalike domain`() {
        assertFalse(adapter.isValidUbisoftHost("https://ubis0ft.com/"))
    }
    
    @Test
    fun `invalid Ubisoft host - IP address v4`() {
        assertFalse(adapter.isValidUbisoftHost("https://192.168.1.1/"))
    }
    
    @Test
    fun `invalid Ubisoft host - IP address v6`() {
        assertFalse(adapter.isValidUbisoftHost("https://[::1]/"))
    }
    
    @Test
    fun `invalid Ubisoft host - userinfo injection`() {
        assertFalse(adapter.isValidUbisoftHost("https://user@ubisoft.com@attacker.com/"))
    }
    
    @Test
    fun `invalid Ubisoft host - unexpected port`() {
        assertFalse(adapter.isValidUbisoftHost("https://www.ubisoft.com:8080/"))
    }
    
    @Test
    fun `invalid Ubisoft host - empty URL`() {
        assertFalse(adapter.isValidUbisoftHost(""))
    }
    
    @Test
    fun `invalid Ubisoft host - malformed URL`() {
        assertFalse(adapter.isValidUbisoftHost("not a url"))
    }
    
    // =========================================================================
    // Claim Host Validation
    // =========================================================================
    
    @Test
    fun `valid claim host - register ubisoft com`() {
        assertTrue(adapter.isValidClaimHost("https://register.ubisoft.com/game-slug"))
    }
    
    @Test
    fun `invalid claim host - www ubisoft com is NOT claim host`() {
        assertFalse(adapter.isValidClaimHost("https://www.ubisoft.com/"))
    }
    
    @Test
    fun `invalid claim host - HTTP scheme`() {
        assertFalse(adapter.isValidClaimHost("http://register.ubisoft.com/game"))
    }
    
    @Test
    fun `invalid claim host - fake register`() {
        assertFalse(adapter.isValidClaimHost("https://register.fakeubisoft.com/game"))
    }
    
    // =========================================================================
    // Login Host Detection
    // =========================================================================
    
    @Test
    fun `isLoginHost - connect ubisoft com`() {
        assertTrue(adapter.isLoginHost("https://connect.ubisoft.com/login"))
    }
    
    @Test
    fun `isLoginHost - subdomain of connect`() {
        assertTrue(adapter.isLoginHost("https://subdomain.connect.ubisoft.com/"))
    }
    
    @Test
    fun `isLoginHost - www ubisoft is NOT login`() {
        assertFalse(adapter.isLoginHost("https://www.ubisoft.com/"))
    }
    
    @Test
    fun `isLoginHost - account ubisoft is NOT login`() {
        assertFalse(adapter.isLoginHost("https://account.ubisoft.com/"))
    }
    
    // =========================================================================
    // Claim URL Parsing
    // =========================================================================
    
    @Test
    fun `parseClaimUrl - valid claim URL`() {
        val slug = adapter.parseClaimUrl("https://register.ubisoft.com/ghost-recon-future-soldier")
        assertEquals("ghost-recon-future-soldier", slug)
    }
    
    @Test
    fun `parseClaimUrl - with en-US suffix`() {
        val slug = adapter.parseClaimUrl("https://register.ubisoft.com/assassins-creed-unity/en-US")
        assertEquals("assassins-creed-unity", slug)
    }
    
    @Test
    fun `parseClaimUrl - invalid host returns null`() {
        assertNull(adapter.parseClaimUrl("https://www.ubisoft.com/game"))
    }
    
    @Test
    fun `parseClaimUrl - no slug returns null`() {
        assertNull(adapter.parseClaimUrl("https://register.ubisoft.com/"))
    }
    
    // =========================================================================
    // Giveaway Detection
    // =========================================================================
    
    @Test
    fun `looksLikeGiveaway - normal game title`() {
        assertTrue(adapter.looksLikeGiveaway("Far Cry 3", "far-cry-3"))
    }
    
    @Test
    fun `looksLikeGiveaway - rejects trial in title`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3 Trial", "far-cry-3"))
    }
    
    @Test
    fun `looksLikeGiveaway - rejects demo in title`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3 Demo", "far-cry-3"))
    }
    
    @Test
    fun `looksLikeGiveaway - rejects beta in title`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3 Beta", "far-cry-3"))
    }
    
    @Test
    fun `looksLikeGiveaway - rejects free week`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3 Free Week", "far-cry-3"))
    }
    
    @Test
    fun `looksLikeGiveaway - rejects free weekend`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3 Free Weekend", "far-cry-3"))
    }
    
    @Test
    fun `looksLikeGiveaway - rejects early access`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3 Early Access", "far-cry-3"))
    }
    
    @Test
    fun `looksLikeGiveaway - rejects playtest`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3 Playtest", "far-cry-3"))
    }
    
    @Test
    fun `looksLikeGiveaway - rejects trial in slug`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3", "far-cry-3-trial"))
    }
    
    @Test
    fun `looksLikeGiveaway - case insensitive`() {
        assertFalse(adapter.looksLikeGiveaway("Far Cry 3 TRIAL", "far-cry-3"))
    }
    
    // =========================================================================
    // Giveaway Type Detection
    // =========================================================================
    
    @Test
    fun `isGiveawayType - gametrial is valid`() {
        assertTrue(adapter.isGiveawayType("gametrial"))
    }
    
    @Test
    fun `isGiveawayType - giveaway is valid`() {
        assertTrue(adapter.isGiveawayType("giveaway"))
    }
    
    @Test
    fun `isGiveawayType - Giveaway with caps`() {
        assertTrue(adapter.isGiveawayType("Giveaway"))
    }
    
    @Test
    fun `isGiveawayType - game is invalid`() {
        assertFalse(adapter.isGiveawayType("game"))
    }
    
    @Test
    fun `isGiveawayType - null is invalid`() {
        assertFalse(adapter.isGiveawayType(null))
    }
    
    @Test
    fun `isFreePlacement - freeevents is valid`() {
        assertTrue(adapter.isFreePlacement("freeevents"))
    }
    
    @Test
    fun `isFreePlacement - other placement is invalid`() {
        assertFalse(adapter.isFreePlacement("news"))
    }
    
    // =========================================================================
    // Time Window Validation
    // =========================================================================
    
    @Test
    fun `validateTimeWindow - valid window`() {
        val now = System.currentTimeMillis()
        val endIn7Days = now + (7L * 24 * 60 * 60 * 1000)
        
        assertNull(adapter.validateTimeWindow(startMs = null, endMs = endIn7Days, nowMs = now))
    }
    
    @Test
    fun `validateTimeWindow - no end date`() {
        val now = System.currentTimeMillis()
        
        assertEquals("no end date", adapter.validateTimeWindow(startMs = null, endMs = null, nowMs = now))
    }
    
    @Test
    fun `validateTimeWindow - already ended`() {
        val now = System.currentTimeMillis()
        val endedYesterday = now - (24 * 60 * 60 * 1000)
        
        assertEquals("already ended", adapter.validateTimeWindow(startMs = null, endMs = endedYesterday, nowMs = now))
    }
    
    @Test
    fun `validateTimeWindow - starts in future`() {
        val now = System.currentTimeMillis()
        val startsTomorrow = now + (24 * 60 * 60 * 1000)
        val endsIn7Days = now + (7L * 24 * 60 * 60 * 1000)
        
        assertEquals("starts in future", adapter.validateTimeWindow(startMs = startsTomorrow, endMs = endsIn7Days, nowMs = now))
    }
    
    @Test
    fun `validateTimeWindow - end date too far out (permanent trial)`() {
        val now = System.currentTimeMillis()
        val endsIn100Days = now + (100L * 24 * 60 * 60 * 1000)
        
        assertTrue(adapter.validateTimeWindow(startMs = null, endMs = endsIn100Days, nowMs = now)!!.contains("too far out"))
    }
    
    @Test
    fun `validateTimeWindow - 90 days is still valid`() {
        val now = System.currentTimeMillis()
        val endsIn89Days = now + (89L * 24 * 60 * 60 * 1000)
        
        assertNull(adapter.validateTimeWindow(startMs = null, endMs = endsIn89Days, nowMs = now))
    }
    
    // =========================================================================
    // Page State Classification
    // =========================================================================
    
    @Test
    fun `classifyPageState - giveaway by title`() {
        assertEquals(
            UbisoftPageState.GIVEAWAY,
            adapter.classifyPageState(
                pageTitle = "Giveaway",
                headings = listOf(),
                bodyText = ""
            )
        )
    }
    
    @Test
    fun `classifyPageState - giveaway by heading`() {
        assertEquals(
            UbisoftPageState.GIVEAWAY,
            adapter.classifyPageState(
                pageTitle = "",
                headings = listOf("GET YOUR FREE GAME!"),
                bodyText = ""
            )
        )
    }
    
    @Test
    fun `classifyPageState - trial by heading`() {
        assertEquals(
            UbisoftPageState.TRIAL,
            adapter.classifyPageState(
                pageTitle = "",
                headings = listOf("TRY NOW FOR FREE!"),
                bodyText = ""
            )
        )
    }
    
    @Test
    fun `classifyPageState - trial by free trial text`() {
        assertEquals(
            UbisoftPageState.TRIAL,
            adapter.classifyPageState(
                pageTitle = "",
                headings = listOf("Start your free trial"),
                bodyText = ""
            )
        )
    }
    
    @Test
    fun `classifyPageState - owned by text`() {
        assertEquals(
            UbisoftPageState.OWNED,
            adapter.classifyPageState(
                pageTitle = "",
                headings = listOf(),
                bodyText = "You already own this game"
            )
        )
    }
    
    @Test
    fun `classifyPageState - claimed successfully`() {
        assertEquals(
            UbisoftPageState.CLAIMED,
            adapter.classifyPageState(
                pageTitle = "",
                headings = listOf("Have fun!"),
                bodyText = ""
            )
        )
    }
    
    @Test
    fun `classifyPageState - claimed by body text`() {
        assertEquals(
            UbisoftPageState.CLAIMED,
            adapter.classifyPageState(
                pageTitle = "",
                headings = listOf(),
                bodyText = "The game has been successfully added to your library"
            )
        )
    }
    
    @Test
    fun `classifyPageState - blocked by DataDome`() {
        assertEquals(
            UbisoftPageState.BLOCKED,
            adapter.classifyPageState(
                pageTitle = "",
                headings = listOf(),
                bodyText = "Checking your browser before accessing the site. Please wait..."
            )
        )
    }
    
    @Test
    fun `classifyPageState - unknown`() {
        assertEquals(
            UbisoftPageState.UNKNOWN,
            adapter.classifyPageState(
                pageTitle = "Some Page",
                headings = listOf("Welcome"),
                bodyText = "Loading content..."
            )
        )
    }
    
    // =========================================================================
    // Ownership Detection
    // =========================================================================
    
    @Test
    fun `isAlreadyOwned - already own`() {
        assertTrue(adapter.isAlreadyOwned("You already own this game"))
    }
    
    @Test
    fun `isAlreadyOwned - already in your library`() {
        assertTrue(adapter.isAlreadyOwned("This game is already in your library"))
    }
    
    @Test
    fun `isAlreadyOwned - already have this`() {
        assertTrue(adapter.isAlreadyOwned("You already have this game"))
    }
    
    @Test
    fun `isAlreadyOwned - you own this`() {
        assertTrue(adapter.isAlreadyOwned("You own this product"))
    }
    
    @Test
    fun `isAlreadyOwned - case insensitive`() {
        assertTrue(adapter.isAlreadyOwned("YOU ALREADY OWN THIS"))
    }
    
    @Test
    fun `isAlreadyOwned - not owned`() {
        assertFalse(adapter.isAlreadyOwned("Claim your free game now!"))
    }
    
    // =========================================================================
    // Claim Confirmation Detection
    // =========================================================================
    
    @Test
    fun `isClaimConfirmed - have fun heading`() {
        assertTrue(adapter.isClaimConfirmed(listOf("Have fun!"), ""))
    }
    
    @Test
    fun `isClaimConfirmed - successfully added`() {
        assertTrue(adapter.isClaimConfirmed(listOf(), "The game has been successfully added"))
    }
    
    @Test
    fun `isClaimConfirmed - added to your`() {
        assertTrue(adapter.isClaimConfirmed(listOf(), "has been added to your Ubisoft account"))
    }
    
    @Test
    fun `isClaimConfirmed - is now yours`() {
        assertTrue(adapter.isClaimConfirmed(listOf(), "Far Cry 3 is now yours!"))
    }
    
    @Test
    fun `isClaimConfirmed - you now own`() {
        assertTrue(adapter.isClaimConfirmed(listOf(), "You now own Far Cry 3"))
    }
    
    @Test
    fun `isClaimConfirmed - not confirmed`() {
        assertFalse(adapter.isClaimConfirmed(listOf("Get your free game"), "Click to claim"))
    }
    
    // =========================================================================
    // Claim Disabled Tests
    // =========================================================================
    
    @Test
    fun `claim returns Failed when disabled`() = runTest {
        val giveaway = Giveaway(
            id = "test-ubi-game",
            title = "Test Ubisoft Game",
            store = GameStore.UBISOFT,
            claimUrl = "https://register.ubisoft.com/test-game",
            imageUrl = null,
            type = GiveawayType.FREE_TO_KEEP,
            source = DiscoverySource.GAMER_POWER
        )
        
        val result = adapter.claim(giveaway)
        
        assertTrue(result is ClaimResult.Failed)
        val failed = result as ClaimResult.Failed
        assertEquals("test-ubi-game", failed.giveawayId)
        assertTrue(failed.reason.contains("disabled"))
        assertFalse(failed.isRetryable)
    }
    
    // =========================================================================
    // Factory Integration Tests
    // =========================================================================
    
    @Test
    fun `factory returns Ubisoft adapter`() {
        val factory = DefaultStoreAdapterFactory()
        val adapter = factory.getAdapter(GameStore.UBISOFT)
        
        assertNotNull(adapter)
        assertEquals(GameStore.UBISOFT, adapter!!.store)
    }
    
    @Test
    fun `factory includes Ubisoft in discovery adapters`() {
        val factory = DefaultStoreAdapterFactory()
        val discoveryAdapters = factory.getDiscoveryAdapters()
        
        assertTrue(discoveryAdapters.any { it.store == GameStore.UBISOFT })
    }
    
    @Test
    fun `factory excludes Ubisoft from claiming adapters`() {
        val factory = DefaultStoreAdapterFactory()
        val claimingAdapters = factory.getClaimingAdapters()
        
        assertFalse(claimingAdapters.any { it.store == GameStore.UBISOFT })
    }
}
