package com.navos.gameclaimer.domain.repository

import com.navos.gameclaimer.domain.model.AuthResult
import com.navos.gameclaimer.domain.model.AuthSession
import com.navos.gameclaimer.domain.model.AuthSessionState
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.StoreAccount
import kotlinx.coroutines.flow.Flow

/**
 * Phase 5: Authentication Repository Interface
 * 
 * Defines the contract for managing store authentication state.
 * 
 * SECURITY REQUIREMENTS:
 * - Session tokens never exposed in return values
 * - All sensitive operations logged without credentials
 * - Sessions verified server-side before sensitive operations
 * - Account data sanitized before display
 */
interface AuthRepository {
    
    /**
     * Get the current authentication state for a store.
     * Emits updates when state changes.
     */
    fun getAuthState(store: GameStore): Flow<AuthSessionState>
    
    /**
     * Get the current session for a store (if authenticated).
     * Returns null if no session exists.
     */
    suspend fun getCurrentSession(store: GameStore): AuthSession?
    
    /**
     * Get the account associated with the current session.
     * Returns null if not authenticated.
     */
    suspend fun getCurrentAccount(store: GameStore): StoreAccount?
    
    /**
     * Get all known accounts for a store.
     * These may or may not have active sessions.
     */
    suspend fun getAccounts(store: GameStore): List<StoreAccount>
    
    /**
     * Check if the user has an active, valid session for a store.
     * This performs a lightweight check - may not verify with server.
     */
    suspend fun hasValidSession(store: GameStore): Boolean
    
    /**
     * Verify the current session is still valid by checking with the store.
     * Updates the session state based on server response.
     */
    suspend fun verifySession(store: GameStore): Boolean
    
    /**
     * Start an authentication flow for a store.
     * Returns immediately - actual auth happens in browser UI.
     */
    suspend fun startAuthentication(store: GameStore)
    
    /**
     * Called when browser-based authentication completes.
     * This should be called by the browser UI when auth is detected.
     */
    suspend fun onAuthenticationComplete(
        store: GameStore,
        account: StoreAccount
    ): AuthResult
    
    /**
     * Called when authentication fails.
     */
    suspend fun onAuthenticationFailed(
        store: GameStore,
        result: AuthResult.Failed
    )
    
    /**
     * Log out from a store.
     * Clears session data and cookies.
     */
    suspend fun logout(store: GameStore)
    
    /**
     * Log out from all stores.
     */
    suspend fun logoutAll()
    
    /**
     * Remove a stored account (but not its session data).
     * For switching between accounts.
     */
    suspend fun removeAccount(accountId: String)
    
    /**
     * Clear all stored data for a store.
     * This is a destructive operation - use with caution.
     */
    suspend fun clearStoreData(store: GameStore)
}
