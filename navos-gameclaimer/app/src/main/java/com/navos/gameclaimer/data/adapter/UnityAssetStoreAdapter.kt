package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*

/**
 * Phase 11: Unity Asset Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Weekly free asset from Publisher of the Week promotion
 * - Asset Types: 3D models, textures, scripts, tools, complete projects
 * - Authentication: Unity ID (login.unity.com)
 * 
 * DISCOVERY:
 * - Publisher sale page: https://assetstore.unity.com/publisher-sale
 * - Entitlements API: /api/users/entitlements (requires auth)
 * - Weekly coupon code changes every promotion
 * 
 * LIMITATIONS:
 * - Free asset requires weekly coupon code (changes frequently)
 * - Checkout at pay.unity.com requires billing info setup
 * - Terms acceptance and EU withdrawal waiver required
 * - Coupon validity cannot be assumed - page is authoritative
 * - Mobile claiming not feasible (complex checkout flow)
 * 
 * SECURITY:
 * - Discovery is READ-ONLY - never triggers claims or ownership changes
 * - All URLs validated against trusted Unity domains
 * - HTTPS-only, no IP literals, no userinfo in URLs
 * - Discovering an asset is NOT proof of ownership
 * - Coupon codes are transient; never persist in logs or exports
 * - Entitlements API is ONLY reliable ownership signal
 * - Terms/billing acceptance are user-controlled actions
 * - No credential logging, cookie/token exposure
 * 
 * COUPON HANDLING:
 * - Coupon codes change weekly
 * - Never assume a coupon makes an asset free
 * - Never persist coupons beyond current discovery cycle
 * - Page "to pay now" amount is authoritative, not coupon presence
 * 
 * UPSTREAM REFERENCE: src/stores/unity.py (~500 lines)
 */
class UnityAssetStoreAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.UNITY_ASSET_STORE
    override val displayName: String = "Unity Asset Store"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled - requires checkout with billing/terms
    override val requiresAuth: Boolean = true  // Unity ID required for claims
    
    companion object {
        // Unity Asset Store URLs
        const val URL_BASE = "https://assetstore.unity.com"
        const val URL_SALE = "https://assetstore.unity.com/publisher-sale"
        const val URL_ORDERS = "https://assetstore.unity.com/orders"
        const val URL_PACKAGES = "https://assetstore.unity.com/packages/"
        
        // Unity ID login
        const val URL_LOGIN = "https://id.unity.com/en/login"
        
        // Unity Checkout
        const val URL_CHECKOUT = "https://pay.unity.com"
        
        // API endpoints
        const val API_ENTITLEMENTS = "/api/users/entitlements"
        
        // Trusted domains - exact matches for security
        val TRUSTED_DOMAINS = setOf(
            "assetstore.unity.com",
            "unity.com",
            "id.unity.com",         // Login
            "login.unity.com",      // Login (redirect)
            "pay.unity.com"         // Checkout
        )
        
        // Unity package URL pattern: https://assetstore.unity.com/packages/category/subcategory/name-12345
        // Can have multiple path segments before the name-ID part
        // The trailing number is the package ID
        val PACKAGE_URL_REGEX = Regex(
            """https://assetstore\.unity\.com/packages/(?:[a-z0-9-]+/)+([a-z0-9-]+-(\d+))""",
            RegexOption.IGNORE_CASE
        )
        
        // Coupon code pattern from page text: "coupon code ABC123_XYZ"
        val COUPON_REGEX = Regex("""coupon code\s+([A-Z0-9][A-Z0-9_-]{3,24})""", RegexOption.IGNORE_CASE)
        
        // Asset name from giveaway block
        val GIVEAWAY_NAME_REGEX = Regex(
            """PUBLISHER ASSET GIVEAWAY\s+(.+?)\s+Add this week""",
            RegexOption.IGNORE_CASE
        )
        
        // Total amount pattern: "to pay now $0.00" or "to pay now 0,00"
        val TOTAL_REGEX = Regex("""to pay now[^\d]{0,12}([\d]+[.,][\d]{2})""", RegexOption.IGNORE_CASE)
    }
    
    /**
     * Discover the weekly free asset from Unity's publisher sale page.
     * 
     * Discovery process:
     * 1. Fetch publisher sale page
     * 2. Look for "PUBLISHER ASSET GIVEAWAY" section
     * 3. Extract asset name, link, and coupon code
     * 4. Validate asset URL is proper Unity package
     * 
     * NOTE: This is READ-ONLY - never triggers claims or ownership changes.
     * Discovering an asset with a coupon is NOT proof of ownership.
     * Coupon codes are transient and may be invalid.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        // Phase 11: Discovery via page parsing would be implemented here
        // In Android, this would require:
        // - WebView to fetch publisher-sale page
        // - Parse HTML for giveaway section
        // - Extract coupon code (for display only, not automation)
        // - Validate package URL structure
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a Unity asset is still available with the weekly coupon.
     * 
     * Verification checks:
     * - Asset page loads successfully
     * - Coupon is still mentioned on publisher-sale page
     * - Asset can be added to cart
     * 
     * SECURITY: 
     * - Never assume coupon validity - page is authoritative
     * - A valid coupon does NOT mean user owns the asset
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Would check:
        // - Asset page loads
        // - "Buy Now" button available
        // - Coupon still listed on sale page
        // 
        // SECURITY: Coupon presence is NOT proof of ownership
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this asset.
     * 
     * Eligibility depends on:
     * - User logged in via Unity ID
     * - Asset not already in entitlements
     * - Weekly coupon still valid
     * - Billing info configured (one-time setup)
     * 
     * Returns UNKNOWN when evidence is insufficient.
     * Never infers ALREADY_OWNED from discovery alone.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Would check:
        // - Unity session active (via orders page load test)
        // - Asset not in entitlements API
        // 
        // SECURITY: Entitlements API is ONLY reliable ownership signal
        return ClaimEligibility.REQUIRES_AUTH
    }
    
    /**
     * Claim a Unity asset.
     * 
     * DISABLED in Phase 11 - reasons:
     * - Complex checkout flow at pay.unity.com
     * - Billing information required (one-time human setup)
     * - Terms of Service acceptance required
     * - EU right-of-withdrawal waiver required
     * - Tax exemption question must be answered
     * - Coupon must be applied and total verified as $0.00
     * - Mobile claiming not feasible
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Unity claiming disabled - requires checkout with billing setup, terms acceptance, and coupon application",
            isRetryable = false
        )
    }
    
    /**
     * Get the Unity ID login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated with Unity.
     * 
     * Auth check via orders page navigation:
     * - Stays on assetstore.unity.com = signed in
     * - Redirects to login.unity.com = signed out
     */
    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check Unity auth state via WebView orders page test
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
     * Check if host is a trusted Unity domain.
     * 
     * Protects against:
     * - Suffix attacks: fakeunity.com
     * - Prefix attacks: unity.com.attacker.com
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
     * Parse package ID from Unity Asset Store URL.
     * 
     * URL format: https://assetstore.unity.com/packages/category/name-12345
     * 
     * @return package ID (numeric) if valid Unity URL, null otherwise
     */
    fun parsePackageUrl(url: String): String? {
        if (!isValidUrl(url)) return null
        val match = PACKAGE_URL_REGEX.find(url) ?: return null
        return match.groupValues[2]  // Group 2 is the numeric ID
    }
    
    /**
     * Extract slug from URL for identification.
     * 
     * The slug is the last path segment: "name-12345" from packages/category/name-12345
     */
    fun extractSlug(url: String): String {
        return url.trimEnd('/').substringAfterLast('/')
    }
    
    /**
     * Parse asset name from tab title.
     * 
     * Unity titles follow: "Asset Name | Category | Unity Asset Store"
     * 
     * @return asset name, or empty string if cannot parse
     */
    fun parseAssetNameFromTitle(title: String): String {
        val parts = title.split(" | ").map { it.trim() }
        return if (parts.size >= 2 && parts.last() == "Unity Asset Store") {
            parts.first()
        } else {
            ""
        }
    }
    
    /**
     * Parse free asset from publisher sale page state.
     * 
     * Page must contain:
     * - "PUBLISHER ASSET GIVEAWAY" section
     * - Coupon code
     * - Link to asset package
     * 
     * @param pageText Body text from sale page
     * @param giftLinks Links with "free gift" or "free asset" text
     * @return Parsed asset info, or null if not found
     */
    fun parseFreeAsset(pageText: String, giftLinks: List<GiftLink>): UnityFreeAsset? {
        // All three must be present: coupon, name, and link
        val couponMatch = COUPON_REGEX.find(pageText) ?: return null
        val nameMatch = GIVEAWAY_NAME_REGEX.find(pageText) ?: return null
        val link = giftLinks.firstOrNull { it.href.isNotBlank() } ?: return null
        
        val href = if (link.href.startsWith("http")) link.href else "$URL_BASE${link.href}"
        
        // Validate URL is proper Unity package
        if (!isValidUrl(href)) return null
        
        return UnityFreeAsset(
            title = nameMatch.groupValues[1].trim(),
            url = href,
            slug = extractSlug(href),
            packageId = parsePackageUrl(href) ?: "",
            coupon = couponMatch.groupValues[1].trim()
        )
    }
    
    /**
     * Parse checkout total amount.
     * 
     * Looks for "to pay now $X.XX" pattern.
     * 
     * @return amount as double, or -1.0 if cannot parse
     */
    fun parseCheckoutTotal(text: String): Double {
        val match = TOTAL_REGEX.find(text) ?: return -1.0
        return try {
            match.groupValues[1].replace(",", ".").toDouble()
        } catch (e: NumberFormatException) {
            -1.0
        }
    }
    
    /**
     * Validate checkout total is zero before any claim attempt.
     * 
     * SECURITY: Never proceed with claim if total is not exactly 0.00
     */
    fun isCheckoutFree(total: Double): Boolean {
        return total == 0.0
    }
}

/**
 * Represents a link from the giveaway section.
 */
data class GiftLink(
    val href: String,
    val text: String
)

/**
 * Represents a Unity free asset from discovery.
 * 
 * IMPORTANT: 
 * - A discovered asset is NOT proof of ownership
 * - The coupon may be invalid or expired
 * - Use entitlements API to check actual ownership
 * - Never persist coupon codes beyond discovery cycle
 */
data class UnityFreeAsset(
    val title: String,
    val url: String,
    val slug: String,
    val packageId: String,
    val coupon: String  // Transient - do not persist
) {
    /**
     * Convert to Giveaway for adapter interface.
     * 
     * NOTE: Coupon is NOT included - it's transient and should
     * never be logged or exported.
     */
    fun toGiveaway(): Giveaway {
        return Giveaway(
            id = "unity_$packageId",
            title = title,
            store = GameStore.UNITY_ASSET_STORE,
            type = GiveawayType.FREE_TO_KEEP,
            claimUrl = url,
            originalPrice = null,
            imageUrl = null,
            description = "Weekly free asset - requires coupon",
            startTime = null,
            endTime = null,
            source = DiscoverySource.STORE_DIRECT,
            externalId = packageId
        )
    }
}
