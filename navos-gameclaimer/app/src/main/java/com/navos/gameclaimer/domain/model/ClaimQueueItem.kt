package com.navos.gameclaimer.domain.model

import kotlinx.serialization.Serializable

/**
 * Phase 13: Claim queue item for persistent recovery queue.
 * 
 * GPT REQUIREMENTS:
 * - Persist work state for recovery after process death/restart
 * - Bounded exponential backoff with jitter for transient errors
 * - Cap retries and avoid tight loops
 * - State transitions must be transactional and safe under concurrent workers
 * - Never persist credentials, cookies, tokens, or raw authentication material
 * 
 * DESIGN:
 * - Minimal data: only what's needed to retry/track
 * - No sensitive data stored
 * - Deterministic retry scheduling with jitter
 */
@Serializable
data class ClaimQueueItem(
    /** Unique ID for this queue entry */
    val queueId: String,
    
    /** ID of the giveaway to claim */
    val giveawayId: String,
    
    /** Store where the giveaway is located */
    val store: GameStore,
    
    /** Current state of this queue item */
    val state: ClaimQueueState,
    
    /** When this item was added to the queue */
    val enqueuedAt: Long,
    
    /** When this item should next be attempted */
    val scheduledAt: Long,
    
    /** Number of attempts made */
    val attemptCount: Int = 0,
    
    /** Maximum attempts allowed */
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    
    /** Last outcome (if any attempt was made) */
    val lastOutcome: ClaimOutcome? = null,
    
    /** When the last attempt was made */
    val lastAttemptAt: Long? = null,
    
    /** Optional priority (higher = sooner) */
    val priority: Int = PRIORITY_NORMAL,
    
    /** Whether this was user-initiated vs automated */
    val isUserInitiated: Boolean = false
) {
    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val PRIORITY_HIGH = 10
        const val PRIORITY_NORMAL = 5
        const val PRIORITY_LOW = 1
        
        // Backoff configuration
        const val INITIAL_BACKOFF_MS = 60_000L        // 1 minute
        const val MAX_BACKOFF_MS = 3_600_000L         // 1 hour
        const val BACKOFF_MULTIPLIER = 2.0
        const val JITTER_FACTOR = 0.2                  // ±20%
        
        /**
         * Create a new queue item for a giveaway.
         * 
         * User-initiated items automatically get high priority unless
         * explicitly overridden.
         */
        fun create(
            giveaway: Giveaway,
            isUserInitiated: Boolean = false,
            priority: Int = PRIORITY_NORMAL
        ): ClaimQueueItem {
            val now = System.currentTimeMillis()
            // User-initiated items get high priority by default
            val effectivePriority = if (isUserInitiated && priority == PRIORITY_NORMAL) {
                PRIORITY_HIGH
            } else {
                priority
            }
            return ClaimQueueItem(
                queueId = "queue:${giveaway.id}:${now}",
                giveawayId = giveaway.id,
                store = giveaway.store,
                state = ClaimQueueState.PENDING,
                enqueuedAt = now,
                scheduledAt = now,
                priority = effectivePriority,
                isUserInitiated = isUserInitiated
            )
        }
    }
    
    /**
     * Calculate the next retry time with exponential backoff and jitter.
     * 
     * Formula: min(INITIAL * MULTIPLIER^attempt, MAX) * (1 ± JITTER)
     */
    fun calculateNextRetryTime(baseTime: Long = System.currentTimeMillis()): Long {
        val exponentialBackoff = INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER, attemptCount.toDouble())
        val cappedBackoff = minOf(exponentialBackoff, MAX_BACKOFF_MS.toDouble())
        
        // Add jitter: ±20% random variation
        val jitterRange = cappedBackoff * JITTER_FACTOR
        val jitter = (Math.random() * 2 - 1) * jitterRange
        
        return baseTime + (cappedBackoff + jitter).toLong()
    }
    
    /**
     * Whether this item has exhausted its retry attempts.
     */
    val isExhausted: Boolean
        get() = attemptCount >= maxAttempts
    
    /**
     * Whether this item can be retried based on last outcome.
     */
    val canRetry: Boolean
        get() = !isExhausted && (lastOutcome?.isRetryable == true)
    
    /**
     * Whether this item requires user action before proceeding.
     */
    val needsUserAction: Boolean
        get() = lastOutcome?.requiresUserAction == true
}

/**
 * State of a claim queue item.
 * 
 * GPT SAFETY REQUIREMENT:
 * - UNCERTAIN state is for items where claim execution may have reached the store
 *   but we don't have confirmation. These MUST NOT automatically retry.
 */
@Serializable
enum class ClaimQueueState {
    /** Waiting to be processed */
    PENDING,
    
    /** Currently being processed */
    IN_PROGRESS,
    
    /** Waiting for retry after transient failure (pre-claim only) */
    RETRY_SCHEDULED,
    
    /** Successfully claimed - terminal state */
    COMPLETED,
    
    /** Failed permanently - terminal state */
    FAILED,
    
    /** Requires user action - paused state */
    BLOCKED_USER_ACTION,
    
    /** Cancelled by user or system - terminal state */
    CANCELLED,
    
    /** Discovery-only adapter, cannot claim - terminal state */
    NOT_SUPPORTED,
    
    /**
     * UNCERTAIN: Claim execution may have reached the store but we lost confirmation.
     * 
     * GPT CRITICAL SAFETY STATE:
     * - Used when: timeout after possible submission, process crash during claim,
     *   interrupted IN_PROGRESS on recovery
     * - MUST NOT automatically retry (could duplicate side effect at store)
     * - Requires manual verification or reconciliation before any action
     * - User must explicitly resolve (verify ownership, then mark complete/retry)
     */
    UNCERTAIN;
    
    /**
     * Whether this is a terminal state (no more automatic processing).
     */
    val isTerminal: Boolean
        get() = this in setOf(COMPLETED, FAILED, CANCELLED, NOT_SUPPORTED)
    
    /**
     * Whether this state can be automatically retried.
     * UNCERTAIN is explicitly excluded - it requires manual resolution.
     */
    val canAutoRetry: Boolean
        get() = this == RETRY_SCHEDULED
    
    /**
     * Whether this state requires user intervention.
     */
    val requiresUserAction: Boolean
        get() = this in setOf(BLOCKED_USER_ACTION, UNCERTAIN)
}

/**
 * Summary of queue state for UI display.
 */
data class ClaimQueueSummary(
    val pendingCount: Int,
    val inProgressCount: Int,
    val retryScheduledCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val blockedCount: Int,
    val uncertainCount: Int,
    val totalCount: Int
) {
    val activeCount: Int
        get() = pendingCount + inProgressCount + retryScheduledCount
    
    val terminalCount: Int
        get() = completedCount + failedCount
    
    /** Items requiring user attention (blocked + uncertain) */
    val requiresAttentionCount: Int
        get() = blockedCount + uncertainCount
}
