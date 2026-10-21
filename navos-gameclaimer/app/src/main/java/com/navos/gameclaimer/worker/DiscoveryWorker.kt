package com.navos.gameclaimer.worker

import android.content.Context
import androidx.work.*
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import com.navos.gameclaimer.data.remote.GamerPowerApiClient
import com.navos.gameclaimer.domain.model.DiscoveryResult
import com.navos.gameclaimer.domain.model.Giveaway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Phase 6: WorkManager worker for background giveaway discovery.
 * 
 * DESIGN:
 * - Runs periodically (default: every 6 hours, minimum 15 min)
 * - Persists discovered giveaways to Room
 * - Idempotent and safe for retries
 * - Handles network failures gracefully
 * 
 * ANDROID LIMITATIONS:
 * - WorkManager is best-effort, not precise timing
 * - May be deferred due to Doze mode, battery optimization
 * - Do not promise exact execution times to users
 * 
 * SECURITY:
 * - Discovery is READ-ONLY (never claims)
 * - All URLs validated before storage
 * - No credentials involved
 */
class DiscoveryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "discovery_worker"
        const val TAG = "discovery"
        
        // Input data keys
        const val KEY_PLATFORM_FILTER = "platform_filter"
        
        // Output data keys
        const val KEY_GIVEAWAYS_FOUND = "giveaways_found"
        const val KEY_ERROR_MESSAGE = "error_message"
        
        // Default intervals
        const val DEFAULT_REPEAT_INTERVAL_HOURS = 6L
        const val MIN_REPEAT_INTERVAL_MINUTES = 15L
        
        /**
         * Enqueue a one-time discovery run.
         */
        fun enqueueOneTime(
            context: Context,
            platformFilter: String? = null
        ): Operation {
            val inputData = workDataOf(
                KEY_PLATFORM_FILTER to platformFilter
            )
            
            val request = OneTimeWorkRequestBuilder<DiscoveryWorker>()
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
                    "${WORK_NAME}_onetime",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
        
        /**
         * Schedule periodic discovery.
         * 
         * NOTE: Android may defer execution due to battery optimization.
         * Do not promise exact timing to users.
         */
        fun schedulePeriodic(
            context: Context,
            intervalHours: Long = DEFAULT_REPEAT_INTERVAL_HOURS
        ): Operation {
            val request = PeriodicWorkRequestBuilder<DiscoveryWorker>(
                repeatInterval = intervalHours,
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
         * Cancel scheduled discovery.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
        
        /**
         * Get work constraints.
         * 
         * Requires network but NOT unmetered (cellular is fine for small API calls).
         */
        private fun getConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        }
    }
    
    private val database by lazy { GameClaimerDatabase.getInstance(applicationContext) }
    private val giveawayDao by lazy { database.giveawayDao() }
    private val apiClient by lazy { GamerPowerApiClient() }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val platformFilter = inputData.getString(KEY_PLATFORM_FILTER)
            
            // Fetch from GamerPower API
            val result = apiClient.fetchGiveaways(platform = platformFilter)
            
            when (result) {
                is DiscoveryResult.Success -> {
                    // Persist to Room
                    val entities = result.giveaways.map { GiveawayEntity.fromDomain(it) }
                    giveawayDao.upsertAll(entities)
                    
                    // Cleanup old expired entries (older than 30 days)
                    val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                    giveawayDao.deleteExpiredBefore(thirtyDaysAgo)
                    
                    val outputData = workDataOf(
                        KEY_GIVEAWAYS_FOUND to result.giveaways.size
                    )
                    Result.success(outputData)
                }
                
                is DiscoveryResult.Empty -> {
                    val outputData = workDataOf(KEY_GIVEAWAYS_FOUND to 0)
                    Result.success(outputData)
                }
                
                is DiscoveryResult.Failed -> {
                    if (result.isRetryable && runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        val outputData = workDataOf(KEY_ERROR_MESSAGE to result.error)
                        Result.failure(outputData)
                    }
                }
                
                is DiscoveryResult.Cancelled -> {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                val outputData = workDataOf(
                    KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")
                )
                Result.failure(outputData)
            }
        }
    }
}
