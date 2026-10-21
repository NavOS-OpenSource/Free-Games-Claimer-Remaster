package com.navos.gameclaimer.data.local.dao

import androidx.room.*
import com.navos.gameclaimer.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for claimed games history.
 */
@Dao
interface ClaimedGameDao {
    
    @Query("SELECT * FROM claimed_games ORDER BY claimedAt DESC")
    fun getAllClaimedGames(): Flow<List<ClaimedGameEntity>>
    
    @Query("SELECT * FROM claimed_games WHERE store = :store ORDER BY claimedAt DESC")
    fun getClaimedGamesByStore(store: String): Flow<List<ClaimedGameEntity>>
    
    @Query("SELECT * FROM claimed_games WHERE store = :store AND gameId = :gameId LIMIT 1")
    suspend fun getClaimedGame(store: String, gameId: String): ClaimedGameEntity?
    
    @Query("SELECT COUNT(*) FROM claimed_games")
    suspend fun getClaimedCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: ClaimedGameEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(games: List<ClaimedGameEntity>): List<Long>
    
    @Delete
    suspend fun delete(game: ClaimedGameEntity)
    
    @Query("DELETE FROM claimed_games")
    suspend fun deleteAll()
    
    // For export - returns all without Flow
    @Query("SELECT * FROM claimed_games ORDER BY claimedAt DESC")
    suspend fun getAllForExport(): List<ClaimedGameEntity>
}

/**
 * DAO for app settings.
 */
@Dao
interface SettingsDao {
    
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): SettingsEntity?
    
    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun getSettingFlow(key: String): Flow<SettingsEntity?>
    
    @Query("SELECT * FROM settings")
    suspend fun getAllSettings(): List<SettingsEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingsEntity)
    
    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
    
    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}

/**
 * DAO for store sessions.
 * 
 * SECURITY: getForExport() excludes secret fields.
 */
@Dao
interface StoreSessionDao {
    
    @Query("SELECT * FROM store_sessions WHERE store = :store LIMIT 1")
    suspend fun getSession(store: String): StoreSessionEntity?
    
    @Query("SELECT * FROM store_sessions WHERE store = :store")
    fun getSessionFlow(store: String): Flow<StoreSessionEntity?>
    
    @Query("SELECT * FROM store_sessions")
    suspend fun getAllSessions(): List<StoreSessionEntity>
    
    // For export - EXCLUDES sessionTokenEncrypted
    @Query("SELECT id, store, displayName, lastVerified, isAuthenticated, NULL as sessionTokenEncrypted, 0 as isSecret FROM store_sessions")
    suspend fun getAllForExport(): List<StoreSessionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: StoreSessionEntity)
    
    @Query("DELETE FROM store_sessions WHERE store = :store")
    suspend fun delete(store: String)
    
    @Query("DELETE FROM store_sessions")
    suspend fun deleteAll()
}

/**
 * DAO for available (unclaimed) games.
 */
@Dao
interface AvailableGameDao {
    
    @Query("SELECT * FROM available_games WHERE store = :store ORDER BY offerEndTime ASC")
    fun getAvailableByStore(store: String): Flow<List<AvailableGameEntity>>
    
    @Query("SELECT * FROM available_games WHERE offerEndTime > :now OR offerEndTime IS NULL ORDER BY store, offerEndTime ASC")
    fun getActiveGames(now: Long): Flow<List<AvailableGameEntity>>
    
    @Query("SELECT * FROM available_games WHERE store = :store AND gameId = :gameId LIMIT 1")
    suspend fun getGame(store: String, gameId: String): AvailableGameEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: AvailableGameEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(games: List<AvailableGameEntity>)
    
    @Query("DELETE FROM available_games WHERE offerEndTime < :now AND offerEndTime IS NOT NULL")
    suspend fun deleteExpired(now: Long): Int
    
    @Query("DELETE FROM available_games")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM available_games ORDER BY store, offerEndTime ASC")
    suspend fun getAllForExport(): List<AvailableGameEntity>
}
