package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 7: Itch.io Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via GamerPower API only (no public giveaway listing)
 * - Authentication: WebView login at itch.io/login
 * - Claiming: Direct game page claim button
 * 
 * LIMITATIONS:
 * - No official API for giveaway discovery
 * - Giveaways are individual developer decisions
 * - Some games require itch.io account, others don't
 * - DLC always included (no separate toggle)
 * 
 * SECURITY:
 * - Auth via WebView only
 * - Email/password form on itch.io/login
 * - OTP/2FA may be required
 * 
 * UPSTREAM REFERENCE: Handled via gamerpower.py side store logic
 */
class ItchioStoreAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.ITCH_IO
    override val displayName: String = "Itch.io"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled until WebView integration
    override val requiresAuth: Boolean = true
    
    companion object {
        // Itch.io URLs
        const val URL_HOME = "https://itch.io"
        const val URL_LOGIN = "https://itch.io/login"
        const val URL_LIBRARY = "https://itch.io/my-purchases"
        
        // Trusted domains for Itch.io
        // Note: Each developer has their own subdomain (e.g., developer.itch.io)
        val TRUSTED_DOMAINS = setOf(
            "itch.io"
        )
        
        // Itch.io game URL patterns
        // Standard: https://developer.itch.io/game-name
        // Sale page: https://itch.io/s/XXXXX/sale-name
        val ITCHIO_GAME_REGEX = Regex("""https://([a-z0-9-]+)\.itch\.io/([a-z0-9-]+)""")
        val ITCHIO_SALE_REGEX = Regex("""https://itch\.io/s/(\d+)/([a-z0-9-]+)""")
    }
    
    /**
     * Discover free giveaways from Itch.io.
     * 
     * Itch.io has no central giveaway listing - each developer
     * sets their own pricing. Discovery relies on GamerPower.
     * 
     * NOTE: This is READ-ONLY - never triggers claims.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 7: Discovery via GamerPower (already implemented in Phase 6)
        // Itch.io doesn't have a giveaway search page
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a giveaway is still valid.
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Would navigate to the game page and check:
        // - Page loads successfully
        // - Price is $0 or "free" or "name your price" with $0 minimum
        // - Download/claim button exists
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Would check:
        // - User is logged in
        // - Game not already owned (in library)
        // - Some games don't require login for free download
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
            reason = "Itch.io claiming disabled - requires WebView integration",
            isRetryable = false
        )
    }
    
    /**
     * Get the Itch.io login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated.
     * 
     * Would check for itch.io auth cookies.
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check for Itch.io auth cookies in WebView
        return false
    }
    
    /**
     * Parse Itch.io game info from URL.
     * 
     * @return Pair of (developer, game-slug) or null
     */
    fun parseGameUrl(url: String): Pair<String, String>? {
        val match = ITCHIO_GAME_REGEX.find(url) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }
    
    /**
     * Check if a URL is a valid Itch.io subdomain.
     * 
     * Itch.io uses developer.itch.io subdomains, so we need to verify
     * the host ends with .itch.io (with leading dot for safety).
     */
    fun isValidItchioHost(host: String): Boolean {
        val lower = host.lowercase()
        return lower == "itch.io" || lower.endsWith(".itch.io")
    }
}
