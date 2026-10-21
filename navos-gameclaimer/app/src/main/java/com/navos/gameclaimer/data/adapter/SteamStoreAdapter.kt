package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 7: Steam Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via GamerPower API (already implemented) + Steam's own search page
 * - Authentication: WebView login at store.steampowered.com/login
 * - Claiming: Requires user interaction (Steam Guard, 2FA)
 * 
 * LIMITATIONS:
 * - No automated claiming in Phase 7 (requires Steam Guard handling)
 * - Regional restrictions may apply
 * - Steam's anti-bot protections require careful navigation
 * 
 * SECURITY:
 * - Auth via WebView only (no credential storage)
 * - All URLs validated against trusted domains
 * - No auto-form submission
 * 
 * UPSTREAM REFERENCE: src/stores/steam.py (50KB)
 */
class SteamStoreAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.STEAM
    override val displayName: String = "Steam"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled until Steam Guard handling implemented
    override val requiresAuth: Boolean = true
    
    companion object {
        // Steam Store URLs
        const val URL_STORE = "https://store.steampowered.com/?l=english"
        const val URL_LOGIN = "https://store.steampowered.com/login/"
        const val URL_FREE_SEARCH = "https://store.steampowered.com/search/?maxprice=free&specials=1&ignore_preferences=1"
        
        // Trusted domains for Steam
        val TRUSTED_DOMAINS = setOf(
            "store.steampowered.com",
            "steampowered.com",
            "steamcommunity.com"
        )
        
        // Steam item URL pattern: app/sub/bundle
        val STEAM_ITEM_REGEX = Regex("""https://store\.steampowered\.com/(?:agecheck/)?(app|sub|bundle)/(\d+)""")
    }
    
    /**
     * Discover free giveaways from Steam.
     * 
     * In Phase 7, discovery is delegated to GamerPower API client.
     * Future: Direct Steam search page scraping.
     * 
     * NOTE: This is READ-ONLY - never triggers claims.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 7: Discovery via GamerPower (already implemented in Phase 6)
        // Steam-specific discovery would scrape URL_FREE_SEARCH
        // For now, return empty - GamerPower provides Steam giveaways
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a giveaway is still valid.
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Would navigate to giveaway.claimUrl and check:
        // - Page loads successfully
        // - "Add to Library" or "Play Game" button exists
        // - Not a demo (check for demo_above_purchase)
        // - Price is 100% off
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Would check:
        // - User is logged in
        // - Game not already owned
        // - Region allows claim
        // - Not age-gated without verification
        return ClaimEligibility.REQUIRES_AUTH // Default: need auth to check
    }
    
    /**
     * Claim a giveaway.
     * 
     * DISABLED in Phase 7 - requires Steam Guard handling.
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Steam claiming disabled - requires Steam Guard handling",
            isRetryable = false
        )
    }
    
    /**
     * Get the Steam login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated.
     * 
     * Would check cookies for steamLoginSecure or similar.
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check for Steam auth cookies in WebView
        return false
    }
    
    /**
     * Parse Steam app/package ID from URL with security validation.
     * 
     * Security: Rejects deceptive hosts, unsafe schemes, and attack patterns.
     */
    fun parseItemId(url: String): Pair<String, String>? {
        // Security: Validate URL structure and scheme
        if (!isSecureUrl(url)) return null
        
        // Security: Validate host is actually Steam
        if (!isValidSteamHost(url)) return null
        
        // Security: Check for path traversal attacks
        if (hasPathTraversal(url)) return null
        
        val match = STEAM_ITEM_REGEX.find(url) ?: return null
        return match.groupValues[1] to match.groupValues[2] // (type, id)
    }
    
    /**
     * Validates URL is HTTPS and well-formed.
     */
    private fun isSecureUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            uri.scheme?.lowercase() == "https" && uri.port == -1 // No explicit port
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates host is actually a Steam domain, not a lookalike.
     * 
     * Protects against:
     * - Suffix attacks: fakesteampowered.com
     * - Prefix attacks: store.steampowered.com.attacker.com
     * - IP addresses
     */
    private fun isValidSteamHost(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            
            // Reject IP addresses
            if (host.matches(Regex("""^\d+\.\d+\.\d+\.\d+$"""))) return false
            if (host.startsWith("[")) return false // IPv6
            
            // Must be exactly steampowered.com or subdomain thereof
            // OR exactly steamcommunity.com or subdomain thereof
            host == "steampowered.com" || host.endsWith(".steampowered.com") ||
            host == "steamcommunity.com" || host.endsWith(".steamcommunity.com")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Detects path traversal attack patterns.
     */
    private fun hasPathTraversal(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains("..") ||
               normalized.contains("%2e%2e") ||
               normalized.contains("%252e") ||
               normalized.contains("%00")
    }
}
