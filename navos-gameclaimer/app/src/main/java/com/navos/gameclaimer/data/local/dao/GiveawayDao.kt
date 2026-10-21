package com.navos.gameclaimer.data.local.dao

import androidx.room.*
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 6: Data Access Object for giveaways.
 * 
 * DESIGN:
 * - Safe upsert: preserves user state (isClaimed, isDismissed, claimedAt, discoveredAt)
 * - Discovery refresh only updates source data, never loses user actions
 * - Separate queries for active vs expired giveaways
 * - Flow-based observers for UI reactivity
 * 
 * IMPORTANT: We use IGNORE for insert + manual update to preserve user fields.
 * REPLACE would delete-and-recreate, losing isClaimed/isDismissed state.
 */
@Dao
abstract class GiveawayDao {
    
    // ===== Insert/Update =====
    
    /**
     * Insert a new giveaway (fails silently if exists).
     * Use with updateFromSource for safe upsert pattern.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(giveaway: GiveawayEntity): Long
    
    /**
     * Update source-provided fields only, preserving user state.
     * Returns rows affected (0 if not found).
     */
    @Query("""
        UPDATE giveaways SET
            title = :title,
            type = :type,
            claimUrl = :claimUrl,
            originalPrice = :originalPrice,
            imageUrl = :imageUrl,
            description = :description,
            startTime = :startTime,
            endTime = :endTime,
            lastCheckedAt = :lastCheckedAt
        WHERE id = :id
    """)
    abstract suspend fun updateFromSource(
        id: String,
        title: String,
        type: String,
        claimUrl: String,
        originalPrice: String?,
        imageUrl: String?,
        description: String?,
        startTime: Long?,
        endTime: Long?,
        lastCheckedAt: Long
    ): Int
    
    /**
     * Safe upsert: insert if new, update source fields if exists.
     * NEVER loses user state (isClaimed, isDismissed, discoveredAt, claimedAt).
     */
    @Transaction
    open suspend fun upsert(giveaway: GiveawayEntity) {
        val insertResult = insertIgnore(giveaway)
        if (insertResult == -1L) {
            // Row exists, update source fields only
            updateFromSource(
                id = giveaway.id,
                title = giveaway.title,
                type = giveaway.type,
                claimUrl = giveaway.claimUrl,
                originalPrice = giveaway.originalPrice,
                imageUrl = giveaway.imageUrl,
                description = giveaway.description,
                startTime = giveaway.startTime,
                endTime = giveaway.endTime,
                lastCheckedAt = giveaway.lastCheckedAt
            )
        }
    }
    
    /**
     * Safe upsert for multiple giveaways.
     */
    @Transaction
    open suspend fun upsertAll(giveaways: List<GiveawayEntity>) {
        for (giveaway in giveaways) {
            upsert(giveaway)
        }
    }
    
    // ===== Query Active Giveaways =====
    
    /**
     * Get all active (non-expired, non-dismissed) giveaways.
     */
    @Query("""
        SELECT * FROM giveaways 
        WHERE isDismissed = 0 
        AND (endTime IS NULL OR endTime > :now)
        ORDER BY endTime ASC, discoveredAt DESC
    """)
    abstract fun getActiveGiveaways(now: Long = System.currentTimeMillis()): Flow<List<GiveawayEntity>>
    
    /**
     * Get active giveaways for a specific store.
     */
    @Query("""
        SELECT * FROM giveaways 
        WHERE store = :store 
        AND isDismissed = 0 
        AND (endTime IS NULL OR endTime > :now)
        ORDER BY endTime ASC
    """)
    abstract fun getActiveGiveawaysForStore(
        store: String, 
        now: Long = System.currentTimeMillis()
    ): Flow<List<GiveawayEntity>>
    
    /**
     * Get unclaimed giveaways (eligible for auto-claim).
     */
    @Query("""
        SELECT * FROM giveaways 
        WHERE isClaimed = 0 
        AND isDismissed = 0 
        AND (endTime IS NULL OR endTime > :now)
        ORDER BY endTime ASC
    """)
    abstract fun getUnclaimedGiveaways(now: Long = System.currentTimeMillis()): Flow<List<GiveawayEntity>>
    
    // ===== Single Giveaway Operations =====
    
    /**
     * Get a giveaway by ID.
     */
    @Query("SELECT * FROM giveaways WHERE id = :id")
    abstract suspend fun getById(id: String): GiveawayEntity?
    
    /**
     * Get a giveaway by external ID and source.
     */
    @Query("SELECT * FROM giveaways WHERE externalId = :externalId AND source = :source")
    abstract suspend fun getByExternalId(externalId: String, source: String): GiveawayEntity?
    
    // ===== Update Status =====
    
    /**
     * Mark a giveaway as claimed.
     */
    @Query("UPDATE giveaways SET isClaimed = 1, claimedAt = :claimedAt WHERE id = :id")
    abstract suspend fun markClaimed(id: String, claimedAt: Long = System.currentTimeMillis())
    
    /**
     * Mark a giveaway as dismissed.
     */
    @Query("UPDATE giveaways SET isDismissed = 1 WHERE id = :id")
    abstract suspend fun markDismissed(id: String)
    
    /**
     * Update last checked timestamp.
     */
    @Query("UPDATE giveaways SET lastCheckedAt = :lastCheckedAt WHERE id = :id")
    abstract suspend fun updateLastChecked(id: String, lastCheckedAt: Long = System.currentTimeMillis())
    
    // ===== Cleanup =====
    
    /**
     * Delete expired giveaways older than the given threshold.
     * 
     * SAFETY: Never deletes claimed giveaways (preserves claim history).
     */
    @Query("""
        DELETE FROM giveaways 
        WHERE endTime IS NOT NULL 
        AND endTime < :threshold
        AND isClaimed = 0
    """)
    abstract suspend fun deleteExpiredBefore(threshold: Long)
    
    /**
     * Delete all giveaways.
     */
    @Query("DELETE FROM giveaways")
    abstract suspend fun deleteAll()
    
    // ===== Statistics =====
    
    /**
     * Count active giveaways.
     */
    @Query("""
        SELECT COUNT(*) FROM giveaways 
        WHERE isDismissed = 0 
        AND (endTime IS NULL OR endTime > :now)
    """)
    abstract suspend fun countActive(now: Long = System.currentTimeMillis()): Int
    
    /**
     * Count claimed giveaways.
     */
    @Query("SELECT COUNT(*) FROM giveaways WHERE isClaimed = 1")
    abstract suspend fun countClaimed(): Int
}
