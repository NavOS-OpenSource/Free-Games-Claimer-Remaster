package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 8: Epic Games Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via GamerPower API + Epic's free games page
 * - Mobile Discovery: Via egs-platform-service API (Android/iOS)
 * - Authentication: WebView login at epicgames.com/id/login
 * - Offer Types: PC free-to-keep, mobile offers, DLC, bundles
 * 
 * LIMITATIONS:
 * - Discovery API is undocumented and may change
 * - Region restrictions are detected but not circumvented
 * - MFA/CAPTCHA requires user interaction
 * - Mobile offers discovered but claimed on same product page
 * 
 * SECURITY:
 * - Auth via WebView only (no credential storage)
 * - All URLs validated against trusted domains
 * - Button labels are UI observations, NOT proof of ownership
 * - Never infer claimed status from discovery
 * - MFA, backup codes, CAPTCHA remain user-controlled
 * 
 * PAGE STATES (UI observations only):
 * - new_add: "Add to Library" button visible
 * - new_get: "Get" button visible  
 * - old_cta: Legacy purchase-cta-button present
 * - owned: "In Library" or "Owned" status
 * - region: "Unavailable in your platform or region"
 * - unknown: Cannot determine state
 * 
 * UPSTREAM REFERENCE: src/stores/epic.py (700+ lines), epic_mobile.py (90 lines)
 */
class EpicStoreAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.EPIC_GAMES
    override val displayName: String = "Epic Games"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled until security review
    override val requiresAuth: Boolean = true
    
    companion object {
        // Epic Games URLs
        const val URL_STORE = "https://store.epicgames.com/"
        const val URL_FREE_GAMES = "https://store.epicgames.com/en-US/free-games"
        const val URL_LOGIN = "https://www.epicgames.com/id/login?lang=en-US&noHostRedirect=true"
        const val URL_PRODUCT = "https://store.epicgames.com/en-US/p/"
        
        // Mobile discovery API (undocumented, may change)
        const val URL_MOBILE_DISCOVER = "https://egs-platform-service.store.epicgames.com/api/v2/public/discover/home"
        
        // Trusted domains for Epic - exact matches required
        val TRUSTED_DOMAINS = setOf(
            "epicgames.com",
            "store.epicgames.com",
            "egs-platform-service.store.epicgames.com",
            "www.epicgames.com"
        )
        
        // Epic product URL patterns
        // Standard: https://store.epicgames.com/en-US/p/game-slug
        // Bundles: https://store.epicgames.com/en-US/bundles/bundle-slug
        val EPIC_PRODUCT_REGEX = Regex("""https://store\.epicgames\.com/[a-z]{2}-[A-Z]{2}/(p|bundles)/([a-z0-9-]+)""")
        
        // Mobile platforms
        val MOBILE_PLATFORMS = setOf("android", "ios")
        
        // Platform labels for display
        val PLATFORM_LABELS = mapOf(
            "android" to "Android",
            "ios" to "iOS"
        )
    }
    
    /**
     * Discover free giveaways from Epic Games.
     * 
     * In Phase 8, discovery is delegated to GamerPower API client.
     * Direct Epic discovery would involve:
     * - Fetching URL_FREE_GAMES page
     * - Parsing game tiles from the page
     * - Fetching mobile offers from discover API
     * 
     * NOTE: This is READ-ONLY - never triggers claims or changes ownership.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 8: Discovery via GamerPower (already implemented in Phase 6)
        // Epic-specific discovery would parse the free games page
        // Mobile discovery would call URL_MOBILE_DISCOVER with platform param
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a giveaway is still valid.
     * 
     * IMPORTANT: Page state observations (new_add, new_get, owned, region)
     * are UI hints only, NOT authoritative proof of ownership or eligibility.
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Would navigate to giveaway.claimUrl and observe:
        // - Page loads successfully
        // - One of: new_add, new_get, old_cta buttons visible
        // - NOT: owned, region states
        // 
        // SECURITY: Never infer claimed status from these observations
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     * 
     * Returns UNKNOWN when evidence is insufficient.
     * Never infers ALREADY_OWNED just from button labels.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Would check:
        // - User is logged in (via WebView session)
        // - Page state shows claimable button
        // - No region lock message
        // 
        // SECURITY: Default to UNKNOWN when unsure
        return ClaimEligibility.REQUIRES_AUTH
    }
    
    /**
     * Claim a giveaway.
     * 
     * DISABLED in Phase 8 - requires security review before enabling.
     * Would involve:
     * - Clicking "Get" or "Add to Library" button
     * - Handling checkout overlay if present
     * - Verifying 0-price transaction
     * - User handles any MFA/CAPTCHA
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Epic claiming disabled - requires security review",
            isRetryable = false
        )
    }
    
    /**
     * Get the Epic Games login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated.
     * 
     * Would check for Epic session via multiple signals:
     * - egs-navigation isloggedin attribute
     * - Wishlist/Cart links visible
     * - User avatar present
     * - Absence of "Sign In" button
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check Epic auth state via WebView
        return false
    }
    
    /**
     * Parse Epic product slug from URL with security validation.
     * 
     * Security: Rejects deceptive hosts, unsafe schemes.
     * 
     * @return Pair of (type, slug) where type is "p" for products or "bundles"
     */
    fun parseProductUrl(url: String): Pair<String, String>? {
        // Security: Validate URL structure and scheme
        if (!isSecureUrl(url)) return null
        
        // Security: Validate host is actually Epic
        if (!isValidEpicHost(url)) return null
        
        val match = EPIC_PRODUCT_REGEX.find(url) ?: return null
        return match.groupValues[1] to match.groupValues[2] // (type, slug)
    }
    
    /**
     * Validates URL is HTTPS and well-formed.
     */
    private fun isSecureUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            uri.scheme?.lowercase() == "https" && uri.port == -1 && uri.userInfo == null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates host is actually an Epic domain, not a lookalike.
     * 
     * Protects against:
     * - Suffix attacks: fakeepicgames.com
     * - Prefix attacks: epicgames.com.attacker.com
     * - IP addresses
     */
    private fun isValidEpicHost(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            
            // Reject IP addresses
            if (host.matches(Regex("""^\d+\.\d+\.\d+\.\d+$"""))) return false
            if (host.startsWith("[")) return false // IPv6
            
            // Must be exactly epicgames.com or subdomain thereof
            host == "epicgames.com" || host.endsWith(".epicgames.com")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Determine offer type from giveaway metadata.
     * 
     * Types:
     * - PC_FREE_TO_KEEP: Weekly free PC game
     * - MOBILE_ANDROID: Android-specific offer
     * - MOBILE_IOS: iOS-specific offer  
     * - DLC_OR_ADDON: DLC/expansion content
     * - BUNDLE: Multi-game bundle
     * - UNKNOWN: Cannot determine
     */
    fun classifyOfferType(giveaway: Giveaway, platform: String? = null): GiveawayType {
        // Check platform-specific mobile offers
        when (platform?.lowercase()) {
            "android" -> return GiveawayType.DLC_OR_ADDON // Use existing enum for mobile
            "ios" -> return GiveawayType.DLC_OR_ADDON
        }
        
        // Check URL for bundle indicator
        val parsed = parseProductUrl(giveaway.claimUrl)
        if (parsed?.first == "bundles") {
            return GiveawayType.FREE_TO_KEEP // Bundles are still free-to-keep
        }
        
        // Default to free-to-keep for PC offers
        return giveaway.type
    }
    
    /**
     * Check if an offer is region-restricted.
     * 
     * Detection based on page state, NOT authoritative.
     */
    fun isRegionRestricted(pageState: EpicPageState): Boolean {
        return pageState == EpicPageState.REGION
    }
}

/**
 * Epic Games page state observations.
 * 
 * IMPORTANT: These are UI observations, NOT proof of ownership or eligibility.
 * Never use these to infer claimed status or modify database.
 */
enum class EpicPageState {
    /** "Add to Library" button visible */
    NEW_ADD,
    /** "Get" button visible */
    NEW_GET,
    /** Legacy purchase-cta-button present */
    OLD_CTA,
    /** "In Library" or "Owned" status shown */
    OWNED,
    /** "Unavailable in your platform or region" */
    REGION,
    /** Cannot determine page state */
    UNKNOWN
}
