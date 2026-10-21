package com.navos.gameclaimer.domain.adapter

import com.navos.gameclaimer.domain.model.ClaimEligibility
import com.navos.gameclaimer.domain.model.ClaimResult
import com.navos.gameclaimer.domain.model.DiscoveryResult
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.Giveaway
import kotlinx.coroutines.flow.Flow

/**
 * Phase 6: Store Adapter Contract
 * 
 * Defines the interface that each game store integration must implement.
 * 
 * DESIGN PRINCIPLES:
 * 1. Discovery is SEPARATE from claiming - different methods, different flows
 * 2. All operations are suspendable and cancellable
 * 3. Adapters are stateless - auth state comes from AuthRepository
 * 4. Results are strongly typed - no magic strings or error codes
 * 5. Adapters validate all inputs and outputs
 * 
 * SECURITY:
 * - Adapters MUST NOT log credentials, cookies, or session tokens
 * - All URLs MUST be validated before navigation
 * - Claims MUST be explicit user-initiated actions, never automatic
 * - External data is untrusted until validated
 */
interface StoreAdapter {
    
    /**
     * The game store this adapter handles.
     */
    val store: GameStore
    
    /**
     * Human-readable name for display.
     */
    val displayName: String
    
    /**
     * Whether this adapter supports giveaway discovery.
     */
    val supportsDiscovery: Boolean
    
    /**
     * Whether this adapter supports claiming (requires WebView integration).
     */
    val supportsClaiming: Boolean
    
    /**
     * Whether authentication is required for this store.
     */
    val requiresAuth: Boolean
    
    // ===== Discovery Operations (READ-ONLY) =====
    
    /**
     * Discover available giveaways from this store.
     * 
     * This is a READ-ONLY operation that:
     * - Fetches available giveaways from APIs or web scraping
     * - Validates and normalizes the data
     * - Returns discovered giveaways
     * 
     * This DOES NOT:
     * - Claim any offers
     * - Modify library state
     * - Require authentication (unless the store mandates it)
     * 
     * @param timeoutMs Maximum time for discovery operation
     * @return DiscoveryResult with found giveaways or error details
     */
    suspend fun discoverGiveaways(timeoutMs: Long = 30_000L): DiscoveryResult
    
    /**
     * Check if a specific giveaway is still available.
     * 
     * @param giveaway The giveaway to verify
     * @return Updated giveaway info, or null if no longer available
     */
    suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway?
    
    // ===== Eligibility Operations (READ-ONLY) =====
    
    /**
     * Check if the current user is eligible to claim a giveaway.
     * 
     * This is a READ-ONLY operation that checks:
     * - Authentication status
     * - Ownership status (already owned?)
     * - Region eligibility
     * - Offer expiration
     * 
     * @param giveaway The giveaway to check eligibility for
     * @return Eligibility status
     */
    suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility
    
    // ===== Claim Operations (WRITE, USER-INITIATED) =====
    
    /**
     * Claim a giveaway.
     * 
     * This is a WRITE operation that:
     * - Requires explicit user initiation
     * - Requires authentication
     * - Adds the game/content to user's library
     * 
     * SECURITY: This method MUST NOT be called automatically.
     * The user must explicitly request the claim.
     * 
     * @param giveaway The giveaway to claim
     * @return Result of the claim attempt
     */
    suspend fun claim(giveaway: Giveaway): ClaimResult
    
    // ===== Authentication Requirements =====
    
    /**
     * Get the authentication URL for this store.
     * 
     * @return URL to navigate to for authentication
     */
    fun getAuthUrl(): String
    
    /**
     * Check if the user is currently authenticated with this store.
     * 
     * @return true if authenticated, false otherwise
     */
    suspend fun isAuthenticated(): Boolean
}

/**
 * Configuration for a store adapter.
 */
data class StoreAdapterConfig(
    /** Whether to enable this adapter */
    val enabled: Boolean = true,
    
    /** Timeout for discovery operations (ms) */
    val discoveryTimeoutMs: Long = 30_000L,
    
    /** Timeout for claim operations (ms) */
    val claimTimeoutMs: Long = 60_000L,
    
    /** Maximum retries on transient failures */
    val maxRetries: Int = 3,
    
    /** Delay between retries (ms) */
    val retryDelayMs: Long = 1_000L
)

/**
 * Factory for creating store adapters.
 * 
 * This allows for dependency injection and testing with fake adapters.
 */
interface StoreAdapterFactory {
    /**
     * Get an adapter for the specified store.
     * 
     * @param store The game store
     * @return StoreAdapter instance, or null if not supported
     */
    fun getAdapter(store: GameStore): StoreAdapter?
    
    /**
     * Get all available adapters.
     * 
     * @return List of all registered adapters
     */
    fun getAllAdapters(): List<StoreAdapter>
    
    /**
     * Get adapters that support discovery.
     */
    fun getDiscoveryAdapters(): List<StoreAdapter> = 
        getAllAdapters().filter { it.supportsDiscovery }
    
    /**
     * Get adapters that support claiming.
     */
    fun getClaimAdapters(): List<StoreAdapter> = 
        getAllAdapters().filter { it.supportsClaiming }
}
