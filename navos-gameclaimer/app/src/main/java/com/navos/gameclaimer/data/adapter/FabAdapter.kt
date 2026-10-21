package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 11: Fab Adapter (Epic's Asset Marketplace)
 * 
 * CAPABILITIES:
 * - Discovery: Via free content blade API (/i/blades/free_content_blade)
 * - Asset Types: Limited-time free assets, 3D models, textures, audio, etc.
 * - Authentication: Epic SSO (shares session with Epic Games Store)
 * 
 * DISCOVERY API:
 * - Blade endpoint: https://www.fab.com/i/blades/free_content_blade
 * - Library endpoint: https://www.fab.com/i/library/search?count=100&source=acquired
 * - User endpoint: https://www.fab.com/i/users/me
 * 
 * LIMITATIONS:
 * - Free content blade API is undocumented and may change
 * - Only isLimitedFreeContent=true marks genuinely free assets
 * - Checkout requires Epic payment iframe (cross-origin)
 * - EULA modal and hCaptcha require user interaction
 * - Mobile claiming not feasible (desktop browser checkout)
 * 
 * SECURITY:
 * - Discovery is READ-ONLY - never triggers claims or ownership changes
 * - All URLs validated against trusted Fab/Epic domains
 * - HTTPS-only, no IP literals, no userinfo in URLs
 * - Discovering an asset is NOT proof of ownership
 * - EULA acceptance and checkout are user-controlled actions
 * - No credential logging, cookie/token exposure
 * 
 * FREE ASSET VALIDATION:
 * - isLimitedFreeContent MUST be true (blade-level flag)
 * - blockContentType MUST be "listings" (not subscriptions/bundles)
 * - Each tile must have a valid listing.uid
 * - Never assume free from price alone - Fab controls the promotion
 * 
 * UPSTREAM REFERENCE: src/stores/epic_fab.py (~450 lines)
 */
class FabAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.FAB
    override val displayName: String = "Fab"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled - requires Epic iframe checkout
    override val requiresAuth: Boolean = true  // Epic SSO required for claims
    
    companion object {
        // Fab URLs
        const val URL_BASE = "https://www.fab.com"
        const val URL_FREE = "https://www.fab.com/limited-time-free?lang=en"
        const val URL_LOGIN = "https://www.fab.com/login"  // Redirects to Epic SSO
        const val URL_LISTING = "https://www.fab.com/listings/"
        
        // Fab API endpoints (JSON, requires Cloudflare bypass from browser context)
        const val API_BLADE = "/i/blades/free_content_blade"
        const val API_ME = "/i/users/me"
        const val API_LIBRARY = "/i/library/search?count=100&source=acquired"
        
        // Trusted domains - exact matches for security
        val TRUSTED_DOMAINS = setOf(
            "fab.com",
            "www.fab.com",
            "epicgames.com",        // SSO login
            "www.epicgames.com"     // SSO login
        )
        
        // Fab listing URL pattern: https://www.fab.com/listings/{uid}
        // UIDs can be alphanumeric with hyphens
        val FAB_LISTING_REGEX = Regex("""https://(?:www\.)?fab\.com/listings/([a-zA-Z0-9-]+)""", RegexOption.IGNORE_CASE)
        
        // Free content blade validation
        const val FREE_BLOCK_TYPE = "listings"
        
        // Asset listing types that can be free
        val VALID_LISTING_TYPES = setOf(
            "3d-model",
            "audio",
            "texture",
            "material",
            "hdri",
            "blueprint",
            "plugin",
            "code-plugin",
            "environment"
        )
    }
    
    /**
     * Discover free assets from Fab's limited-time free section.
     * 
     * Discovery process:
     * 1. Fetch free content blade API
     * 2. Validate isLimitedFreeContent flag is true
     * 3. Validate blockContentType is "listings"
     * 4. Parse each tile's listing for uid, title, seller, type
     * 5. Build listing URLs for valid assets
     * 
     * NOTE: This is READ-ONLY - never triggers claims or changes ownership.
     * Discovering an asset is NOT proof of ownership.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 11: Discovery via blade API would be implemented here
        // In Android, this would require:
        // - WebView to handle Cloudflare protection
        // - Parse JSON response for blade.tiles[].listing
        // - Validate isLimitedFreeContent=true at blade level
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a Fab asset is still available as free.
     * 
     * Verification checks:
     * - Asset URL is valid and loads
     * - Asset is still in free content blade
     * - Price shows as free with -100% discount
     * 
     * SECURITY: Never infer owned status from verification alone.
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Would navigate to listing page and check:
        // - Page loads successfully
        // - "-100%" or "Free" in price area
        // - "Buy Now" or "Get" button visible
        // 
        // SECURITY: Page showing "free" is NOT proof user owns it
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this asset.
     * 
     * Eligibility depends on:
     * - User logged in via Epic SSO
     * - Asset still in free promotion
     * - Asset not already in user's library
     * 
     * Returns UNKNOWN when evidence is insufficient.
     * Never infers ALREADY_OWNED from discovery alone.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Would check:
        // - Fab session active (via /i/users/me)
        // - Asset not in library (via /i/library/search)
        // 
        // SECURITY: Library check is the ONLY reliable ownership signal
        return ClaimEligibility.REQUIRES_AUTH
    }
    
    /**
     * Claim a Fab asset.
     * 
     * DISABLED in Phase 11 - reasons:
     * - Checkout via Epic payment iframe (cross-origin, cannot automate)
     * - EULA modal requires user consent
     * - hCaptcha may appear (requires human)
     * - EU right-of-withdrawal waiver needs explicit acceptance
     * - Mobile claiming not feasible
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Fab claiming disabled - requires Epic iframe checkout with user consent",
            isRetryable = false
        )
    }
    
    /**
     * Get the Fab login URL (redirects to Epic SSO).
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated with Fab.
     * 
     * Auth check via /i/users/me endpoint:
     * - 200 = signed in
     * - 401 = signed out
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check Fab auth state via WebView /i/users/me call
        return false
    }
    
    /**
     * Validate URL is safe for navigation.
     * 
     * Checks:
     * - HTTPS scheme only
     * - No userinfo (user:pass@host)
     * - No IP address literals
     * - No unexpected ports
     * - Host is in trusted domains list
     * 
     * @return true if URL is safe to navigate to
     */
    fun isValidUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            
            // HTTPS only
            if (uri.scheme?.lowercase() != "https") return false
            
            // No userinfo
            if (uri.userInfo != null) return false
            
            // Get host
            val host = uri.host?.lowercase() ?: return false
            
            // No IP addresses (IPv4)
            if (host.matches(Regex("""^\d+\.\d+\.\d+\.\d+$"""))) return false
            
            // No IPv6 literals
            if (host.startsWith("[")) return false
            
            // No explicit ports (use default 443)
            if (uri.port != -1) return false
            
            // Must match trusted domain exactly or be subdomain
            isValidHost(host)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if host is a trusted Fab/Epic domain.
     * 
     * Protects against:
     * - Suffix attacks: fakefab.com
     * - Prefix attacks: fab.com.attacker.com
     * - Lookalike domains
     */
    fun isValidHost(host: String): Boolean {
        val normalizedHost = host.lowercase()
        
        // Check exact match first
        if (normalizedHost in TRUSTED_DOMAINS) return true
        
        // Check subdomain of trusted domain
        for (domain in TRUSTED_DOMAINS) {
            if (normalizedHost.endsWith(".$domain")) return true
        }
        
        return false
    }
    
    /**
     * Parse Fab listing UID from URL.
     * 
     * @return listing UID if valid Fab URL, null otherwise
     */
    fun parseListingUrl(url: String): String? {
        if (!isValidUrl(url)) return null
        val match = FAB_LISTING_REGEX.find(url) ?: return null
        return match.groupValues[1]
    }
    
    /**
     * Parse free listings from Fab's blade API response.
     * 
     * Response structure:
     * {
     *   "isLimitedFreeContent": true,  // MUST be true for free assets
     *   "blockContentType": "listings", // MUST be "listings"
     *   "tiles": [
     *     {
     *       "listing": {
     *         "uid": "abc-123",
     *         "title": "Asset Name",
     *         "user": { "sellerName": "Creator" },
     *         "listingType": "3d-model"
     *       }
     *     }
     *   ]
     * }
     * 
     * @return List of parsed listings with uid, title, seller, type
     */
    fun parseFreeBlade(blade: Map<String, Any?>): List<FabListing> {
        // Validate blade-level flags FIRST
        if (blade["isLimitedFreeContent"] != true) {
            return emptyList()
        }
        
        if (blade["blockContentType"] != FREE_BLOCK_TYPE) {
            return emptyList()
        }
        
        val tiles = blade["tiles"] as? List<*> ?: return emptyList()
        
        return tiles.mapNotNull { tile ->
            parseTileListing(tile as? Map<*, *>)
        }
    }
    
    /**
     * Parse a single tile's listing from blade response.
     */
    private fun parseTileListing(tile: Map<*, *>?): FabListing? {
        val listing = tile?.get("listing") as? Map<*, *> ?: return null
        
        val uid = (listing["uid"] as? String)?.trim()
        if (uid.isNullOrEmpty()) return null
        
        val title = (listing["title"] as? String)?.trim() ?: "Fab asset"
        
        // Seller can be nested object or plain string
        val seller = when (val user = listing["user"]) {
            is Map<*, *> -> (user["sellerName"] as? String)?.trim() ?: ""
            is String -> user.trim()
            else -> ""
        }
        
        val listingType = (listing["listingType"] as? String)?.trim() ?: ""
        
        return FabListing(
            uid = uid,
            title = title,
            seller = seller,
            listingType = listingType,
            url = "$URL_LISTING$uid"
        )
    }
    
    /**
     * Validate a listing type is eligible for free promotion.
     */
    fun isValidListingType(type: String): Boolean {
        return type.lowercase() in VALID_LISTING_TYPES
    }
}

/**
 * Represents a Fab asset listing from discovery.
 * 
 * IMPORTANT: A discovered listing is NOT proof of ownership.
 * Use library API to check actual ownership.
 */
data class FabListing(
    val uid: String,
    val title: String,
    val seller: String,
    val listingType: String,
    val url: String
) {
    /**
     * Convert to Giveaway for adapter interface.
     */
    fun toGiveaway(): Giveaway {
        return Giveaway(
            id = "fab_$uid",
            title = title,
            store = GameStore.FAB,
            type = GiveawayType.FREE_TO_KEEP,
            claimUrl = url,
            originalPrice = null,
            imageUrl = null,
            description = "By $seller",
            startTime = null,
            endTime = null,
            source = DiscoverySource.STORE_DIRECT,
            externalId = uid
        )
    }
}
