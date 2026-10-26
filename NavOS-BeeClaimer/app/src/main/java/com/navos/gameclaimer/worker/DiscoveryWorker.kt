package com.navos.gameclaimer.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.navos.gameclaimer.data.adapter.SteamStoreAdapter
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import com.navos.gameclaimer.data.remote.GamerPowerApiClient
import com.navos.gameclaimer.domain.model.DiscoveryResult
import com.navos.gameclaimer.domain.model.Giveaway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
        private const val TAG = "DiscoveryWorker"
        
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
    private val steamAdapter by lazy { SteamStoreAdapter() }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val platformFilter = inputData.getString(KEY_PLATFORM_FILTER)
            
            // Fetch from BOTH sources in parallel
            val gamerPowerDeferred = async { apiClient.fetchGiveaways(platform = platformFilter) }
            val steamDirectDeferred = async { steamAdapter.discoverGiveaways(timeoutMs = 30_000) }
            
            val gamerPowerResult = gamerPowerDeferred.await()
            val steamDirectResult = steamDirectDeferred.await()
            
            // Collect all giveaways
            val allGiveaways = mutableListOf<Giveaway>()
            var hadSuccess = false
            
            // Process GamerPower results
            when (gamerPowerResult) {
                is DiscoveryResult.Success -> {
                    hadSuccess = true
                    allGiveaways.addAll(gamerPowerResult.giveaways)
                    Log.d(TAG, "GamerPower: found ${gamerPowerResult.giveaways.size} giveaways")
                }
                is DiscoveryResult.Empty -> {
                    hadSuccess = true
                    Log.d(TAG, "GamerPower: no giveaways found")
                }
                is DiscoveryResult.Failed -> {
                    Log.w(TAG, "GamerPower failed: ${gamerPowerResult.error}")
                }
                is DiscoveryResult.Cancelled -> {
                    Log.w(TAG, "GamerPower cancelled")
                }
            }
            
            // Process Steam direct search results (MANDATORY - always fetch)
            when (steamDirectResult) {
                is DiscoveryResult.Success -> {
                    hadSuccess = true
                    // Merge Steam results, avoiding duplicates by URL
                    val existingUrls = allGiveaways.map { it.claimUrl }.toSet()
                    val newSteamGames = steamDirectResult.giveaways.filter { it.claimUrl !in existingUrls }
                    allGiveaways.addAll(newSteamGames)
                    Log.d(TAG, "Steam direct: found ${steamDirectResult.giveaways.size} giveaways, ${newSteamGames.size} new")
                }
                is DiscoveryResult.Empty -> {
                    hadSuccess = true
                    Log.d(TAG, "Steam direct: no giveaways found")
                }
                is DiscoveryResult.Failed -> {
                    Log.w(TAG, "Steam direct failed: ${steamDirectResult.error}")
                }
                is DiscoveryResult.Cancelled -> {
                    Log.w(TAG, "Steam direct cancelled")
                }
            }
            
            if (hadSuccess) {
                // Deduplicate by normalized URL
                val deduped = allGiveaways.distinctBy { normalizeUrl(it.claimUrl) }
                Log.d(TAG, "Total after dedup: ${deduped.size} giveaways")
                
                // Persist to Room
                val entities = deduped.map { GiveawayEntity.fromDomain(it) }
                giveawayDao.upsertAll(entities)
                
                // Cleanup old expired entries (older than 30 days)
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                giveawayDao.deleteExpiredBefore(thirtyDaysAgo)
                
                val outputData = workDataOf(
                    KEY_GIVEAWAYS_FOUND to deduped.size
                )
                Result.success(outputData)
            } else {
                // Both sources failed
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    val outputData = workDataOf(KEY_ERROR_MESSAGE to "All discovery sources failed")
                    Result.failure(outputData)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed", e)
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
    
    /**
     * Normalize URL for deduplication.
     * Removes query params except essential ones, normalizes protocol.
     */
    private fun normalizeUrl(url: String): String {
        return url
            .replace("http://", "https://")
            .replace(Regex("\\?.*"), "")  // Remove query params
            .trimEnd('/')
            .lowercase()
    }
}
