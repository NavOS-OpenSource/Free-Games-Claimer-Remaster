package com.navos.gameclaimer.automation

import com.navos.gameclaimer.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 13: Tests for ClaimQueueItem and retry backoff logic.
 * 
 * GPT REQUIREMENTS TESTED:
 * - Bounded exponential backoff with jitter
 * - Cap retries and avoid tight loops
 * - State classification and transitions
 */
class ClaimQueueItemTest {
    
    // ===== Factory Tests =====
    
    @Test
    fun `create factory method produces valid queue item`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway, isUserInitiated = false)
        
        assertEquals(giveaway.id, item.giveawayId)
        assertEquals(giveaway.store, item.store)
        assertEquals(ClaimQueueState.PENDING, item.state)
        assertEquals(0, item.attemptCount)
        assertEquals(ClaimQueueItem.DEFAULT_MAX_ATTEMPTS, item.maxAttempts)
        assertFalse(item.isUserInitiated)
        assertTrue(item.queueId.startsWith("queue:"))
    }
    
    @Test
    fun `user-initiated item has high priority`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway, isUserInitiated = true)
        
        assertTrue(item.isUserInitiated)
        assertEquals(ClaimQueueItem.PRIORITY_HIGH, item.priority)
    }
    
    @Test
    fun `auto-queued item has normal priority`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway, isUserInitiated = false)
        
        assertFalse(item.isUserInitiated)
        assertEquals(ClaimQueueItem.PRIORITY_NORMAL, item.priority)
    }
    
    // ===== Backoff Calculation Tests =====
    
    @Test
    fun `calculateNextRetryTime increases exponentially`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val baseTime = 1_000_000_000L // Fixed base for determinism
        
        val item0 = ClaimQueueItem.create(giveaway).copy(attemptCount = 0)
        val item1 = item0.copy(attemptCount = 1)
        val item2 = item0.copy(attemptCount = 2)
        val item3 = item0.copy(attemptCount = 3)
        
        // Calculate retry times (will have jitter)
        val retry0 = item0.calculateNextRetryTime(baseTime)
        val retry1 = item1.calculateNextRetryTime(baseTime)
        val retry2 = item2.calculateNextRetryTime(baseTime)
        val retry3 = item3.calculateNextRetryTime(baseTime)
        
        // Each should be roughly 2x the previous (within jitter bounds)
        // Attempt 0: ~1 minute, Attempt 1: ~2 minutes, Attempt 2: ~4 minutes, etc.
        
        // Verify increasing trend
        assertTrue("Retry 1 should be after retry 0", retry1 > retry0)
        assertTrue("Retry 2 should be after retry 1", retry2 > retry1)
        assertTrue("Retry 3 should be after retry 2", retry3 > retry2)
    }
    
    @Test
    fun `calculateNextRetryTime respects maximum backoff cap`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val baseTime = 0L
        
        // With many attempts, should hit the cap
        val item = ClaimQueueItem.create(giveaway).copy(attemptCount = 100)
        val retryTime = item.calculateNextRetryTime(baseTime)
        
        // Max backoff is 1 hour (3_600_000ms), plus up to 20% jitter
        val maxWithJitter = ClaimQueueItem.MAX_BACKOFF_MS * 1.2
        
        assertTrue("Retry time should be capped", retryTime <= maxWithJitter)
    }
    
    @Test
    fun `calculateNextRetryTime has jitter (not deterministic)`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(attemptCount = 1)
        val baseTime = 1_000_000_000L
        
        // Run multiple times and check for variation
        val times = (1..100).map { item.calculateNextRetryTime(baseTime) }
        val uniqueTimes = times.toSet()
        
        // With 100 samples and 40% jitter range, we should see variation
        assertTrue("Jitter should produce varied times", uniqueTimes.size > 1)
    }
    
    @Test
    fun `backoff constants are reasonable`() {
        // Verify constants make sense
        assertTrue("Initial backoff should be at least 1 minute", 
            ClaimQueueItem.INITIAL_BACKOFF_MS >= 60_000)
        assertTrue("Max backoff should be at most 1 hour", 
            ClaimQueueItem.MAX_BACKOFF_MS <= 3_600_000)
        assertTrue("Backoff multiplier should be > 1", 
            ClaimQueueItem.BACKOFF_MULTIPLIER > 1.0)
        assertTrue("Jitter factor should be reasonable (0-0.5)", 
            ClaimQueueItem.JITTER_FACTOR in 0.0..0.5)
    }
    
    // ===== Exhaustion Tests =====
    
    @Test
    fun `isExhausted is false when attempts remain`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            attemptCount = 1,
            maxAttempts = 3
        )
        
        assertFalse(item.isExhausted)
    }
    
    @Test
    fun `isExhausted is true when max attempts reached`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            attemptCount = 3,
            maxAttempts = 3
        )
        
        assertTrue(item.isExhausted)
    }
    
    @Test
    fun `isExhausted is true when attempts exceed max`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            attemptCount = 5,
            maxAttempts = 3
        )
        
        assertTrue(item.isExhausted)
    }
    
    // ===== canRetry Tests =====
    
    @Test
    fun `canRetry is true for retryable outcome with attempts remaining`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            attemptCount = 1,
            maxAttempts = 3,
            lastOutcome = ClaimOutcome.TemporarilyUnavailable(
                giveawayId = "test:giveaway:1",
                reason = "Server error"
            )
        )
        
        assertTrue(item.canRetry)
    }
    
    @Test
    fun `canRetry is false for permanent failure`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            attemptCount = 1,
            maxAttempts = 3,
            lastOutcome = ClaimOutcome.PermanentFailure(
                giveawayId = "test:giveaway:1",
                reason = "Not found"
            )
        )
        
        assertFalse(item.canRetry)
    }
    
    @Test
    fun `canRetry is false when exhausted`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            attemptCount = 3,
            maxAttempts = 3,
            lastOutcome = ClaimOutcome.TemporarilyUnavailable(
                giveawayId = "test:giveaway:1",
                reason = "Server error"
            )
        )
        
        assertFalse("Should not retry when exhausted", item.canRetry)
    }
    
    // ===== needsUserAction Tests =====
    
    @Test
    fun `needsUserAction is true for RequiresAuth`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            lastOutcome = ClaimOutcome.RequiresAuth(
                giveawayId = "test:giveaway:1",
                store = GameStore.EPIC_GAMES
            )
        )
        
        assertTrue(item.needsUserAction)
    }
    
    @Test
    fun `needsUserAction is true for RequiresManualAction`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            lastOutcome = ClaimOutcome.RequiresManualAction(
                giveawayId = "test:giveaway:1",
                actionType = ManualActionType.CAPTCHA
            )
        )
        
        assertTrue(item.needsUserAction)
    }
    
    @Test
    fun `needsUserAction is true for Uncertain`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            lastOutcome = ClaimOutcome.Uncertain(
                giveawayId = "test:giveaway:1",
                attemptDescription = "Clicked button",
                uncertaintyReason = "No confirmation"
            )
        )
        
        assertTrue(item.needsUserAction)
    }
    
    @Test
    fun `needsUserAction is false for retryable error`() {
        val giveaway = createTestGiveaway("test:giveaway:1")
        val item = ClaimQueueItem.create(giveaway).copy(
            lastOutcome = ClaimOutcome.TemporarilyUnavailable(
                giveawayId = "test:giveaway:1",
                reason = "Server error"
            )
        )
        
        assertFalse(item.needsUserAction)
    }
    
    // ===== State Coverage =====
    
    @Test
    fun `ClaimQueueState has all expected values`() {
        val expected = listOf(
            ClaimQueueState.PENDING,
            ClaimQueueState.IN_PROGRESS,
            ClaimQueueState.RETRY_SCHEDULED,
            ClaimQueueState.COMPLETED,
            ClaimQueueState.FAILED,
            ClaimQueueState.BLOCKED_USER_ACTION,
            ClaimQueueState.CANCELLED,
            ClaimQueueState.NOT_SUPPORTED
        )
        
        assertEquals(expected.size, ClaimQueueState.values().size)
        for (state in expected) {
            assertTrue(ClaimQueueState.values().contains(state))
        }
    }
    
    // ===== Summary Tests =====
    
    @Test
    fun `ClaimQueueSummary calculates activeCount correctly`() {
        val summary = ClaimQueueSummary(
            pendingCount = 5,
            inProgressCount = 2,
            retryScheduledCount = 3,
            completedCount = 10,
            failedCount = 1,
            blockedCount = 1,
            totalCount = 22
        )
        
        assertEquals(10, summary.activeCount) // 5 + 2 + 3
    }
    
    @Test
    fun `ClaimQueueSummary calculates terminalCount correctly`() {
        val summary = ClaimQueueSummary(
            pendingCount = 5,
            inProgressCount = 2,
            retryScheduledCount = 3,
            completedCount = 10,
            failedCount = 4,
            blockedCount = 1,
            totalCount = 25
        )
        
        assertEquals(14, summary.terminalCount) // 10 + 4
    }
    
    // ===== Helper =====
    
    private fun createTestGiveaway(id: String): Giveaway {
        return Giveaway(
            id = id,
            title = "Test Game",
            store = GameStore.EPIC_GAMES,
            type = GiveawayType.FREE_TO_KEEP,
            claimUrl = "https://store.epicgames.com/test",
            source = DiscoverySource.GAMER_POWER
        )
    }
}
