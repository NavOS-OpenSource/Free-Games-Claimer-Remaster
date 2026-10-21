package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.MicrosoftStoreAdapter
import com.navos.gameclaimer.data.adapter.MicrosoftOfferType
import com.navos.gameclaimer.data.adapter.MicrosoftPageState
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
 * Phase 10: Microsoft Store Adapter Tests
 * 
 * Covers:
 * - Basic adapter configuration
 * - URL validation (HTTPS, trusted domains, attack prevention)
 * - Product ID parsing and validation
 * - Price/offer type classification
 * - Page state detection
 * - Claim disabled verification
 */
class MicrosoftStoreAdapterTest {
    
    private lateinit var adapter: MicrosoftStoreAdapter
    
    @Before
    fun setup() {
        adapter = MicrosoftStoreAdapter()
    }
    
    // =========================================================================
    // Basic Configuration Tests
    // =========================================================================
    
    @Test
    fun `adapter has correct store type`() {
        assertEquals(GameStore.MICROSOFT_STORE, adapter.store)
    }
    
    @Test
    fun `adapter has correct display name`() {
        assertEquals("Microsoft Store", adapter.displayName)
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
        assertEquals("https://login.live.com/", adapter.getAuthUrl())
    }
    
    // =========================================================================
    // URL Validation - Valid Cases
    // =========================================================================
    
    @Test
    fun `valid Microsoft host - account microsoft com`() {
        assertTrue(adapter.isValidMicrosoftHost("https://account.microsoft.com/billing/redeem"))
    }
    
    @Test
    fun `valid Microsoft host - xbox com`() {
        assertTrue(adapter.isValidMicrosoftHost("https://www.xbox.com/en-US/games/store/game/9NKX70BBCDRN"))
    }
    
    @Test
    fun `valid Microsoft host - login live com`() {
        assertTrue(adapter.isValidMicrosoftHost("https://login.live.com/"))
    }
    
    @Test
    fun `valid Microsoft host - storeedgefd API`() {
        assertTrue(adapter.isValidMicrosoftHost("https://storeedgefd.dsx.mp.microsoft.com/v9.0/products/ABC"))
    }
    
    @Test
    fun `valid Microsoft host - subdomain of microsoft com`() {
        assertTrue(adapter.isValidMicrosoftHost("https://subdomain.account.microsoft.com/page"))
    }
    
    // =========================================================================
    // URL Validation - Invalid Cases
    // =========================================================================
    
    @Test
    fun `invalid Microsoft host - HTTP scheme`() {
        assertFalse(adapter.isValidMicrosoftHost("http://account.microsoft.com/"))
    }
    
    @Test
    fun `invalid Microsoft host - fake domain suffix attack`() {
        assertFalse(adapter.isValidMicrosoftHost("https://fakemicrosoft.com/"))
    }
    
    @Test
    fun `invalid Microsoft host - fake domain prefix attack`() {
        assertFalse(adapter.isValidMicrosoftHost("https://microsoft.com.attacker.com/"))
    }
    
    @Test
    fun `invalid Microsoft host - lookalike domain`() {
        assertFalse(adapter.isValidMicrosoftHost("https://micr0soft.com/"))
    }
    
    @Test
    fun `invalid Microsoft host - IP address v4`() {
        assertFalse(adapter.isValidMicrosoftHost("https://192.168.1.1/"))
    }
    
    @Test
    fun `invalid Microsoft host - IP address v6`() {
        assertFalse(adapter.isValidMicrosoftHost("https://[::1]/"))
    }
    
    @Test
    fun `invalid Microsoft host - userinfo injection`() {
        assertFalse(adapter.isValidMicrosoftHost("https://user@microsoft.com@attacker.com/"))
    }
    
    @Test
    fun `invalid Microsoft host - unexpected port`() {
        assertFalse(adapter.isValidMicrosoftHost("https://account.microsoft.com:8080/"))
    }
    
    @Test
    fun `invalid Microsoft host - empty URL`() {
        assertFalse(adapter.isValidMicrosoftHost(""))
    }
    
    @Test
    fun `invalid Microsoft host - malformed URL`() {
        assertFalse(adapter.isValidMicrosoftHost("not a url"))
    }
    
    // =========================================================================
    // Login Host Detection
    // =========================================================================
    
    @Test
    fun `isLoginHost - login live com`() {
        assertTrue(adapter.isLoginHost("https://login.live.com/oauth/authorize"))
    }
    
    @Test
    fun `isLoginHost - login microsoftonline com`() {
        assertTrue(adapter.isLoginHost("https://login.microsoftonline.com/common/oauth2"))
    }
    
    @Test
    fun `isLoginHost - login microsoft com`() {
        assertTrue(adapter.isLoginHost("https://login.microsoft.com/"))
    }
    
    @Test
    fun `isLoginHost - account microsoft is NOT login`() {
        assertFalse(adapter.isLoginHost("https://account.microsoft.com/"))
    }
    
    @Test
    fun `isLoginHost - xbox is NOT login`() {
        assertFalse(adapter.isLoginHost("https://www.xbox.com/"))
    }
    
    // =========================================================================
    // Product ID Validation
    // =========================================================================
    
    @Test
    fun `valid product ID - standard format`() {
        assertTrue(adapter.isValidProductId("9NKX70BBCDRN"))
    }
    
    @Test
    fun `valid product ID - lowercase converted`() {
        assertTrue(adapter.isValidProductId("9nkx70bbcdrn"))
    }
    
    @Test
    fun `invalid product ID - too short`() {
        assertFalse(adapter.isValidProductId("9NKX70BB"))
    }
    
    @Test
    fun `invalid product ID - too long`() {
        assertFalse(adapter.isValidProductId("9NKX70BBCDRNXXX"))
    }
    
    @Test
    fun `invalid product ID - special characters`() {
        assertFalse(adapter.isValidProductId("9NKX70BB-DRN"))
    }
    
    @Test
    fun `invalid product ID - empty`() {
        assertFalse(adapter.isValidProductId(""))
    }
    
    // =========================================================================
    // Product URL Parsing
    // =========================================================================
    
    @Test
    fun `parse Xbox product URL`() {
        val url = "https://www.xbox.com/en-US/games/store/halo-infinite/9NKX70BBCDRN"
        val productId = adapter.parseProductUrl(url)
        assertEquals("9NKX70BBCDRN", productId)
    }
    
    @Test
    fun `parse Xbox product URL - different region`() {
        val url = "https://www.xbox.com/en-GB/games/store/some-game/9PP71GF8F05J"
        val productId = adapter.parseProductUrl(url)
        assertEquals("9PP71GF8F05J", productId)
    }
    
    @Test
    fun `parse product URL - invalid host rejected`() {
        val url = "https://fakexbox.com/en-US/games/store/game/9NKX70BBCDRN"
        assertNull(adapter.parseProductUrl(url))
    }
    
    @Test
    fun `parse product URL - no product ID in path`() {
        val url = "https://www.xbox.com/en-US/games"
        assertNull(adapter.parseProductUrl(url))
    }
    
    // =========================================================================
    // Product API URL Generation
    // =========================================================================
    
    @Test
    fun `getProductApiUrl - valid product ID`() {
        val url = adapter.getProductApiUrl("9NKX70BBCDRN", "US")
        assertNotNull(url)
        assertTrue(url!!.contains("9NKX70BBCDRN"))
        assertTrue(url.contains("market=US"))
    }
    
    @Test
    fun `getProductApiUrl - invalid product ID`() {
        assertNull(adapter.getProductApiUrl("invalid"))
    }
    
    @Test
    fun `getProductPageUrl - valid product ID`() {
        val url = adapter.getProductPageUrl("9NKX70BBCDRN")
        assertNotNull(url)
        assertTrue(url!!.contains("9NKX70BBCDRN"))
    }
    
    @Test
    fun `getProductPageUrl - invalid product ID`() {
        assertNull(adapter.getProductPageUrl("bad"))
    }
    
    // =========================================================================
    // Price Classification
    // =========================================================================
    
    @Test
    fun `isFreeToKeep - MSRP positive, price zero`() {
        assertTrue(adapter.isFreeToKeep(price = 0.0, msrp = 59.99, displayPrice = "$0.00"))
    }
    
    @Test
    fun `isFreeToKeep - Game Pass Included is NOT free to keep`() {
        assertFalse(adapter.isFreeToKeep(price = 0.0, msrp = 59.99, displayPrice = "Included"))
    }
    
    @Test
    fun `isFreeToKeep - MSRP zero is free to play, not giveaway`() {
        assertFalse(adapter.isFreeToKeep(price = 0.0, msrp = 0.0, displayPrice = "Free"))
    }
    
    @Test
    fun `isFreeToKeep - paid game`() {
        assertFalse(adapter.isFreeToKeep(price = 59.99, msrp = 59.99, displayPrice = "$59.99"))
    }
    
    @Test
    fun `isFreeToKeep - null price`() {
        assertFalse(adapter.isFreeToKeep(price = null, msrp = 59.99, displayPrice = null))
    }
    
    // =========================================================================
    // Offer Type Classification
    // =========================================================================
    
    @Test
    fun `classifyOfferType - free to keep`() {
        assertEquals(
            MicrosoftOfferType.FREE_TO_KEEP,
            adapter.classifyOfferType(displayPrice = "$0.00", price = 0.0, msrp = 59.99)
        )
    }
    
    @Test
    fun `classifyOfferType - Game Pass`() {
        assertEquals(
            MicrosoftOfferType.GAME_PASS,
            adapter.classifyOfferType(displayPrice = "Included", price = 0.0, msrp = 59.99)
        )
    }
    
    @Test
    fun `classifyOfferType - free to play`() {
        assertEquals(
            MicrosoftOfferType.FREE_TO_PLAY,
            adapter.classifyOfferType(displayPrice = "Free", price = 0.0, msrp = 0.0)
        )
    }
    
    @Test
    fun `classifyOfferType - paid`() {
        assertEquals(
            MicrosoftOfferType.PAID,
            adapter.classifyOfferType(displayPrice = "$59.99", price = 59.99, msrp = 59.99)
        )
    }
    
    @Test
    fun `classifyOfferType - unknown`() {
        assertEquals(
            MicrosoftOfferType.UNKNOWN,
            adapter.classifyOfferType(displayPrice = null, price = null, msrp = null)
        )
    }
    
    // =========================================================================
    // Page State Classification
    // =========================================================================
    
    @Test
    fun `classifyPageState - owned by text`() {
        assertEquals(
            MicrosoftPageState.OWNED,
            adapter.classifyPageState(
                hasGetButton = false,
                hasInstallButton = false,
                hasPlayButton = false,
                bodyText = "You own this product"
            )
        )
    }
    
    @Test
    fun `classifyPageState - owned by library text`() {
        assertEquals(
            MicrosoftPageState.OWNED,
            adapter.classifyPageState(
                hasGetButton = false,
                hasInstallButton = false,
                hasPlayButton = false,
                bodyText = "This game is in your library"
            )
        )
    }
    
    @Test
    fun `classifyPageState - owned by install button`() {
        assertEquals(
            MicrosoftPageState.OWNED,
            adapter.classifyPageState(
                hasGetButton = false,
                hasInstallButton = true,
                hasPlayButton = false,
                bodyText = ""
            )
        )
    }
    
    @Test
    fun `classifyPageState - owned by play button`() {
        assertEquals(
            MicrosoftPageState.OWNED,
            adapter.classifyPageState(
                hasGetButton = false,
                hasInstallButton = false,
                hasPlayButton = true,
                bodyText = ""
            )
        )
    }
    
    @Test
    fun `classifyPageState - get available`() {
        assertEquals(
            MicrosoftPageState.GET_AVAILABLE,
            adapter.classifyPageState(
                hasGetButton = true,
                hasInstallButton = false,
                hasPlayButton = false,
                bodyText = ""
            )
        )
    }
    
    @Test
    fun `classifyPageState - error`() {
        assertEquals(
            MicrosoftPageState.ERROR,
            adapter.classifyPageState(
                hasGetButton = false,
                hasInstallButton = false,
                hasPlayButton = false,
                bodyText = "This page might be temporarily down"
            )
        )
    }
    
    @Test
    fun `classifyPageState - unknown`() {
        assertEquals(
            MicrosoftPageState.UNKNOWN,
            adapter.classifyPageState(
                hasGetButton = false,
                hasInstallButton = false,
                hasPlayButton = false,
                bodyText = "Loading..."
            )
        )
    }
    
    // =========================================================================
    // Claim Disabled Tests
    // =========================================================================
    
    @Test
    fun `claim returns Failed when disabled`() = runTest {
        val giveaway = Giveaway(
            id = "test-ms-game",
            title = "Test Microsoft Game",
            store = GameStore.MICROSOFT_STORE,
            claimUrl = "https://www.xbox.com/en-US/games/store/test/9NKX70BBCDRN",
            imageUrl = null,
            type = GiveawayType.FREE_TO_KEEP,
            source = DiscoverySource.GAMER_POWER
        )
        
        val result = adapter.claim(giveaway)
        
        assertTrue(result is ClaimResult.Failed)
        val failed = result as ClaimResult.Failed
        assertEquals("test-ms-game", failed.giveawayId)
        assertTrue(failed.reason.contains("disabled"))
        assertFalse(failed.isRetryable)
    }
    
    // =========================================================================
    // Factory Integration Tests
    // =========================================================================
    
    @Test
    fun `factory returns Microsoft adapter`() {
        val factory = DefaultStoreAdapterFactory()
        val adapter = factory.getAdapter(GameStore.MICROSOFT_STORE)
        
        assertNotNull(adapter)
        assertEquals(GameStore.MICROSOFT_STORE, adapter!!.store)
    }
    
    @Test
    fun `factory includes Microsoft in discovery adapters`() {
        val factory = DefaultStoreAdapterFactory()
        val discoveryAdapters = factory.getDiscoveryAdapters()
        
        assertTrue(discoveryAdapters.any { it.store == GameStore.MICROSOFT_STORE })
    }
    
    @Test
    fun `factory excludes Microsoft from claiming adapters`() {
        val factory = DefaultStoreAdapterFactory()
        val claimingAdapters = factory.getClaimingAdapters()
        
        assertFalse(claimingAdapters.any { it.store == GameStore.MICROSOFT_STORE })
    }
    
    @Test
    fun `factory has 8 total adapters in Phase 10`() {
        val factory = DefaultStoreAdapterFactory()
        assertEquals(8, factory.getAllAdapters().size)
    }
}
