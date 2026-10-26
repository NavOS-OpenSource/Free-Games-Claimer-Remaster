package com.navos.gameclaimer.domain.model

import kotlinx.serialization.Serializable

/**
 * Phase 13: Comprehensive claim outcome classification.
 * 
 * GPT REQUIREMENTS:
 * - Classify outcomes distinctly: success, already claimed, temporarily unavailable,
 *   authentication required, CAPTCHA/MFA/manual action required, permanent failure,
 *   and retryable network/server failure.
 * - Never mark claimed without store confirmation.
 * - If a workflow cannot safely establish claim success, record an uncertain/manual-verification outcome.
 * 
 * SECURITY:
 * - Only SUCCESS indicates confirmed ownership
 * - All other states require different handling
 * - UNCERTAIN must never be treated as claimed
 */
@Serializable
sealed class ClaimOutcome {
    /** Unique ID of the giveaway this outcome relates to */
    abstract val giveawayId: String
    
    /** Timestamp when this outcome was recorded */
    abstract val timestamp: Long
    
    /**
     * CONFIRMED SUCCESS: Store explicitly confirmed ownership.
     * 
     * This is the ONLY outcome that should mark a giveaway as claimed.
     * A click or request is NOT proof - this requires verified confirmation.
     */
    @Serializable
    data class Success(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** Optional confirmation ID from the store */
        val confirmationId: String? = null,
        /** How the success was verified */
        val verificationMethod: VerificationMethod = VerificationMethod.UNVERIFIED
    ) : ClaimOutcome()
    
    /**
     * ALREADY OWNED: User already has this content.
     * 
     * Not an error - just means no action needed.
     * Should be marked as claimed if we can verify ownership.
     */
    @Serializable
    data class AlreadyOwned(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ClaimOutcome()
    
    /**
     * REQUIRES AUTHENTICATION: User needs to log in first.
     * 
     * Retry policy: Do NOT auto-retry - requires user action.
     * UI should prompt user to authenticate with the store.
     */
    @Serializable
    data class RequiresAuth(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** Which store needs authentication */
        val store: GameStore
    ) : ClaimOutcome()
    
    /**
     * REQUIRES MANUAL ACTION: CAPTCHA, MFA, passkey, or other user interaction.
     * 
     * Retry policy: Do NOT auto-retry - requires user interaction.
     * UI should direct user to complete the action manually.
     */
    @Serializable
    data class RequiresManualAction(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** Type of action required */
        val actionType: ManualActionType,
        /** Optional URL to complete the action */
        val actionUrl: String? = null
    ) : ClaimOutcome()
    
    /**
     * TEMPORARILY UNAVAILABLE: Transient server/network issue.
     * 
     * Retry policy: Exponential backoff with jitter, capped retries.
     * Examples: 5xx errors, timeouts, rate limits, connection failures.
     */
    @Serializable
    data class TemporarilyUnavailable(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** Human-readable reason */
        val reason: String,
        /** HTTP status code if applicable */
        val httpStatus: Int? = null,
        /** Suggested retry delay in milliseconds */
        val retryAfterMs: Long? = null
    ) : ClaimOutcome()
    
    /**
     * PERMANENT FAILURE: Cannot be claimed, do not retry.
     * 
     * Retry policy: NEVER retry automatically.
     * Examples: 4xx errors (except auth), invalid URL, offer ended.
     */
    @Serializable
    data class PermanentFailure(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** Human-readable reason */
        val reason: String,
        /** Error code if available */
        val errorCode: String? = null
    ) : ClaimOutcome()
    
    /**
     * UNCERTAIN: Claim was attempted but we cannot confirm success.
     * 
     * CRITICAL: This MUST NOT be treated as claimed.
     * Requires manual user verification.
     * 
     * Examples: Request sent but response unclear, page navigation succeeded
     * but no confirmation element found, timeout after click.
     */
    @Serializable
    data class Uncertain(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** What was attempted */
        val attemptDescription: String,
        /** Why we can't confirm */
        val uncertaintyReason: String
    ) : ClaimOutcome()
    
    /**
     * DISCOVERY ONLY: Adapter does not support claiming.
     * 
     * Not an error - this adapter is designed for discovery only.
     * User must claim manually at the store.
     */
    @Serializable
    data class DiscoveryOnly(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** Store for this giveaway */
        val store: GameStore
    ) : ClaimOutcome()
    
    /**
     * CANCELLED: Claim was cancelled before completion.
     * 
     * Retry policy: Can retry if user re-initiates.
     */
    @Serializable
    data class Cancelled(
        override val giveawayId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** Why it was cancelled */
        val reason: String? = null
    ) : ClaimOutcome()
    
    // ===== Classification Helpers =====
    
    /** Whether this outcome indicates confirmed ownership */
    val isConfirmedOwned: Boolean
        get() = this is Success || this is AlreadyOwned
    
    /** Whether this outcome is retryable */
    val isRetryable: Boolean
        get() = this is TemporarilyUnavailable
    
    /** Whether this requires user action before retry */
    val requiresUserAction: Boolean
        get() = this is RequiresAuth || this is RequiresManualAction || this is Uncertain
    
    /** Whether this is a terminal failure (no point retrying) */
    val isTerminalFailure: Boolean
        get() = this is PermanentFailure
    
    /** Whether claiming is not supported for this giveaway */
    val isDiscoveryOnly: Boolean
        get() = this is DiscoveryOnly
}

/**
 * How a successful claim was verified.
 */
@Serializable
enum class VerificationMethod {
    /** Verified by store API response confirming ownership */
    API_CONFIRMATION,
    
    /** Verified by detecting "owned" state on page */
    PAGE_STATE_OWNED,
    
    /** Verified by library presence check */
    LIBRARY_CHECK,
    
    /** Success assumed from button state change (lower confidence) */
    BUTTON_STATE_CHANGE,
    
    /** Not verified - treat with caution */
    UNVERIFIED
}

/**
 * Types of manual actions that may be required.
 */
@Serializable
enum class ManualActionType {
    /** CAPTCHA verification required */
    CAPTCHA,
    
    /** Two-factor authentication required */
    TWO_FACTOR_AUTH,
    
    /** Passkey/security key required */
    PASSKEY,
    
    /** Age verification required */
    AGE_VERIFICATION,
    
    /** Terms acceptance required */
    TERMS_ACCEPTANCE,
    
    /** External code redemption required (e.g., Steam key) */
    EXTERNAL_REDEMPTION,
    
    /** Payment method required (even for free items) */
    PAYMENT_METHOD_REQUIRED,
    
    /** Email verification required */
    EMAIL_VERIFICATION,
    
    /** Unknown manual action */
    UNKNOWN
}
