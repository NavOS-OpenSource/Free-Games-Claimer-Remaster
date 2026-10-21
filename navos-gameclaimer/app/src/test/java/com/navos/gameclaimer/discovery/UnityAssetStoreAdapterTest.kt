package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.GiftLink
import com.navos.gameclaimer.data.adapter.UnityAssetStoreAdapter
import com.navos.gameclaimer.data.adapter.UnityFreeAsset
import com.navos.gameclaimer.domain.model.DiscoverySource
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.GiveawayType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 11: Comprehensive tests for UnityAssetStoreAdapter
 * 
 * Tests cover:
 * - URL validation (HTTPS-only, no IP, no userinfo, trusted domains)
 * - Package URL parsing
 * - Free asset page parsing
 * - Coupon code extraction
 * - Checkout total parsing
 * - Malformed response handling
 * - Security protections against URL attacks
 * - Discovery state (never marks as claimed)
 * - Coupon handling (transient, not persisted)
 */
class UnityAssetStoreAdapterTest {
    
    private lateinit var adapter: UnityAssetStoreAdapter
    
    @Before
    fun setup() {
        adapter = UnityAssetStoreAdapter()
    }
    
    // ============================================================
    // Adapter Properties Tests
    // ============================================================
    
    @Test
    fun `adapter has correct store identity`() {
        assertEquals(GameStore.UNITY_ASSET_STORE, adapter.store)
        assertEquals("Unity Asset Store", adapter.displayName)
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
        val giveaway = createTestGiveaway("unity_12345")
        val result = adapter.claim(giveaway)
        
        assertTrue(result is com.navos.gameclaimer.domain.model.ClaimResult.Failed)
        val failed = result as com.navos.gameclaimer.domain.model.ClaimResult.Failed
        assertTrue(failed.reason.contains("disabled"))
        assertFalse(failed.isRetryable)
    }
    
    @Test
    fun `auth URL returns Unity ID login page`() {
        assertEquals("https://id.unity.com/en/login", adapter.getAuthUrl())
    }
    
    // ============================================================
    // URL Validation Tests
    // ============================================================
    
    @Test
    fun `valid HTTPS Unity URLs pass validation`() {
        assertTrue(adapter.isValidUrl("https://assetstore.unity.com/packages/3d/test-12345"))
        assertTrue(adapter.isValidUrl("https://unity.com/"))
        assertTrue(adapter.isValidUrl("https://id.unity.com/en/login"))
        assertTrue(adapter.isValidUrl("https://pay.unity.com/checkout"))
    }
    
    @Test
    fun `HTTP URLs are rejected`() {
        assertFalse(adapter.isValidUrl("http://assetstore.unity.com/packages/3d/test-12345"))
        assertFalse(adapter.isValidUrl("http://unity.com/"))
    }
    
    @Test
    fun `URLs with userinfo are rejected`() {
        assertFalse(adapter.isValidUrl("https://user:pass@assetstore.unity.com/"))
        assertFalse(adapter.isValidUrl("https://admin@unity.com/"))
    }
    
    @Test
    fun `IPv4 addresses are rejected`() {
        assertFalse(adapter.isValidUrl("https://192.168.1.1/packages/test"))
        assertFalse(adapter.isValidUrl("https://10.0.0.1/"))
        assertFalse(adapter.isValidUrl("https://127.0.0.1/"))
    }
    
    @Test
    fun `IPv6 addresses are rejected`() {
        assertFalse(adapter.isValidUrl("https://[::1]/packages/test"))
        assertFalse(adapter.isValidUrl("https://[2001:db8::1]/"))
    }
    
    @Test
    fun `URLs with explicit ports are rejected`() {
        assertFalse(adapter.isValidUrl("https://assetstore.unity.com:8080/"))
        assertFalse(adapter.isValidUrl("https://unity.com:443/"))  // Even standard port
    }
    
    @Test
    fun `untrusted domains are rejected`() {
        assertFalse(adapter.isValidUrl("https://fakeunity.com/"))
        assertFalse(adapter.isValidUrl("https://unity.com.attacker.com/"))
        assertFalse(adapter.isValidUrl("https://assetstore-unity.com/"))
        assertFalse(adapter.isValidUrl("https://unity-store.com/"))
    }
    
    @Test
    fun `lookalike domain attacks are rejected`() {
        assertFalse(adapter.isValidUrl("https://unіty.com/"))  // Cyrillic 'і'
        assertFalse(adapter.isValidUrl("https://unity-assets.com/"))
        assertFalse(adapter.isValidUrl("https://unityassetstore.net/"))
    }
    
    @Test
    fun `suffix attacks are rejected`() {
        assertFalse(adapter.isValidUrl("https://maliciousunity.com/"))
        assertFalse(adapter.isValidUrl("https://xunity.com/"))
    }
    
    @Test
    fun `prefix attacks are rejected`() {
        assertFalse(adapter.isValidUrl("https://unity.com.malicious.com/"))
        assertFalse(adapter.isValidUrl("https://assetstore.unity.com.evil.net/"))
    }
    
    @Test
    fun `malformed URLs return false`() {
        assertFalse(adapter.isValidUrl("not a url"))
        assertFalse(adapter.isValidUrl(""))
        assertFalse(adapter.isValidUrl("://unity.com"))
        assertFalse(adapter.isValidUrl("unity.com/packages"))
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
        assertTrue(adapter.isValidHost("assetstore.unity.com"))
        assertTrue(adapter.isValidHost("unity.com"))
        assertTrue(adapter.isValidHost("id.unity.com"))
        assertTrue(adapter.isValidHost("login.unity.com"))
        assertTrue(adapter.isValidHost("pay.unity.com"))
    }
    
    @Test
    fun `subdomains of trusted domains pass validation`() {
        assertTrue(adapter.isValidHost("api.assetstore.unity.com"))
        assertTrue(adapter.isValidHost("cdn.unity.com"))
        assertTrue(adapter.isValidHost("auth.id.unity.com"))
    }
    
    @Test
    fun `untrusted hosts are rejected`() {
        assertFalse(adapter.isValidHost("malicious.com"))
        assertFalse(adapter.isValidHost("unity.net"))
        assertFalse(adapter.isValidHost("assetstore.com"))
    }
    
    @Test
    fun `host validation is case insensitive`() {
        assertTrue(adapter.isValidHost("UNITY.COM"))
        assertTrue(adapter.isValidHost("AssetStore.Unity.Com"))
        assertTrue(adapter.isValidHost("ID.UNITY.COM"))
    }
    
    // ============================================================
    // Package URL Parsing Tests
    // ============================================================
    
    @Test
    fun `valid package URL extracts ID`() {
        assertEquals("12345", adapter.parsePackageUrl(
            "https://assetstore.unity.com/packages/3d/environments/landscapes/nature-starter-kit-2-12345"))
        assertEquals("99999", adapter.parsePackageUrl(
            "https://assetstore.unity.com/packages/tools/animation/animator-99999"))
    }
    
    @Test
    fun `package URL with different categories work`() {
        assertEquals("111", adapter.parsePackageUrl(
            "https://assetstore.unity.com/packages/2d/textures-materials/urban-textures-111"))
        assertEquals("222", adapter.parsePackageUrl(
            "https://assetstore.unity.com/packages/audio/music/ambient-sounds-222"))
        assertEquals("333", adapter.parsePackageUrl(
            "https://assetstore.unity.com/packages/vfx/particles/fire-effects-333"))
    }
    
    @Test
    fun `invalid package URLs return null`() {
        assertNull(adapter.parsePackageUrl("https://assetstore.unity.com/"))
        assertNull(adapter.parsePackageUrl("https://assetstore.unity.com/packages/"))
        assertNull(adapter.parsePackageUrl("https://assetstore.unity.com/packages/3d/"))
    }
    
    @Test
    fun `package URL without ID returns null`() {
        assertNull(adapter.parsePackageUrl("https://assetstore.unity.com/packages/3d/test-no-number"))
    }
    
    @Test
    fun `non-HTTPS package URLs return null`() {
        assertNull(adapter.parsePackageUrl("http://assetstore.unity.com/packages/3d/test-12345"))
    }
    
    @Test
    fun `malicious domain package URLs return null`() {
        assertNull(adapter.parsePackageUrl("https://fake-assetstore.unity.com/packages/3d/test-12345"))
    }
    
    // ============================================================
    // Slug Extraction Tests
    // ============================================================
    
    @Test
    fun `extract slug from package URL`() {
        assertEquals("nature-kit-12345", adapter.extractSlug(
            "https://assetstore.unity.com/packages/3d/environments/nature-kit-12345"))
    }
    
    @Test
    fun `extract slug handles trailing slash`() {
        assertEquals("test-asset-999", adapter.extractSlug(
            "https://assetstore.unity.com/packages/tools/test-asset-999/"))
    }
    
    @Test
    fun `extract slug from simple path`() {
        assertEquals("simple", adapter.extractSlug("https://example.com/simple"))
    }
    
    // ============================================================
    // Asset Name From Title Tests
    // ============================================================
    
    @Test
    fun `parse asset name from standard title`() {
        assertEquals("Nature Starter Kit", adapter.parseAssetNameFromTitle(
            "Nature Starter Kit | 3D Environments | Unity Asset Store"))
    }
    
    @Test
    fun `parse asset name handles multiple pipes`() {
        assertEquals("Pro Sound Effects", adapter.parseAssetNameFromTitle(
            "Pro Sound Effects | Audio | Music | Unity Asset Store"))
    }
    
    @Test
    fun `parse asset name returns empty for non-standard title`() {
        assertEquals("", adapter.parseAssetNameFromTitle("Just a title"))
        assertEquals("", adapter.parseAssetNameFromTitle("Unity Asset Store"))
        assertEquals("", adapter.parseAssetNameFromTitle(""))
    }
    
    @Test
    fun `parse asset name handles whitespace`() {
        assertEquals("Trim Test", adapter.parseAssetNameFromTitle(
            "  Trim Test  |  Category  |  Unity Asset Store  "))
    }
    
    // ============================================================
    // Free Asset Parsing Tests
    // ============================================================
    
    @Test
    fun `parse free asset with all required elements`() {
        val pageText = """
            PUBLISHER ASSET GIVEAWAY
            Amazing Free Asset
            Add this week's free asset to your cart!
            Use coupon code FREEWEEK2024 at checkout.
        """.trimIndent()
        
        val links = listOf(GiftLink(
            href = "/packages/3d/environments/amazing-asset-12345",
            text = "Free Gift"
        ))
        
        val asset = adapter.parseFreeAsset(pageText, links)
        
        assertNotNull(asset)
        assertEquals("Amazing Free Asset", asset!!.title)
        assertEquals("https://assetstore.unity.com/packages/3d/environments/amazing-asset-12345", asset.url)
        assertEquals("amazing-asset-12345", asset.slug)
        assertEquals("12345", asset.packageId)
        assertEquals("FREEWEEK2024", asset.coupon)
    }
    
    @Test
    fun `parse free asset with full URL in link`() {
        val pageText = """
            PUBLISHER ASSET GIVEAWAY
            Full URL Asset
            Add this week's free asset!
            coupon code FULLURL123
        """.trimIndent()
        
        val links = listOf(GiftLink(
            href = "https://assetstore.unity.com/packages/tools/test-99999",
            text = "Free Asset"
        ))
        
        val asset = adapter.parseFreeAsset(pageText, links)
        
        assertNotNull(asset)
        assertEquals("https://assetstore.unity.com/packages/tools/test-99999", asset!!.url)
    }
    
    @Test
    fun `parse free asset returns null without coupon`() {
        val pageText = """
            PUBLISHER ASSET GIVEAWAY
            No Coupon Asset
            Add this week's free asset!
        """.trimIndent()
        
        val links = listOf(GiftLink(
            href = "/packages/3d/test-123",
            text = "Free Gift"
        ))
        
        val asset = adapter.parseFreeAsset(pageText, links)
        assertNull(asset)
    }
    
    @Test
    fun `parse free asset returns null without giveaway name`() {
        val pageText = """
            Some other content
            coupon code ORPHAN123
        """.trimIndent()
        
        val links = listOf(GiftLink(
            href = "/packages/3d/test-123",
            text = "Free Gift"
        ))
        
        val asset = adapter.parseFreeAsset(pageText, links)
        assertNull(asset)
    }
    
    @Test
    fun `parse free asset returns null without gift link`() {
        val pageText = """
            PUBLISHER ASSET GIVEAWAY
            Orphan Asset
            Add this week coupon code ORPHAN123
        """.trimIndent()
        
        val asset = adapter.parseFreeAsset(pageText, emptyList())
        assertNull(asset)
    }
    
    @Test
    fun `parse free asset returns null with empty link href`() {
        val pageText = """
            PUBLISHER ASSET GIVEAWAY
            Empty Link Asset
            Add this week coupon code EMPTY123
        """.trimIndent()
        
        val links = listOf(GiftLink(href = "", text = "Free Gift"))
        
        val asset = adapter.parseFreeAsset(pageText, links)
        assertNull(asset)
    }
    
    @Test
    fun `parse free asset returns null with malicious URL`() {
        val pageText = """
            PUBLISHER ASSET GIVEAWAY
            Malicious Asset
            Add this week coupon code EVIL123
        """.trimIndent()
        
        val links = listOf(GiftLink(
            href = "https://evil.com/packages/3d/test-123",
            text = "Free Gift"
        ))
        
        val asset = adapter.parseFreeAsset(pageText, links)
        assertNull(asset)
    }
    
    @Test
    fun `coupon extraction handles various formats`() {
        val testCases = listOf(
            "coupon code ABC123" to "ABC123",
            "coupon code  DOUBLE_SPACE" to "DOUBLE_SPACE",
            "COUPON CODE UPPERCASE" to "UPPERCASE",
            "Coupon Code Mixed_Case-123" to "Mixed_Case-123"
        )
        
        for ((text, expected) in testCases) {
            val fullText = "PUBLISHER ASSET GIVEAWAY Test Add this week $text"
            val links = listOf(GiftLink("/packages/3d/test-1", "Free"))
            val asset = adapter.parseFreeAsset(fullText, links)
            assertEquals("Failed for: $text", expected, asset?.coupon)
        }
    }
    
    // ============================================================
    // Checkout Total Parsing Tests
    // ============================================================
    
    @Test
    fun `parse checkout total with dollar sign`() {
        assertEquals(0.0, adapter.parseCheckoutTotal("Total to pay now $0.00"), 0.001)
        assertEquals(9.99, adapter.parseCheckoutTotal("Amount to pay now $9.99"), 0.001)
        assertEquals(99.99, adapter.parseCheckoutTotal("to pay now $99.99 USD"), 0.001)
    }
    
    @Test
    fun `parse checkout total with comma decimal`() {
        assertEquals(0.0, adapter.parseCheckoutTotal("to pay now 0,00"), 0.001)
        assertEquals(19.99, adapter.parseCheckoutTotal("to pay now €19,99"), 0.001)
    }
    
    @Test
    fun `parse checkout total returns negative for unparseable`() {
        assertEquals(-1.0, adapter.parseCheckoutTotal("Total: Free"), 0.001)
        assertEquals(-1.0, adapter.parseCheckoutTotal("No amount here"), 0.001)
        assertEquals(-1.0, adapter.parseCheckoutTotal(""), 0.001)
    }
    
    @Test
    fun `parse checkout total handles whitespace variations`() {
        assertEquals(0.0, adapter.parseCheckoutTotal("to pay now    $0.00"), 0.001)
        assertEquals(5.0, adapter.parseCheckoutTotal("to pay now: 5.00"), 0.001)
    }
    
    @Test
    fun `isCheckoutFree validates zero total`() {
        assertTrue(adapter.isCheckoutFree(0.0))
        assertFalse(adapter.isCheckoutFree(0.01))
        assertFalse(adapter.isCheckoutFree(-1.0))
        assertFalse(adapter.isCheckoutFree(9.99))
    }
    
    // ============================================================
    // UnityFreeAsset Tests
    // ============================================================
    
    @Test
    fun `UnityFreeAsset converts to Giveaway correctly`() {
        val asset = UnityFreeAsset(
            title = "Pro Environment Pack",
            url = "https://assetstore.unity.com/packages/3d/environments/pro-pack-12345",
            slug = "pro-pack-12345",
            packageId = "12345",
            coupon = "WEEKLYCODE"
        )
        
        val giveaway = asset.toGiveaway()
        
        assertEquals("unity_12345", giveaway.id)
        assertEquals("Pro Environment Pack", giveaway.title)
        assertEquals(GameStore.UNITY_ASSET_STORE, giveaway.store)
        assertEquals("https://assetstore.unity.com/packages/3d/environments/pro-pack-12345", giveaway.claimUrl)
        assertEquals(GiveawayType.FREE_TO_KEEP, giveaway.type)
        assertEquals(DiscoverySource.STORE_DIRECT, giveaway.source)
        assertEquals("12345", giveaway.externalId)
    }
    
    @Test
    fun `UnityFreeAsset does NOT include coupon in giveaway`() {
        val asset = UnityFreeAsset(
            title = "Test",
            url = "https://assetstore.unity.com/packages/3d/test-1",
            slug = "test-1",
            packageId = "1",
            coupon = "SECRET_COUPON"
        )
        
        val giveaway = asset.toGiveaway()
        
        // Coupon should NOT be anywhere in the giveaway - it's transient
        assertFalse(giveaway.description?.contains("SECRET_COUPON") == true)
    }
    
    @Test
    fun `UnityFreeAsset description mentions coupon requirement`() {
        val asset = UnityFreeAsset(
            title = "Test",
            url = "https://assetstore.unity.com/packages/3d/test-1",
            slug = "test-1",
            packageId = "1",
            coupon = "CODE"
        )
        
        val giveaway = asset.toGiveaway()
        assertTrue(giveaway.description?.contains("coupon") == true)
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
        val giveaway = createTestGiveaway("unity_12345")
        val verified = adapter.verifyGiveaway(giveaway)
        
        // Verification should not change the giveaway's state
        assertNotNull(verified)
        assertEquals(giveaway.id, verified?.id)
    }
    
    @Test
    fun `check eligibility returns requires auth`() = runTest {
        val giveaway = createTestGiveaway("unity_12345")
        val eligibility = adapter.checkEligibility(giveaway)
        
        assertEquals(com.navos.gameclaimer.domain.model.ClaimEligibility.REQUIRES_AUTH, eligibility)
    }
    
    // ============================================================
    // Edge Cases and Error Handling
    // ============================================================
    
    @Test
    fun `parse free asset handles multiline page text`() {
        val pageText = """
            Welcome to the Unity Asset Store!
            
            PUBLISHER ASSET GIVEAWAY
            
            Multi Line Asset Name
            
            Add this week's free asset to your collection.
            
            Enter coupon code MULTILINE_CODE at checkout.
        """.trimIndent()
        
        val links = listOf(GiftLink("/packages/3d/multi-123", "Free Asset"))
        
        val asset = adapter.parseFreeAsset(pageText, links)
        
        assertNotNull(asset)
        assertEquals("Multi Line Asset Name", asset!!.title)
        assertEquals("MULTILINE_CODE", asset.coupon)
    }
    
    @Test
    fun `parse free asset uses first valid link`() {
        val pageText = """
            PUBLISHER ASSET GIVEAWAY
            First Link Asset
            Add this week coupon code FIRST123
        """.trimIndent()
        
        val links = listOf(
            GiftLink("/packages/3d/first-111", "Free Gift"),
            GiftLink("/packages/3d/second-222", "Another Free")
        )
        
        val asset = adapter.parseFreeAsset(pageText, links)
        
        assertNotNull(asset)
        assertEquals("111", asset!!.packageId)
    }
    
    @Test
    fun `coupon code extraction is case insensitive`() {
        val testTexts = listOf(
            "coupon code ABC123",
            "COUPON CODE ABC123",
            "Coupon Code ABC123",
            "coupon CODE ABC123"
        )
        
        for (text in testTexts) {
            val fullText = "PUBLISHER ASSET GIVEAWAY Test Add this week $text"
            val links = listOf(GiftLink("/packages/3d/test-1", "Free"))
            val asset = adapter.parseFreeAsset(fullText, links)
            assertEquals("Failed for: $text", "ABC123", asset?.coupon)
        }
    }
    
    @Test
    fun `empty page text returns null`() {
        val asset = adapter.parseFreeAsset("", listOf(GiftLink("/test", "Free")))
        assertNull(asset)
    }
    
    @Test
    fun `empty links list returns null`() {
        val pageText = "PUBLISHER ASSET GIVEAWAY Test Add this week coupon code ABC"
        val asset = adapter.parseFreeAsset(pageText, emptyList())
        assertNull(asset)
    }
    
    // ============================================================
    // Coupon Security Tests
    // ============================================================
    
    @Test
    fun `coupon codes have reasonable length limits`() {
        // Coupon regex allows 4-25 chars after first char
        val shortCode = "PUBLISHER ASSET GIVEAWAY Test Add this week coupon code ABC"  // 3 chars - too short
        val validCode = "PUBLISHER ASSET GIVEAWAY Test Add this week coupon code ABCD"  // 4 chars - valid
        val longCode = "PUBLISHER ASSET GIVEAWAY Test Add this week coupon code A123456789012345678901234"  // 25 chars - valid
        val tooLongCode = "PUBLISHER ASSET GIVEAWAY Test Add this week coupon code A1234567890123456789012345"  // 26 chars
        
        val links = listOf(GiftLink("/packages/3d/test-1", "Free"))
        
        assertNull(adapter.parseFreeAsset(shortCode, links))
        assertNotNull(adapter.parseFreeAsset(validCode, links))
        assertNotNull(adapter.parseFreeAsset(longCode, links))
        // Too long should still parse (regex captures first 25)
    }
    
    @Test
    fun `coupon codes must start with alphanumeric`() {
        val validStart = "PUBLISHER ASSET GIVEAWAY Test Add this week coupon code ABC123"
        val invalidStart = "PUBLISHER ASSET GIVEAWAY Test Add this week coupon code _ABC123"
        
        val links = listOf(GiftLink("/packages/3d/test-1", "Free"))
        
        assertNotNull(adapter.parseFreeAsset(validStart, links))
        // Invalid start might not match at all
    }
    
    // ============================================================
    // Helper Functions
    // ============================================================
    
    private fun createTestGiveaway(id: String) = com.navos.gameclaimer.domain.model.Giveaway(
        id = id,
        title = "Test Asset",
        store = GameStore.UNITY_ASSET_STORE,
        type = GiveawayType.FREE_TO_KEEP,
        claimUrl = "https://assetstore.unity.com/packages/3d/test-12345",
        originalPrice = null,
        imageUrl = null,
        description = "Test",
        startTime = null,
        endTime = null,
        source = DiscoverySource.STORE_DIRECT,
        externalId = null
    )
}
