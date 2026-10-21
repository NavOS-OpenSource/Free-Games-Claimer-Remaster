package com.navos.gameclaimer.domain.model

import java.util.UUID

/**
 * Phase 5: Authentication Domain Models
 * 
 * These models define the authentication and session state for game stores.
 * 
 * SECURITY DESIGN:
 * - Session tokens are NEVER exposed in toString() or logs
 * - Cookie storage is handled by Android WebView (system-managed)
 * - App-managed tokens will use Android Keystore encryption (Phase 5+)
 * - All sensitive fields are excluded from serialization/export
 */

/**
 * Represents a user account for a specific game store.
 * 
 * One store can have multiple accounts (different users),
 * but only one can be active at a time per store.
 */
data class StoreAccount(
    val id: String = UUID.randomUUID().toString(),
    val store: GameStore,
    val displayName: String?,
    val email: String?, // May be partially masked in UI
    val avatarUrl: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
) {
    /**
     * Safe string representation - no sensitive data
     */
    override fun toString(): String = "StoreAccount(id=$id, store=$store, displayName=$displayName)"
}

/**
 * Represents the authentication session for a store account.
 * 
 * SECURITY: This class should NEVER be logged or serialized to external storage.
 * Session validity is determined by cookie presence and server response.
 */
data class AuthSession(
    val accountId: String,
    val store: GameStore,
    val state: AuthSessionState,
    val lastVerifiedAt: Long?,
    val expiresAt: Long?, // null = unknown/session-based
    val requiresMfa: Boolean = false,
    val mfaType: MfaType? = null
) {
    /**
     * Check if session appears valid (not expired).
     * Actual validity must be verified server-side.
     */
    fun isApparentlyValid(): Boolean {
        if (state != AuthSessionState.AUTHENTICATED) return false
        val expiry = expiresAt ?: return true // Unknown expiry = assume valid
        return System.currentTimeMillis() < expiry
    }
    
    /**
     * Safe string representation - no tokens
     */
    override fun toString(): String = 
        "AuthSession(accountId=$accountId, store=$store, state=$state, requiresMfa=$requiresMfa)"
}

/**
 * Authentication session states.
 */
enum class AuthSessionState {
    /** No session exists */
    NONE,
    
    /** User is currently authenticating (browser open) */
    AUTHENTICATING,
    
    /** Waiting for MFA/2FA/Steam Guard completion */
    AWAITING_MFA,
    
    /** Session is authenticated and valid */
    AUTHENTICATED,
    
    /** Session expired - needs re-authentication */
    EXPIRED,
    
    /** Authentication failed */
    FAILED,
    
    /** User explicitly logged out */
    LOGGED_OUT
}

/**
 * Types of multi-factor authentication challenges.
 */
enum class MfaType {
    /** Time-based one-time password (authenticator app) */
    TOTP,
    
    /** Steam Guard email code */
    STEAM_GUARD_EMAIL,
    
    /** Steam Guard mobile authenticator */
    STEAM_GUARD_MOBILE,
    
    /** SMS verification code */
    SMS,
    
    /** Email verification code */
    EMAIL_CODE,
    
    /** CAPTCHA challenge */
    CAPTCHA,
    
    /** Device/browser verification */
    DEVICE_VERIFICATION,
    
    /** Other/unknown MFA type */
    OTHER
}

/**
 * Result of an authentication attempt.
 */
sealed class AuthResult {
    /**
     * Authentication successful
     */
    data class Success(
        val account: StoreAccount,
        val session: AuthSession
    ) : AuthResult()
    
    /**
     * MFA/2FA required - user must complete challenge
     */
    data class MfaRequired(
        val mfaType: MfaType,
        val message: String? = null
    ) : AuthResult()
    
    /**
     * Authentication cancelled by user
     */
    data object Cancelled : AuthResult()
    
    /**
     * Authentication failed
     */
    data class Failed(
        val reason: AuthFailureReason,
        val message: String? = null
    ) : AuthResult()
}

/**
 * Reasons for authentication failure.
 */
enum class AuthFailureReason {
    /** Invalid credentials */
    INVALID_CREDENTIALS,
    
    /** Account locked or suspended */
    ACCOUNT_LOCKED,
    
    /** Network error */
    NETWORK_ERROR,
    
    /** Server returned an error */
    SERVER_ERROR,
    
    /** SSL/TLS certificate error (never bypassed) */
    CERTIFICATE_ERROR,
    
    /** Navigation blocked (non-HTTPS, invalid domain) */
    NAVIGATION_BLOCKED,
    
    /** Session storage failed */
    STORAGE_ERROR,
    
    /** Unknown error */
    UNKNOWN
}

/**
 * Events that can occur during browser-based authentication.
 */
sealed class BrowserAuthEvent {
    /** Page started loading */
    data class PageStarted(val url: String) : BrowserAuthEvent()
    
    /** Page finished loading */
    data class PageFinished(val url: String, val title: String?) : BrowserAuthEvent()
    
    /** Navigation was blocked (security) */
    data class NavigationBlocked(val url: String, val reason: String) : BrowserAuthEvent()
    
    /** SSL error occurred (never bypassed) */
    data class SslError(val url: String) : BrowserAuthEvent()
    
    /** Login form detected */
    data class LoginFormDetected(val url: String) : BrowserAuthEvent()
    
    /** MFA challenge detected */
    data class MfaChallengeDetected(val mfaType: MfaType) : BrowserAuthEvent()
    
    /** Authentication appears complete (redirect to logged-in page) */
    data class AuthenticationComplete(val url: String) : BrowserAuthEvent()
    
    /** Page load error */
    data class LoadError(val errorCode: Int, val description: String?) : BrowserAuthEvent()
}

/**
 * Configuration for store-specific authentication behavior.
 * 
 * ACCOUNT ISOLATION LIMITATION (Phase 5 design decision):
 * Android WebView's CookieManager shares cookies across all WebViews in the app's
 * single browser profile. True per-account session isolation would require separate
 * processes or custom cookie encryption - neither is implemented.
 * 
 * Therefore: ONLY ONE ACTIVE ACCOUNT PER STORE is supported.
 * The data model tracks which account is active, but browser sessions are NOT isolated.
 */
data class StoreAuthConfig(
    val store: GameStore,
    val loginUrl: String,
    val logoutUrl: String?,
    val successUrlPatterns: List<String>, // URLs that indicate successful login
    val mfaUrlPatterns: List<String>, // URLs that indicate MFA challenge
    val errorUrlPatterns: List<String>, // URLs that indicate error
    val allowedDomains: List<String>, // Domains allowed during auth flow
    val sessionDurationHint: Long? = null // Expected session duration in ms
) {
    companion object {
        /**
         * Default configurations for supported stores.
         * 
         * Note: These are starting points - actual detection may need refinement
         * based on store-specific behavior during implementation.
         * 
         * LIMITATION: Only one active account per store is supported due to
         * WebView cookie sharing. See class documentation.
         */
        fun forStore(store: GameStore): StoreAuthConfig = when (store) {
            GameStore.EPIC_GAMES -> StoreAuthConfig(
                store = store,
                loginUrl = "https://www.epicgames.com/id/login",
                logoutUrl = "https://www.epicgames.com/id/logout",
                successUrlPatterns = listOf(
                    "https://store.epicgames.com",
                    "https://www.epicgames.com/account"
                ),
                mfaUrlPatterns = listOf(
                    "https://www.epicgames.com/id/login/mfa"
                ),
                errorUrlPatterns = listOf(
                    "https://www.epicgames.com/id/login?error="
                ),
                allowedDomains = listOf(
                    "epicgames.com",
                    "unrealengine.com" // Epic's auth can redirect here
                )
            )
            
            GameStore.GOG -> StoreAuthConfig(
                store = store,
                loginUrl = "https://login.gog.com/auth",
                logoutUrl = "https://www.gog.com/logout",
                successUrlPatterns = listOf(
                    "https://www.gog.com/account",
                    "https://www.gog.com/u/"
                ),
                mfaUrlPatterns = listOf(
                    "https://login.gog.com/two_step"
                ),
                errorUrlPatterns = listOf(
                    "https://login.gog.com/auth?error="
                ),
                allowedDomains = listOf(
                    "gog.com"
                )
            )
            
            GameStore.STEAM -> StoreAuthConfig(
                store = store,
                loginUrl = "https://store.steampowered.com/login/",
                logoutUrl = "https://store.steampowered.com/logout/",
                successUrlPatterns = listOf(
                    "https://store.steampowered.com/account/",
                    "https://steamcommunity.com/my/"
                ),
                mfaUrlPatterns = listOf(
                    "https://store.steampowered.com/login/?steamguard=",
                    "https://store.steampowered.com/twofactor/"
                ),
                errorUrlPatterns = listOf(
                    "https://store.steampowered.com/login/?error="
                ),
                allowedDomains = listOf(
                    "steampowered.com",
                    "steamcommunity.com"
                )
            )
            
            GameStore.AMAZON_PRIME -> StoreAuthConfig(
                store = store,
                loginUrl = "https://www.amazon.com/ap/signin?_encoding=UTF8&openid.mode=checkid_setup",
                logoutUrl = "https://www.amazon.com/gp/flex/sign-out.html",
                successUrlPatterns = listOf(
                    "https://gaming.amazon.com/home",
                    "https://www.amazon.com/gp/yourstore/"
                ),
                mfaUrlPatterns = listOf(
                    "https://www.amazon.com/ap/mfa",
                    "https://www.amazon.com/ap/cvf/"
                ),
                errorUrlPatterns = listOf(
                    "https://www.amazon.com/ap/signin?error="
                ),
                allowedDomains = listOf(
                    "amazon.com",
                    "gaming.amazon.com"
                )
            )
            
            // Phase 7 additions
            GameStore.ITCH_IO -> StoreAuthConfig(
                store = store,
                loginUrl = "https://itch.io/login",
                logoutUrl = "https://itch.io/logout",
                successUrlPatterns = listOf(
                    "https://itch.io/my-purchases",
                    "https://itch.io/my-collections"
                ),
                mfaUrlPatterns = listOf(
                    "https://itch.io/login/totp"
                ),
                errorUrlPatterns = listOf(
                    "https://itch.io/login?error="
                ),
                allowedDomains = listOf(
                    "itch.io"
                )
            )
            
            GameStore.INDIEGALA -> StoreAuthConfig(
                store = store,
                loginUrl = "https://www.indiegala.com/login",
                logoutUrl = "https://www.indiegala.com/logout",
                successUrlPatterns = listOf(
                    "https://www.indiegala.com/library",
                    "https://www.indiegala.com/profile"
                ),
                mfaUrlPatterns = emptyList(), // IndieGala MFA patterns unknown
                errorUrlPatterns = listOf(
                    "https://www.indiegala.com/login?error="
                ),
                allowedDomains = listOf(
                    "indiegala.com"
                )
            )
        }
    }
}
