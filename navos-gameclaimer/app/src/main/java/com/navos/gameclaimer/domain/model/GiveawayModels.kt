package com.navos.gameclaimer.domain.model

import kotlinx.serialization.Serializable

/**
 * Phase 6: Giveaway Domain Models
 * 
 * Normalized models for discovered giveaways across all stores.
 * 
 * DESIGN PRINCIPLES:
 * 1. Discovery is separate from claiming - discovering an offer does NOT acquire it
 * 2. Preserve distinction between offer types (free-to-keep, trials, demos, etc.)
 * 3. All external data is untrusted - validate before use
 * 4. Source identity preserved - never lose provenance
 */

/**
 * Type of giveaway/offer discovered.
 * 
 * IMPORTANT: Only FREE_TO_KEEP grants permanent ownership after claim.
 * All other types are temporary or limited access.
 */
enum class GiveawayType {
    /** Permanently free once claimed - player keeps the game forever */
    FREE_TO_KEEP,
    
    /** Free for a limited time window (e.g., free weekend), reverts after */
    FREE_WEEKEND,
    
    /** Demo/trial version - limited content or time */
    DEMO_OR_TRIAL,
    
    /** Always free game (F2P), not a limited-time giveaway */
    FREE_TO_PLAY,
    
    /** DLC, in-game items, or other bonus content */
    DLC_OR_ADDON,
    
    /** Subscription benefit (e.g., Prime Gaming) */
    SUBSCRIPTION_PERK,
    
    /** Unknown type - needs manual verification */
    UNKNOWN
}

/**
 * Eligibility status for claiming a giveaway.
 */
enum class ClaimEligibility {
    /** User is eligible to claim this offer */
    ELIGIBLE,
    
    /** User already owns this game/content */
    ALREADY_OWNED,
    
    /** Offer requires authentication first */
    REQUIRES_AUTH,
    
    /** User's region is not eligible */
    REGION_LOCKED,
    
    /** Offer has expired */
    EXPIRED,
    
    /** Eligibility unknown - needs verification */
    UNKNOWN
}

/**
 * Result of a claim attempt.
 */
sealed class ClaimResult {
    /** Claim succeeded - game/content added to library */
    data class Success(
        val giveawayId: String,
        val claimedAt: Long = System.currentTimeMillis()
    ) : ClaimResult()
    
    /** Already owned - no action needed */
    data class AlreadyOwned(val giveawayId: String) : ClaimResult()
    
    /** Claim failed with a specific reason */
    data class Failed(
        val giveawayId: String,
        val reason: String,
        val isRetryable: Boolean = false
    ) : ClaimResult()
    
    /** Requires user interaction (CAPTCHA, 2FA, etc.) */
    data class RequiresInteraction(
        val giveawayId: String,
        val interactionType: String
    ) : ClaimResult()
}

/**
 * A discovered giveaway/offer.
 * 
 * SECURITY: All fields from external sources are treated as untrusted.
 * URLs are validated before navigation. No automatic claims.
 */
@Serializable
data class Giveaway(
    /** Unique identifier (format: source:store:external_id) */
    val id: String,
    
    /** Display title of the game/content */
    val title: String,
    
    /** Store where the giveaway is available */
    val store: GameStore,
    
    /** Type of offer */
    val type: GiveawayType,
    
    /** Canonical URL to claim page (HTTPS only, validated) */
    val claimUrl: String,
    
    /** Original price when not on giveaway (for display) */
    val originalPrice: String? = null,
    
    /** URL to game/offer image */
    val imageUrl: String? = null,
    
    /** Brief description */
    val description: String? = null,
    
    /** When the giveaway started (epoch millis) */
    val startTime: Long? = null,
    
    /** When the giveaway ends (epoch millis) */
    val endTime: Long? = null,
    
    /** Source that discovered this giveaway */
    val source: DiscoverySource,
    
    /** When this entry was last checked/refreshed */
    val lastCheckedAt: Long = System.currentTimeMillis(),
    
    /** External ID from the source (for deduplication) */
    val externalId: String? = null
) {
    /**
     * Check if the giveaway is still within its availability window.
     */
    fun isAvailable(now: Long = System.currentTimeMillis()): Boolean {
        val afterStart = startTime?.let { now >= it } ?: true
        val beforeEnd = endTime?.let { now < it } ?: true
        return afterStart && beforeEnd
    }
    
    /**
     * Check if this is a permanent free-to-keep offer.
     */
    fun isPermanentlyFree(): Boolean = type == GiveawayType.FREE_TO_KEEP
}

/**
 * Source of giveaway discovery.
 */
@Serializable
enum class DiscoverySource {
    /** GamerPower API */
    GAMER_POWER,
    
    /** Direct store scraping/API */
    STORE_DIRECT,
    
    /** Manual user entry */
    USER_ADDED,
    
    /** Unknown/other source */
    OTHER
}

/**
 * Discovery result from a single source.
 */
sealed class DiscoveryResult {
    /** Successful discovery with giveaways found */
    data class Success(
        val giveaways: List<Giveaway>,
        val source: DiscoverySource,
        val durationMs: Long
    ) : DiscoveryResult()
    
    /** Discovery completed but no giveaways found */
    data class Empty(
        val source: DiscoverySource,
        val durationMs: Long
    ) : DiscoveryResult()
    
    /** Discovery failed */
    data class Failed(
        val source: DiscoverySource,
        val error: String,
        val isRetryable: Boolean = true
    ) : DiscoveryResult()
    
    /** Discovery was cancelled */
    data class Cancelled(
        val source: DiscoverySource
    ) : DiscoveryResult()
}

/**
 * Aggregated discovery status across all sources.
 */
data class DiscoveryStatus(
    val isRunning: Boolean,
    val lastRunAt: Long?,
    val successfulSources: List<DiscoverySource>,
    val failedSources: List<Pair<DiscoverySource, String>>,
    val totalGiveawaysFound: Int
)
