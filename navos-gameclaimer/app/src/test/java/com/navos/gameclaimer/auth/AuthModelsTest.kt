package com.navos.gameclaimer.auth

import com.navos.gameclaimer.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 5: Authentication Unit Tests
 * 
 * Tests authentication models, state transitions, and security patterns.
 * 
 * Note: These are model-only tests. Actual WebView behavior is tested
 * in instrumented tests with a real device/emulator.
 */
class AuthModelsTest {
    
    // ===== StoreAuthConfig Tests =====
    
    @Test
    fun `Epic Games config has correct login URL`() {
        val config = StoreAuthConfig.forStore(GameStore.EPIC_GAMES)
        assertEquals("https://www.epicgames.com/id/login", config.loginUrl)
        assertTrue(config.allowedDomains.contains("epicgames.com"))
    }
    
    @Test
    fun `Steam config has correct login URL`() {
        val config = StoreAuthConfig.forStore(GameStore.STEAM)
        assertEquals("https://store.steampowered.com/login/", config.loginUrl)
        assertTrue(config.allowedDomains.contains("steampowered.com"))
        assertTrue(config.allowedDomains.contains("steamcommunity.com"))
    }
    
    @Test
    fun `GOG config has correct login URL`() {
        val config = StoreAuthConfig.forStore(GameStore.GOG)
        assertEquals("https://login.gog.com/auth", config.loginUrl)
        assertTrue(config.allowedDomains.contains("gog.com"))
    }
    
    @Test
    fun `Amazon Prime config has correct login URL`() {
        val config = StoreAuthConfig.forStore(GameStore.AMAZON_PRIME)
        assertTrue(config.loginUrl.startsWith("https://www.amazon.com/ap/signin"))
        assertTrue(config.allowedDomains.contains("amazon.com"))
        assertTrue(config.allowedDomains.contains("gaming.amazon.com"))
    }
    
    @Test
    fun `All store configs use HTTPS login URLs`() {
        GameStore.entries.forEach { store ->
            val config = StoreAuthConfig.forStore(store)
            assertTrue(
                "Login URL for ${store.name} should be HTTPS",
                config.loginUrl.startsWith("https://")
            )
        }
    }
    
    @Test
    fun `All store configs have non-empty allowed domains`() {
        GameStore.entries.forEach { store ->
            val config = StoreAuthConfig.forStore(store)
            assertTrue(
                "Allowed domains for ${store.name} should not be empty",
                config.allowedDomains.isNotEmpty()
            )
        }
    }
    
    // ===== AuthSession Tests =====
    
    @Test
    fun `AuthSession is valid when authenticated and not expired`() {
        val session = AuthSession(
            accountId = "test-id",
            store = GameStore.EPIC_GAMES,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600_000 // 1 hour
        )
        assertTrue(session.isApparentlyValid())
    }
    
    @Test
    fun `AuthSession is invalid when expired`() {
        val session = AuthSession(
            accountId = "test-id",
            store = GameStore.EPIC_GAMES,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis() - 7200_000,
            expiresAt = System.currentTimeMillis() - 3600_000 // Expired 1 hour ago
        )
        assertFalse(session.isApparentlyValid())
    }
    
    @Test
    fun `AuthSession is invalid when not authenticated`() {
        val session = AuthSession(
            accountId = "test-id",
            store = GameStore.EPIC_GAMES,
            state = AuthSessionState.NONE,
            lastVerifiedAt = null,
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        assertFalse(session.isApparentlyValid())
    }
    
    @Test
    fun `AuthSession with null expiry is valid when authenticated`() {
        val session = AuthSession(
            accountId = "test-id",
            store = GameStore.EPIC_GAMES,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis(),
            expiresAt = null // Session-based, no known expiry
        )
        assertTrue(session.isApparentlyValid())
    }
    
    @Test
    fun `AuthSession toString does not contain sensitive data`() {
        val session = AuthSession(
            accountId = "secret-account-id",
            store = GameStore.STEAM,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis(),
            expiresAt = null
        )
        val str = session.toString()
        assertFalse("Should not contain token", str.contains("token"))
        assertFalse("Should not contain cookie", str.contains("cookie"))
        assertFalse("Should not contain password", str.contains("password"))
    }
    
    // ===== StoreAccount Tests =====
    
    @Test
    fun `StoreAccount toString does not expose email`() {
        val account = StoreAccount(
            id = "test-id",
            store = GameStore.EPIC_GAMES,
            displayName = "TestUser",
            email = "secret@email.com",
            avatarUrl = null
        )
        val str = account.toString()
        assertFalse("Should not contain email address", str.contains("secret@email.com"))
        assertTrue("Should contain display name", str.contains("TestUser"))
    }
    
    @Test
    fun `StoreAccount generates unique ID`() {
        val account1 = StoreAccount(store = GameStore.EPIC_GAMES, displayName = "User1", email = null, avatarUrl = null)
        val account2 = StoreAccount(store = GameStore.EPIC_GAMES, displayName = "User2", email = null, avatarUrl = null)
        assertNotEquals(account1.id, account2.id)
    }
    
    // ===== AuthResult Tests =====
    
    @Test
    fun `AuthResult Success contains account and session`() {
        val account = StoreAccount(store = GameStore.GOG, displayName = "Test", email = null, avatarUrl = null)
        val session = AuthSession(
            accountId = account.id,
            store = GameStore.GOG,
            state = AuthSessionState.AUTHENTICATED,
            lastVerifiedAt = System.currentTimeMillis(),
            expiresAt = null
        )
        
        val result = AuthResult.Success(account, session)
        assertEquals(account, result.account)
        assertEquals(session, result.session)
    }
    
    @Test
    fun `AuthResult MfaRequired carries mfa type`() {
        val result = AuthResult.MfaRequired(MfaType.STEAM_GUARD_EMAIL, "Check your email")
        assertEquals(MfaType.STEAM_GUARD_EMAIL, result.mfaType)
        assertEquals("Check your email", result.message)
    }
    
    @Test
    fun `AuthResult Failed carries failure reason`() {
        val result = AuthResult.Failed(AuthFailureReason.CERTIFICATE_ERROR, "Invalid cert")
        assertEquals(AuthFailureReason.CERTIFICATE_ERROR, result.reason)
    }
    
    // ===== AuthSessionState Tests =====
    
    @Test
    fun `AuthSessionState enum has expected values`() {
        val states = AuthSessionState.entries
        assertTrue(states.contains(AuthSessionState.NONE))
        assertTrue(states.contains(AuthSessionState.AUTHENTICATING))
        assertTrue(states.contains(AuthSessionState.AWAITING_MFA))
        assertTrue(states.contains(AuthSessionState.AUTHENTICATED))
        assertTrue(states.contains(AuthSessionState.EXPIRED))
        assertTrue(states.contains(AuthSessionState.FAILED))
        assertTrue(states.contains(AuthSessionState.LOGGED_OUT))
    }
    
    // ===== MfaType Tests =====
    
    @Test
    fun `MfaType enum has expected values`() {
        val types = MfaType.entries
        assertTrue(types.contains(MfaType.TOTP))
        assertTrue(types.contains(MfaType.STEAM_GUARD_EMAIL))
        assertTrue(types.contains(MfaType.STEAM_GUARD_MOBILE))
        assertTrue(types.contains(MfaType.SMS))
        assertTrue(types.contains(MfaType.EMAIL_CODE))
        assertTrue(types.contains(MfaType.CAPTCHA))
        assertTrue(types.contains(MfaType.DEVICE_VERIFICATION))
        assertTrue(types.contains(MfaType.OTHER))
    }
    
    // ===== BrowserAuthEvent Tests =====
    
    @Test
    fun `BrowserAuthEvent PageFinished carries URL and title`() {
        val event = BrowserAuthEvent.PageFinished("https://example.com", "Example")
        assertEquals("https://example.com", event.url)
        assertEquals("Example", event.title)
    }
    
    @Test
    fun `BrowserAuthEvent NavigationBlocked carries reason`() {
        val event = BrowserAuthEvent.NavigationBlocked("http://insecure.com", "Only HTTPS allowed")
        assertEquals("http://insecure.com", event.url)
        assertEquals("Only HTTPS allowed", event.reason)
    }
    
    @Test
    fun `BrowserAuthEvent SslError indicates security issue`() {
        val event = BrowserAuthEvent.SslError("https://badcert.example.com")
        assertTrue(event is BrowserAuthEvent.SslError)
    }
}

/**
 * URL Validation Tests
 * 
 * Tests the URL validation logic that would be in SecureBrowserWrapper.
 * Since SecureBrowserWrapper requires Android context, we test the logic here.
 */
class UrlValidationTest {
    
    // Simulated validation logic (matches SecureBrowserWrapper)
    private fun isValidSecureUrl(url: String, allowedDomains: List<String>): Boolean {
        val trimmed = url.trim()
        
        // Must be HTTPS
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }
        
        // Block localhost and private IPs
        val blockedPatterns = listOf(
            "localhost", "127.0.0.1", "192.168.", "10.0.",
            "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.",
            "0.0.0.0", "[::1]"
        )
        
        for (pattern in blockedPatterns) {
            if (trimmed.contains(pattern, ignoreCase = true)) {
                return false
            }
        }
        
        // Extract domain
        val domain = try {
            val withoutProtocol = trimmed.substringAfter("://")
            withoutProtocol.substringBefore("/").substringBefore("?").substringBefore(":")
        } catch (e: Exception) {
            return false
        }
        
        // Check allowed domains
        return allowedDomains.any { allowed ->
            domain.equals(allowed, ignoreCase = true) ||
            domain.endsWith(".$allowed", ignoreCase = true)
        }
    }
    
    @Test
    fun `HTTPS URLs are allowed`() {
        assertTrue(isValidSecureUrl(
            "https://store.epicgames.com/free-games",
            listOf("epicgames.com")
        ))
    }
    
    @Test
    fun `HTTP URLs are rejected`() {
        assertFalse(isValidSecureUrl(
            "http://store.epicgames.com/free-games",
            listOf("epicgames.com")
        ))
    }
    
    @Test
    fun `Localhost URLs are rejected`() {
        assertFalse(isValidSecureUrl(
            "https://localhost/test",
            listOf("localhost")
        ))
    }
    
    @Test
    fun `127_0_0_1 URLs are rejected`() {
        assertFalse(isValidSecureUrl(
            "https://127.0.0.1:8080/test",
            listOf("epicgames.com")
        ))
    }
    
    @Test
    fun `Private IP 192_168 URLs are rejected`() {
        assertFalse(isValidSecureUrl(
            "https://192.168.1.1/admin",
            listOf("epicgames.com")
        ))
    }
    
    @Test
    fun `Private IP 10_0 URLs are rejected`() {
        assertFalse(isValidSecureUrl(
            "https://10.0.0.1/admin",
            listOf("epicgames.com")
        ))
    }
    
    @Test
    fun `URLs with unallowed domains are rejected`() {
        assertFalse(isValidSecureUrl(
            "https://malicious-site.com/fake-login",
            listOf("epicgames.com", "unrealengine.com")
        ))
    }
    
    @Test
    fun `Subdomains of allowed domains are accepted`() {
        assertTrue(isValidSecureUrl(
            "https://www.epicgames.com/id/login",
            listOf("epicgames.com")
        ))
        assertTrue(isValidSecureUrl(
            "https://store.steampowered.com/login",
            listOf("steampowered.com", "steamcommunity.com")
        ))
    }
    
    @Test
    fun `URLs with query params are validated`() {
        assertTrue(isValidSecureUrl(
            "https://www.epicgames.com/id/login?redirect=store",
            listOf("epicgames.com")
        ))
    }
    
    @Test
    fun `Case insensitive domain matching works`() {
        assertTrue(isValidSecureUrl(
            "https://STORE.EPICGAMES.COM/free-games",
            listOf("epicgames.com")
        ))
    }
}
