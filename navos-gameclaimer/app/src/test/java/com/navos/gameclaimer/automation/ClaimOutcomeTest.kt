package com.navos.gameclaimer.automation

import com.navos.gameclaimer.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 13: Tests for ClaimOutcome classification and behavior.
 * 
 * GPT REQUIREMENTS TESTED:
 * - Classify outcomes distinctly
 * - Only SUCCESS indicates confirmed ownership
 * - isRetryable only for transient failures
 * - requiresUserAction for auth/manual/uncertain
 * - UNCERTAIN must NOT be treated as claimed
 */
class ClaimOutcomeTest {
    
    // ===== Success Outcome Tests =====
    
    @Test
    fun `Success outcome is confirmed owned`() {
        val outcome = ClaimOutcome.Success(
            giveawayId = "test:123",
            verificationMethod = VerificationMethod.API_CONFIRMATION
        )
        
        assertTrue("Success should be confirmed owned", outcome.isConfirmedOwned)
        assertFalse("Success should not be retryable", outcome.isRetryable)
        assertFalse("Success should not require user action", outcome.requiresUserAction)
        assertFalse("Success should not be terminal failure", outcome.isTerminalFailure)
        assertFalse("Success should not be discovery only", outcome.isDiscoveryOnly)
    }
    
    @Test
    fun `Success with different verification methods`() {
        val methods = VerificationMethod.values()
        
        for (method in methods) {
            val outcome = ClaimOutcome.Success(
                giveawayId = "test:$method",
                verificationMethod = method
            )
            assertTrue("Success ($method) should be confirmed owned", outcome.isConfirmedOwned)
        }
    }
    
    // ===== AlreadyOwned Outcome Tests =====
    
    @Test
    fun `AlreadyOwned outcome is confirmed owned`() {
        val outcome = ClaimOutcome.AlreadyOwned(giveawayId = "test:123")
        
        assertTrue("AlreadyOwned should be confirmed owned", outcome.isConfirmedOwned)
        assertFalse("AlreadyOwned should not be retryable", outcome.isRetryable)
        assertFalse("AlreadyOwned should not require user action", outcome.requiresUserAction)
        assertFalse("AlreadyOwned should not be terminal failure", outcome.isTerminalFailure)
    }
    
    // ===== RequiresAuth Outcome Tests =====
    
    @Test
    fun `RequiresAuth outcome requires user action`() {
        val outcome = ClaimOutcome.RequiresAuth(
            giveawayId = "test:123",
            store = GameStore.EPIC_GAMES
        )
        
        assertFalse("RequiresAuth should NOT be confirmed owned", outcome.isConfirmedOwned)
        assertFalse("RequiresAuth should NOT be retryable automatically", outcome.isRetryable)
        assertTrue("RequiresAuth MUST require user action", outcome.requiresUserAction)
        assertFalse("RequiresAuth should not be terminal failure", outcome.isTerminalFailure)
    }
    
    // ===== RequiresManualAction Outcome Tests =====
    
    @Test
    fun `RequiresManualAction for CAPTCHA requires user action`() {
        val outcome = ClaimOutcome.RequiresManualAction(
            giveawayId = "test:123",
            actionType = ManualActionType.CAPTCHA
        )
        
        assertFalse("CAPTCHA should NOT be confirmed owned", outcome.isConfirmedOwned)
        assertFalse("CAPTCHA should NOT be auto-retryable", outcome.isRetryable)
        assertTrue("CAPTCHA MUST require user action", outcome.requiresUserAction)
    }
    
    @Test
    fun `RequiresManualAction for 2FA requires user action`() {
        val outcome = ClaimOutcome.RequiresManualAction(
            giveawayId = "test:123",
            actionType = ManualActionType.TWO_FACTOR_AUTH
        )
        
        assertTrue("2FA MUST require user action", outcome.requiresUserAction)
        assertFalse("2FA should NOT be auto-retryable", outcome.isRetryable)
    }
    
    @Test
    fun `RequiresManualAction for passkey requires user action`() {
        val outcome = ClaimOutcome.RequiresManualAction(
            giveawayId = "test:123",
            actionType = ManualActionType.PASSKEY
        )
        
        assertTrue("Passkey MUST require user action", outcome.requiresUserAction)
        assertFalse("Passkey should NOT be auto-retryable", outcome.isRetryable)
    }
    
    @Test
    fun `RequiresManualAction for external redemption requires user action`() {
        val outcome = ClaimOutcome.RequiresManualAction(
            giveawayId = "test:123",
            actionType = ManualActionType.EXTERNAL_REDEMPTION,
            actionUrl = "https://store.steampowered.com/account/registerkey"
        )
        
        assertTrue("External redemption MUST require user action", outcome.requiresUserAction)
        assertFalse("External redemption should NOT be auto-retryable", outcome.isRetryable)
    }
    
    // ===== TemporarilyUnavailable Outcome Tests =====
    
    @Test
    fun `TemporarilyUnavailable is retryable`() {
        val outcome = ClaimOutcome.TemporarilyUnavailable(
            giveawayId = "test:123",
            reason = "Server error",
            httpStatus = 503
        )
        
        assertFalse("Temp unavailable should NOT be confirmed owned", outcome.isConfirmedOwned)
        assertTrue("Temp unavailable MUST be retryable", outcome.isRetryable)
        assertFalse("Temp unavailable should not require user action", outcome.requiresUserAction)
        assertFalse("Temp unavailable should not be terminal failure", outcome.isTerminalFailure)
    }
    
    @Test
    fun `TemporarilyUnavailable with retry-after suggestion`() {
        val outcome = ClaimOutcome.TemporarilyUnavailable(
            giveawayId = "test:123",
            reason = "Rate limited",
            httpStatus = 429,
            retryAfterMs = 60_000L
        )
        
        assertTrue("Rate limited should be retryable", outcome.isRetryable)
        assertEquals(60_000L, outcome.retryAfterMs)
    }
    
    // ===== PermanentFailure Outcome Tests =====
    
    @Test
    fun `PermanentFailure is terminal`() {
        val outcome = ClaimOutcome.PermanentFailure(
            giveawayId = "test:123",
            reason = "Offer ended"
        )
        
        assertFalse("Permanent failure should NOT be confirmed owned", outcome.isConfirmedOwned)
        assertFalse("Permanent failure should NOT be retryable", outcome.isRetryable)
        assertFalse("Permanent failure should not require user action", outcome.requiresUserAction)
        assertTrue("Permanent failure MUST be terminal", outcome.isTerminalFailure)
    }
    
    @Test
    fun `PermanentFailure with error code`() {
        val outcome = ClaimOutcome.PermanentFailure(
            giveawayId = "test:123",
            reason = "Not found",
            errorCode = "404"
        )
        
        assertTrue(outcome.isTerminalFailure)
        assertEquals("404", outcome.errorCode)
    }
    
    // ===== Uncertain Outcome Tests (CRITICAL) =====
    
    @Test
    fun `Uncertain outcome is NOT confirmed owned - CRITICAL SAFETY`() {
        val outcome = ClaimOutcome.Uncertain(
            giveawayId = "test:123",
            attemptDescription = "Clicked claim button",
            uncertaintyReason = "No confirmation dialog appeared"
        )
        
        assertFalse("CRITICAL: Uncertain MUST NOT be confirmed owned", outcome.isConfirmedOwned)
        assertFalse("Uncertain should not be retryable automatically", outcome.isRetryable)
        assertTrue("Uncertain MUST require user action/verification", outcome.requiresUserAction)
        assertFalse("Uncertain should not be terminal failure", outcome.isTerminalFailure)
    }
    
    @Test
    fun `Uncertain with various scenarios requires manual verification`() {
        val scenarios = listOf(
            "Request sent but response unclear" to "HTTP 200 but body empty",
            "Page navigation succeeded" to "No confirmation element found",
            "Button clicked" to "Timeout waiting for response",
            "API call completed" to "Response format unexpected"
        )
        
        for ((attempt, reason) in scenarios) {
            val outcome = ClaimOutcome.Uncertain(
                giveawayId = "test:scenario",
                attemptDescription = attempt,
                uncertaintyReason = reason
            )
            
            assertFalse("CRITICAL: Uncertain ($attempt) MUST NOT be owned", outcome.isConfirmedOwned)
            assertTrue("Uncertain ($attempt) MUST require user action", outcome.requiresUserAction)
        }
    }
    
    // ===== DiscoveryOnly Outcome Tests =====
    
    @Test
    fun `DiscoveryOnly is marked correctly`() {
        val outcome = ClaimOutcome.DiscoveryOnly(
            giveawayId = "test:123",
            store = GameStore.FANATICAL
        )
        
        assertFalse("Discovery only should NOT be confirmed owned", outcome.isConfirmedOwned)
        assertFalse("Discovery only should not be retryable", outcome.isRetryable)
        assertFalse("Discovery only should not require user action", outcome.requiresUserAction)
        assertFalse("Discovery only should not be terminal failure", outcome.isTerminalFailure)
        assertTrue("Discovery only MUST be marked as discovery only", outcome.isDiscoveryOnly)
    }
    
    @Test
    fun `DiscoveryOnly for all discovery-only stores`() {
        // Currently all adapters are discovery-only
        val discoveryOnlyStores = GameStore.values()
        
        for (store in discoveryOnlyStores) {
            val outcome = ClaimOutcome.DiscoveryOnly(
                giveawayId = "test:${store.name}",
                store = store
            )
            
            assertTrue("${store.name} discovery only should be marked", outcome.isDiscoveryOnly)
            assertFalse("${store.name} discovery only NOT owned", outcome.isConfirmedOwned)
        }
    }
    
    // ===== Cancelled Outcome Tests =====
    
    @Test
    fun `Cancelled outcome is not confirmed owned`() {
        val outcome = ClaimOutcome.Cancelled(
            giveawayId = "test:123",
            reason = "User requested cancellation"
        )
        
        assertFalse("Cancelled should NOT be confirmed owned", outcome.isConfirmedOwned)
        assertFalse("Cancelled should not be retryable", outcome.isRetryable)
        assertFalse("Cancelled should not require user action", outcome.requiresUserAction)
        assertFalse("Cancelled should not be terminal failure", outcome.isTerminalFailure)
    }
    
    // ===== ManualActionType Coverage =====
    
    @Test
    fun `All ManualActionType values are defined`() {
        val expected = listOf(
            ManualActionType.CAPTCHA,
            ManualActionType.TWO_FACTOR_AUTH,
            ManualActionType.PASSKEY,
            ManualActionType.AGE_VERIFICATION,
            ManualActionType.TERMS_ACCEPTANCE,
            ManualActionType.EXTERNAL_REDEMPTION,
            ManualActionType.PAYMENT_METHOD_REQUIRED,
            ManualActionType.EMAIL_VERIFICATION,
            ManualActionType.UNKNOWN
        )
        
        assertEquals(expected.size, ManualActionType.values().size)
        for (type in expected) {
            assertTrue("ManualActionType.$type should exist", ManualActionType.values().contains(type))
        }
    }
    
    // ===== VerificationMethod Coverage =====
    
    @Test
    fun `All VerificationMethod values are defined`() {
        val expected = listOf(
            VerificationMethod.API_CONFIRMATION,
            VerificationMethod.PAGE_STATE_OWNED,
            VerificationMethod.LIBRARY_CHECK,
            VerificationMethod.BUTTON_STATE_CHANGE,
            VerificationMethod.UNVERIFIED
        )
        
        assertEquals(expected.size, VerificationMethod.values().size)
    }
}
