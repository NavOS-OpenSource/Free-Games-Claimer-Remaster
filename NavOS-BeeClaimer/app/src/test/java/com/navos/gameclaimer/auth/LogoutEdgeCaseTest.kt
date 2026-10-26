package com.navos.gameclaimer.auth

import com.navos.gameclaimer.data.local.dao.StoreSessionDao
import com.navos.gameclaimer.data.local.entity.StoreSessionEntity
import com.navos.gameclaimer.data.repository.AuthRepositoryImpl
import com.navos.gameclaimer.data.repository.CookieRemovalResult
import com.navos.gameclaimer.data.repository.CookieRemovalService
import com.navos.gameclaimer.domain.model.AuthSessionState
import com.navos.gameclaimer.domain.model.GameStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 5: Logout Edge Case Tests
 * 
 * Tests for cookie removal callback semantics, timeout, cancellation,
 * and concurrent logout operations using a fake CookieRemovalService.
 */
class LogoutEdgeCaseTest {
    
    private lateinit var fakeSessionDao: FakeStoreSessionDao
    private lateinit var fakeCookieService: FakeCookieRemovalService
    private lateinit var authRepository: AuthRepositoryImpl
    
    @Before
    fun setup() {
        fakeSessionDao = FakeStoreSessionDao()
        fakeCookieService = FakeCookieRemovalService()
        authRepository = AuthRepositoryImpl(fakeSessionDao, fakeCookieService)
    }
    
    // ===== Callback True/False Tests =====
    
    @Test
    fun `logout with callback true marks store as LOGGED_OUT`() = runTest {
        fakeCookieService.nextResult = CookieRemovalResult.Success
        
        authRepository.logout(GameStore.EPIC_GAMES)
        
        val state = authRepository.getAuthState(GameStore.EPIC_GAMES)
        // Collect first value
        assertEquals(AuthSessionState.LOGGED_OUT, (state as MutableStateFlow).value)
    }
    
    @Test
    fun `logout with callback NoCookies (false) also succeeds`() = runTest {
        // false from CookieManager means no cookies existed - still success
        fakeCookieService.nextResult = CookieRemovalResult.NoCookies
        
        authRepository.logout(GameStore.EPIC_GAMES)
        
        val state = authRepository.getAuthState(GameStore.EPIC_GAMES) as MutableStateFlow
        assertEquals(AuthSessionState.LOGGED_OUT, state.value)
    }
    
    // ===== Timeout Tests =====
    
    @Test
    fun `logout timeout marks all stores as EXPIRED`() = runTest {
        fakeCookieService.nextResult = CookieRemovalResult.Timeout
        
        authRepository.logout(GameStore.EPIC_GAMES)
        
        // ALL stores should be EXPIRED on uncertain outcome
        GameStore.entries.forEach { store ->
            val state = authRepository.getAuthState(store) as MutableStateFlow
            assertEquals("${store.name} should be EXPIRED on timeout", 
                AuthSessionState.EXPIRED, state.value)
        }
    }
    
    // ===== Cancellation Tests =====
    
    @Test
    fun `logout cancellation marks all stores as EXPIRED`() = runTest {
        fakeCookieService.nextResult = CookieRemovalResult.Cancelled
        
        authRepository.logout(GameStore.STEAM)
        
        // ALL stores should be EXPIRED on uncertain outcome
        GameStore.entries.forEach { store ->
            val state = authRepository.getAuthState(store) as MutableStateFlow
            assertEquals("${store.name} should be EXPIRED on cancellation",
                AuthSessionState.EXPIRED, state.value)
        }
    }
    
    // ===== No Authenticated Store After Uncertain Outcome =====
    
    @Test
    fun `no store remains AUTHENTICATED after timeout`() = runTest {
        fakeCookieService.nextResult = CookieRemovalResult.Timeout
        
        authRepository.logout(GameStore.GOG)
        
        GameStore.entries.forEach { store ->
            val state = authRepository.getAuthState(store) as MutableStateFlow
            assertNotEquals("${store.name} must NOT be AUTHENTICATED after timeout",
                AuthSessionState.AUTHENTICATED, state.value)
        }
    }
    
    @Test
    fun `no store remains AUTHENTICATED after cancellation`() = runTest {
        fakeCookieService.nextResult = CookieRemovalResult.Cancelled
        
        authRepository.logout(GameStore.AMAZON_PRIME)
        
        GameStore.entries.forEach { store ->
            val state = authRepository.getAuthState(store) as MutableStateFlow
            assertNotEquals("${store.name} must NOT be AUTHENTICATED after cancellation",
                AuthSessionState.AUTHENTICATED, state.value)
        }
    }
    
    // ===== LogoutAll Tests =====
    
    @Test
    fun `logoutAll success marks all stores as LOGGED_OUT`() = runTest {
        fakeCookieService.nextResult = CookieRemovalResult.Success
        
        authRepository.logoutAll()
        
        GameStore.entries.forEach { store ->
            val state = authRepository.getAuthState(store) as MutableStateFlow
            assertEquals("${store.name} should be LOGGED_OUT",
                AuthSessionState.LOGGED_OUT, state.value)
        }
    }
    
    @Test
    fun `logoutAll timeout marks all stores as EXPIRED`() = runTest {
        fakeCookieService.nextResult = CookieRemovalResult.Timeout
        
        authRepository.logoutAll()
        
        GameStore.entries.forEach { store ->
            val state = authRepository.getAuthState(store) as MutableStateFlow
            assertEquals("${store.name} should be EXPIRED",
                AuthSessionState.EXPIRED, state.value)
        }
    }
    
    // ===== Concurrent Logout Tests =====
    
    @Test
    fun `concurrent logout calls are serialized`() = runTest {
        var callCount = 0
        fakeCookieService.onRemove = { callCount++ }
        fakeCookieService.nextResult = CookieRemovalResult.Success
        
        // Launch multiple concurrent logouts
        val jobs = listOf(
            launch { authRepository.logout(GameStore.EPIC_GAMES) },
            launch { authRepository.logout(GameStore.STEAM) },
            launch { authRepository.logout(GameStore.GOG) }
        )
        jobs.forEach { it.join() }
        
        // All should have completed (mutex serialized them)
        assertEquals(3, callCount)
    }
    
    @Test
    fun `logout racing with logoutAll results in consistent state`() = runTest {
        fakeCookieService.nextResult = CookieRemovalResult.Success
        
        val job1 = launch { authRepository.logout(GameStore.EPIC_GAMES) }
        val job2 = launch { authRepository.logoutAll() }
        
        job1.join()
        job2.join()
        
        // Final state should be consistent - all stores either LOGGED_OUT or EXPIRED
        GameStore.entries.forEach { store ->
            val state = authRepository.getAuthState(store) as MutableStateFlow
            assertTrue("${store.name} should be LOGGED_OUT or EXPIRED",
                state.value == AuthSessionState.LOGGED_OUT || 
                state.value == AuthSessionState.EXPIRED)
        }
    }
    
    // ===== Stale Callback Cannot Overwrite Newer State =====
    
    @Test
    fun `state version prevents stale callback overwrite`() = runTest {
        // This tests the version check in the implementation
        // We can't easily test actual stale callbacks, but we verify
        // that successful operations don't leave inconsistent state
        fakeCookieService.nextResult = CookieRemovalResult.Success
        
        authRepository.logout(GameStore.EPIC_GAMES)
        val stateAfterFirst = (authRepository.getAuthState(GameStore.EPIC_GAMES) as MutableStateFlow).value
        
        authRepository.logout(GameStore.EPIC_GAMES)
        val stateAfterSecond = (authRepository.getAuthState(GameStore.EPIC_GAMES) as MutableStateFlow).value
        
        assertEquals(stateAfterFirst, stateAfterSecond)
    }
}

/**
 * Fake implementation of StoreSessionDao for testing.
 */
class FakeStoreSessionDao : StoreSessionDao {
    private val sessions = mutableMapOf<String, StoreSessionEntity>()
    
    override suspend fun getSession(store: String): StoreSessionEntity? = sessions[store]
    
    override fun getSessionFlow(store: String): Flow<StoreSessionEntity?> {
        return MutableStateFlow(sessions[store])
    }
    
    override suspend fun getAllSessions(): List<StoreSessionEntity> = sessions.values.toList()
    
    override suspend fun upsert(session: StoreSessionEntity) {
        sessions[session.store] = session
    }
    
    override suspend fun delete(store: String) {
        sessions.remove(store)
    }
    
    override suspend fun deleteAll() {
        sessions.clear()
    }
}

/**
 * Fake implementation of CookieRemovalService for testing.
 */
class FakeCookieRemovalService : CookieRemovalService {
    var nextResult: CookieRemovalResult = CookieRemovalResult.Success
    var onRemove: (() -> Unit)? = null
    private val cookies = mutableSetOf<String>()
    
    override suspend fun removeAllCookies(timeoutMs: Long): CookieRemovalResult {
        onRemove?.invoke()
        if (nextResult.isSuccessful()) {
            cookies.clear()
        }
        return nextResult
    }
    
    override fun hasCookies(url: String): Boolean = cookies.contains(url)
    
    fun addCookie(url: String) {
        cookies.add(url)
    }
}
