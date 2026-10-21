package com.navos.gameclaimer.data.repository

import android.webkit.CookieManager
import com.navos.gameclaimer.data.local.dao.StoreSessionDao
import com.navos.gameclaimer.data.local.entity.StoreSessionEntity
import com.navos.gameclaimer.domain.model.AuthResult
import com.navos.gameclaimer.domain.model.AuthSession
import com.navos.gameclaimer.domain.model.AuthSessionState
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.StoreAccount
import com.navos.gameclaimer.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Phase 5: Authentication Repository Implementation
 * 
 * Manages authentication state and session persistence.
 * 
 * SECURITY ARCHITECTURE:
 * 
 * 1. WebView Cookies (System-Managed):
 *    - Stored by Android's CookieManager in WebView database
 *    - Protected by app sandbox, NOT encrypted at rest
 *    - This is a platform limitation we cannot change
 *    - Clear on logout via CookieManager.removeAllCookies()
 * 
 * 2. Session Metadata (App-Managed):
 *    - Account info, session state stored in Room database
 *    - NO actual session tokens stored in Room
 *    - Session validity determined by cookie presence + server check
 * 
 * 3. Future Enhancement (Phase 5+):
 *    - For APIs that return explicit tokens (not cookie-based),
 *      encrypt tokens using Android Keystore before Room storage
 * 
 * WHAT THIS DOES NOT DO:
 * - Does NOT encrypt WebView cookies (platform limitation)
 * - Does NOT store raw session tokens
 * - Does NOT log any credential information
 */
class AuthRepositoryImpl(
    private val sessionDao: StoreSessionDao
) : AuthRepository {
    
    // In-memory state flows for each store
    private val authStates = mutableMapOf<GameStore, MutableStateFlow<AuthSessionState>>()
    
    // In-memory session cache (not persisted with tokens)
    private val sessions = mutableMapOf<GameStore, AuthSession>()
    
    // In-memory account cache
    private val accounts = mutableMapOf<GameStore, MutableList<StoreAccount>>()
    
    init {
        // Initialize state flows for all stores
        GameStore.entries.forEach { store ->
            authStates[store] = MutableStateFlow(AuthSessionState.NONE)
        }
    }
    
    override fun getAuthState(store: GameStore): Flow<AuthSessionState> {
        return authStates.getOrPut(store) { 
            MutableStateFlow(AuthSessionState.NONE) 
        }
    }
    
    override suspend fun getCurrentSession(store: GameStore): AuthSession? {
        // First check in-memory cache
        sessions[store]?.let { return it }
        
        // Then check persisted session metadata
        val entity = sessionDao.getSession(store.name)
        if (entity != null && !entity.isSecret) {
            // Reconstruct session from metadata
            val session = AuthSession(
                accountId = entity.id.toString(),
                store = store,
                state = if (entity.isAuthenticated) AuthSessionState.AUTHENTICATED else AuthSessionState.NONE,
                lastVerifiedAt = entity.lastVerified,
                expiresAt = null, // Unknown - we rely on cookie expiry
                requiresMfa = false
            )
            sessions[store] = session
            return session
        }
        
        return null
    }
    
    override suspend fun getCurrentAccount(store: GameStore): StoreAccount? {
        val session = getCurrentSession(store) ?: return null
        return accounts[store]?.find { it.id == session.accountId }
    }
    
    override suspend fun getAccounts(store: GameStore): List<StoreAccount> {
        return accounts[store]?.toList() ?: emptyList()
    }
    
    override suspend fun hasValidSession(store: GameStore): Boolean {
        val session = getCurrentSession(store) ?: return false
        if (!session.isApparentlyValid()) return false
        
        // Check if cookies still exist
        return hasCookiesForStore(store)
    }
    
    override suspend fun verifySession(store: GameStore): Boolean {
        // For now, we just check cookie presence
        // Full verification would require making a request to the store
        val hasSession = hasValidSession(store)
        
        authStates[store]?.value = if (hasSession) {
            AuthSessionState.AUTHENTICATED
        } else {
            AuthSessionState.EXPIRED
        }
        
        return hasSession
    }
    
    override suspend fun startAuthentication(store: GameStore) {
        authStates[store]?.value = AuthSessionState.AUTHENTICATING
    }
    
    override suspend fun onAuthenticationComplete(
        store: GameStore,
        account: StoreAccount
    ): AuthResult {
        // Update in-memory state
        val session = AuthSession(
            accountId = account.id,
            store = store,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis(),
            expiresAt = null
        )
        
        sessions[store] = session
        accounts.getOrPut(store) { mutableListOf() }.let { list ->
            // Remove existing account with same ID, add new
            list.removeAll { it.id == account.id }
            list.add(account)
        }
        
        // Persist session metadata (NOT tokens)
        val entity = StoreSessionEntity(
            id = 0, // Auto-generate
            store = store.name,
            displayName = account.displayName,
            lastVerified = System.currentTimeMillis(),
            isAuthenticated = true,
            sessionTokenEncrypted = null, // We don't store tokens
            isSecret = false
        )
        sessionDao.upsert(entity)
        
        // Update state flow
        authStates[store]?.value = AuthSessionState.AUTHENTICATED
        
        return AuthResult.Success(account, session)
    }
    
    override suspend fun onAuthenticationFailed(
        store: GameStore,
        result: AuthResult.Failed
    ) {
        sessions.remove(store)
        authStates[store]?.value = AuthSessionState.FAILED
    }
    
    override suspend fun logout(store: GameStore) {
        // Clear in-memory state
        sessions.remove(store)
        authStates[store]?.value = AuthSessionState.LOGGED_OUT
        
        // Clear persisted session
        sessionDao.delete(store.name)
        
        // Clear cookies for this store's domain
        // Note: CookieManager doesn't support per-domain clearing easily
        // We clear all and require re-authentication for other stores too
        // This is a trade-off for security
        clearAllCookies()
    }
    
    override suspend fun logoutAll() {
        // Clear all in-memory state
        sessions.clear()
        GameStore.entries.forEach { store ->
            authStates[store]?.value = AuthSessionState.LOGGED_OUT
        }
        
        // Clear all persisted sessions
        sessionDao.deleteAll()
        
        // Clear all cookies
        clearAllCookies()
    }
    
    override suspend fun removeAccount(accountId: String) {
        accounts.values.forEach { list ->
            list.removeAll { it.id == accountId }
        }
    }
    
    override suspend fun clearStoreData(store: GameStore) {
        logout(store)
        accounts.remove(store)
    }
    
    // ===== Cookie Helpers =====
    
    /**
     * Check if cookies exist for a store's domain.
     * 
     * SECURITY: We only check presence, never expose cookie values.
     */
    private fun hasCookiesForStore(store: GameStore): Boolean {
        val cookies = CookieManager.getInstance().getCookie(store.baseUrl)
        return !cookies.isNullOrEmpty()
    }
    
    /**
     * Clear all WebView cookies.
     * 
     * Note: CookieManager doesn't easily support per-domain clearing.
     * We clear all and accept that users must re-auth to other stores.
     */
    private fun clearAllCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
    
    /**
     * Restore session state from persisted data on app startup.
     */
    suspend fun restoreSessions() {
        GameStore.entries.forEach { store ->
            val entity = sessionDao.getSession(store.name)
            if (entity != null && entity.isAuthenticated && !entity.isSecret) {
                // Check if cookies still exist
                if (hasCookiesForStore(store)) {
                    val session = AuthSession(
                        accountId = entity.id.toString(),
                        store = store,
                        state = AuthSessionState.AUTHENTICATED,
                        lastVerifiedAt = entity.lastVerified,
                        expiresAt = null
                    )
                    sessions[store] = session
                    
                    val account = StoreAccount(
                        id = entity.id.toString(),
                        store = store,
                        displayName = entity.displayName,
                        email = null,
                        avatarUrl = null,
                        lastActiveAt = entity.lastVerified
                    )
                    accounts.getOrPut(store) { mutableListOf() }.add(account)
                    
                    authStates[store]?.value = AuthSessionState.AUTHENTICATED
                } else {
                    // Cookies expired - session invalid
                    authStates[store]?.value = AuthSessionState.EXPIRED
                }
            }
        }
    }
}
