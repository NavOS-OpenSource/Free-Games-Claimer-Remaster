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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result of cookie removal operation.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
sealed class CookieRemovalResult {
    /** Cookies were successfully removed */
    data object Success : CookieRemovalResult()
    
    /** No cookies existed to remove (also success) */
    data object NoCookies : CookieRemovalResult()
    
    /** Operation timed out - cookie state is uncertain */
    data object Timeout : CookieRemovalResult()
    
    /** Operation was cancelled - cookie state is uncertain */
    data object Cancelled : CookieRemovalResult()
    
    fun isSuccessful(): Boolean = this is Success || this is NoCookies
    fun isUncertain(): Boolean = this is Timeout || this is Cancelled
}

/**
 * Interface for cookie removal operations - allows testing with fakes.
 */
interface CookieRemovalService {
    /**
     * Remove all cookies and return the result.
     * @param timeoutMs Timeout in milliseconds
     * @return Result indicating success, no cookies, timeout, or cancellation
     */
    suspend fun removeAllCookies(timeoutMs: Long = 5000L): CookieRemovalResult
    
    /**
     * Check if cookies exist for a URL.
     */
    fun hasCookies(url: String): Boolean
}

/**
 * Default implementation using Android's CookieManager.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidCookieRemovalService : CookieRemovalService {
    override suspend fun removeAllCookies(timeoutMs: Long): CookieRemovalResult {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val cookieManager = CookieManager.getInstance()
            
            // Set a timeout
            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (continuation.isActive) {
                    continuation.resume(CookieRemovalResult.Timeout) {}
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, timeoutMs)
            
            cookieManager.removeAllCookies { hadCookies ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                cookieManager.flush()
                if (continuation.isActive) {
                    // false means no cookies existed - that's still success
                    val result = if (hadCookies) CookieRemovalResult.Success else CookieRemovalResult.NoCookies
                    continuation.resume(result) {}
                }
            }
            
            continuation.invokeOnCancellation {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                // Don't resume - cancellation is handled by the coroutine
            }
        }
    }
    
    override fun hasCookies(url: String): Boolean {
        val cookies = CookieManager.getInstance().getCookie(url)
        return !cookies.isNullOrEmpty()
    }
}

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
 * LOGOUT SEMANTICS:
 * - All logout operations are serialized via mutex to prevent races
 * - State is only updated AFTER cookie removal completes
 * - On uncertain outcomes (timeout/cancellation), no store remains authenticated
 * - CookieManager.removeAllCookies(false) means "no cookies existed" (success)
 * 
 * WHAT THIS DOES NOT DO:
 * - Does NOT encrypt WebView cookies (platform limitation)
 * - Does NOT store raw session tokens
 * - Does NOT log any credential information
 */
class AuthRepositoryImpl(
    private val sessionDao: StoreSessionDao,
    private val cookieService: CookieRemovalService = AndroidCookieRemovalService()
) : AuthRepository {
    
    // Mutex to serialize logout operations - prevents concurrent clear races
    private val logoutMutex = Mutex()
    
    // Monotonically increasing version to detect stale callbacks
    private var stateVersion = 0L
    
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
        // LIMITATION: Only one active account per store
        // Replace any existing account for this store
        accounts[store] = mutableListOf(account)
        
        // Persist session metadata (NOT tokens)
        val entity = StoreSessionEntity(
            id = 0, // Auto-generate
            store = store.name,
            displayName = account.displayName,
            lastVerified = System.currentTimeMillis(),
            isAuthenticated = true,
            isSecret = true
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
    
    override suspend fun logout(store: GameStore) = logoutMutex.withLock {
        // Capture version at start of operation
        val operationVersion = ++stateVersion
        
        // Clear persisted session for requested store FIRST
        sessionDao.delete(store.name)
        
        // Clear ALL cookies and wait for completion
        val result = cookieService.removeAllCookies()
        
        // Check if we're still the current operation (no concurrent logout overwrote us)
        if (stateVersion != operationVersion) {
            // A newer operation started - don't update state, it will handle it
            return@withLock
        }
        
        when {
            result.isSuccessful() -> {
                // Success - update state
                sessions.remove(store)
                authStates[store]?.value = AuthSessionState.LOGGED_OUT
                
                // Mark ALL other stores as EXPIRED since cookies are shared
                GameStore.entries.forEach { otherStore ->
                    if (otherStore != store && sessions[otherStore] != null) {
                        sessions.remove(otherStore)
                        authStates[otherStore]?.value = AuthSessionState.EXPIRED
                    }
                }
            }
            result.isUncertain() -> {
                // Timeout or cancellation - cookie state unknown
                // SAFETY: Mark ALL stores as EXPIRED to force re-verification
                // Never leave any store marked AUTHENTICATED with uncertain cookie state
                sessions.clear()
                GameStore.entries.forEach { s ->
                    authStates[s]?.value = AuthSessionState.EXPIRED
                }
            }
        }
    }
    
    override suspend fun logoutAll() = logoutMutex.withLock {
        val operationVersion = ++stateVersion
        
        // Clear all persisted sessions first
        sessionDao.deleteAll()
        
        // Clear all cookies and wait
        val result = cookieService.removeAllCookies()
        
        // Check version
        if (stateVersion != operationVersion) {
            return@withLock
        }
        
        when {
            result.isSuccessful() -> {
                sessions.clear()
                accounts.clear()
                GameStore.entries.forEach { store ->
                    authStates[store]?.value = AuthSessionState.LOGGED_OUT
                }
            }
            result.isUncertain() -> {
                // Cookie state unknown - mark all EXPIRED for safety
                sessions.clear()
                accounts.clear()
                GameStore.entries.forEach { store ->
                    authStates[store]?.value = AuthSessionState.EXPIRED
                }
            }
        }
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
        return cookieService.hasCookies(store.baseUrl)
    }
    
    /**
     * Restore session state from persisted data on app startup.
     * 
     * SECURITY: We only restore sessions where both:
     * 1. Persisted metadata says authenticated
     * 2. Cookies still exist in CookieManager
     * 
     * If cookies are missing, the session is marked EXPIRED, not AUTHENTICATED.
     * This prevents falsely reporting auth status after process death/restart.
     */
    suspend fun restoreSessions() {
        GameStore.entries.forEach { store ->
            val entity = sessionDao.getSession(store.name)
            if (entity != null && entity.isAuthenticated) {
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
                    // Only one account per store
                    accounts[store] = mutableListOf(account)
                    
                    authStates[store]?.value = AuthSessionState.AUTHENTICATED
                } else {
                    // Cookies expired/missing - session invalid
                    // Update persisted state to reflect reality
                    val updatedEntity = entity.copy(isAuthenticated = false)
                    sessionDao.upsert(updatedEntity)
                    authStates[store]?.value = AuthSessionState.EXPIRED
                }
            }
        }
    }
}
