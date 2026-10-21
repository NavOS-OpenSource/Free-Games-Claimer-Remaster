package com.navos.gameclaimer.browser

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.navos.gameclaimer.domain.model.AuthSession
import com.navos.gameclaimer.domain.model.AuthSessionState
import com.navos.gameclaimer.domain.model.BrowserAuthEvent
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.StoreAuthConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 5: Instrumented Browser Security Tests
 * 
 * Tests WebView security configuration and session handling on a real device.
 * 
 * SECURITY TESTS:
 * - HTTPS-only navigation
 * - SSL error handling (never bypassed)
 * - Domain restriction
 * - Cookie management
 * - Session state transitions
 * 
 * Note: These tests run on an Android device/emulator.
 * They do NOT connect to live store servers.
 */
@RunWith(AndroidJUnit4::class)
class SecureBrowserInstrumentedTest {
    
    private lateinit var context: android.content.Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear cookies before each test
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
    
    @After
    fun teardown() {
        // Clean up cookies after tests
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
    
    // ===== StoreAuthConfig Tests =====
    
    @Test
    fun storeAuthConfig_epicGames_hasValidConfiguration() {
        val config = StoreAuthConfig.forStore(GameStore.EPIC_GAMES)
        
        assertTrue("Login URL should be HTTPS", 
            config.loginUrl.startsWith("https://"))
        assertTrue("Should have epicgames.com in allowed domains",
            config.allowedDomains.contains("epicgames.com"))
        assertTrue("Should have success URL patterns",
            config.successUrlPatterns.isNotEmpty())
        assertTrue("Should have MFA URL patterns",
            config.mfaUrlPatterns.isNotEmpty())
    }
    
    @Test
    fun storeAuthConfig_steam_hasValidConfiguration() {
        val config = StoreAuthConfig.forStore(GameStore.STEAM)
        
        assertTrue("Login URL should be HTTPS",
            config.loginUrl.startsWith("https://"))
        assertTrue("Should have steampowered.com in allowed domains",
            config.allowedDomains.contains("steampowered.com"))
        assertTrue("Should have steamcommunity.com in allowed domains",
            config.allowedDomains.contains("steamcommunity.com"))
    }
    
    @Test
    fun storeAuthConfig_gog_hasValidConfiguration() {
        val config = StoreAuthConfig.forStore(GameStore.GOG)
        
        assertTrue("Login URL should be HTTPS",
            config.loginUrl.startsWith("https://"))
        assertTrue("Should have gog.com in allowed domains",
            config.allowedDomains.contains("gog.com"))
    }
    
    @Test
    fun storeAuthConfig_amazonPrime_hasValidConfiguration() {
        val config = StoreAuthConfig.forStore(GameStore.AMAZON_PRIME)
        
        assertTrue("Login URL should be HTTPS",
            config.loginUrl.startsWith("https://"))
        assertTrue("Should have amazon.com in allowed domains",
            config.allowedDomains.contains("amazon.com"))
        assertTrue("Should have gaming.amazon.com in allowed domains",
            config.allowedDomains.contains("gaming.amazon.com"))
    }
    
    @Test
    fun storeAuthConfig_allStores_useHttpsLoginUrls() {
        GameStore.entries.forEach { store ->
            val config = StoreAuthConfig.forStore(store)
            assertTrue(
                "Store ${store.name} should have HTTPS login URL",
                config.loginUrl.startsWith("https://")
            )
        }
    }
    
    // ===== SecureBrowserWrapper Creation Tests =====
    
    @Test
    fun secureBrowserWrapper_createsForAllStores() {
        GameStore.entries.forEach { store ->
            val wrapper = SecureBrowserWrapper.forStore(context, store)
            assertNotNull("Should create wrapper for ${store.name}", wrapper)
            wrapper.destroy()
        }
    }
    
    @Test
    fun secureBrowserWrapper_initialState_isCorrect() {
        val wrapper = SecureBrowserWrapper.forStore(context, GameStore.EPIC_GAMES)
        
        assertEquals("Initial URL should be empty", "", wrapper.currentUrl)
        assertEquals("Initial title should be empty", "", wrapper.pageTitle)
        assertFalse("Should not have session cookies initially", wrapper.hasSessionCookies())
        
        wrapper.destroy()
    }
    
    // ===== URL Validation Tests =====
    
    @Test
    fun secureBrowserWrapper_rejectsHttpUrl() = runBlocking {
        val wrapper = SecureBrowserWrapper.forStore(context, GameStore.EPIC_GAMES)
        
        val result = wrapper.loadUrl("http://store.epicgames.com/free-games")
        assertFalse("HTTP URL should be rejected", result)
        
        // Should emit NavigationBlocked event
        withTimeout(1000) {
            val event = wrapper.events.first()
            assertTrue("Should emit NavigationBlocked", event is BrowserAuthEvent.NavigationBlocked)
            assertEquals("Only HTTPS URLs allowed", (event as BrowserAuthEvent.NavigationBlocked).reason)
        }
        
        wrapper.destroy()
    }
    
    @Test
    fun secureBrowserWrapper_rejectsLocalhostUrl() = runBlocking {
        val wrapper = SecureBrowserWrapper.forStore(context, GameStore.EPIC_GAMES)
        
        val result = wrapper.loadUrl("https://localhost/test")
        assertFalse("Localhost URL should be rejected", result)
        
        withTimeout(1000) {
            val event = wrapper.events.first()
            assertTrue("Should emit NavigationBlocked", event is BrowserAuthEvent.NavigationBlocked)
        }
        
        wrapper.destroy()
    }
    
    @Test
    fun secureBrowserWrapper_rejectsPrivateIpUrl() = runBlocking {
        val wrapper = SecureBrowserWrapper.forStore(context, GameStore.EPIC_GAMES)
        
        val result = wrapper.loadUrl("https://192.168.1.1/admin")
        assertFalse("Private IP URL should be rejected", result)
        
        withTimeout(1000) {
            val event = wrapper.events.first()
            assertTrue("Should emit NavigationBlocked", event is BrowserAuthEvent.NavigationBlocked)
        }
        
        wrapper.destroy()
    }
    
    @Test
    fun secureBrowserWrapper_rejectsUnallowedDomain() = runBlocking {
        val wrapper = SecureBrowserWrapper.forStore(context, GameStore.EPIC_GAMES)
        
        val result = wrapper.loadUrl("https://malicious-site.com/fake-login")
        assertFalse("Unallowed domain should be rejected", result)
        
        withTimeout(1000) {
            val event = wrapper.events.first()
            assertTrue("Should emit NavigationBlocked", event is BrowserAuthEvent.NavigationBlocked)
            assertTrue("Reason should mention domain", 
                (event as BrowserAuthEvent.NavigationBlocked).reason.contains("Domain not allowed"))
        }
        
        wrapper.destroy()
    }
    
    // ===== Cookie Management Tests =====
    
    @Test
    fun cookieManager_initiallyEmpty() {
        val cookies = CookieManager.getInstance().getCookie("https://store.epicgames.com")
        assertTrue("Cookies should be empty initially", cookies.isNullOrEmpty())
    }
    
    @Test
    fun cookieManager_canSetAndRetrieveCookies() {
        val testUrl = "https://test.example.com"
        val cookieValue = "test_session=abc123"
        
        CookieManager.getInstance().setCookie(testUrl, cookieValue)
        CookieManager.getInstance().flush()
        
        val retrieved = CookieManager.getInstance().getCookie(testUrl)
        assertNotNull("Should retrieve cookie", retrieved)
        assertTrue("Should contain cookie value", retrieved!!.contains("test_session"))
    }
    
    @Test
    fun cookieManager_clearingWorks() {
        val testUrl = "https://test.example.com"
        CookieManager.getInstance().setCookie(testUrl, "test=value")
        CookieManager.getInstance().flush()
        
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        
        val cookies = CookieManager.getInstance().getCookie(testUrl)
        assertTrue("Cookies should be cleared", cookies.isNullOrEmpty())
    }
    
    // ===== AuthSession Tests =====
    
    @Test
    fun authSession_validWhenAuthenticatedAndNotExpired() {
        val session = AuthSession(
            accountId = "test-id",
            store = GameStore.EPIC_GAMES,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000 // 1 hour from now
        )
        
        assertTrue("Session should be valid", session.isApparentlyValid())
    }
    
    @Test
    fun authSession_invalidWhenExpired() {
        val session = AuthSession(
            accountId = "test-id",
            store = GameStore.EPIC_GAMES,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis() - 7200_000,
            expiresAt = System.currentTimeMillis() - 3600_000 // Expired 1 hour ago
        )
        
        assertFalse("Expired session should be invalid", session.isApparentlyValid())
    }
    
    @Test
    fun authSession_invalidWhenNotAuthenticated() {
        val session = AuthSession(
            accountId = "test-id",
            store = GameStore.EPIC_GAMES,
            state = AuthSessionState.NONE,
            lastVerifiedAt = null,
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        
        assertFalse("Non-authenticated session should be invalid", session.isApparentlyValid())
    }
    
    @Test
    fun authSession_validWithNullExpiry() {
        val session = AuthSession(
            accountId = "test-id",
            store = GameStore.EPIC_GAMES,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis(),
            expiresAt = null // Unknown expiry
        )
        
        assertTrue("Session with null expiry should be valid when authenticated", 
            session.isApparentlyValid())
    }
    
    // ===== Security Verification Tests =====
    
    @Test
    fun allStoreConfigs_haveHttpsLoginUrls() {
        GameStore.entries.forEach { store ->
            val config = StoreAuthConfig.forStore(store)
            assertTrue(
                "Store ${store.name} login URL must be HTTPS",
                config.loginUrl.startsWith("https://")
            )
        }
    }
    
    @Test
    fun allStoreConfigs_haveNonEmptyAllowedDomains() {
        GameStore.entries.forEach { store ->
            val config = StoreAuthConfig.forStore(store)
            assertTrue(
                "Store ${store.name} must have allowed domains",
                config.allowedDomains.isNotEmpty()
            )
        }
    }
    
    @Test
    fun allStoreConfigs_successUrlsAreHttps() {
        GameStore.entries.forEach { store ->
            val config = StoreAuthConfig.forStore(store)
            config.successUrlPatterns.forEach { url ->
                assertTrue(
                    "Success URL pattern for ${store.name} must be HTTPS: $url",
                    url.startsWith("https://")
                )
            }
        }
    }
    
    @Test
    fun allStoreConfigs_mfaUrlsAreHttps() {
        GameStore.entries.forEach { store ->
            val config = StoreAuthConfig.forStore(store)
            config.mfaUrlPatterns.forEach { url ->
                assertTrue(
                    "MFA URL pattern for ${store.name} must be HTTPS: $url",
                    url.startsWith("https://")
                )
            }
        }
    }
}
