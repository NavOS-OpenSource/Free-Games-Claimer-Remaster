package com.navos.gameclaimer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.navos.gameclaimer.domain.model.ClaimQueueItem
import com.navos.gameclaimer.domain.model.ClaimQueueState
import com.navos.gameclaimer.domain.model.GameStore

/**
 * Phase 13: Room entity for persistent claim queue.
 * 
 * GPT REQUIREMENTS:
 * - Persist work state for recovery after process death/restart
 * - Store only the minimum necessary information
 * - Never persist credentials, cookies, access tokens, or raw authentication material
 * - Make state transitions transactional and safe under concurrent workers
 * 
 * SECURITY:
 * - NO sensitive data: no cookies, tokens, passwords, session data
 * - Only stores IDs, state, timestamps, and outcome metadata
 * - Outcome stored as JSON string (sanitized before storage)
 */
@Entity(
    tableName = "claim_queue",
    indices = [
        Index(value = ["giveawayId"], unique = true),
        Index(value = ["state"]),
        Index(value = ["scheduledAt"]),
        Index(value = ["store"]),
        Index(value = ["state", "scheduledAt"])  // For efficient query of ready items
    ]
)
data class ClaimQueueEntity(
    /** Unique queue entry ID */
    @PrimaryKey
    val queueId: String,
    
    /** Referenced giveaway ID */
    val giveawayId: String,
    
    /** Store name (enum name) */
    val store: String,
    
    /** Current state (enum name) */
    val state: String,
    
    /** When added to queue (epoch millis) */
    val enqueuedAt: Long,
    
    /** When to attempt next (epoch millis) */
    val scheduledAt: Long,
    
    /** Number of attempts made */
    val attemptCount: Int,
    
    /** Maximum attempts allowed */
    val maxAttempts: Int,
    
    /** Last outcome type (enum/class name) for filtering */
    val lastOutcomeType: String? = null,
    
    /** Last outcome details (JSON, sanitized) */
    val lastOutcomeJson: String? = null,
    
    /** When last attempted (epoch millis) */
    val lastAttemptAt: Long? = null,
    
    /** Priority (higher = sooner) */
    val priority: Int,
    
    /** Whether user-initiated */
    val isUserInitiated: Boolean,
    
    /** Version for optimistic locking */
    val version: Int = 1
) {
    /**
     * Convert to domain model.
     * 
     * Note: lastOutcome is not fully deserialized here - it's done
     * in the repository layer where the serializer is available.
     */
    fun toDomain(): ClaimQueueItem {
        return ClaimQueueItem(
            queueId = queueId,
            giveawayId = giveawayId,
            store = GameStore.valueOf(store),
            state = ClaimQueueState.valueOf(state),
            enqueuedAt = enqueuedAt,
            scheduledAt = scheduledAt,
            attemptCount = attemptCount,
            maxAttempts = maxAttempts,
            lastOutcome = null, // Deserialized in repository
            lastAttemptAt = lastAttemptAt,
            priority = priority,
            isUserInitiated = isUserInitiated
        )
    }
    
    companion object {
        /**
         * Create entity from domain model.
         * 
         * Note: lastOutcome must be serialized to JSON by the repository
         * before calling this, and passed as outcomeJson.
         */
        fun fromDomain(
            item: ClaimQueueItem,
            outcomeType: String? = null,
            outcomeJson: String? = null
        ): ClaimQueueEntity {
            return ClaimQueueEntity(
                queueId = item.queueId,
                giveawayId = item.giveawayId,
                store = item.store.name,
                state = item.state.name,
                enqueuedAt = item.enqueuedAt,
                scheduledAt = item.scheduledAt,
                attemptCount = item.attemptCount,
                maxAttempts = item.maxAttempts,
                lastOutcomeType = outcomeType,
                lastOutcomeJson = outcomeJson,
                lastAttemptAt = item.lastAttemptAt,
                priority = item.priority,
                isUserInitiated = item.isUserInitiated
            )
        }
    }
}
