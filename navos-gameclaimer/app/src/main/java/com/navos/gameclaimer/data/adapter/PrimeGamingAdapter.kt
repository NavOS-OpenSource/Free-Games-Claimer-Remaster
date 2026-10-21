/**
 * Prime Gaming (Amazon Luna) Store Adapter
 *
 * PHASE 9: Discovery-only adapter for Prime Gaming offers.
 *
 * SCOPE:
 * - Discovery of available free games from luna.amazon.com/claims
 * - Manual user-controlled authentication via WebView
 * - Code extraction ONLY after explicit user action (not automatic)
 * - NO automated claiming or redemption
 *
 * SECURITY:
 * - supportsClaiming = false (disabled)
 * - HTTPS-only, domain validation for amazon.com/luna.amazon.com
 * - Codes are NOT automatically extracted or persisted
 * - Authentication is user-controlled (no passkey bypass, no MFA automation)
 * - All offer metadata and URLs treated as untrusted input
 *
 * LIMITATIONS:
 * - Undocumented page structure, may change without notice
 * - Requires Amazon Prime subscription for offers to be visible
 * - External codes (GOG, Legacy Games, Microsoft) require separate redemption
 */
package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterConfig
import com.navos.gameclaimer.domain.model.*
import java.net.URI

/**
 * Prime Gaming offer types based on how they are redeemed.
 */
enum class PrimeOfferType {
    /** Direct account-linked game (Epic, Amazon Games) - no code needed */
    ACCOUNT_LINKED,
    /** External code redemption (GOG, Legacy Games, Microsoft) */
    EXTERNAL_CODE,
    /** In-game content/DLC */
    IN_GAME_CONTENT,
    /** Unknown offer type */
    UNKNOWN
}

/**
 * External store where a Prime code can be redeemed.
 */
enum class PrimeCodeStore(val displayName: String, val redeemUrl: String?) {
    GOG("GOG.com", "https://www.gog.com/redeem"),
    LEGACY_GAMES("Legacy Games", null), // Per-game promo pages
    MICROSOFT("Microsoft Store", "https://account.microsoft.com/billing/redeem"),
    XBOX("Xbox", "https://account.microsoft.com/billing/redeem"),
    EPIC("Epic Games", null), // Account-linked, no code
    AMAZON("Amazon Games", null), // Account-linked, no code
    STEAM("Steam", "https://store.steampowered.com/account/registerkey"),
    BATTLE_NET("Battle.net", null),
    UBISOFT("Ubisoft Connect", null),
    EA_APP("EA App", null),
    UNKNOWN("Unknown", null);

    companion object {
        /**
         * Detect store from URL slug or page text.
         * URL patterns: /claims/game-name-PLATFORM/dp/
         */
        fun fromSlug(slug: String): PrimeCodeStore {
            val lowerSlug = slug.lowercase()
            return when {
                lowerSlug.endsWith("-epic") || "epic" in lowerSlug.split("-") -> EPIC
                lowerSlug.endsWith("-gog") || "gog" in lowerSlug.split("-") -> GOG
                lowerSlug.endsWith("-legacy") || "legacy" in lowerSlug.split("-") -> LEGACY_GAMES
                lowerSlug.endsWith("-aga") || "aga" in lowerSlug.split("-") -> AMAZON
                else -> UNKNOWN
            }
        }

        fun fromPageText(text: String): PrimeCodeStore {
            val lowerText = text.lowercase()
            return when {
                "epic games store" in lowerText || "epic games launcher" in lowerText -> EPIC
                "gog.com" in lowerText -> GOG
                "legacy games" in lowerText -> LEGACY_GAMES
                "microsoft store" in lowerText -> MICROSOFT
                "xbox" in lowerText -> XBOX
                "amazon games" in lowerText || "amazon luna" in lowerText -> AMAZON
                "steam" in lowerText -> STEAM
                "battle.net" in lowerText || "blizzard" in lowerText -> BATTLE_NET
                "ubisoft connect" in lowerText || "uplay" in lowerText -> UBISOFT
                "ea app" in lowerText || "origin" in lowerText -> EA_APP
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Page states observed during Prime Gaming navigation.
 * These are UI observations ONLY, not proof of ownership or claim success.
 */
enum class PrimePageState {
    /** Login required - "Sign in" button visible */
    SIGN_IN_REQUIRED,
    /** Logged in, viewing claims list */
    CLAIMS_LIST,
    /** Viewing individual offer detail */
    OFFER_DETAIL,
    /** Offer can be claimed - "Get game" / "Claim" button visible */
    CLAIMABLE,
    /** Offer already collected */
    COLLECTED,
    /** Security code / MFA required */
    SECURITY_CODE,
    /** Unknown state */
    UNKNOWN
}

class PrimeGamingAdapter(
    private val config: StoreAdapterConfig = StoreAdapterConfig()
) : StoreAdapter {

    override val store: GameStore = GameStore.AMAZON_PRIME
    override val displayName: String = "Prime Gaming"
    override val supportsDiscovery: Boolean = true
    override val supportsClaiming: Boolean = false  // Disabled - requires external redemption
    override val requiresAuth: Boolean = true

    companion object {
        // Prime Gaming URLs
        const val URL_BASE = "https://luna.amazon.com"
        const val URL_CLAIMS = "https://luna.amazon.com/claims/home"
        const val URL_LOGIN = "https://www.amazon.com/ap/signin"

        // Trusted domains for Prime Gaming
        val TRUSTED_DOMAINS = setOf(
            "luna.amazon.com",
            "amazon.com",
            "www.amazon.com"
        )

        // Trusted redemption destination domains
        val TRUSTED_REDEEM_DOMAINS = setOf(
            "gog.com",
            "www.gog.com",
            "legacygames.com",
            "promo.legacygames.com",
            "account.microsoft.com",
            "store.steampowered.com"
        )

        // Code pattern: typical redemption codes are 12-30 uppercase alphanumeric with dashes
        private val CODE_PATTERN = Regex("^[A-Z0-9-]{12,30}$")
    }

    // ===== Discovery Operations =====

    /**
     * Discover free giveaways from Prime Gaming.
     * 
     * In Phase 9, discovery is delegated to GamerPower API client.
     * Direct Prime discovery would involve parsing luna.amazon.com/claims.
     * 
     * NOTE: This is READ-ONLY - never triggers claims or changes ownership.
     */
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult {
        return DiscoveryResult.Empty(
            source = DiscoverySource.STORE_DIRECT,
            durationMs = 0
        )
    }

    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? {
        // Verification would check if offer is still available on Prime Gaming
        return giveaway
    }

    // ===== Eligibility Operations =====

    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility {
        // Prime eligibility requires:
        // 1. Amazon Prime subscription
        // 2. Signed in to Amazon
        // 3. Offer not already collected
        return ClaimEligibility.REQUIRES_AUTH
    }

    // ===== Claim Operations (DISABLED) =====

    /**
     * Claiming is DISABLED for Prime Gaming.
     *
     * Reasons:
     * 1. Prime Gaming requires complex Amazon authentication (passkeys, MFA, security codes)
     * 2. External codes require redemption on separate stores (GOG, Legacy Games, Microsoft)
     * 3. Account-linked games (Epic, Amazon) require OAuth flows
     * 4. Security risk: automated claiming could trigger account locks
     */
    override suspend fun claim(giveaway: Giveaway): ClaimResult {
        return ClaimResult.Failed(
            giveawayId = giveaway.id,
            reason = "Prime Gaming claiming is disabled. Prime offers require Amazon " +
                "authentication and may need external redemption on GOG, Legacy Games, " +
                "or Microsoft Store.",
            isRetryable = false
        )
    }

    // ===== Authentication =====

    override fun getAuthUrl(): String = URL_LOGIN

    override suspend fun isAuthenticated(): Boolean {
        // Would check for valid Amazon session cookies
        return false
    }

    // ===== URL Validation =====

    /**
     * Validate if a URL is safe for Prime Gaming navigation.
     * Rejects lookalike domains, IP literals, userinfo, unexpected ports, non-HTTPS.
     */
    fun isValidPrimeHost(url: String): Boolean {
        return try {
            val uri = URI(url)

            // Must be HTTPS
            if (uri.scheme?.lowercase() != "https") return false

            // No userinfo (user:pass@host)
            if (uri.userInfo != null) return false

            val host = uri.host?.lowercase() ?: return false

            // Reject IP addresses (v4 and v6)
            if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return false
            if (host.startsWith("[") || host.contains("::")) return false

            // No unexpected ports (only default 443)
            if (uri.port != -1 && uri.port != 443) return false

            // Check against trusted domains (exact match or subdomain)
            TRUSTED_DOMAINS.any { trusted ->
                host == trusted || host.endsWith(".$trusted")
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate if a redemption URL is safe for navigation.
     * Only permits explicitly reviewed HTTPS destination hosts.
     */
    fun isValidRedeemHost(url: String): Boolean {
        return try {
            val uri = URI(url)

            // Must be HTTPS
            if (uri.scheme?.lowercase() != "https") return false

            // No userinfo
            if (uri.userInfo != null) return false

            val host = uri.host?.lowercase() ?: return false

            // Reject IP addresses
            if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return false
            if (host.startsWith("[") || host.contains("::")) return false

            // No unexpected ports
            if (uri.port != -1 && uri.port != 443) return false

            // Check against trusted redemption domains
            TRUSTED_REDEEM_DOMAINS.any { trusted ->
                host == trusted || host.endsWith(".$trusted")
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parse a Prime Gaming offer URL to extract the game slug.
     * URL pattern: /claims/{game-slug}/dp/{offer-id}
     *
     * @return Pair of (slug, offerId) or null if not a valid offer URL
     */
    fun parseOfferUrl(url: String): Pair<String, String>? {
        if (!isValidPrimeHost(url)) return null

        return try {
            val uri = URI(url)
            val path = uri.path ?: return null

            // Pattern: /claims/{slug}/dp/{id}
            val regex = Regex("/claims/([^/]+)/dp/([^/]+)")
            val match = regex.find(path)

            if (match != null) {
                val slug = match.groupValues[1]
                val offerId = match.groupValues[2]
                Pair(slug, offerId)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Classify the offer type based on the detected store.
     */
    fun classifyOfferType(store: PrimeCodeStore): PrimeOfferType {
        return when (store) {
            PrimeCodeStore.EPIC, PrimeCodeStore.AMAZON -> PrimeOfferType.ACCOUNT_LINKED
            PrimeCodeStore.GOG, PrimeCodeStore.LEGACY_GAMES, PrimeCodeStore.MICROSOFT,
            PrimeCodeStore.XBOX, PrimeCodeStore.STEAM -> PrimeOfferType.EXTERNAL_CODE
            PrimeCodeStore.BATTLE_NET, PrimeCodeStore.UBISOFT, PrimeCodeStore.EA_APP -> PrimeOfferType.ACCOUNT_LINKED
            PrimeCodeStore.UNKNOWN -> PrimeOfferType.UNKNOWN
        }
    }

    /**
     * Check if a string looks like a valid redemption code.
     * Does NOT extract codes - this is for validation only.
     */
    fun isValidCodeFormat(code: String): Boolean {
        return CODE_PATTERN.matches(code.trim())
    }

    /**
     * Get the redemption URL for a store, if available.
     * Returns null for account-linked stores that don't use codes.
     */
    fun getRedeemUrl(store: PrimeCodeStore): String? {
        return store.redeemUrl
    }

    /**
     * Check if a store requires external code redemption.
     */
    fun requiresExternalRedemption(store: PrimeCodeStore): Boolean {
        return store.redeemUrl != null
    }
}
