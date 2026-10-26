package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.FabAdapter
import com.navos.gameclaimer.data.adapter.FabListing
import com.navos.gameclaimer.domain.model.DiscoverySource
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.GiveawayType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 11: Comprehensive tests for FabAdapter
 * 
 * Tests cover:
 * - URL validation (HTTPS-only, no IP, no userinfo, trusted domains)
 * - Listing URL parsing
 * - Free blade API response parsing
 * - Malformed response handling
 * - Free vs paid classification
 * - Security protections against URL attacks
 * - Discovery state (never marks as claimed)
 */
class FabAdapterTest {
    
    private lateinit var adapter: FabAdapter
    
    @Before
    fun setup() {
        adapter = FabAdapter()
    }
    
    // ============================================================
    // Adapter Properties Tests
    // ============================================================
    
    @Test
    fun `adapter has correct store identity`() {
        assertEquals(GameStore.FAB, adapter.store)
        assertEquals("Fab", adapter.displayName)
    }
    
    @Test
    fun `adapter supports discovery but not claiming`() {
        assertTrue(adapter.supportsDiscovery)
        assertFalse(adapter.supportsClaiming)
    }
    
    @Test
    fun `adapter requires authentication`() {
        assertTrue(adapter.requiresAuth)
    }
    
    @Test
    fun `claim always fails with disabled message`() = runTest {
        val giveaway = createTestGiveaway("fab_test-123")
        val result = adapter.claim(giveaway)
        
        assertTrue(result is com.navos.gameclaimer.domain.model.ClaimResult.Failed)
        val failed = result as com.navos.gameclaimer.domain.model.ClaimResult.Failed
        assertTrue(failed.reason.contains("disabled"))
        assertFalse(failed.isRetryable)
    }
    
    @Test
    fun `auth URL returns Fab login page`() {
        assertEquals("https://www.fab.com/login", adapter.getAuthUrl())
    }
    
    // ============================================================
    // URL Validation Tests
    // ============================================================
    
    @Test
    fun `valid HTTPS Fab URL passes validation`() {
        assertTrue(adapter.isValidUrl("https://www.fab.com/listings/abc-123"))
        assertTrue(adapter.isValidUrl("https://fab.com/listings/def-456"))
    }
    
    @Test
    fun `valid HTTPS Epic URL passes validation for SSO`() {
        assertTrue(adapter.isValidUrl("https://www.epicgames.com/id/login"))
        assertTrue(adapter.isValidUrl("https://epicgames.com/store"))
    }
    
    @Test
    fun `HTTP URLs are rejected`() {
        assertFalse(adapter.isValidUrl("http://www.fab.com/listings/abc-123"))
        assertFalse(adapter.isValidUrl("http://fab.com/"))
    }
    
    @Test
    fun `URLs with userinfo are rejected`() {
        assertFalse(adapter.isValidUrl("https://user:pass@www.fab.com/listings/abc"))
        assertFalse(adapter.isValidUrl("https://admin@fab.com/"))
    }
    
    @Test
    fun `IPv4 addresses are rejected`() {
        assertFalse(adapter.isValidUrl("https://192.168.1.1/listings/abc"))
        assertFalse(adapter.isValidUrl("https://10.0.0.1/"))
        assertFalse(adapter.isValidUrl("https://127.0.0.1/"))
    }
    
    @Test
    fun `IPv6 addresses are rejected`() {
        assertFalse(adapter.isValidUrl("https://[::1]/listings/abc"))
        assertFalse(adapter.isValidUrl("https://[2001:db8::1]/"))
    }
    
    @Test
    fun `URLs with explicit ports are rejected`() {
        assertFalse(adapter.isValidUrl("https://www.fab.com:8080/listings/abc"))
        assertFalse(adapter.isValidUrl("https://fab.com:443/"))  // Even standard port
    }
    
    @Test
    fun `untrusted domains are rejected`() {
        assertFalse(adapter.isValidUrl("https://fakefab.com/listings/abc"))
        assertFalse(adapter.isValidUrl("https://fab.com.attacker.com/"))
        assertFalse(adapter.isValidUrl("https://notfab.com/"))
        assertFalse(adapter.isValidUrl("https://epic-games.com/"))
    }
    
    @Test
    fun `lookalike domain attacks are rejected`() {
        assertFalse(adapter.isValidUrl("https://fаb.com/"))  // Cyrillic 'а'
        assertFalse(adapter.isValidUrl("https://fab-store.com/"))
        assertFalse(adapter.isValidUrl("https://fabcom.net/"))
        assertFalse(adapter.isValidUrl("https://epicgames-store.com/"))
    }
    
    @Test
    fun `suffix attacks are rejected`() {
        assertFalse(adapter.isValidUrl("https://maliciousfab.com/"))
        assertFalse(adapter.isValidUrl("https://xfab.com/"))
    }
    
    @Test
    fun `prefix attacks are rejected`() {
        assertFalse(adapter.isValidUrl("https://fab.com.malicious.com/"))
        assertFalse(adapter.isValidUrl("https://www.fab.com.evil.net/"))
    }
    
    @Test
    fun `malformed URLs return false`() {
        assertFalse(adapter.isValidUrl("not a url"))
        assertFalse(adapter.isValidUrl(""))
        assertFalse(adapter.isValidUrl("://fab.com"))
        assertFalse(adapter.isValidUrl("fab.com/listings"))
    }
    
    @Test
    fun `javascript and data URLs are rejected`() {
        assertFalse(adapter.isValidUrl("javascript:alert(1)"))
        assertFalse(adapter.isValidUrl("data:text/html,<h1>fake</h1>"))
    }
    
    // ============================================================
    // Host Validation Tests
    // ============================================================
    
    @Test
    fun `exact trusted domains pass validation`() {
        assertTrue(adapter.isValidHost("fab.com"))
        assertTrue(adapter.isValidHost("www.fab.com"))
        assertTrue(adapter.isValidHost("epicgames.com"))
        assertTrue(adapter.isValidHost("www.epicgames.com"))
    }
    
    @Test
    fun `subdomains of trusted domains pass validation`() {
        assertTrue(adapter.isValidHost("api.fab.com"))
        assertTrue(adapter.isValidHost("store.epicgames.com"))
        assertTrue(adapter.isValidHost("cdn.www.fab.com"))
    }
    
    @Test
    fun `untrusted hosts are rejected`() {
        assertFalse(adapter.isValidHost("malicious.com"))
        assertFalse(adapter.isValidHost("fab.net"))
        assertFalse(adapter.isValidHost("epicgames.org"))
    }
    
    @Test
    fun `host validation is case insensitive`() {
        assertTrue(adapter.isValidHost("FAB.COM"))
        assertTrue(adapter.isValidHost("Www.Fab.Com"))
        assertTrue(adapter.isValidHost("EPICGAMES.COM"))
    }
    
    // ============================================================
    // Listing URL Parsing Tests
    // ============================================================
    
    @Test
    fun `valid listing URL extracts UID`() {
        assertEquals("abc-123-def", adapter.parseListingUrl("https://www.fab.com/listings/abc-123-def"))
        assertEquals("12345678-abcd-1234-efgh-123456789012", 
            adapter.parseListingUrl("https://fab.com/listings/12345678-abcd-1234-efgh-123456789012"))
    }
    
    @Test
    fun `listing URL with www prefix works`() {
        assertEquals("test-uid", adapter.parseListingUrl("https://www.fab.com/listings/test-uid"))
    }
    
    @Test
    fun `listing URL without www prefix works`() {
        assertEquals("test-uid", adapter.parseListingUrl("https://fab.com/listings/test-uid"))
    }
    
    @Test
    fun `invalid listing URLs return null`() {
        assertNull(adapter.parseListingUrl("https://fab.com/not-listings/abc"))
        assertNull(adapter.parseListingUrl("https://www.fab.com/products/abc"))
        assertNull(adapter.parseListingUrl("https://malicious.com/listings/abc"))
    }
    
    @Test
    fun `non-HTTPS listing URLs return null`() {
        assertNull(adapter.parseListingUrl("http://www.fab.com/listings/abc"))
    }
    
    // ============================================================
    // Free Blade Parsing Tests
    // ============================================================
    
    @Test
    fun `valid free blade with listings parses correctly`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf(
                    "listing" to mapOf(
                        "uid" to "abc-123",
                        "title" to "Test Asset",
                        "user" to mapOf("sellerName" to "Test Creator"),
                        "listingType" to "3d-model"
                    )
                )
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals(1, listings.size)
        assertEquals("abc-123", listings[0].uid)
        assertEquals("Test Asset", listings[0].title)
        assertEquals("Test Creator", listings[0].seller)
        assertEquals("3d-model", listings[0].listingType)
    }
    
    @Test
    fun `blade without isLimitedFreeContent returns empty`() {
        val blade = mapOf(
            "isLimitedFreeContent" to false,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf("listing" to mapOf("uid" to "abc", "title" to "Test"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertTrue(listings.isEmpty())
    }
    
    @Test
    fun `blade with null isLimitedFreeContent returns empty`() {
        val blade = mapOf(
            "isLimitedFreeContent" to null,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf("listing" to mapOf("uid" to "abc", "title" to "Test"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertTrue(listings.isEmpty())
    }
    
    @Test
    fun `blade with wrong blockContentType returns empty`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "subscriptions",
            "tiles" to listOf(
                mapOf("listing" to mapOf("uid" to "abc", "title" to "Test"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertTrue(listings.isEmpty())
    }
    
    @Test
    fun `blade with bundles blockContentType returns empty`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "bundles",
            "tiles" to listOf(
                mapOf("listing" to mapOf("uid" to "abc", "title" to "Test"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertTrue(listings.isEmpty())
    }
    
    @Test
    fun `blade with null tiles returns empty`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to null
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertTrue(listings.isEmpty())
    }
    
    @Test
    fun `blade with empty tiles returns empty`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to emptyList<Any>()
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertTrue(listings.isEmpty())
    }
    
    @Test
    fun `tiles without uid are skipped`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf("listing" to mapOf("title" to "No UID")),
                mapOf("listing" to mapOf("uid" to "valid", "title" to "Has UID"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals(1, listings.size)
        assertEquals("valid", listings[0].uid)
    }
    
    @Test
    fun `tiles with empty uid are skipped`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf("listing" to mapOf("uid" to "", "title" to "Empty UID")),
                mapOf("listing" to mapOf("uid" to "   ", "title" to "Whitespace UID")),
                mapOf("listing" to mapOf("uid" to "valid", "title" to "Valid UID"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals(1, listings.size)
        assertEquals("valid", listings[0].uid)
    }
    
    @Test
    fun `tiles with null listing are skipped`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf("listing" to null),
                mapOf("listing" to mapOf("uid" to "valid", "title" to "Valid"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals(1, listings.size)
    }
    
    @Test
    fun `seller name from nested user object`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf(
                    "listing" to mapOf(
                        "uid" to "test",
                        "title" to "Test",
                        "user" to mapOf("sellerName" to "Nested Seller")
                    )
                )
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals("Nested Seller", listings[0].seller)
    }
    
    @Test
    fun `seller name from plain string user`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf(
                    "listing" to mapOf(
                        "uid" to "test",
                        "title" to "Test",
                        "user" to "Plain String Seller"
                    )
                )
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals("Plain String Seller", listings[0].seller)
    }
    
    @Test
    fun `missing title defaults to Fab asset`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf("listing" to mapOf("uid" to "test"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals("Fab asset", listings[0].title)
    }
    
    @Test
    fun `multiple valid listings parse correctly`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf("listing" to mapOf("uid" to "uid-1", "title" to "Asset 1")),
                mapOf("listing" to mapOf("uid" to "uid-2", "title" to "Asset 2")),
                mapOf("listing" to mapOf("uid" to "uid-3", "title" to "Asset 3"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals(3, listings.size)
        assertEquals(setOf("uid-1", "uid-2", "uid-3"), listings.map { it.uid }.toSet())
    }
    
    // ============================================================
    // Listing Type Validation Tests
    // ============================================================
    
    @Test
    fun `valid listing types pass validation`() {
        assertTrue(adapter.isValidListingType("3d-model"))
        assertTrue(adapter.isValidListingType("audio"))
        assertTrue(adapter.isValidListingType("texture"))
        assertTrue(adapter.isValidListingType("material"))
        assertTrue(adapter.isValidListingType("hdri"))
        assertTrue(adapter.isValidListingType("blueprint"))
        assertTrue(adapter.isValidListingType("plugin"))
        assertTrue(adapter.isValidListingType("code-plugin"))
        assertTrue(adapter.isValidListingType("environment"))
    }
    
    @Test
    fun `listing type validation is case insensitive`() {
        assertTrue(adapter.isValidListingType("3D-MODEL"))
        assertTrue(adapter.isValidListingType("Audio"))
        assertTrue(adapter.isValidListingType("TEXTURE"))
    }
    
    @Test
    fun `invalid listing types fail validation`() {
        assertFalse(adapter.isValidListingType("subscription"))
        assertFalse(adapter.isValidListingType("bundle"))
        assertFalse(adapter.isValidListingType("unknown"))
        assertFalse(adapter.isValidListingType(""))
    }
    
    // ============================================================
    // FabListing Tests
    // ============================================================
    
    @Test
    fun `FabListing converts to Giveaway correctly`() {
        val listing = FabListing(
            uid = "test-uid-123",
            title = "Epic 3D Model",
            seller = "Pro Creator",
            listingType = "3d-model",
            url = "https://www.fab.com/listings/test-uid-123"
        )
        
        val giveaway = listing.toGiveaway()
        
        assertEquals("fab_test-uid-123", giveaway.id)
        assertEquals("Epic 3D Model", giveaway.title)
        assertEquals(GameStore.FAB, giveaway.store)
        assertEquals("https://www.fab.com/listings/test-uid-123", giveaway.claimUrl)
        assertEquals(GiveawayType.FREE_TO_KEEP, giveaway.type)
        assertEquals(DiscoverySource.STORE_DIRECT, giveaway.source)
        assertEquals("test-uid-123", giveaway.externalId)
    }
    
    @Test
    fun `FabListing description includes seller`() {
        val listing = FabListing(
            uid = "test",
            title = "Test",
            seller = "Creator Name",
            listingType = "audio",
            url = "https://www.fab.com/listings/test"
        )
        
        val giveaway = listing.toGiveaway()
        assertEquals("By Creator Name", giveaway.description)
    }
    
    // ============================================================
    // Edge Cases and Error Handling
    // ============================================================
    
    @Test
    fun `empty blade map returns empty list`() {
        val listings = adapter.parseFreeBlade(emptyMap())
        assertTrue(listings.isEmpty())
    }
    
    @Test
    fun `blade with unexpected structure does not crash`() {
        val weirdBlade = mapOf(
            "isLimitedFreeContent" to "true",  // String instead of boolean
            "blockContentType" to "listings",
            "tiles" to "not a list"
        )
        
        // Should not crash, just return empty
        val listings = adapter.parseFreeBlade(weirdBlade)
        assertTrue(listings.isEmpty())
    }
    
    @Test
    fun `duplicate UIDs in blade are all parsed`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf("listing" to mapOf("uid" to "same-uid", "title" to "First")),
                mapOf("listing" to mapOf("uid" to "same-uid", "title" to "Second"))
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        // Duplicates are allowed at parse level - deduplication is caller's responsibility
        assertEquals(2, listings.size)
    }
    
    @Test
    fun `whitespace in values is trimmed`() {
        val blade = mapOf(
            "isLimitedFreeContent" to true,
            "blockContentType" to "listings",
            "tiles" to listOf(
                mapOf(
                    "listing" to mapOf(
                        "uid" to "  test-uid  ",
                        "title" to "  Test Title  ",
                        "user" to mapOf("sellerName" to "  Seller  "),
                        "listingType" to "  3d-model  "
                    )
                )
            )
        )
        
        val listings = adapter.parseFreeBlade(blade)
        assertEquals("test-uid", listings[0].uid)
        assertEquals("Test Title", listings[0].title)
        assertEquals("Seller", listings[0].seller)
        assertEquals("3d-model", listings[0].listingType)
    }
    
    // ============================================================
    // Discovery State Tests (Security)
    // ============================================================
    
    @Test
    fun `discovery does not mark offers as claimed`() = runTest {
        val result = adapter.discoverGiveaways(5000)
        
        // Discovery result should be empty (not implemented) or contain unclaimed offers
        assertTrue(result is com.navos.gameclaimer.domain.model.DiscoveryResult.Empty)
    }
    
    @Test
    fun `verify giveaway preserves original state`() = runTest {
        val giveaway = createTestGiveaway("fab_test-123")
        val verified = adapter.verifyGiveaway(giveaway)
        
        // Verification should not change the giveaway's state
        assertNotNull(verified)
        assertEquals(giveaway.id, verified?.id)
    }
    
    @Test
    fun `check eligibility returns requires auth`() = runTest {
        val giveaway = createTestGiveaway("fab_test-123")
        val eligibility = adapter.checkEligibility(giveaway)
        
        assertEquals(com.navos.gameclaimer.domain.model.ClaimEligibility.REQUIRES_AUTH, eligibility)
    }
    
    // ============================================================
    // Helper Functions
    // ============================================================
    
    private fun createTestGiveaway(id: String) = com.navos.gameclaimer.domain.model.Giveaway(
        id = id,
        title = "Test Asset",
        store = GameStore.FAB,
        type = GiveawayType.FREE_TO_KEEP,
        claimUrl = "https://www.fab.com/listings/test",
        originalPrice = null,
        imageUrl = null,
        description = "Test",
        startTime = null,
        endTime = null,
        source = DiscoverySource.STORE_DIRECT,
        externalId = null
    )
}
