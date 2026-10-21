package com.navos.gameclaimer.domain.repository

import com.navos.gameclaimer.domain.model.ClaimResult
import com.navos.gameclaimer.domain.model.FreeGame
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.StoreAuthState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for game store operations.
 * 
 * This defines the contract for interacting with game stores.
 * Implementation will be provided in Phase 5+ (Store Integration).
 * 
 * Security note: Implementations must:
 * - Never log credentials or session tokens
 * - Use secure storage for authentication state
 * - Validate all URLs before navigation
 */
interface GameStoreRepository {
    
    /**
     * Get the current free games available from a store.
     * This is a passive check - no authentication required.
     */
    suspend fun getFreeGames(store: GameStore): Result<List<FreeGame>>
    
    /**
     * Get the authentication state for a store.
     * Emits updates when auth state changes.
     */
    fun getAuthState(store: GameStore): Flow<StoreAuthState>
    
    /**
     * Check if the user is authenticated with a store.
     */
    suspend fun isAuthenticated(store: GameStore): Boolean
    
    /**
     * Attempt to claim a free game.
     * Requires prior authentication.
     */
    suspend fun claimGame(game: FreeGame): ClaimResult
    
    /**
     * Get the list of games the user has already claimed.
     */
    suspend fun getClaimedGames(store: GameStore): Result<List<FreeGame>>
}

/**
 * Repository for local app settings and preferences.
 */
interface SettingsRepository {
    
    /**
     * Get enabled stores for automatic claiming.
     */
    fun getEnabledStores(): Flow<Set<GameStore>>
    
    /**
     * Enable/disable a store for automatic claiming.
     */
    suspend fun setStoreEnabled(store: GameStore, enabled: Boolean)
    
    /**
     * Get auto-claim preference.
     */
    fun isAutoClaimEnabled(): Flow<Boolean>
    
    /**
     * Set auto-claim preference.
     */
    suspend fun setAutoClaimEnabled(enabled: Boolean)
    
    /**
     * Get notification preference.
     */
    fun areNotificationsEnabled(): Flow<Boolean>
    
    /**
     * Set notification preference.
     */
    suspend fun setNotificationsEnabled(enabled: Boolean)
}
