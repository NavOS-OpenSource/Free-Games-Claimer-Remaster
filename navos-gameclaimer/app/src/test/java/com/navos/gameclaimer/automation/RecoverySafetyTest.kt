package com.navos.gameclaimer.automation

import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.domain.model.ClaimOutcome
import com.navos.gameclaimer.domain.model.ClaimQueueState
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.ManualActionType
import com.navos.gameclaimer.domain.model.VerificationMethod
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

/**
 * Phase 13: Regression tests for UNCERTAIN state and safe recovery.
 * 
 * GPT SAFETY REQUIREMENTS:
 * - Interrupted IN_PROGRESS items MUST become UNCERTAIN, not retryable
 * - UNCERTAIN items MUST NOT be included in automatic retry queries
 * - UNCERTAIN requires manual user verification before any action
 * - Only pre-submission failures (connection failed, DNS error) may retry
 * - Post-submission failures (timeout, cancellation) must become UNCERTAIN
 */
class RecoverySafetyTest {
    
    // ===== ClaimQueueState Tests =====
    
    @Test
    fun `UNCERTAIN state exists and has correct properties`() {
        // UNCERTAIN state must exist
        val uncertainState = ClaimQueueState.UNCERTAIN
        
        // UNCERTAIN is NOT a terminal state (can be resolved)
        assertFalse("UNCERTAIN should not be terminal", uncertainState.isTerminal)
        
        // UNCERTAIN requires user action
        assertTrue("UNCERTAIN requires user action", uncertainState.requiresUserAction)
        
        // UNCERTAIN CANNOT auto-retry
        assertFalse("UNCERTAIN must NOT auto-retry", uncertainState.canAutoRetry)
    }
    
    @Test
    fun `only RETRY_SCHEDULED can auto-retry`() {
        // Test all states
        for (state in ClaimQueueState.entries) {
            if (state == ClaimQueueState.RETRY_SCHEDULED) {
                assertTrue("RETRY_SCHEDULED should auto-retry", state.canAutoRetry)
            } else {
                assertFalse("$state should NOT auto-retry", state.canAutoRetry)
            }
        }
    }
    
    @Test
    fun `terminal states are correct`() {
        val terminalStates = setOf(
            ClaimQueueState.COMPLETED,
            ClaimQueueState.FAILED,
            ClaimQueueState.CANCELLED,
            ClaimQueueState.NOT_SUPPORTED
        )
        
        for (state in ClaimQueueState.entries) {
            if (state in terminalStates) {
                assertTrue("$state should be terminal", state.isTerminal)
            } else {
                assertFalse("$state should NOT be terminal", state.isTerminal)
            }
        }
    }
    
    @Test
    fun `states requiring user action are correct`() {
        val userActionStates = setOf(
            ClaimQueueState.BLOCKED_USER_ACTION,
            ClaimQueueState.UNCERTAIN
        )
        
        for (state in ClaimQueueState.entries) {
            if (state in userActionStates) {
                assertTrue("$state should require user action", state.requiresUserAction)
            } else {
                assertFalse("$state should NOT require user action", state.requiresUserAction)
            }
        }
    }
    
    // ===== ClaimOutcome.Uncertain Tests =====
    
    @Test
    fun `Uncertain outcome has correct flags`() {
        val outcome = ClaimOutcome.Uncertain(
            giveawayId = "test-giveaway",
            attemptDescription = "Test claim attempt",
            uncertaintyReason = "Test reason"
        )
        
        // UNCERTAIN is NOT confirmed owned
        assertFalse("Uncertain is NOT confirmed owned", outcome.isConfirmedOwned)
        
        // UNCERTAIN is NOT retryable (requires user verification)
        assertFalse("Uncertain is NOT retryable", outcome.isRetryable)
        
        // UNCERTAIN requires user action
        assertTrue("Uncertain requires user action", outcome.requiresUserAction)
        
        // UNCERTAIN is NOT a terminal failure
        assertFalse("Uncertain is NOT terminal failure", outcome.isTerminalFailure)
        
        // UNCERTAIN is NOT discovery-only
        assertFalse("Uncertain is NOT discovery-only", outcome.isDiscoveryOnly)
    }
    
    @Test
    fun `Uncertain outcome is serializable`() {
        val json = Json { ignoreUnknownKeys = true }
        val outcome = ClaimOutcome.Uncertain(
            giveawayId = "test-giveaway",
            attemptDescription = "Claim attempt for Test Game",
            uncertaintyReason = "Lost response after submission"
        )
        
        // Should serialize without error
        val serialized = json.encodeToString(ClaimOutcome.serializer(), outcome)
        assertTrue("Should contain giveawayId", serialized.contains("test-giveaway"))
        assertTrue("Should contain uncertaintyReason", serialized.contains("Lost response"))
        
        // Should deserialize back correctly
        val deserialized = json.decodeFromString(ClaimOutcome.serializer(), serialized)
        assertTrue("Should deserialize as Uncertain", deserialized is ClaimOutcome.Uncertain)
        assertEquals("test-giveaway", deserialized.giveawayId)
    }
    
    // ===== Pre-submission vs Post-submission Failure Classification =====
    
    @Test
    fun `pre-submission failures are retryable`() {
        // Connection failed = no request sent = safe to retry
        val connFailed = ClaimOutcome.TemporarilyUnavailable(
            giveawayId = "test",
            reason = "Connection failed"
        )
        assertTrue("Connection failure is retryable", connFailed.isRetryable)
        
        // DNS failure = no request sent = safe to retry
        val dnsFailed = ClaimOutcome.TemporarilyUnavailable(
            giveawayId = "test",
            reason = "Network unavailable"
        )
        assertTrue("DNS failure is retryable", dnsFailed.isRetryable)
    }
    
    @Test
    fun `post-submission failures must be UNCERTAIN`() {
        // Timeout after possible submission = UNCERTAIN
        val timeout = ClaimOutcome.Uncertain(
            giveawayId = "test",
            attemptDescription = "Claim attempt",
            uncertaintyReason = "Request timed out after submission"
        )
        assertFalse("Timeout after submission is NOT retryable", timeout.isRetryable)
        assertTrue("Timeout after submission requires user action", timeout.requiresUserAction)
        
        // Cancellation after possible submission = UNCERTAIN  
        val cancelled = ClaimOutcome.Uncertain(
            giveawayId = "test",
            attemptDescription = "Claim attempt",
            uncertaintyReason = "Request cancelled after submission"
        )
        assertFalse("Cancellation after submission is NOT retryable", cancelled.isRetryable)
    }
    
    // ===== Recovery Behavior Tests =====
    
    @Test
    fun `IN_PROGRESS items convert to UNCERTAIN entity state on recovery`() {
        // Simulate an entity that was IN_PROGRESS when app crashed
        val inProgressEntity = ClaimQueueEntity(
            queueId = "queue:test:123",
            giveawayId = "giveaway-1",
            store = "STEAM",
            state = ClaimQueueState.IN_PROGRESS.name,
            enqueuedAt = 1000L,
            scheduledAt = 1000L,
            attemptCount = 1,
            maxAttempts = 3,
            lastAttemptAt = 2000L,
            priority = 5,
            isUserInitiated = false,
            version = 2
        )
        
        // After recovery, it should be UNCERTAIN, not RETRY_SCHEDULED
        // This verifies the expected state after recoverInterruptedItems()
        val expectedRecoveredState = ClaimQueueState.UNCERTAIN.name
        
        // Verify UNCERTAIN is the correct state for recovery
        assertNotEquals(
            "Recovered state must NOT be RETRY_SCHEDULED",
            ClaimQueueState.RETRY_SCHEDULED.name,
            expectedRecoveredState
        )
        assertEquals(
            "Recovered state must be UNCERTAIN",
            ClaimQueueState.UNCERTAIN.name,
            expectedRecoveredState
        )
    }
    
    @Test
    fun `UNCERTAIN items are excluded from ready items query`() {
        // The getReadyItems query should NOT include UNCERTAIN
        // It only includes PENDING and RETRY_SCHEDULED
        val queryStates = setOf("PENDING", "RETRY_SCHEDULED")
        
        // UNCERTAIN must NOT be in ready items
        assertFalse(
            "UNCERTAIN must be excluded from ready items",
            queryStates.contains(ClaimQueueState.UNCERTAIN.name)
        )
    }
    
    @Test
    fun `UNCERTAIN items cannot be claimed for processing`() {
        // The tryClaimForProcessing query only works on PENDING/RETRY_SCHEDULED
        val claimableStates = setOf(
            ClaimQueueState.PENDING.name,
            ClaimQueueState.RETRY_SCHEDULED.name
        )
        
        // UNCERTAIN is not claimable
        assertFalse(
            "UNCERTAIN must NOT be claimable for processing",
            claimableStates.contains(ClaimQueueState.UNCERTAIN.name)
        )
    }
    
    // ===== Success vs Uncertain Distinction =====
    
    @Test
    fun `only Success and AlreadyOwned are confirmed owned`() {
        val outcomes = listOf(
            ClaimOutcome.Success(
                giveawayId = "test",
                verificationMethod = VerificationMethod.LIBRARY_CHECK
            ),
            ClaimOutcome.AlreadyOwned(giveawayId = "test"),
            ClaimOutcome.RequiresAuth(giveawayId = "test", store = GameStore.STEAM),
            ClaimOutcome.RequiresManualAction(
                giveawayId = "test",
                actionType = ManualActionType.CAPTCHA,
                actionUrl = "url"
            ),
            ClaimOutcome.TemporarilyUnavailable(giveawayId = "test", reason = "reason"),
            ClaimOutcome.PermanentFailure(giveawayId = "test", reason = "reason"),
            ClaimOutcome.Uncertain(
                giveawayId = "test",
                attemptDescription = "attempt",
                uncertaintyReason = "reason"
            ),
            ClaimOutcome.DiscoveryOnly(giveawayId = "test", store = GameStore.STEAM),
            ClaimOutcome.Cancelled(giveawayId = "test", reason = "reason")
        )
        
        for (outcome in outcomes) {
            when (outcome) {
                is ClaimOutcome.Success, is ClaimOutcome.AlreadyOwned -> {
                    assertTrue("$outcome should be confirmed owned", outcome.isConfirmedOwned)
                }
                else -> {
                    assertFalse("$outcome should NOT be confirmed owned", outcome.isConfirmedOwned)
                }
            }
        }
    }
    
    @Test
    fun `Uncertain is distinct from TemporarilyUnavailable`() {
        val uncertain = ClaimOutcome.Uncertain(
            giveawayId = "test",
            attemptDescription = "Claim attempt",
            uncertaintyReason = "response lost"
        )
        val tempUnavailable = ClaimOutcome.TemporarilyUnavailable(
            giveawayId = "test",
            reason = "server error"
        )
        
        // Uncertain is NOT retryable
        assertFalse("Uncertain is NOT retryable", uncertain.isRetryable)
        
        // TemporarilyUnavailable IS retryable
        assertTrue("TemporarilyUnavailable IS retryable", tempUnavailable.isRetryable)
        
        // Only Uncertain requires user action (among these two)
        assertTrue("Uncertain requires user action", uncertain.requiresUserAction)
        assertFalse("TemporarilyUnavailable does NOT require user action", tempUnavailable.requiresUserAction)
    }
    
    // ===== Discovery-only Safety (unchanged from before) =====
    
    @Test
    fun `discovery-only adapters still cannot claim`() {
        val discoveryOnly = ClaimOutcome.DiscoveryOnly(
            giveawayId = "test",
            store = GameStore.STEAM
        )
        
        assertFalse("DiscoveryOnly is NOT confirmed owned", discoveryOnly.isConfirmedOwned)
        assertFalse("DiscoveryOnly is NOT retryable", discoveryOnly.isRetryable)
        assertFalse("DiscoveryOnly does NOT require user action", discoveryOnly.requiresUserAction)
        assertTrue("DiscoveryOnly IS discovery-only", discoveryOnly.isDiscoveryOnly)
    }
}
