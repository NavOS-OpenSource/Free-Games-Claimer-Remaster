package com.navos.gameclaimer.data.local.dao

import androidx.room.*
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 6: Data Access Object for giveaways.
 * 
 * DESIGN:
 * - Upsert pattern for deduplication (newer lastCheckedAt wins)
 * - Separate queries for active vs expired giveaways
 * - Flow-based observers for UI reactivity
 */
@Dao
interface GiveawayDao {
    
    // ===== Insert/Update =====
    
    /**
     * Insert or update a giveaway.
     * On conflict, the newer entry (by lastCheckedAt) wins.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(giveaway: GiveawayEntity)
    
    /**
     * Insert or update multiple giveaways.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(giveaways: List<GiveawayEntity>)
    
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
    fun getActiveGiveaways(now: Long = System.currentTimeMillis()): Flow<List<GiveawayEntity>>
    
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
    fun getActiveGiveawaysForStore(
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
    fun getUnclaimedGiveaways(now: Long = System.currentTimeMillis()): Flow<List<GiveawayEntity>>
    
    // ===== Single Giveaway Operations =====
    
    /**
     * Get a giveaway by ID.
     */
    @Query("SELECT * FROM giveaways WHERE id = :id")
    suspend fun getById(id: String): GiveawayEntity?
    
    /**
     * Get a giveaway by external ID and source.
     */
    @Query("SELECT * FROM giveaways WHERE externalId = :externalId AND source = :source")
    suspend fun getByExternalId(externalId: String, source: String): GiveawayEntity?
    
    // ===== Update Status =====
    
    /**
     * Mark a giveaway as claimed.
     */
    @Query("UPDATE giveaways SET isClaimed = 1, claimedAt = :claimedAt WHERE id = :id")
    suspend fun markClaimed(id: String, claimedAt: Long = System.currentTimeMillis())
    
    /**
     * Mark a giveaway as dismissed.
     */
    @Query("UPDATE giveaways SET isDismissed = 1 WHERE id = :id")
    suspend fun markDismissed(id: String)
    
    /**
     * Update last checked timestamp.
     */
    @Query("UPDATE giveaways SET lastCheckedAt = :lastCheckedAt WHERE id = :id")
    suspend fun updateLastChecked(id: String, lastCheckedAt: Long = System.currentTimeMillis())
    
    // ===== Cleanup =====
    
    /**
     * Delete expired giveaways older than the given threshold.
     */
    @Query("""
        DELETE FROM giveaways 
        WHERE endTime IS NOT NULL 
        AND endTime < :threshold
    """)
    suspend fun deleteExpiredBefore(threshold: Long)
    
    /**
     * Delete all giveaways.
     */
    @Query("DELETE FROM giveaways")
    suspend fun deleteAll()
    
    // ===== Statistics =====
    
    /**
     * Count active giveaways.
     */
    @Query("""
        SELECT COUNT(*) FROM giveaways 
        WHERE isDismissed = 0 
        AND (endTime IS NULL OR endTime > :now)
    """)
    suspend fun countActive(now: Long = System.currentTimeMillis()): Int
    
    /**
     * Count claimed giveaways.
     */
    @Query("SELECT COUNT(*) FROM giveaways WHERE isClaimed = 1")
    suspend fun countClaimed(): Int
}
