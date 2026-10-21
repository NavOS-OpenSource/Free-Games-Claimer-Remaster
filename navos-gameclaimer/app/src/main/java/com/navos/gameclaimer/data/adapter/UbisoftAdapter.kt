package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*
import java.net.URI

/**
 * Phase 10: Ubisoft Store Adapter
 * 
 * CAPABILITIES:
 * - Discovery: Via free games page state JSON (window.__PRELOADED_STATE__)
 * - Giveaway Detection: Type=gametrial/giveaway + register.ubisoft.com link
 * - Authentication: WebView login at connect.ubisoft.com
 * 
 * LIMITATIONS:
 * - Claiming requires desktop browser automation (cross-origin Ubisoft Connect overlay)
 * - Giveaways share "gametrial" type with actual trials - need additional filtering
 * - DataDome protection may block automated access
 * 
 * SECURITY:
 * - Auth via WebView only (no credential storage)
 * - All URLs validated against trusted Ubisoft domains
 * - Claiming never automated - user must initiate
 * - TOTP/MFA remain user-controlled
 * - Filters out trials/demos/betas by name patterns
 * 
 * GIVEAWAY DETECTION:
 * - placement = "freeevents"
 * - type = "gametrial" or "giveaway"
 * - Has register.ubisoft.com claim link
 * - NOT named like: trial, demo, beta, free week, early access, playtest
 * - End date within 90 days (real promo, not permanent trial)
 * 
 * UPSTREAM REFERENCE: src/stores/ubisoft.py (550+ lines)
 */
class UbisoftAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {
    
    override val store: GameStore = GameStore.UBISOFT
    override val displayName: String = "Ubisoft"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Requires desktop browser automation
    override val requiresAuth: Boolean = true
    
    companion object {
        // Ubisoft URLs
        const val URL_FREE_GAMES = "https://www.ubisoft.com/en-us/games/free"
        const val URL_ACCOUNT = "https://account.ubisoft.com/en-US/account-information"
        const val URL_LOGIN = "https://connect.ubisoft.com/"
        
        // Trusted Ubisoft domains
        val TRUSTED_DOMAINS = setOf(
            "ubisoft.com",
            "www.ubisoft.com",
            "register.ubisoft.com",
            "connect.ubisoft.com",
            "account.ubisoft.com"
        )
        
        // Claim host - giveaways link here
        const val CLAIM_HOST = "register.ubisoft.com"
        
        // Login host - for auth state detection
        const val LOGIN_HOST = "connect.ubisoft.com"
        
        // Page state marker in HTML
        const val STATE_MARKER = "window.__PRELOADED_STATE__ = "
        
        // Valid giveaway placement in feed
        const val FEED_PLACEMENT = "freeevents"
        
        // Giveaway types in Ubisoft feed
        val GIVEAWAY_TYPES = setOf("gametrial", "giveaway")
        
        // Skip tokens - these are NOT giveaways
        val SKIP_TOKENS = setOf(
            "trial", "demo", "beta", "free week", "free weekend",
            "early access", "test server", "playtest"
        )
        
        // Max window days - real giveaways run for days, not forever
        const val MAX_WINDOW_DAYS = 90
        
        // Claim URL pattern
        val CLAIM_URL_REGEX = Regex("""https://register\.ubisoft\.com/([a-z0-9-]+)""")
    }
    
    /**
     * Discover free giveaways from Ubisoft.
     * 
     * Discovery delegated to GamerPower API in current phase.
     * Direct discovery would involve:
     * - Fetching URL_FREE_GAMES page
     * - Extracting window.__PRELOADED_STATE__ JSON
     * - Filtering entries with placement=freeevents, type=gametrial/giveaway
     * - Excluding trials by name pattern
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
     * Would check:
     * - Claim URL still accessible
     * - Page says "GET YOUR FREE GAME!" (giveaway) not "TRY NOW FOR FREE!" (trial)
     * - Offer hasn't expired
     */
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        return giveaway // Pass-through for now
    }
    
    /**
     * Check if user can claim this giveaway.
     * Returns REQUIRES_AUTH as Ubisoft requires login.
     */
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        return ClaimEligibility.REQUIRES_AUTH
    }
    
    /**
     * Claim a giveaway.
     * 
     * DISABLED - Ubisoft claiming requires:
     * - Desktop browser with headful mode
     * - Cross-origin Ubisoft Connect overlay handling
     * - User interaction for account confirmation
     * - DataDome challenge handling
     * - TOTP/MFA support
     * 
     * Not feasible on Android without full browser automation.
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Ubisoft claiming disabled - requires desktop browser automation",
            isRetryable = false
        )
    }
    
    /**
     * Get the Ubisoft Connect login URL.
     */
    override fun getAuthUrl(): String = URL_LOGIN
    
    /**
     * Check if user is authenticated.
     * Would check if we're on account.ubisoft.com vs connect.ubisoft.com login.
     */
    override suspend fun isAuthenticated(): Boolean {
        return false
    }
    
    // =========================================================================
    // URL Validation
    // =========================================================================
    
    /**
     * Validates URL is from a trusted Ubisoft domain.
     * 
     * Protects against:
     * - Suffix attacks: fakeubisoft.com
     * - Prefix attacks: ubisoft.com.attacker.com
     * - IP addresses (v4 and v6)
     * - Userinfo injection
     * - Non-HTTPS schemes
     * - Unexpected ports
     */
    fun isValidUbisoftHost(url: String): Boolean {
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
     * Validates URL is a claim URL (register.ubisoft.com).
     */
    fun isValidClaimHost(url: String): Boolean {
        return try {
            val uri = URI(url)
            if (uri.scheme?.lowercase() != "https") return false
            if (uri.userInfo != null) return false
            if (uri.port != -1 && uri.port != 443) return false
            
            val host = uri.host?.lowercase() ?: return false
            host == CLAIM_HOST
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
            host == LOGIN_HOST || host.endsWith(".$LOGIN_HOST")
        } catch (e: Exception) {
            false
        }
    }
    
    // =========================================================================
    // Giveaway Detection
    // =========================================================================
    
    /**
     * Extract giveaway slug from claim URL.
     * 
     * Example: https://register.ubisoft.com/ghost-recon-future-soldier -> ghost-recon-future-soldier
     */
    fun parseClaimUrl(url: String): String? {
        if (!isValidClaimHost(url)) return null
        
        return CLAIM_URL_REGEX.find(url)?.groupValues?.getOrNull(1)
    }
    
    /**
     * Check if a feed entry looks like a real giveaway (not a trial).
     * 
     * Rejects entries named like trials/demos/betas.
     */
    fun looksLikeGiveaway(title: String, slug: String?): Boolean {
        val haystack = "$title ${slug ?: ""}".lowercase()
        return SKIP_TOKENS.none { token -> haystack.contains(token) }
    }
    
    /**
     * Validate giveaway time window.
     * 
     * Real giveaways run for days/weeks, not forever.
     * Trials often have sentinel dates decades out.
     * 
     * @param startMs Start timestamp in milliseconds (null = already started)
     * @param endMs End timestamp in milliseconds
     * @param nowMs Current time in milliseconds
     * @return null if valid, rejection reason if not
     */
    fun validateTimeWindow(startMs: Long?, endMs: Long?, nowMs: Long): String? {
        // Check start date
        if (startMs != null && startMs > nowMs) {
            return "starts in future"
        }
        
        // Check end date
        if (endMs == null) {
            return "no end date"
        }
        if (endMs <= nowMs) {
            return "already ended"
        }
        
        // Check window duration
        val daysRemaining = (endMs - nowMs) / (1000 * 60 * 60 * 24)
        if (daysRemaining > MAX_WINDOW_DAYS) {
            return "end date too far out (likely permanent trial)"
        }
        
        return null // Valid
    }
    
    /**
     * Check if entry has valid giveaway type.
     */
    fun isGiveawayType(type: String?): Boolean {
        return type?.lowercase()?.trim() in GIVEAWAY_TYPES
    }
    
    /**
     * Check if entry has valid placement.
     */
    fun isFreePlacement(placement: String?): Boolean {
        return placement == FEED_PLACEMENT
    }
    
    // =========================================================================
    // Page State Detection
    // =========================================================================
    
    /**
     * Classify claim page state from content.
     * 
     * IMPORTANT: These are UI observations, NOT proof of ownership.
     */
    fun classifyPageState(
        pageTitle: String,
        headings: List<String>,
        bodyText: String
    ): UbisoftPageState {
        val title = pageTitle.lowercase().trim()
        val headingsText = headings.joinToString(" ").lowercase()
        val body = bodyText.lowercase()
        
        return when {
            // Giveaway page
            title == "giveaway" || headingsText.contains("get your free game") -> UbisoftPageState.GIVEAWAY
            
            // Trial page (not a giveaway)
            headingsText.contains("try now") || headingsText.contains("free trial") -> UbisoftPageState.TRIAL
            
            // Already owned
            body.contains("already own") || body.contains("already in your") ||
            body.contains("already have this") || body.contains("you own this") -> UbisoftPageState.OWNED
            
            // Claimed successfully
            headingsText.contains("have fun") || body.contains("has been successfully added") ||
            body.contains("has been added to your") || body.contains("is now yours") -> UbisoftPageState.CLAIMED
            
            // DataDome/security block
            body.contains("checking your browser") || body.contains("please wait") -> UbisoftPageState.BLOCKED
            
            else -> UbisoftPageState.UNKNOWN
        }
    }
    
    /**
     * Check if page shows giveaway is already owned.
     */
    fun isAlreadyOwned(bodyText: String): Boolean {
        val text = bodyText.lowercase()
        return text.contains("already own") ||
               text.contains("already in your") ||
               text.contains("already have this") ||
               text.contains("you own this")
    }
    
    /**
     * Check if page confirms claim success.
     */
    fun isClaimConfirmed(headings: List<String>, bodyText: String): Boolean {
        val headingsText = headings.joinToString(" ").lowercase()
        val text = bodyText.lowercase()
        
        if (headingsText.contains("have fun")) return true
        
        return text.contains("has been successfully added") ||
               text.contains("has been added to your") ||
               text.contains("added to your ubisoft") ||
               text.contains("is now yours") ||
               text.contains("you now own")
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
 * Ubisoft page state observations.
 * 
 * IMPORTANT: These are UI observations, NOT proof of ownership.
 */
enum class UbisoftPageState {
    /** Sign-in required */
    SIGN_IN_REQUIRED,
    /** Giveaway page ("GET YOUR FREE GAME!") */
    GIVEAWAY,
    /** Trial page ("TRY NOW FOR FREE!") - not a giveaway */
    TRIAL,
    /** Game already owned */
    OWNED,
    /** Claim successful ("Have fun!") */
    CLAIMED,
    /** DataDome or security challenge */
    BLOCKED,
    /** Cannot determine */
    UNKNOWN
}
