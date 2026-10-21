package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 7: IndieGala Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via GamerPower API (IndieGala hosts frequent giveaways)
 * - Authentication: WebView login at indiegala.com
 * - Claiming: Giveaway page claim button
 * 
 * LIMITATIONS:
 * - Giveaways often require social media actions (join Discord, etc.)
 * - Some giveaways have limited keys
 * - Account level requirements may apply
 * - DLC always included
 * 
 * SECURITY:
 * - Auth via WebView only
 * - Email/password form login
 * - May require CAPTCHA
 * 
 * UPSTREAM REFERENCE: Handled via gamerpower.py side store logic
 */
class IndieGalaStoreAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.INDIEGALA
    override val displayName: String = "IndieGala"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled until WebView integration
    override val requiresAuth: Boolean = true
    
    companion object {
        // IndieGala URLs
        const val URL_HOME = "https://www.indiegala.com"
        const val URL_LOGIN = "https://www.indiegala.com/login"
        const val URL_GIVEAWAYS = "https://www.indiegala.com/giveaways"
        const val URL_LIBRARY = "https://www.indiegala.com/library"
        
        // Trusted domains for IndieGala
        val TRUSTED_DOMAINS = setOf(
            "indiegala.com",
            "www.indiegala.com"
        )
        
        // IndieGala giveaway URL pattern
        val INDIEGALA_GIVEAWAY_REGEX = Regex("""https://(?:www\.)?indiegala\.com/giveaways/([a-z0-9-]+)""")
        val INDIEGALA_FREEBIES_REGEX = Regex("""https://(?:www\.)?indiegala\.com/freebies/([a-z0-9-]+)""")
    }
    
    /**
     * Discover free giveaways from IndieGala.
     * 
     * IndieGala has a giveaways page, but discovery via GamerPower
     * is more reliable for finding currently active offers.
     * 
     * NOTE: This is READ-ONLY - never triggers claims.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 7: Discovery via GamerPower (already implemented in Phase 6)
        // IndieGala-specific discovery could scrape /giveaways page
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a giveaway is still valid.
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Would navigate to the giveaway page and check:
        // - Page loads successfully
        // - Giveaway not ended
        // - Keys still available
        // - No social action requirements (or document them)
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Would check:
        // - User is logged in
        // - Account level sufficient
        // - Social requirements met (if any)
        // - Key still available
        return ClaimEligibility.REQUIRES_AUTH // Default: need auth to check
    }
    
    /**
     * Claim a giveaway.
     * 
     * DISABLED in Phase 7 - requires WebView integration and
     * may need social action handling.
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "IndieGala claiming disabled - requires WebView integration",
            isRetryable = false
        )
    }
    
    /**
     * Get the IndieGala login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated.
     * 
     * Would check for IndieGala auth cookies.
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check for IndieGala auth cookies in WebView
        return false
    }
    
    /**
     * Parse IndieGala giveaway slug from URL with security validation.
     * 
     * Security: Rejects deceptive hosts and unsafe schemes.
     */
    fun parseGiveawaySlug(url: String): String? {
        // Security: Validate URL structure and scheme
        if (!isSecureUrl(url)) return null
        
        // Security: Validate host is actually IndieGala
        if (!isValidIndieGalaHost(url)) return null
        
        INDIEGALA_GIVEAWAY_REGEX.find(url)?.let {
            return it.groupValues[1]
        }
        INDIEGALA_FREEBIES_REGEX.find(url)?.let {
            return it.groupValues[1]
        }
        return null
    }
    
    /**
     * Validates URL is HTTPS and well-formed.
     */
    private fun isSecureUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            uri.scheme?.lowercase() == "https"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates host is actually an IndieGala domain, not a lookalike.
     * 
     * Protects against:
     * - Suffix attacks: fakeindiegala.com
     * - Prefix attacks: indiegala.com.attacker.com
     */
    private fun isValidIndieGalaHost(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            
            // Must be exactly indiegala.com or subdomain thereof (e.g., www.indiegala.com)
            host == "indiegala.com" || host.endsWith(".indiegala.com")
        } catch (e: Exception) {
            false
        }
    }
}
