package com.navos.gameclaimer.data.local.dao

import androidx.room.*
import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 13: Data Access Object for claim queue.
 * 
 * GPT REQUIREMENTS:
 * - Make state transitions transactional and safe under concurrent workers
 * - Ensure duplicate jobs cannot create duplicate claim side effects
 * - Prevent overlapping executions and duplicate workers
 * 
 * DESIGN:
 * - Optimistic locking via version field
 * - Atomic state transitions
 * - Unique constraint on giveawayId prevents duplicates
 * - Ready query uses composite index for efficiency
 */
@Dao
abstract class ClaimQueueDao {
    
    // ===== Insert/Upsert =====
    
    /**
     * Insert a new queue item. Fails silently if giveawayId already queued.
     * 
     * @return Row ID if inserted, -1 if ignored (duplicate)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(item: ClaimQueueEntity): Long
    
    /**
     * Update an existing queue item.
     * 
     * @return Number of rows updated (0 or 1)
     */
    @Update
    abstract suspend fun update(item: ClaimQueueEntity): Int
    
    /**
     * Atomic state transition with optimistic locking.
     * 
     * Only updates if current version matches expectedVersion.
     * Use this for all state changes to ensure safety.
     * 
     * @return Number of rows updated (0 = conflict, 1 = success)
     */
    @Query("""
        UPDATE claim_queue SET 
            state = :newState,
            scheduledAt = :scheduledAt,
            attemptCount = :attemptCount,
            lastAttemptAt = :lastAttemptAt,
            lastOutcomeType = :lastOutcomeType,
            lastOutcomeJson = :lastOutcomeJson,
            version = version + 1
        WHERE queueId = :queueId AND version = :expectedVersion
    """)
    abstract suspend fun atomicStateTransition(
        queueId: String,
        newState: String,
        scheduledAt: Long,
        attemptCount: Int,
        lastAttemptAt: Long?,
        lastOutcomeType: String?,
        lastOutcomeJson: String?,
        expectedVersion: Int
    ): Int
    
    /**
     * Try to claim an item for processing (mark as IN_PROGRESS).
     * 
     * Uses optimistic locking to prevent duplicate workers.
     * Only succeeds if item is PENDING/RETRY_SCHEDULED and version matches.
     * 
     * @return Number of rows updated (0 = item taken by another worker, 1 = success)
     */
    @Query("""
        UPDATE claim_queue SET 
            state = 'IN_PROGRESS',
            version = version + 1
        WHERE queueId = :queueId 
        AND state IN ('PENDING', 'RETRY_SCHEDULED')
        AND version = :expectedVersion
    """)
    abstract suspend fun tryClaimForProcessing(queueId: String, expectedVersion: Int): Int
    
    // ===== Query =====
    
    /**
     * Get a queue item by ID.
     */
    @Query("SELECT * FROM claim_queue WHERE queueId = :queueId")
    abstract suspend fun getById(queueId: String): ClaimQueueEntity?
    
    /**
     * Get a queue item by giveaway ID (if queued).
     */
    @Query("SELECT * FROM claim_queue WHERE giveawayId = :giveawayId")
    abstract suspend fun getByGiveawayId(giveawayId: String): ClaimQueueEntity?
    
    /**
     * Get items ready for processing.
     * 
     * Ready means: (PENDING or RETRY_SCHEDULED) AND scheduledAt <= now
     * Ordered by: priority DESC, scheduledAt ASC
     */
    @Query("""
        SELECT * FROM claim_queue 
        WHERE state IN ('PENDING', 'RETRY_SCHEDULED')
        AND scheduledAt <= :now
        ORDER BY priority DESC, scheduledAt ASC
        LIMIT :limit
    """)
    abstract suspend fun getReadyItems(now: Long, limit: Int = 10): List<ClaimQueueEntity>
    
    /**
     * Get items ready for a specific store.
     */
    @Query("""
        SELECT * FROM claim_queue 
        WHERE state IN ('PENDING', 'RETRY_SCHEDULED')
        AND store = :store
        AND scheduledAt <= :now
        ORDER BY priority DESC, scheduledAt ASC
        LIMIT :limit
    """)
    abstract suspend fun getReadyItemsForStore(
        store: String, 
        now: Long, 
        limit: Int = 10
    ): List<ClaimQueueEntity>
    
    /**
     * Get all items in a specific state.
     */
    @Query("SELECT * FROM claim_queue WHERE state = :state ORDER BY scheduledAt ASC")
    abstract fun getItemsByState(state: String): Flow<List<ClaimQueueEntity>>
    
    /**
     * Get all queue items.
     */
    @Query("SELECT * FROM claim_queue ORDER BY priority DESC, scheduledAt ASC")
    abstract fun getAllItems(): Flow<List<ClaimQueueEntity>>
    
    /**
     * Phase 14: Get recent activity for the Activity screen.
     * Returns items ordered by most recent activity, limited.
     * Uses COALESCE to handle null lastAttemptAt (uses enqueuedAt as fallback).
     */
    @Query("SELECT * FROM claim_queue ORDER BY COALESCE(lastAttemptAt, enqueuedAt) DESC LIMIT :limit")
    abstract fun getRecentActivity(limit: Int = 50): Flow<List<ClaimQueueEntity>>
    
    /**
     * Get items requiring user action.
     */
    @Query("SELECT * FROM claim_queue WHERE state = 'BLOCKED_USER_ACTION' ORDER BY enqueuedAt DESC")
    abstract fun getItemsNeedingUserAction(): Flow<List<ClaimQueueEntity>>
    
    /**
     * Phase 14: Get items in UNCERTAIN state for Needs Attention screen.
     * These require user verification of ownership.
     */
    @Query("SELECT * FROM claim_queue WHERE state = 'UNCERTAIN' ORDER BY lastAttemptAt DESC")
    abstract fun getUncertainItems(): Flow<List<ClaimQueueEntity>>
    
    /**
     * Get items currently in progress.
     */
    @Query("SELECT * FROM claim_queue WHERE state = 'IN_PROGRESS'")
    abstract suspend fun getInProgressItems(): List<ClaimQueueEntity>
    
    // ===== Counts/Summary =====
    
    /**
     * Count items by state.
     */
    @Query("SELECT COUNT(*) FROM claim_queue WHERE state = :state")
    abstract suspend fun countByState(state: String): Int
    
    /**
     * Count ready items.
     */
    @Query("""
        SELECT COUNT(*) FROM claim_queue 
        WHERE state IN ('PENDING', 'RETRY_SCHEDULED')
        AND scheduledAt <= :now
    """)
    abstract suspend fun countReady(now: Long): Int
    
    /**
     * Check if a giveaway is already queued (any non-terminal state).
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM claim_queue 
            WHERE giveawayId = :giveawayId 
            AND state NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'NOT_SUPPORTED')
        )
    """)
    abstract suspend fun isGiveawayQueued(giveawayId: String): Boolean
    
    // ===== Cleanup =====
    
    /**
     * Delete a queue item.
     */
    @Query("DELETE FROM claim_queue WHERE queueId = :queueId")
    abstract suspend fun deleteById(queueId: String)
    
    /**
     * Delete completed items older than threshold.
     */
    @Query("""
        DELETE FROM claim_queue 
        WHERE state IN ('COMPLETED', 'FAILED', 'CANCELLED', 'NOT_SUPPORTED')
        AND lastAttemptAt < :threshold
    """)
    abstract suspend fun deleteOldTerminalItems(threshold: Long)
    
    /**
     * Delete all queue items.
     */
    @Query("DELETE FROM claim_queue")
    abstract suspend fun deleteAll()
    
    // ===== Recovery =====
    
    /**
     * Reset stuck IN_PROGRESS items (e.g., after crash).
     * 
     * GPT SAFETY REQUIREMENT:
     * Items that have been IN_PROGRESS for longer than the timeout
     * are reset to UNCERTAIN - they require manual verification because
     * the claim request may have been submitted to the store.
     * 
     * @param stuckThreshold Timestamp before which items are considered stuck
     * @return Number of items reset
     */
    @Query("""
        UPDATE claim_queue SET 
            state = 'UNCERTAIN',
            version = version + 1
        WHERE state = 'IN_PROGRESS'
        AND lastAttemptAt IS NOT NULL
        AND lastAttemptAt < :stuckThreshold
    """)
    abstract suspend fun resetStuckItems(stuckThreshold: Long): Int
    
    /**
     * Mark all IN_PROGRESS items as UNCERTAIN on startup.
     * 
     * GPT CRITICAL SAFETY REQUIREMENT:
     * On app startup, ANY interrupted IN_PROGRESS items must become UNCERTAIN,
     * NOT automatically retried. The claim request may have reached the store
     * before the app crashed/was killed, so retrying could duplicate the claim.
     * 
     * UNCERTAIN items:
     * - Are NOT included in getReadyItems() queries
     * - Require manual user verification before any action
     * - User must explicitly confirm ownership or allow retry
     */
    @Query("""
        UPDATE claim_queue SET 
            state = 'UNCERTAIN',
            version = version + 1
        WHERE state = 'IN_PROGRESS'
    """)
    abstract suspend fun recoverInterruptedItems(): Int
    
    /**
     * Count UNCERTAIN items requiring verification.
     */
    @Query("SELECT COUNT(*) FROM claim_queue WHERE state = 'UNCERTAIN'")
    abstract suspend fun countUncertain(): Int
    
    /**
     * Manually resolve an UNCERTAIN item after user verification.
     * 
     * This is the ONLY way to move an item out of UNCERTAIN state.
     * The user must verify the claim status at the store first.
     * 
     * @param queueId The item to resolve
     * @param verified True if user confirmed ownership, false to allow retry
     * @param expectedVersion For optimistic locking
     * @return Number of rows updated (0 = conflict, 1 = success)
     */
    @Query("""
        UPDATE claim_queue SET 
            state = CASE 
                WHEN :verified THEN 'COMPLETED'
                ELSE 'RETRY_SCHEDULED'
            END,
            scheduledAt = :scheduledAt,
            version = version + 1
        WHERE queueId = :queueId 
        AND state = 'UNCERTAIN'
        AND version = :expectedVersion
    """)
    abstract suspend fun resolveUncertainItem(
        queueId: String,
        verified: Boolean,
        scheduledAt: Long,
        expectedVersion: Int
    ): Int
}
