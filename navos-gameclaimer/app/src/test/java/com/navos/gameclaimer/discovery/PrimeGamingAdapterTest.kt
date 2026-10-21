package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.PrimeGamingAdapter
import com.navos.gameclaimer.data.adapter.PrimeCodeStore
import com.navos.gameclaimer.data.adapter.PrimeOfferType
import com.navos.gameclaimer.data.adapter.PrimePageState
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.GiveawayType
import com.navos.gameclaimer.domain.model.DiscoverySource
import com.navos.gameclaimer.domain.model.ClaimResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for PrimeGamingAdapter (Phase 9)
 *
 * Coverage:
 * - Store configuration
 * - URL security validation (Prime domains)
 * - Redemption URL security (external store domains)
 * - Offer URL parsing
 * - Code store detection
 * - Offer type classification
 * - Code format validation
 * - Claim disabled verification
 */
class PrimeGamingAdapterTest {

    private lateinit var adapter: PrimeGamingAdapter

    @Before
    fun setup() {
        adapter = PrimeGamingAdapter()
    }

    // ========== Store Configuration ==========

    @Test
    fun `store is AMAZON_PRIME`() {
        assertEquals(GameStore.AMAZON_PRIME, adapter.store)
    }

    @Test
    fun `claiming is disabled`() {
        assertFalse(adapter.supportsClaiming)
    }

    @Test
    fun `login URL is Amazon signin`() {
        assertEquals("https://www.amazon.com/ap/signin", adapter.getAuthUrl())
    }

    @Test
    fun `discovery URL is Luna claims page - check companion object constant`() {
        assertEquals("https://luna.amazon.com/claims/home", PrimeGamingAdapter.URL_CLAIMS)
    }

    // ========== Prime Host Validation ==========

    @Test
    fun `valid Prime host - luna domain`() {
        assertTrue(adapter.isValidPrimeHost("https://luna.amazon.com/claims/home"))
    }

    @Test
    fun `valid Prime host - amazon domain`() {
        assertTrue(adapter.isValidPrimeHost("https://amazon.com/ap/signin"))
    }

    @Test
    fun `valid Prime host - www amazon domain`() {
        assertTrue(adapter.isValidPrimeHost("https://www.amazon.com/ap/signin"))
    }

    @Test
    fun `valid Prime host - subdomain of amazon`() {
        assertTrue(adapter.isValidPrimeHost("https://gaming.amazon.com/home"))
    }

    @Test
    fun `invalid Prime host - HTTP not allowed`() {
        assertFalse(adapter.isValidPrimeHost("http://luna.amazon.com/claims"))
    }

    @Test
    fun `invalid Prime host - fake domain suffix attack`() {
        assertFalse(adapter.isValidPrimeHost("https://amazon.com.attacker.com/claims"))
    }

    @Test
    fun `invalid Prime host - fake domain prefix attack`() {
        assertFalse(adapter.isValidPrimeHost("https://fakeamazon.com/claims"))
    }

    @Test
    fun `invalid Prime host - lookalike domain`() {
        assertFalse(adapter.isValidPrimeHost("https://luna-amazon.com/claims"))
    }

    @Test
    fun `invalid Prime host - IP address v4`() {
        assertFalse(adapter.isValidPrimeHost("https://192.168.1.1/claims"))
    }

    @Test
    fun `invalid Prime host - IP address v6`() {
        assertFalse(adapter.isValidPrimeHost("https://[::1]/claims"))
    }

    @Test
    fun `invalid Prime host - userinfo`() {
        assertFalse(adapter.isValidPrimeHost("https://user:pass@luna.amazon.com/claims"))
    }

    @Test
    fun `invalid Prime host - unexpected port`() {
        assertFalse(adapter.isValidPrimeHost("https://luna.amazon.com:8080/claims"))
    }

    @Test
    fun `invalid Prime host - malformed URL`() {
        assertFalse(adapter.isValidPrimeHost("not-a-url"))
    }

    @Test
    fun `invalid Prime host - empty URL`() {
        assertFalse(adapter.isValidPrimeHost(""))
    }

    @Test
    fun `invalid Prime host - unrelated domain`() {
        assertFalse(adapter.isValidPrimeHost("https://google.com"))
    }

    // ========== Redemption Host Validation ==========

    @Test
    fun `valid redeem host - GOG`() {
        assertTrue(adapter.isValidRedeemHost("https://www.gog.com/redeem"))
    }

    @Test
    fun `valid redeem host - GOG without www`() {
        assertTrue(adapter.isValidRedeemHost("https://gog.com/redeem/ABCD1234"))
    }

    @Test
    fun `valid redeem host - Legacy Games promo`() {
        assertTrue(adapter.isValidRedeemHost("https://promo.legacygames.com/game-prime/"))
    }

    @Test
    fun `valid redeem host - Legacy Games main`() {
        assertTrue(adapter.isValidRedeemHost("https://legacygames.com/my-account/"))
    }

    @Test
    fun `valid redeem host - Microsoft account`() {
        assertTrue(adapter.isValidRedeemHost("https://account.microsoft.com/billing/redeem"))
    }

    @Test
    fun `valid redeem host - Steam`() {
        assertTrue(adapter.isValidRedeemHost("https://store.steampowered.com/account/registerkey"))
    }

    @Test
    fun `invalid redeem host - HTTP not allowed`() {
        assertFalse(adapter.isValidRedeemHost("http://gog.com/redeem"))
    }

    @Test
    fun `invalid redeem host - fake GOG`() {
        assertFalse(adapter.isValidRedeemHost("https://gog.com.attacker.com/redeem"))
    }

    @Test
    fun `invalid redeem host - lookalike Microsoft`() {
        assertFalse(adapter.isValidRedeemHost("https://account-microsoft.com/billing/redeem"))
    }

    @Test
    fun `invalid redeem host - IP address`() {
        assertFalse(adapter.isValidRedeemHost("https://192.168.1.1/redeem"))
    }

    @Test
    fun `invalid redeem host - userinfo`() {
        assertFalse(adapter.isValidRedeemHost("https://user:pass@gog.com/redeem"))
    }

    @Test
    fun `invalid redeem host - unexpected port`() {
        assertFalse(adapter.isValidRedeemHost("https://gog.com:8443/redeem"))
    }

    @Test
    fun `invalid redeem host - unknown domain`() {
        assertFalse(adapter.isValidRedeemHost("https://unknownstore.com/redeem"))
    }

    // ========== Offer URL Parsing ==========

    @Test
    fun `parse valid offer URL`() {
        val url = "https://luna.amazon.com/claims/cool-game-gog/dp/ABC123"
        val result = adapter.parseOfferUrl(url)
        assertNotNull(result)
        assertEquals("cool-game-gog", result?.first)
        assertEquals("ABC123", result?.second)
    }

    @Test
    fun `parse offer URL with long slug`() {
        val url = "https://luna.amazon.com/claims/the-amazing-adventure-game-legacy/dp/XYZ789ABC"
        val result = adapter.parseOfferUrl(url)
        assertNotNull(result)
        assertEquals("the-amazing-adventure-game-legacy", result?.first)
        assertEquals("XYZ789ABC", result?.second)
    }

    @Test
    fun `parse offer URL - reject non-amazon host`() {
        val url = "https://luna.not-amazon.com/claims/game/dp/123"
        assertNull(adapter.parseOfferUrl(url))
    }

    @Test
    fun `parse offer URL - reject non-claims path`() {
        val url = "https://luna.amazon.com/games/something/dp/123"
        assertNull(adapter.parseOfferUrl(url))
    }

    @Test
    fun `parse offer URL - reject malformed`() {
        assertNull(adapter.parseOfferUrl("not-a-url"))
    }

    @Test
    fun `parse offer URL - claims home page returns null`() {
        val url = "https://luna.amazon.com/claims/home"
        assertNull(adapter.parseOfferUrl(url))
    }

    // ========== Code Store Detection ==========

    @Test
    fun `detect store from slug - Epic`() {
        assertEquals(PrimeCodeStore.EPIC, PrimeCodeStore.fromSlug("awesome-game-epic"))
    }

    @Test
    fun `detect store from slug - GOG`() {
        assertEquals(PrimeCodeStore.GOG, PrimeCodeStore.fromSlug("puzzle-game-gog"))
    }

    @Test
    fun `detect store from slug - Legacy Games`() {
        assertEquals(PrimeCodeStore.LEGACY_GAMES, PrimeCodeStore.fromSlug("hidden-object-game-legacy"))
    }

    @Test
    fun `detect store from slug - Amazon (aga)`() {
        assertEquals(PrimeCodeStore.AMAZON, PrimeCodeStore.fromSlug("shooter-game-aga"))
    }

    @Test
    fun `detect store from slug - unknown`() {
        assertEquals(PrimeCodeStore.UNKNOWN, PrimeCodeStore.fromSlug("random-game-name"))
    }

    @Test
    fun `detect store from page text - Epic Games Store`() {
        val text = "Redeem on Epic Games Store"
        assertEquals(PrimeCodeStore.EPIC, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - GOG`() {
        val text = "Get your code at gog.com"
        assertEquals(PrimeCodeStore.GOG, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - Legacy Games`() {
        val text = "Redeem on Legacy Games"
        assertEquals(PrimeCodeStore.LEGACY_GAMES, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - Microsoft Store`() {
        val text = "Available on Microsoft Store"
        assertEquals(PrimeCodeStore.MICROSOFT, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - Xbox`() {
        val text = "Redeem on Xbox"
        assertEquals(PrimeCodeStore.XBOX, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - Amazon Games`() {
        val text = "Play with Amazon Games"
        assertEquals(PrimeCodeStore.AMAZON, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - Steam`() {
        val text = "Activate on Steam"
        assertEquals(PrimeCodeStore.STEAM, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - Battle net`() {
        val text = "Redeem on Battle.net"
        assertEquals(PrimeCodeStore.BATTLE_NET, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - Ubisoft`() {
        val text = "Ubisoft Connect required"
        assertEquals(PrimeCodeStore.UBISOFT, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - EA App`() {
        val text = "Available on EA App"
        assertEquals(PrimeCodeStore.EA_APP, PrimeCodeStore.fromPageText(text))
    }

    @Test
    fun `detect store from page text - unknown`() {
        val text = "Some random text without store info"
        assertEquals(PrimeCodeStore.UNKNOWN, PrimeCodeStore.fromPageText(text))
    }

    // ========== Offer Type Classification ==========

    @Test
    fun `classify offer type - Epic is account linked`() {
        assertEquals(PrimeOfferType.ACCOUNT_LINKED, adapter.classifyOfferType(PrimeCodeStore.EPIC))
    }

    @Test
    fun `classify offer type - Amazon is account linked`() {
        assertEquals(PrimeOfferType.ACCOUNT_LINKED, adapter.classifyOfferType(PrimeCodeStore.AMAZON))
    }

    @Test
    fun `classify offer type - GOG is external code`() {
        assertEquals(PrimeOfferType.EXTERNAL_CODE, adapter.classifyOfferType(PrimeCodeStore.GOG))
    }

    @Test
    fun `classify offer type - Legacy Games is external code`() {
        assertEquals(PrimeOfferType.EXTERNAL_CODE, adapter.classifyOfferType(PrimeCodeStore.LEGACY_GAMES))
    }

    @Test
    fun `classify offer type - Microsoft is external code`() {
        assertEquals(PrimeOfferType.EXTERNAL_CODE, adapter.classifyOfferType(PrimeCodeStore.MICROSOFT))
    }

    @Test
    fun `classify offer type - Xbox is external code`() {
        assertEquals(PrimeOfferType.EXTERNAL_CODE, adapter.classifyOfferType(PrimeCodeStore.XBOX))
    }

    @Test
    fun `classify offer type - Steam is external code`() {
        assertEquals(PrimeOfferType.EXTERNAL_CODE, adapter.classifyOfferType(PrimeCodeStore.STEAM))
    }

    @Test
    fun `classify offer type - Battle net is account linked`() {
        assertEquals(PrimeOfferType.ACCOUNT_LINKED, adapter.classifyOfferType(PrimeCodeStore.BATTLE_NET))
    }

    @Test
    fun `classify offer type - Ubisoft is account linked`() {
        assertEquals(PrimeOfferType.ACCOUNT_LINKED, adapter.classifyOfferType(PrimeCodeStore.UBISOFT))
    }

    @Test
    fun `classify offer type - EA App is account linked`() {
        assertEquals(PrimeOfferType.ACCOUNT_LINKED, adapter.classifyOfferType(PrimeCodeStore.EA_APP))
    }

    @Test
    fun `classify offer type - Unknown is unknown`() {
        assertEquals(PrimeOfferType.UNKNOWN, adapter.classifyOfferType(PrimeCodeStore.UNKNOWN))
    }

    // ========== Code Format Validation ==========

    @Test
    fun `valid code format - GOG style`() {
        assertTrue(adapter.isValidCodeFormat("ABCD-1234-EFGH-5678"))
    }

    @Test
    fun `valid code format - Microsoft style`() {
        assertTrue(adapter.isValidCodeFormat("XXXXX-XXXXX-XXXXX-XXXXX-XXXXX"))
    }

    @Test
    fun `valid code format - no dashes`() {
        assertTrue(adapter.isValidCodeFormat("ABCDEFGHIJK12345"))
    }

    @Test
    fun `valid code format - minimum length`() {
        assertTrue(adapter.isValidCodeFormat("ABCD-1234-EFG"))
    }

    @Test
    fun `invalid code format - too short`() {
        assertFalse(adapter.isValidCodeFormat("ABC123"))
    }

    @Test
    fun `invalid code format - lowercase`() {
        assertFalse(adapter.isValidCodeFormat("abcd-1234-efgh-5678"))
    }

    @Test
    fun `invalid code format - contains spaces`() {
        assertFalse(adapter.isValidCodeFormat("ABCD 1234 EFGH"))
    }

    @Test
    fun `invalid code format - empty`() {
        assertFalse(adapter.isValidCodeFormat(""))
    }

    @Test
    fun `invalid code format - special characters`() {
        assertFalse(adapter.isValidCodeFormat("ABCD!@#$%^&*()"))
    }

    @Test
    fun `code format with whitespace is trimmed`() {
        assertTrue(adapter.isValidCodeFormat("  ABCD-1234-EFGH-5678  "))
    }

    // ========== Redemption URL Retrieval ==========

    @Test
    fun `get redeem URL - GOG`() {
        assertEquals("https://www.gog.com/redeem", adapter.getRedeemUrl(PrimeCodeStore.GOG))
    }

    @Test
    fun `get redeem URL - Microsoft`() {
        assertEquals("https://account.microsoft.com/billing/redeem", adapter.getRedeemUrl(PrimeCodeStore.MICROSOFT))
    }

    @Test
    fun `get redeem URL - Xbox same as Microsoft`() {
        assertEquals("https://account.microsoft.com/billing/redeem", adapter.getRedeemUrl(PrimeCodeStore.XBOX))
    }

    @Test
    fun `get redeem URL - Steam`() {
        assertEquals("https://store.steampowered.com/account/registerkey", adapter.getRedeemUrl(PrimeCodeStore.STEAM))
    }

    @Test
    fun `get redeem URL - Legacy Games is null (per-game)`() {
        assertNull(adapter.getRedeemUrl(PrimeCodeStore.LEGACY_GAMES))
    }

    @Test
    fun `get redeem URL - Epic is null (account linked)`() {
        assertNull(adapter.getRedeemUrl(PrimeCodeStore.EPIC))
    }

    @Test
    fun `get redeem URL - Amazon is null (account linked)`() {
        assertNull(adapter.getRedeemUrl(PrimeCodeStore.AMAZON))
    }

    // ========== External Redemption Check ==========

    @Test
    fun `requires external redemption - GOG`() {
        assertTrue(adapter.requiresExternalRedemption(PrimeCodeStore.GOG))
    }

    @Test
    fun `requires external redemption - Microsoft`() {
        assertTrue(adapter.requiresExternalRedemption(PrimeCodeStore.MICROSOFT))
    }

    @Test
    fun `requires external redemption - Steam`() {
        assertTrue(adapter.requiresExternalRedemption(PrimeCodeStore.STEAM))
    }

    @Test
    fun `does not require external redemption - Epic`() {
        assertFalse(adapter.requiresExternalRedemption(PrimeCodeStore.EPIC))
    }

    @Test
    fun `does not require external redemption - Amazon`() {
        assertFalse(adapter.requiresExternalRedemption(PrimeCodeStore.AMAZON))
    }

    @Test
    fun `does not require external redemption - Legacy Games (per-game pages)`() {
        assertFalse(adapter.requiresExternalRedemption(PrimeCodeStore.LEGACY_GAMES))
    }

    // ========== Claim Disabled ==========

    @Test
    fun `claim returns failed result`() = runTest {
        val result = adapter.claim(
            com.navos.gameclaimer.domain.model.Giveaway(
                id = "test",
                title = "Test Game",
                store = GameStore.AMAZON_PRIME,
                type = GiveawayType.SUBSCRIPTION_PERK,
                claimUrl = "https://luna.amazon.com/claims/test/dp/123",
                source = DiscoverySource.GAMER_POWER
            )
        )

        assertTrue(result is ClaimResult.Failed)
        val failed = result as ClaimResult.Failed
        assertTrue(failed.reason.contains("disabled"))
        assertTrue(failed.reason.contains("Prime"))
    }

    // ========== Page State Enum Coverage ==========

    @Test
    fun `page state enum values exist`() {
        // Ensure all expected states are defined
        assertEquals(7, PrimePageState.values().size)
        assertNotNull(PrimePageState.SIGN_IN_REQUIRED)
        assertNotNull(PrimePageState.CLAIMS_LIST)
        assertNotNull(PrimePageState.OFFER_DETAIL)
        assertNotNull(PrimePageState.CLAIMABLE)
        assertNotNull(PrimePageState.COLLECTED)
        assertNotNull(PrimePageState.SECURITY_CODE)
        assertNotNull(PrimePageState.UNKNOWN)
    }

    // ========== Offer Type Enum Coverage ==========

    @Test
    fun `offer type enum values exist`() {
        assertEquals(4, PrimeOfferType.values().size)
        assertNotNull(PrimeOfferType.ACCOUNT_LINKED)
        assertNotNull(PrimeOfferType.EXTERNAL_CODE)
        assertNotNull(PrimeOfferType.IN_GAME_CONTENT)
        assertNotNull(PrimeOfferType.UNKNOWN)
    }

    // ========== Code Store Display Names ==========

    @Test
    fun `code store display names are set`() {
        assertEquals("GOG.com", PrimeCodeStore.GOG.displayName)
        assertEquals("Legacy Games", PrimeCodeStore.LEGACY_GAMES.displayName)
        assertEquals("Microsoft Store", PrimeCodeStore.MICROSOFT.displayName)
        assertEquals("Xbox", PrimeCodeStore.XBOX.displayName)
        assertEquals("Epic Games", PrimeCodeStore.EPIC.displayName)
        assertEquals("Amazon Games", PrimeCodeStore.AMAZON.displayName)
        assertEquals("Steam", PrimeCodeStore.STEAM.displayName)
        assertEquals("Battle.net", PrimeCodeStore.BATTLE_NET.displayName)
        assertEquals("Ubisoft Connect", PrimeCodeStore.UBISOFT.displayName)
        assertEquals("EA App", PrimeCodeStore.EA_APP.displayName)
        assertEquals("Unknown", PrimeCodeStore.UNKNOWN.displayName)
    }
}
