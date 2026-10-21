package com.navos.gameclaimer.domain.automation

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.domain.adapter.StoreAdapterFactory
import com.navos.gameclaimer.domain.model.*
import com.navos.gameclaimer.worker.ClaimWorker
import com.navos.gameclaimer.worker.DiscoveryWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phase 13: Central orchestration for automation features.
 * 
 * GPT REQUIREMENTS:
 * - Configurable automation scheduling; default interval is 12 hours
 * - Use WorkManager for persistent deferrable background work
 * - Respect Android battery/network constraints and user settings
 * - Prevent overlapping executions and duplicate workers
 * - Store isolation: failure in one store must not prevent others from running
 * 
 * RESPONSIBILITIES:
 * - Schedule/cancel discovery and claim workers
 * - Manage the claim queue (enqueue, cancel, retry)
 * - Provide queue status for UI
 * - Enforce adapter capabilities (supportsClaiming)
 * - Coordinate between discovery and claiming phases
 * 
 * SECURITY:
 * - Never enables claiming for discovery-only adapters
 * - All queue operations are safe against duplicates
 * - No sensitive data in logs or persisted state
 */
class AutomationManager(
    private val context: Context,
    private val database: GameClaimerDatabase,
    private val adapterFactory: StoreAdapterFactory
) {
    private val workManager = WorkManager.getInstance(context)
    private val claimQueueDao = database.claimQueueDao()
    private val giveawayDao = database.giveawayDao()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    // ===== Discovery Scheduling =====
    
    /**
     * Schedule periodic discovery.
     */
    fun scheduleDiscovery(intervalHours: Long = DiscoveryWorker.DEFAULT_REPEAT_INTERVAL_HOURS) {
        DiscoveryWorker.schedulePeriodic(context, intervalHours)
    }
    
    /**
     * Run discovery immediately.
     */
    fun runDiscoveryNow() {
        DiscoveryWorker.enqueueOneTime(context)
    }
    
    /**
     * Cancel discovery scheduling.
     */
    fun cancelDiscovery() {
        DiscoveryWorker.cancel(context)
    }
    
    // ===== Claim Queue Management =====
    
    /**
     * Enqueue a giveaway for claiming.
     * 
     * SECURITY: Checks adapter capabilities before queuing.
     * Discovery-only adapters will be immediately marked as NOT_SUPPORTED.
     * 
     * @param giveaway The giveaway to claim
     * @param isUserInitiated Whether this was explicitly requested by user
     * @return The queue item created, or null if already queued
     */
    suspend fun enqueueForClaim(
        giveaway: Giveaway,
        isUserInitiated: Boolean = false
    ): ClaimQueueItem? {
        // Check if already queued (non-terminal state)
        if (claimQueueDao.isGiveawayQueued(giveaway.id)) {
            return null // Already in queue
        }
        
        // Check adapter capability
        val adapter = adapterFactory.getAdapter(giveaway.store)
        val supportsClaiming = adapter?.supportsClaiming == true
        
        // Create queue item
        val item = ClaimQueueItem.create(
            giveaway = giveaway,
            isUserInitiated = isUserInitiated,
            priority = if (isUserInitiated) ClaimQueueItem.PRIORITY_HIGH else ClaimQueueItem.PRIORITY_NORMAL
        )
        
        // If adapter doesn't support claiming, mark immediately as NOT_SUPPORTED
        val finalItem = if (!supportsClaiming) {
            item.copy(
                state = ClaimQueueState.NOT_SUPPORTED,
                lastOutcome = ClaimOutcome.DiscoveryOnly(
                    giveawayId = giveaway.id,
                    store = giveaway.store
                )
            )
        } else {
            item
        }
        
        // Serialize outcome if present
        val outcomeType = finalItem.lastOutcome?.let { it::class.simpleName }
        val outcomeJson = finalItem.lastOutcome?.let { 
            try { json.encodeToString(it) } catch (e: Exception) { null }
        }
        
        // Insert into database
        val entity = ClaimQueueEntity.fromDomain(finalItem, outcomeType, outcomeJson)
        val inserted = claimQueueDao.insertIgnore(entity)
        
        return if (inserted != -1L) finalItem else null
    }
    
    /**
     * Enqueue multiple giveaways for claiming.
     * 
     * @return Number of items successfully enqueued
     */
    suspend fun enqueueMultiple(
        giveaways: List<Giveaway>,
        isUserInitiated: Boolean = false
    ): Int {
        var count = 0
        for (giveaway in giveaways) {
            if (enqueueForClaim(giveaway, isUserInitiated) != null) {
                count++
            }
        }
        return count
    }
    
    /**
     * Cancel a queued item.
     */
    suspend fun cancelQueueItem(queueId: String): Boolean {
        val entity = claimQueueDao.getById(queueId) ?: return false
        
        // Only cancel if not in terminal state
        val state = ClaimQueueState.valueOf(entity.state)
        if (state in listOf(
            ClaimQueueState.COMPLETED, 
            ClaimQueueState.FAILED, 
            ClaimQueueState.CANCELLED,
            ClaimQueueState.NOT_SUPPORTED
        )) {
            return false
        }
        
        val outcome = ClaimOutcome.Cancelled(
            giveawayId = entity.giveawayId,
            reason = "Cancelled by user"
        )
        
        val updated = claimQueueDao.atomicStateTransition(
            queueId = queueId,
            newState = ClaimQueueState.CANCELLED.name,
            scheduledAt = entity.scheduledAt,
            attemptCount = entity.attemptCount,
            lastAttemptAt = System.currentTimeMillis(),
            lastOutcomeType = "Cancelled",
            lastOutcomeJson = json.encodeToString(outcome),
            expectedVersion = entity.version
        )
        
        return updated > 0
    }
    
    /**
     * Retry a failed or blocked item.
     */
    suspend fun retryQueueItem(queueId: String): Boolean {
        val entity = claimQueueDao.getById(queueId) ?: return false
        
        // Only retry if in a retryable state
        val state = ClaimQueueState.valueOf(entity.state)
        if (state !in listOf(
            ClaimQueueState.FAILED,
            ClaimQueueState.BLOCKED_USER_ACTION,
            ClaimQueueState.CANCELLED
        )) {
            return false
        }
        
        // Reset to pending
        val updated = claimQueueDao.atomicStateTransition(
            queueId = queueId,
            newState = ClaimQueueState.PENDING.name,
            scheduledAt = System.currentTimeMillis(),
            attemptCount = 0, // Reset attempt count for manual retry
            lastAttemptAt = null,
            lastOutcomeType = null,
            lastOutcomeJson = null,
            expectedVersion = entity.version
        )
        
        return updated > 0
    }
    
    /**
     * Remove a queue item (terminal states only).
     */
    suspend fun removeQueueItem(queueId: String): Boolean {
        val entity = claimQueueDao.getById(queueId) ?: return false
        
        // Only remove terminal items
        val state = ClaimQueueState.valueOf(entity.state)
        if (state !in listOf(
            ClaimQueueState.COMPLETED,
            ClaimQueueState.FAILED,
            ClaimQueueState.CANCELLED,
            ClaimQueueState.NOT_SUPPORTED
        )) {
            return false
        }
        
        claimQueueDao.deleteById(queueId)
        return true
    }
    
    // ===== Claim Execution =====
    
    /**
     * Process a specific queue item now.
     */
    fun processQueueItemNow(queueId: String) {
        ClaimWorker.enqueueOneTime(context, queueId = queueId, isUserInitiated = true)
    }
    
    /**
     * Process all ready items.
     */
    fun processReadyItems(maxItems: Int = 10) {
        ClaimWorker.enqueueBatch(context, maxItems)
    }
    
    /**
     * Schedule periodic claim automation.
     * 
     * NOTE: Currently all adapters have supportsClaiming=false,
     * so this will process items but they'll all be marked NOT_SUPPORTED.
     * This is the expected behavior per GPT requirements.
     */
    fun scheduleClaimAutomation(intervalHours: Long = ClaimWorker.DEFAULT_CLAIM_INTERVAL_HOURS) {
        ClaimWorker.schedulePeriodic(context, intervalHours)
    }
    
    /**
     * Cancel claim automation.
     */
    fun cancelClaimAutomation() {
        ClaimWorker.cancelAll(context)
    }
    
    // ===== Status and Observability =====
    
    /**
     * Get all queue items as a Flow.
     */
    fun getAllQueueItems(): Flow<List<ClaimQueueItem>> {
        return claimQueueDao.getAllItems().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get items needing user action.
     */
    fun getItemsNeedingUserAction(): Flow<List<ClaimQueueItem>> {
        return claimQueueDao.getItemsNeedingUserAction().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get queue summary statistics.
     */
    suspend fun getQueueSummary(): ClaimQueueSummary {
        return ClaimQueueSummary(
            pendingCount = claimQueueDao.countByState(ClaimQueueState.PENDING.name),
            inProgressCount = claimQueueDao.countByState(ClaimQueueState.IN_PROGRESS.name),
            retryScheduledCount = claimQueueDao.countByState(ClaimQueueState.RETRY_SCHEDULED.name),
            completedCount = claimQueueDao.countByState(ClaimQueueState.COMPLETED.name),
            failedCount = claimQueueDao.countByState(ClaimQueueState.FAILED.name),
            blockedCount = claimQueueDao.countByState(ClaimQueueState.BLOCKED_USER_ACTION.name),
            uncertainCount = claimQueueDao.countByState(ClaimQueueState.UNCERTAIN.name),
            totalCount = claimQueueDao.countByState(ClaimQueueState.PENDING.name) +
                        claimQueueDao.countByState(ClaimQueueState.IN_PROGRESS.name) +
                        claimQueueDao.countByState(ClaimQueueState.RETRY_SCHEDULED.name) +
                        claimQueueDao.countByState(ClaimQueueState.COMPLETED.name) +
                        claimQueueDao.countByState(ClaimQueueState.FAILED.name) +
                        claimQueueDao.countByState(ClaimQueueState.BLOCKED_USER_ACTION.name) +
                        claimQueueDao.countByState(ClaimQueueState.CANCELLED.name) +
                        claimQueueDao.countByState(ClaimQueueState.NOT_SUPPORTED.name) +
                        claimQueueDao.countByState(ClaimQueueState.UNCERTAIN.name)
        )
    }
    
    /**
     * Get WorkManager info for discovery worker.
     */
    fun getDiscoveryWorkInfo(): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkFlow(DiscoveryWorker.WORK_NAME)
    }
    
    /**
     * Get WorkManager info for claim worker.
     */
    fun getClaimWorkInfo(): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkFlow(ClaimWorker.WORK_NAME)
    }
    
    // ===== Maintenance =====
    
    /**
     * Clean up old terminal queue items.
     * 
     * @param olderThanDays Remove terminal items older than this many days
     */
    suspend fun cleanupOldItems(olderThanDays: Int = 30) {
        val threshold = System.currentTimeMillis() - (olderThanDays.toLong() * 24 * 60 * 60 * 1000)
        claimQueueDao.deleteOldTerminalItems(threshold)
    }
    
    /**
     * Reset stuck items (called on app startup).
     * 
     * Items that were IN_PROGRESS when the app was killed are reset
     * Recover any interrupted claim operations.
     * 
     * GPT SAFETY REQUIREMENT:
     * Called on app startup to handle items that were IN_PROGRESS when app crashed.
     * These items are moved to UNCERTAIN state (NOT retryable) because the claim
     * request may have been submitted to the store before the crash.
     * 
     * UNCERTAIN items require manual user verification before any action.
     */
    suspend fun recoverFromCrash(): Int {
        return claimQueueDao.recoverInterruptedItems()
    }
}
