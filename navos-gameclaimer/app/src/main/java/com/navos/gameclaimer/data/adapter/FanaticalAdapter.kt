package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 12: Fanatical Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via GamerPower API (Fanatical hosts giveaways with keys)
 * - Authentication: WebView login at fanatical.com (modal-based)
 * - Claiming: DISABLED - external key redemption required
 * 
 * FANATICAL CHARACTERISTICS:
 * - Digital game key retailer (like Humble Bundle)
 * - Giveaways distribute keys for Steam, Epic, GOG, etc.
 * - Keys must be redeemed on the target platform (not Fanatical itself)
 * - Account required to claim keys
 * - Some giveaways have limited quantities
 * 
 * SECURITY:
 * - Discovery only (supportsClaiming=false)
 * - HTTPS required for all URLs
 * - No automatic login, modal interaction, or token extraction
 * - No localStorage/session manipulation
 * - External key redemption stays manual
 * 
 * UPSTREAM REFERENCE: Handled via gamerpower.py with FAN_* JS helpers
 */
class FanaticalAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.FANATICAL
    override val displayName: String = "Fanatical"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled - key redemption is external
    override val requiresAuth: Boolean = true
    
    companion object {
        // Fanatical URLs
        const val URL_HOME = "https://www.fanatical.com"
        const val URL_EN_HOME = "https://www.fanatical.com/en"
        
        // Trusted domains for Fanatical
        val TRUSTED_DOMAINS = setOf(
            "fanatical.com",
            "www.fanatical.com"
        )
        
        // Fanatical giveaway/bundle URL patterns
        // Examples:
        // - https://www.fanatical.com/en/game/game-name
        // - https://www.fanatical.com/en/bundle/bundle-name
        // - https://www.fanatical.com/en/pick-and-mix/pick-and-mix-name
        val FANATICAL_GAME_REGEX = Regex(
            """https://(?:www\.)?fanatical\.com/[a-z]{2}/game/([a-z0-9-]+)"""
        )
        val FANATICAL_BUNDLE_REGEX = Regex(
            """https://(?:www\.)?fanatical\.com/[a-z]{2}/bundle/([a-z0-9-]+)"""
        )
        val FANATICAL_PICK_MIX_REGEX = Regex(
            """https://(?:www\.)?fanatical\.com/[a-z]{2}/pick-and-mix/([a-z0-9-]+)"""
        )
        
        // Generic path pattern for any Fanatical product
        val FANATICAL_PRODUCT_REGEX = Regex(
            """https://(?:www\.)?fanatical\.com/[a-z]{2}/(game|bundle|pick-and-mix|dlc)/([a-z0-9-]+)"""
        )
        
        /**
         * Platforms that Fanatical keys can be redeemed on.
         * Keys are NOT redeemed on Fanatical - they're for these external platforms.
         */
        val KEY_REDEMPTION_PLATFORMS = setOf(
            GameStore.STEAM,
            GameStore.EPIC_GAMES,
            GameStore.GOG,
            GameStore.UBISOFT
        )
    }
    
    /**
     * Discover free giveaways from Fanatical.
     * 
     * Fanatical giveaways are discovered via GamerPower API which monitors
     * their free game offerings. Direct site scraping is not implemented
     * as Fanatical's site uses dynamic JS rendering.
     * 
     * NOTE: This is READ-ONLY - never triggers claims.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 12: Discovery via GamerPower integration
        // GamerPowerApiClient.mapUrlToExternalStore() identifies Fanatical giveaways
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a giveaway is still valid.
     * 
     * Would require WebView navigation to the Fanatical page to verify:
     * - Page loads successfully
     * - Giveaway not ended
     * - Keys still available (Fanatical shows "Out of stock" when depleted)
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Validate URL before any verification
        if (!isValidFanaticalUrl(giveaway.claimUrl)) return null
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     * 
     * Fanatical giveaways require:
     * 1. Logged-in Fanatical account
     * 2. Keys still available (limited quantity)
     * 3. User hasn't already claimed this giveaway
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Validate URL first
        if (!isValidFanaticalUrl(giveaway.claimUrl)) {
            return ClaimEligibility.UNKNOWN
        }
        
        // Would need WebView session to check:
        // - bsauth localStorage token for login status
        // - Page shows "Get Key" vs "Out of stock" vs "Already claimed"
        return ClaimEligibility.REQUIRES_AUTH
    }
    
    /**
     * Claim a giveaway.
     * 
     * DISABLED in Phase 12 - Fanatical claiming requires:
     * 1. Modal-based login (not standard form)
     * 2. JS interaction to open login modal
     * 3. Key retrieval after claim
     * 4. External platform key redemption (Steam, Epic, etc.)
     * 
     * This is intentionally disabled as:
     * - Automated modal interaction is complex
     * - Key redemption on external platforms is out of scope
     * - Users should manually review keys and redeem them
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Fanatical claiming disabled - keys must be redeemed manually on Steam/Epic/GOG",
            isRetryable = false
        )
    }
    
    /**
     * Get the Fanatical home URL.
     * 
     * NOTE: Fanatical doesn't have a dedicated login URL - login is via
     * a modal opened from the header's "Sign in" button on any page.
     */
    override fun getAuthUrl(): String = URL_EN_HOME
    
    /**
     * Check if user is authenticated.
     * 
     * Fanatical stores auth in localStorage as 'bsauth' JSON with a token field.
     * Would need WebView to check: JSON.parse(localStorage.getItem('bsauth')).token
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check for bsauth localStorage token in WebView
        return false
    }
    
    /**
     * Parse Fanatical product slug from URL with security validation.
     * 
     * @param url URL to parse
     * @return Pair of (type, slug) or null if invalid
     * 
     * Security: Rejects deceptive hosts and unsafe schemes.
     */
    fun parseProductSlug(url: String): Pair<String, String>? {
        // Security: Validate URL structure and scheme
        if (!isSecureUrl(url)) return null
        
        // Security: Validate host is actually Fanatical
        if (!isValidFanaticalHost(url)) return null
        
        FANATICAL_PRODUCT_REGEX.find(url)?.let { match ->
            val type = match.groupValues[1]
            val slug = match.groupValues[2]
            return Pair(type, slug)
        }
        
        return null
    }
    
    /**
     * Validates URL is a legitimate Fanatical URL.
     * 
     * Security checks:
     * - HTTPS scheme required
     * - Host is fanatical.com or www.fanatical.com
     * - No IP literals, userinfo, or unexpected ports
     */
    fun isValidFanaticalUrl(url: String): Boolean {
        if (!isSecureUrl(url)) return false
        if (!isValidFanaticalHost(url)) return false
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
     * Validates host is actually a Fanatical domain, not a lookalike.
     * 
     * Protects against:
     * - Suffix attacks: fakefanatical.com
     * - Prefix attacks: fanatical.com.attacker.com
     * - IP literals: https://192.168.1.1/fanatical/
     */
    private fun isValidFanaticalHost(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            
            // Reject IP literals
            if (host.matches(Regex("""^\d+\.\d+\.\d+\.\d+$"""))) return false
            if (host.startsWith("[")) return false // IPv6
            
            // Must be exactly fanatical.com or subdomain thereof
            host == "fanatical.com" || host.endsWith(".fanatical.com")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get the platform where a Fanatical key should be redeemed.
     * 
     * Fanatical sells keys for various platforms. This would be determined
     * from the product page which shows "Activates on: Steam" etc.
     * 
     * @return The target platform for key redemption, or null if unknown
     */
    fun getKeyRedemptionPlatform(giveaway: Giveaway): GameStore? {
        // Would need to parse product page to determine target platform
        // For now, return null - discovery shows it's a Fanatical giveaway,
        // but user must check the page to see where the key activates
        return null
    }
}
