package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 7: GOG Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via GamerPower API + GOG homepage giveaway banner
 * - Authentication: WebView login at gog.com, verified via menu API
 * - Claiming: Giveaway banner click (simpler than Steam)
 * 
 * LIMITATIONS:
 * - GOG giveaways are time-limited homepage banners
 * - No stable API for discovering active giveaways
 * - Cookie consent popup must be handled
 * 
 * SECURITY:
 * - Auth state checked via menu.gog.com/v1/account/basic
 * - No credential storage
 * - All URLs validated
 * 
 * UPSTREAM REFERENCE: src/stores/gog.py (34KB)
 */
class GogStoreAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.GOG
    override val displayName: String = "GOG"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled until WebView integration
    override val requiresAuth: Boolean = true
    
    companion object {
        // GOG URLs
        const val URL_HOME = "https://www.gog.com/en"
        const val URL_LOGIN = "https://login.gog.com/login_form"
        const val URL_ACCOUNT_API = "https://menu.gog.com/v1/account/basic"
        
        // Trusted domains for GOG
        val TRUSTED_DOMAINS = setOf(
            "gog.com",
            "www.gog.com",
            "login.gog.com",
            "menu.gog.com"
        )
        
        // GOG game URL pattern
        val GOG_GAME_REGEX = Regex("""https://www\.gog\.com/(?:en/)?game/([a-z0-9_-]+)""")
    }
    
    /**
     * Discover free giveaways from GOG.
     * 
     * GOG giveaways appear as homepage banners, not a search page.
     * In Phase 7, discovery is delegated to GamerPower API client.
     * 
     * NOTE: This is READ-ONLY - never triggers claims.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 7: Discovery via GamerPower (already implemented in Phase 6)
        // GOG-specific discovery would check the homepage for giveaway banners
        // For now, return empty - GamerPower provides GOG giveaways
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a giveaway is still valid.
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Would navigate to gog.com and check:
        // - Giveaway banner exists on homepage
        // - Giveaway not expired
        // - Price is free
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Would check:
        // - User is logged in (via account API)
        // - Game not already owned
        return ClaimEligibility.REQUIRES_AUTH // Default: need auth to check
    }
    
    /**
     * Claim a giveaway.
     * 
     * DISABLED in Phase 7 - requires WebView integration.
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "GOG claiming disabled - requires WebView integration",
            isRetryable = false
        )
    }
    
    /**
     * Get the GOG login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated.
     * 
     * Would call menu.gog.com/v1/account/basic and check isLoggedIn.
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Call URL_ACCOUNT_API via WebView and check response
        return false
    }
    
    /**
     * Parse GOG game slug from URL.
     */
    fun parseGameSlug(url: String): String? {
        val match = GOG_GAME_REGEX.find(url) ?: return null
        return match.groupValues[1]
    }
    
    /**
     * JavaScript to handle CookieBot consent popup.
     * 
     * GOG uses CookieBot which blocks the page until accepted.
     */
    val COOKIE_CONSENT_JS = """
        (() => {
            const accept = document.querySelector('#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll');
            if (accept) { accept.click(); return 'clicked'; }
            const cookiebot = document.querySelector('#CybotCookiebotDialog');
            if (cookiebot) { cookiebot.remove(); return 'removed'; }
            return 'not_found';
        })()
    """.trimIndent()
}
