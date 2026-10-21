package com.navos.gameclaimer.worker

import android.content.Context
import androidx.work.*
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.domain.adapter.StoreAdapterFactory
import com.navos.gameclaimer.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Phase 13: WorkManager worker for claim automation.
 * 
 * GPT REQUIREMENTS:
 * - Use WorkManager for persistent deferrable background work
 * - Prevent overlapping executions and duplicate workers
 * - Support cancellation and graceful process interruption
 * - Do not promise exact execution times
 * - Failure in one store must not prevent other stores from running
 * - Adapters with supportsClaiming=false must remain discovery-only
 * - Never mark claimed without store confirmation
 * - Never perform paid purchases without explicit consent
 * 
 * DESIGN:
 * - Processes items from claim_queue table
 * - Uses optimistic locking to prevent duplicate work
 * - Respects adapter capabilities (supportsClaiming flag)
 * - Records detailed outcomes for each attempt
 * - Schedules retries with exponential backoff
 * 
 * SECURITY:
 * - Discovery-only adapters NEVER attempt claims
 * - No automatic login/modal interaction
 * - No paid purchases
 * - Privacy-safe logging (no credentials/tokens)
 */
class ClaimWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "claim_worker"
        const val TAG = "claiming"
        
        // Input data keys
        const val KEY_GIVEAWAY_ID = "giveaway_id"
        const val KEY_QUEUE_ID = "queue_id"
        const val KEY_MAX_ITEMS = "max_items"
        const val KEY_IS_USER_INITIATED = "is_user_initiated"
        
        // Output data keys
        const val KEY_PROCESSED_COUNT = "processed_count"
        const val KEY_SUCCESS_COUNT = "success_count"
        const val KEY_FAILED_COUNT = "failed_count"
        const val KEY_SKIPPED_COUNT = "skipped_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        
        // Defaults
        const val DEFAULT_MAX_ITEMS = 10
        const val DEFAULT_CLAIM_INTERVAL_HOURS = 12L
        const val MIN_CLAIM_INTERVAL_HOURS = 1L
        const val DELAY_BETWEEN_CLAIMS_MS = 5_000L
        
        private val json = Json { 
            ignoreUnknownKeys = true 
            encodeDefaults = true
        }
        
        /**
         * Enqueue a one-time claim run for a specific giveaway.
         */
        fun enqueueOneTime(
            context: Context,
            giveawayId: String? = null,
            queueId: String? = null,
            isUserInitiated: Boolean = true
        ): Operation {
            val inputData = workDataOf(
                KEY_GIVEAWAY_ID to giveawayId,
                KEY_QUEUE_ID to queueId,
                KEY_IS_USER_INITIATED to isUserInitiated,
                KEY_MAX_ITEMS to 1
            )
            
            val request = OneTimeWorkRequestBuilder<ClaimWorker>()
                .setInputData(inputData)
                .addTag(TAG)
                .setConstraints(getConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            return WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_onetime_${giveawayId ?: queueId ?: "batch"}",
                    ExistingWorkPolicy.KEEP,  // Prevent duplicate for same item
                    request
                )
        }
        
        /**
         * Enqueue processing of ready queue items.
         */
        fun enqueueBatch(
            context: Context,
            maxItems: Int = DEFAULT_MAX_ITEMS
        ): Operation {
            val inputData = workDataOf(KEY_MAX_ITEMS to maxItems)
            
            val request = OneTimeWorkRequestBuilder<ClaimWorker>()
                .setInputData(inputData)
                .addTag(TAG)
                .setConstraints(getConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            return WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_batch",
                    ExistingWorkPolicy.KEEP,  // Prevent overlapping batch runs
                    request
                )
        }
        
        /**
         * Schedule periodic claim automation.
         * 
         * NOTE: Android may defer execution. Do not promise exact timing.
         * This is currently a NO-OP placeholder because all adapters have
         * supportsClaiming=false. Uncomment when claiming is enabled.
         */
        fun schedulePeriodic(
            context: Context,
            intervalHours: Long = DEFAULT_CLAIM_INTERVAL_HOURS
        ): Operation {
            // SECURITY: All adapters currently have supportsClaiming=false
            // This scheduling exists for future use but won't claim anything yet
            val safeInterval = intervalHours.coerceAtLeast(MIN_CLAIM_INTERVAL_HOURS)
            
            val request = PeriodicWorkRequestBuilder<ClaimWorker>(
                repeatInterval = safeInterval,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 30,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .addTag(TAG)
                .setConstraints(getConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            return WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
        
        /**
         * Cancel all claim work.
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
        
        /**
         * Cancel periodic claim automation only.
         */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
        
        /**
         * Get work constraints.
         * 
         * Requires network. WiFi-only is configurable via settings (future).
         */
        private fun getConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        }
    }
    
    private val database by lazy { GameClaimerDatabase.getInstance(applicationContext) }
    private val claimQueueDao by lazy { database.claimQueueDao() }
    private val giveawayDao by lazy { database.giveawayDao() }
    
    // Note: In production, this would be injected via Hilt/Dagger
    // For now, we'll need to get it via the Application
    private val adapterFactory: StoreAdapterFactory by lazy {
        (applicationContext as? com.navos.gameclaimer.GameClaimerApplication)?.adapterFactory
            ?: throw IllegalStateException("Application must provide StoreAdapterFactory")
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var processedCount = 0
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0
        
        try {
            // First, recover any interrupted items from previous run
            val recoveredCount = claimQueueDao.recoverInterruptedItems()
            if (recoveredCount > 0) {
                // Log recovery (privacy-safe)
                android.util.Log.i("ClaimWorker", "Recovered $recoveredCount interrupted queue items")
            }
            
            // Get specific item or batch
            val specificGiveawayId = inputData.getString(KEY_GIVEAWAY_ID)
            val specificQueueId = inputData.getString(KEY_QUEUE_ID)
            val maxItems = inputData.getInt(KEY_MAX_ITEMS, DEFAULT_MAX_ITEMS)
            val isUserInitiated = inputData.getBoolean(KEY_IS_USER_INITIATED, false)
            
            val itemsToProcess = when {
                specificQueueId != null -> {
                    listOfNotNull(claimQueueDao.getById(specificQueueId))
                }
                specificGiveawayId != null -> {
                    listOfNotNull(claimQueueDao.getByGiveawayId(specificGiveawayId))
                }
                else -> {
                    claimQueueDao.getReadyItems(System.currentTimeMillis(), maxItems)
                }
            }
            
            // Process each item
            for (entity in itemsToProcess) {
                if (isStopped) {
                    // Graceful cancellation
                    android.util.Log.i("ClaimWorker", "Work cancelled, stopping processing")
                    break
                }
                
                val outcome = processQueueItem(entity)
                processedCount++
                
                when {
                    outcome.isConfirmedOwned -> successCount++
                    outcome.isTerminalFailure -> failedCount++
                    outcome.isDiscoveryOnly -> skippedCount++
                }
                
                // Delay between items to avoid rate limiting
                if (itemsToProcess.size > 1 && processedCount < itemsToProcess.size) {
                    delay(DELAY_BETWEEN_CLAIMS_MS)
                }
            }
            
            val outputData = workDataOf(
                KEY_PROCESSED_COUNT to processedCount,
                KEY_SUCCESS_COUNT to successCount,
                KEY_FAILED_COUNT to failedCount,
                KEY_SKIPPED_COUNT to skippedCount
            )
            
            Result.success(outputData)
            
        } catch (e: Exception) {
            android.util.Log.e("ClaimWorker", "Claim worker failed", e)
            
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                val outputData = workDataOf(
                    KEY_PROCESSED_COUNT to processedCount,
                    KEY_SUCCESS_COUNT to successCount,
                    KEY_FAILED_COUNT to failedCount,
                    KEY_SKIPPED_COUNT to skippedCount,
                    KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")
                )
                Result.failure(outputData)
            }
        }
    }
    
    /**
     * Process a single queue item.
     * 
     * Returns the outcome, and updates the queue state.
     */
    private suspend fun processQueueItem(entity: ClaimQueueEntity): ClaimOutcome {
        val now = System.currentTimeMillis()
        
        // Try to claim the item for processing (optimistic lock)
        val claimed = claimQueueDao.tryClaimForProcessing(entity.queueId, entity.version)
        if (claimed == 0) {
            // Another worker got it, or state changed
            android.util.Log.d("ClaimWorker", "Item ${entity.queueId} already claimed by another worker")
            return ClaimOutcome.Cancelled(
                giveawayId = entity.giveawayId,
                reason = "Claimed by another worker"
            )
        }
        
        // Get the giveaway details
        val giveawayEntity = giveawayDao.getById(entity.giveawayId)
        if (giveawayEntity == null) {
            // Giveaway no longer exists
            val outcome = ClaimOutcome.PermanentFailure(
                giveawayId = entity.giveawayId,
                reason = "Giveaway not found in database"
            )
            updateQueueWithOutcome(entity, outcome, ClaimQueueState.FAILED)
            return outcome
        }
        
        val giveaway = giveawayEntity.toDomain()
        
        // Check if giveaway is still available
        if (!giveaway.isAvailable()) {
            val outcome = ClaimOutcome.PermanentFailure(
                giveawayId = entity.giveawayId,
                reason = "Giveaway has expired"
            )
            updateQueueWithOutcome(entity, outcome, ClaimQueueState.FAILED)
            return outcome
        }
        
        // Get the adapter for this store
        val adapter = adapterFactory.getAdapter(giveaway.store)
        if (adapter == null) {
            val outcome = ClaimOutcome.PermanentFailure(
                giveawayId = entity.giveawayId,
                reason = "No adapter available for store: ${giveaway.store.displayName}"
            )
            updateQueueWithOutcome(entity, outcome, ClaimQueueState.FAILED)
            return outcome
        }
        
        // CRITICAL: Check if adapter supports claiming
        // GPT REQUIREMENT: "Adapters with supportsClaiming=false must remain discovery-only"
        if (!adapter.supportsClaiming) {
            val outcome = ClaimOutcome.DiscoveryOnly(
                giveawayId = entity.giveawayId,
                store = giveaway.store
            )
            updateQueueWithOutcome(entity, outcome, ClaimQueueState.NOT_SUPPORTED)
            return outcome
        }
        
        // Check authentication status
        if (adapter.requiresAuth && !adapter.isAuthenticated()) {
            val outcome = ClaimOutcome.RequiresAuth(
                giveawayId = entity.giveawayId,
                store = giveaway.store
            )
            updateQueueWithOutcome(entity, outcome, ClaimQueueState.BLOCKED_USER_ACTION)
            return outcome
        }
        
        // Check eligibility before attempting claim
        val eligibility = adapter.checkEligibility(giveaway)
        when (eligibility) {
            ClaimEligibility.ALREADY_OWNED -> {
                val outcome = ClaimOutcome.AlreadyOwned(giveawayId = entity.giveawayId)
                updateQueueWithOutcome(entity, outcome, ClaimQueueState.COMPLETED)
                // Also mark in giveaways table
                giveawayDao.markClaimed(entity.giveawayId)
                return outcome
            }
            ClaimEligibility.REQUIRES_AUTH -> {
                val outcome = ClaimOutcome.RequiresAuth(
                    giveawayId = entity.giveawayId,
                    store = giveaway.store
                )
                updateQueueWithOutcome(entity, outcome, ClaimQueueState.BLOCKED_USER_ACTION)
                return outcome
            }
            ClaimEligibility.REGION_LOCKED -> {
                val outcome = ClaimOutcome.PermanentFailure(
                    giveawayId = entity.giveawayId,
                    reason = "Region locked - not available in your region"
                )
                updateQueueWithOutcome(entity, outcome, ClaimQueueState.FAILED)
                return outcome
            }
            ClaimEligibility.EXPIRED -> {
                val outcome = ClaimOutcome.PermanentFailure(
                    giveawayId = entity.giveawayId,
                    reason = "Giveaway has expired"
                )
                updateQueueWithOutcome(entity, outcome, ClaimQueueState.FAILED)
                return outcome
            }
            ClaimEligibility.ELIGIBLE, ClaimEligibility.UNKNOWN -> {
                // Proceed with claim attempt
            }
        }
        
        // Attempt the claim
        val claimResult = try {
            adapter.claim(giveaway)
        } catch (e: Exception) {
            // Network or unexpected error
            ClaimResult.Failed(
                giveawayId = entity.giveawayId,
                reason = e.message ?: "Unknown error during claim",
                isRetryable = true
            )
        }
        
        // Convert ClaimResult to ClaimOutcome and update state
        val outcome = convertToOutcome(claimResult, giveaway)
        val newState = determineNewState(outcome, entity)
        updateQueueWithOutcome(entity, outcome, newState)
        
        // If successful, also update the giveaways table
        if (outcome.isConfirmedOwned) {
            giveawayDao.markClaimed(entity.giveawayId)
        }
        
        return outcome
    }
    
    /**
     * Convert ClaimResult (from adapter) to ClaimOutcome (our detailed model).
     */
    private fun convertToOutcome(result: ClaimResult, giveaway: Giveaway): ClaimOutcome {
        return when (result) {
            is ClaimResult.Success -> ClaimOutcome.Success(
                giveawayId = result.giveawayId,
                timestamp = result.claimedAt,
                // Note: Adapters would need to provide verification method
                verificationMethod = VerificationMethod.UNVERIFIED
            )
            is ClaimResult.AlreadyOwned -> ClaimOutcome.AlreadyOwned(
                giveawayId = result.giveawayId
            )
            is ClaimResult.Failed -> {
                if (result.isRetryable) {
                    ClaimOutcome.TemporarilyUnavailable(
                        giveawayId = result.giveawayId,
                        reason = result.reason
                    )
                } else {
                    ClaimOutcome.PermanentFailure(
                        giveawayId = result.giveawayId,
                        reason = result.reason
                    )
                }
            }
            is ClaimResult.RequiresInteraction -> ClaimOutcome.RequiresManualAction(
                giveawayId = result.giveawayId,
                actionType = mapInteractionType(result.interactionType),
                actionUrl = giveaway.claimUrl
            )
        }
    }
    
    /**
     * Map interaction type string to ManualActionType enum.
     */
    private fun mapInteractionType(type: String): ManualActionType {
        return when (type.lowercase()) {
            "captcha" -> ManualActionType.CAPTCHA
            "2fa", "mfa", "two_factor" -> ManualActionType.TWO_FACTOR_AUTH
            "passkey" -> ManualActionType.PASSKEY
            "age" -> ManualActionType.AGE_VERIFICATION
            "terms" -> ManualActionType.TERMS_ACCEPTANCE
            "redemption", "key" -> ManualActionType.EXTERNAL_REDEMPTION
            "payment" -> ManualActionType.PAYMENT_METHOD_REQUIRED
            "email" -> ManualActionType.EMAIL_VERIFICATION
            else -> ManualActionType.UNKNOWN
        }
    }
    
    /**
     * Determine new queue state based on outcome.
     */
    private fun determineNewState(outcome: ClaimOutcome, entity: ClaimQueueEntity): ClaimQueueState {
        return when {
            outcome.isConfirmedOwned -> ClaimQueueState.COMPLETED
            outcome.isTerminalFailure -> ClaimQueueState.FAILED
            outcome.isDiscoveryOnly -> ClaimQueueState.NOT_SUPPORTED
            outcome is ClaimOutcome.Cancelled -> ClaimQueueState.CANCELLED
            outcome.requiresUserAction -> ClaimQueueState.BLOCKED_USER_ACTION
            outcome.isRetryable -> {
                // Check if we've exhausted retries
                val newAttemptCount = entity.attemptCount + 1
                if (newAttemptCount >= entity.maxAttempts) {
                    ClaimQueueState.FAILED
                } else {
                    ClaimQueueState.RETRY_SCHEDULED
                }
            }
            else -> ClaimQueueState.FAILED
        }
    }
    
    /**
     * Update queue item with outcome.
     */
    private suspend fun updateQueueWithOutcome(
        entity: ClaimQueueEntity,
        outcome: ClaimOutcome,
        newState: ClaimQueueState
    ) {
        val now = System.currentTimeMillis()
        val newAttemptCount = entity.attemptCount + 1
        
        // Calculate next scheduled time for retries
        val scheduledAt = if (newState == ClaimQueueState.RETRY_SCHEDULED) {
            val item = entity.toDomain().copy(attemptCount = newAttemptCount)
            item.calculateNextRetryTime(now)
        } else {
            entity.scheduledAt
        }
        
        // Serialize outcome (sanitized - no sensitive data in ClaimOutcome)
        val outcomeType = outcome::class.simpleName
        val outcomeJson = try {
            json.encodeToString(outcome)
        } catch (e: Exception) {
            null
        }
        
        // Use atomic transition to prevent race conditions
        val updated = claimQueueDao.atomicStateTransition(
            queueId = entity.queueId,
            newState = newState.name,
            scheduledAt = scheduledAt,
            attemptCount = newAttemptCount,
            lastAttemptAt = now,
            lastOutcomeType = outcomeType,
            lastOutcomeJson = outcomeJson,
            expectedVersion = entity.version + 1 // We already incremented in tryClaimForProcessing
        )
        
        if (updated == 0) {
            // This shouldn't happen in normal operation, but log it
            android.util.Log.w("ClaimWorker", "Failed to update queue item ${entity.queueId} - version conflict")
        }
    }
}
