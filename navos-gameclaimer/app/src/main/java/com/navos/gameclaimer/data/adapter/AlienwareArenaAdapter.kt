package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 12: Alienware Arena Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via GamerPower API (Alienware Arena hosts key giveaways)
 * - Authentication: WebView login at alienwarearena.com
 * - Claiming: DISABLED - external key redemption required
 * 
 * ALIENWARE ARENA CHARACTERISTICS:
 * - Gaming community platform by Dell/Alienware
 * - Giveaways distribute keys for Steam, Epic, GOG, etc.
 * - Account level (ARP - Arena Reward Points) gating for some giveaways
 * - Region restrictions on many giveaways
 * - Keys must be redeemed on the target platform
 * - Often requires linking gaming profiles (Steam, etc.)
 * 
 * SECURITY:
 * - Discovery only (supportsClaiming=false)
 * - HTTPS required for all URLs
 * - No automatic login or account level verification
 * - No region spoofing or bypass attempts
 * - External key redemption stays manual
 * 
 * UPSTREAM REFERENCE: Handled via gamerpower.py with classify_target()
 */
class AlienwareArenaAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.ALIENWARE_ARENA
    override val displayName: String = "Alienware Arena"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled - key redemption is external
    override val requiresAuth: Boolean = true
    
    companion object {
        // Alienware Arena URLs
        const val URL_HOME = "https://www.alienwarearena.com"
        const val URL_LOGIN = "https://www.alienwarearena.com/login"
        const val URL_GIVEAWAYS = "https://www.alienwarearena.com/ucf/show/giveaways"
        
        // Trusted domains for Alienware Arena
        val TRUSTED_DOMAINS = setOf(
            "alienwarearena.com",
            "www.alienwarearena.com",
            "na.alienwarearena.com",  // North America
            "eu.alienwarearena.com",  // Europe
            "latam.alienwarearena.com"  // Latin America
        )
        
        // Alienware Arena giveaway URL patterns
        // Examples:
        // - https://www.alienwarearena.com/ucf/show/2156789/boards/contest-and-giveaways-702/Giveaway/game-name-key-giveaway
        // - https://na.alienwarearena.com/ucf/show/2156789/...
        val ALIENWARE_GIVEAWAY_REGEX = Regex(
            """https://(?:(?:www|na|eu|latam)\.)?alienwarearena\.com/ucf/show/(\d+)/.*"""
        )
        
        // UCF show ID pattern (numeric ID)
        val ALIENWARE_UCF_ID_REGEX = Regex(
            """https://(?:(?:www|na|eu|latam)\.)?alienwarearena\.com/ucf/show/(\d+)(?:/.*)?"""
        )
        
        // Member profile pattern
        val ALIENWARE_MEMBER_REGEX = Regex(
            """https://(?:(?:www|na|eu|latam)\.)?alienwarearena\.com/member/(\d+)(?:/.*)?"""
        )
        
        /**
         * Platforms that Alienware Arena keys can be redeemed on.
         * Keys are NOT redeemed on Alienware Arena - they're for these external platforms.
         */
        val KEY_REDEMPTION_PLATFORMS = setOf(
            GameStore.STEAM,
            GameStore.EPIC_GAMES,
            GameStore.GOG,
            GameStore.UBISOFT
        )
        
        /**
         * Known regional subdomains for Alienware Arena.
         */
        val REGIONAL_SUBDOMAINS = setOf(
            "na",    // North America
            "eu",    // Europe
            "latam", // Latin America
            "www"    // Global
        )
    }
    
    /**
     * Discover free giveaways from Alienware Arena.
     * 
     * Alienware Arena giveaways are discovered via GamerPower API which monitors
     * their key distribution events. Direct site scraping is not implemented
     * as many giveaways have region/level restrictions.
     * 
     * NOTE: This is READ-ONLY - never triggers claims.
     * NOTE: Discovery does NOT imply eligibility - account level and region checks required.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 12: Discovery via GamerPower integration
        // GamerPowerApiClient.mapUrlToExternalStore() identifies Alienware giveaways
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a giveaway is still valid.
     * 
     * Would require WebView navigation to verify:
     * - Page loads successfully
     * - Giveaway not ended
     * - Keys still available
     * - User's region is eligible
     * - User's ARP level is sufficient
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Validate URL before any verification
        if (!isValidAlienwareUrl(giveaway.claimUrl)) return null
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     * 
     * Alienware Arena giveaways have multiple eligibility requirements:
     * 1. Logged-in account
     * 2. Sufficient ARP (Arena Reward Points) level
     * 3. Region eligibility
     * 4. Keys still available (limited quantity)
     * 5. User hasn't already claimed
     * 6. Linked gaming profiles (sometimes required)
     * 
     * NOTE: Discovery alone cannot determine eligibility - these checks
     * require authenticated API calls or WebView inspection.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Validate URL first
        if (!isValidAlienwareUrl(giveaway.claimUrl)) {
            return ClaimEligibility.UNKNOWN
        }
        
        // Would need authenticated session to check:
        // - ARP level vs requirement
        // - Region match
        // - Key availability
        return ClaimEligibility.REQUIRES_AUTH
    }
    
    /**
     * Claim a giveaway.
     * 
     * DISABLED in Phase 12 - Alienware Arena claiming requires:
     * 1. Authenticated session
     * 2. ARP level verification
     * 3. Region eligibility check
     * 4. Key retrieval after claim
     * 5. External platform key redemption (Steam, Epic, etc.)
     * 
     * This is intentionally disabled as:
     * - Account level requirements vary per giveaway
     * - Region restrictions are common
     * - Key redemption on external platforms is out of scope
     * - Users should verify eligibility before attempting claims
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Alienware Arena claiming disabled - keys must be redeemed manually on Steam/Epic/GOG",
            isRetryable = false
        )
    }
    
    /**
     * Get the Alienware Arena login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated.
     * 
     * Would need WebView to check auth cookies or page state.
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check for Alienware Arena auth cookies in WebView
        return false
    }
    
    /**
     * Parse Alienware Arena UCF show ID from URL with security validation.
     * 
     * @param url URL to parse
     * @return UCF show ID (numeric) or null if invalid
     * 
     * Security: Rejects deceptive hosts and unsafe schemes.
     */
    fun parseUcfShowId(url: String): String? {
        // Security: Validate URL structure and scheme
        if (!isSecureUrl(url)) return null
        
        // Security: Validate host is actually Alienware Arena
        if (!isValidAlienwareHost(url)) return null
        
        ALIENWARE_UCF_ID_REGEX.find(url)?.let { match ->
            return match.groupValues[1]
        }
        
        return null
    }
    
    /**
     * Validates URL is a legitimate Alienware Arena URL.
     * 
     * Security checks:
     * - HTTPS scheme required
     * - Host is alienwarearena.com or recognized subdomain
     * - No IP literals, userinfo, or unexpected ports
     */
    fun isValidAlienwareUrl(url: String): Boolean {
        if (!isSecureUrl(url)) return false
        if (!isValidAlienwareHost(url)) return false
        return true
    }
    
    /**
     * Validates URL is HTTPS and well-formed.
     * 
     * Rejects:
     * - Non-HTTPS schemes
     * - Malformed URIs
     * - URLs with userinfo (user:pass@host)
     */
    private fun isSecureUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            
            // Must be HTTPS
            if (uri.scheme?.lowercase() != "https") return false
            
            // Reject userinfo (potential credential leak)
            if (uri.userInfo != null) return false
            
            // Reject unexpected ports (should be 443 or default)
            if (uri.port != -1 && uri.port != 443) return false
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates host is actually an Alienware Arena domain, not a lookalike.
     * 
     * Protects against:
     * - Suffix attacks: fakealienwarearena.com
     * - Prefix attacks: alienwarearena.com.attacker.com
     * - IP literals: https://192.168.1.1/alienware/
     * 
     * Allows known regional subdomains: na, eu, latam, www
     */
    private fun isValidAlienwareHost(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            
            // Reject IP literals
            if (host.matches(Regex("""^\d+\.\d+\.\d+\.\d+$"""))) return false
            if (host.startsWith("[")) return false // IPv6
            
            // Must be exactly alienwarearena.com or known subdomain thereof
            if (host == "alienwarearena.com") return true
            
            // Check for valid subdomains
            if (host.endsWith(".alienwarearena.com")) {
                val subdomain = host.removeSuffix(".alienwarearena.com")
                // Only allow known subdomains or single-level subdomains
                return subdomain in REGIONAL_SUBDOMAINS || !subdomain.contains(".")
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Extract region from Alienware Arena URL.
     * 
     * @return Region code (na, eu, latam) or null for global/unknown
     */
    fun extractRegion(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return null
            
            if (host.endsWith(".alienwarearena.com")) {
                val subdomain = host.removeSuffix(".alienwarearena.com")
                if (subdomain in REGIONAL_SUBDOMAINS && subdomain != "www") {
                    return subdomain
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get the platform where an Alienware Arena key should be redeemed.
     * 
     * Alienware Arena distributes keys for various platforms. This would be
     * determined from the giveaway page which shows the target platform.
     * 
     * @return The target platform for key redemption, or null if unknown
     */
    fun getKeyRedemptionPlatform(giveaway: Giveaway): GameStore? {
        // Would need to parse giveaway page to determine target platform
        // For now, return null - discovery shows it's an Alienware giveaway,
        // but user must check the page to see where the key activates
        return null
    }
}
