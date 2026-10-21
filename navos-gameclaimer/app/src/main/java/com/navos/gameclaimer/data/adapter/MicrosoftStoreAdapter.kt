package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*
import java.net.URI

/**
 * Phase 10: Microsoft Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via product API (storeedgefd.dsx.mp.microsoft.com)
 * - Code Redemption: Via account.microsoft.com/billing/redeem (browser required)
 * - Authentication: WebView login at login.live.com
 * - Price Detection: Free-to-keep vs Game Pass vs paid
 * 
 * LIMITATIONS:
 * - Claiming requires desktop browser automation (cross-origin iframe)
 * - Code redemption requires user interaction
 * - Purchase window runs in separate process (buynowui)
 * - Regional pricing varies per market
 * 
 * SECURITY:
 * - Auth via WebView only (no credential storage)
 * - All URLs validated against trusted Microsoft domains
 * - Code redemption never automated - user must initiate
 * - TOTP/MFA remain user-controlled
 * - Product ID validated (12 chars, alphanumeric, uppercase)
 * 
 * UPSTREAM REFERENCE: src/stores/microsoft.py (550+ lines)
 */
class MicrosoftStoreAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.MICROSOFT_STORE
    override val displayName: String = "Microsoft Store"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Requires desktop browser automation
    override val requiresAuth: Boolean = true
    
    companion object {
        // Microsoft Store URLs
        const val URL_REDEEM = "https://account.microsoft.com/billing/redeem"
        const val URL_PRODUCT_PAGE = "https://www.xbox.com/en-US/games/store/game/"
        const val URL_LOGIN = "https://login.live.com/"
        
        // Product API for price/availability checks
        const val URL_PRODUCT_API = "https://storeedgefd.dsx.mp.microsoft.com/v9.0/products/"
        
        // Trusted Microsoft domains - exact matches or subdomains
        val TRUSTED_DOMAINS = setOf(
            "microsoft.com",
            "account.microsoft.com",
            "login.live.com",
            "login.microsoftonline.com",
            "login.microsoft.com",
            "xbox.com",
            "www.xbox.com",
            "storeedgefd.dsx.mp.microsoft.com"
        )
        
        // Login hosts - used to detect auth state
        val LOGIN_HOSTS = setOf(
            "login.live.com",
            "login.microsoftonline.com",
            "login.microsoft.com"
        )
        
        // Product ID pattern: 12 alphanumeric uppercase chars (e.g., 9NKX70BBCDRN)
        val PRODUCT_ID_REGEX = Regex("""^[A-Z0-9]{12}$""")
        
        // Xbox/Microsoft product URL patterns
        val PRODUCT_URL_REGEX = Regex("""https://(www\.)?xbox\.com/[a-z]{2}-[A-Z]{2}/games/store/[^/]+/([A-Z0-9]{12})""")
        val MS_STORE_REGEX = Regex("""https://www\.microsoft\.com/[a-z]{2}-[a-z]{2}/p/[^/]+/([A-Z0-9]{12})""")
    }
    
    /**
     * Discover free giveaways from Microsoft Store.
     * 
     * Discovery delegated to GamerPower API in current phase.
     * Direct discovery would involve:
     * - Fetching product details from storeedgefd API
     * - Checking MSRP > 0 and current price = 0 (free-to-keep)
     * - Filtering out Game Pass "Included" items
     * 
     * NOTE: This is READ-ONLY - never triggers claims or changes ownership.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }
    
    /**
     * Verify a giveaway is still valid.
     * 
     * Would fetch product from API and check:
     * - Product exists and is available
     * - Price is 0 (free)
     * - MSRP > 0 (normally costs money, so it's a giveaway)
     * - Not "Included" (Game Pass)
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     * Returns REQUIRES_AUTH as Microsoft requires login.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        return ClaimEligibility.REQUIRES_AUTH
    }
    
    /**
     * Claim a giveaway.
     * 
     * DISABLED - Microsoft claiming requires:
     * - Desktop browser with headful mode
     * - Cross-origin iframe handling (buynowui purchase window)
     * - User interaction for checkout confirmation
     * - TOTP/MFA handling
     * 
     * Not feasible on Android without full browser automation.
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Microsoft claiming disabled - requires desktop browser automation",
            isRetryable = false
        )
    }
    
    /**
     * Get the Microsoft login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated.
     * Would check if we're on account.microsoft.com vs login hosts.
     */
    override suspend fun isAuthenticated(): Boolean {
        return false
    }
    
    // =========================================================================
    // URL Validation
    // =========================================================================
    
    /**
     * Validates URL is from a trusted Microsoft domain.
     * 
     * Protects against:
     * - Suffix attacks: fakemicrosoft.com
     * - Prefix attacks: microsoft.com.attacker.com
     * - IP addresses (v4 and v6)
     * - Userinfo injection: user@microsoft.com@attacker.com
     * - Non-HTTPS schemes
     * - Unexpected ports
     */
    fun isValidMicrosoftHost(url: String): Boolean {
        return try {
            val uri = URI(url)
            
            // Must be HTTPS
            if (uri.scheme?.lowercase() != "https") return false
            
            // No userinfo allowed
            if (uri.userInfo != null) return false
            
            // No non-standard ports
            if (uri.port != -1 && uri.port != 443) return false
            
            val host = uri.host?.lowercase() ?: return false
            
            // Reject IP addresses
            if (isIpAddress(host)) return false
            
            // Check against trusted domains
            TRUSTED_DOMAINS.any { trusted ->
                host == trusted || host.endsWith(".$trusted")
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates URL is a login host (for auth state detection).
     */
    fun isLoginHost(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            LOGIN_HOSTS.any { loginHost ->
                host == loginHost || host.endsWith(".$loginHost")
            }
        } catch (e: Exception) {
            false
        }
    }
    
    // =========================================================================
    // Product ID Parsing
    // =========================================================================
    
    /**
     * Extract product ID from Xbox/Microsoft Store URL.
     * 
     * Supports:
     * - https://www.xbox.com/en-US/games/store/game-name/9NKX70BBCDRN
     * - https://www.microsoft.com/en-us/p/game-name/9NKX70BBCDRN
     * 
     * @return Product ID (12 chars) or null if not found/invalid
     */
    fun parseProductUrl(url: String): String? {
        // Security: Validate URL first
        if (!isValidMicrosoftHost(url)) return null
        
        // Try Xbox URL pattern
        PRODUCT_URL_REGEX.find(url)?.let { match ->
            return match.groupValues[2]
        }
        
        // Try Microsoft Store pattern
        MS_STORE_REGEX.find(url)?.let { match ->
            return match.groupValues[1]
        }
        
        // Fallback: Extract last 12-char segment from path
        return extractProductIdFromPath(url)
    }
    
    /**
     * Extract product ID from URL path segments.
     * Store IDs are 12 characters, digits and capitals.
     */
    private fun extractProductIdFromPath(url: String): String? {
        return try {
            val path = URI(url).path?.split("/")?.filter { it.isNotBlank() } ?: return null
            path.lastOrNull { PRODUCT_ID_REGEX.matches(it.uppercase()) }?.uppercase()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validate a product ID format.
     */
    fun isValidProductId(productId: String): Boolean {
        return PRODUCT_ID_REGEX.matches(productId.uppercase())
    }
    
    /**
     * Build product API URL for fetching price/availability.
     */
    fun getProductApiUrl(productId: String, market: String = "US"): String? {
        if (!isValidProductId(productId)) return null
        return "${URL_PRODUCT_API}${productId.uppercase()}?market=$market&locale=en-us&deviceFamily=Windows.Xbox"
    }
    
    /**
     * Build product page URL for display.
     */
    fun getProductPageUrl(productId: String): String? {
        if (!isValidProductId(productId)) return null
        return "$URL_PRODUCT_PAGE${productId.uppercase()}"
    }
    
    // =========================================================================
    // Price Analysis
    // =========================================================================
    
    /**
     * Determine if a product is free-to-keep (giveaway).
     * 
     * A giveaway is:
     * - MSRP > 0 (normally costs money)
     * - Current price = 0 (free right now)
     * - NOT "Included" (that's Game Pass)
     */
    fun isFreeToKeep(price: Double?, msrp: Double?, displayPrice: String?): Boolean {
        // "Included" is Game Pass, not a giveaway
        if (displayPrice?.trim()?.lowercase() == "included") return false
        
        val hasMsrp = msrp != null && msrp > 0
        val isFree = price != null && price == 0.0
        
        return hasMsrp && isFree
    }
    
    /**
     * Classify Microsoft offer type.
     */
    fun classifyOfferType(displayPrice: String?, price: Double?, msrp: Double?): MicrosoftOfferType {
        val display = displayPrice?.trim()?.lowercase() ?: ""
        
        return when {
            display == "included" -> MicrosoftOfferType.GAME_PASS
            display == "free" || (price == 0.0 && (msrp == null || msrp == 0.0)) -> MicrosoftOfferType.FREE_TO_PLAY
            isFreeToKeep(price, msrp, displayPrice) -> MicrosoftOfferType.FREE_TO_KEEP
            price != null && price > 0 -> MicrosoftOfferType.PAID
            else -> MicrosoftOfferType.UNKNOWN
        }
    }
    
    // =========================================================================
    // Page State Detection
    // =========================================================================
    
    /**
     * Classify page state from UI observations.
     * 
     * IMPORTANT: These are UI hints only, NOT proof of ownership.
     */
    fun classifyPageState(
        hasGetButton: Boolean,
        hasInstallButton: Boolean,
        hasPlayButton: Boolean,
        bodyText: String
    ): MicrosoftPageState {
        val text = bodyText.lowercase()
        
        return when {
            text.contains("you own this") || text.contains("in your library") -> MicrosoftPageState.OWNED
            hasInstallButton || hasPlayButton -> MicrosoftPageState.OWNED
            hasGetButton -> MicrosoftPageState.GET_AVAILABLE
            text.contains("might be temporarily down") || text.contains("page not found") -> MicrosoftPageState.ERROR
            else -> MicrosoftPageState.UNKNOWN
        }
    }
    
    // =========================================================================
    // Helpers
    // =========================================================================
    
    private fun isIpAddress(host: String): Boolean {
        // IPv4
        if (host.matches(Regex("""^\d+\.\d+\.\d+\.\d+$"""))) return true
        // IPv6
        if (host.startsWith("[") || host.contains(":")) return true
        return false
    }
}

/**
 * Microsoft Store offer types.
 */
enum class MicrosoftOfferType {
    /** Free-to-keep giveaway (MSRP > 0, price = 0) */
    FREE_TO_KEEP,
    /** Game Pass included title */
    GAME_PASS,
    /** Free-to-play game */
    FREE_TO_PLAY,
    /** Paid game */
    PAID,
    /** Cannot determine */
    UNKNOWN
}

/**
 * Microsoft Store page state observations.
 * 
 * IMPORTANT: These are UI observations, NOT proof of ownership.
 */
enum class MicrosoftPageState {
    /** Sign-in required */
    SIGN_IN_REQUIRED,
    /** "Get" button available */
    GET_AVAILABLE,
    /** Game owned (Install/Play visible) */
    OWNED,
    /** Purchase window open */
    PURCHASE_WINDOW,
    /** Transaction complete */
    COMPLETE,
    /** Error page */
    ERROR,
    /** Cannot determine */
    UNKNOWN
}
