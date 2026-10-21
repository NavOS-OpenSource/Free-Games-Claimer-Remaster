package com.navos.gameclaimer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.navos.gameclaimer.data.local.dao.*
import com.navos.gameclaimer.data.local.entity.*

/**
 * Room database for NavOS GameClaimer.
 * 
 * Schema version history:
 * - Version 1: Initial schema with claimed_games, settings, store_sessions, available_games
 * 
 * SECURITY NOTES:
 * - Database file is stored in app-private storage
 * - No WAL mode to prevent data leakage in journal files
 * - Backup is disabled at app level (AndroidManifest)
 * - store_sessions table stores ENCRYPTED tokens using Android Keystore (Phase 5)
 *   - Currently stores placeholder fields; actual encryption implemented in Phase 5
 *   - sessionTokenEncrypted will contain AES-GCM encrypted tokens
 *   - Keys are managed via androidx.security:security-crypto (EncryptedSharedPreferences pattern)
 * - Sessions are NEVER included in export/import operations
 * - See data_extraction_rules.xml for backup exclusions
 */
@Database(
    entities = [
        ClaimedGameEntity::class,
        SettingsEntity::class,
        StoreSessionEntity::class,
        AvailableGameEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class GameClaimerDatabase : RoomDatabase() {
    
    abstract fun claimedGameDao(): ClaimedGameDao
    abstract fun settingsDao(): SettingsDao
    abstract fun storeSessionDao(): StoreSessionDao
    abstract fun availableGameDao(): AvailableGameDao
    
    companion object {
        private const val DATABASE_NAME = "gameclaimer.db"
        
        @Volatile
        private var INSTANCE: GameClaimerDatabase? = null
        
        fun getInstance(context: Context): GameClaimerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): GameClaimerDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                GameClaimerDatabase::class.java,
                DATABASE_NAME
            )
            // Disable WAL for security - prevents journal file data leakage
            .setJournalMode(JournalMode.TRUNCATE)
            // Add migrations
            .addMigrations(*ALL_MIGRATIONS)
            // In debug, allow destructive migration for dev convenience
            // In production, always provide proper migrations
            .build()
        }
        
        /**
         * All database migrations.
         * 
         * MIGRATION STRATEGY:
         * 1. Always preserve user data during migrations
         * 2. Never drop tables with user history
         * 3. Add new columns with sensible defaults
         * 4. Test migrations with synthetic data before release
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            // Future migrations go here
            // MIGRATION_1_2,
            // MIGRATION_2_3,
        )
        
        // Example migration for reference (not active yet)
        // val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         database.execSQL(
        //             "ALTER TABLE claimed_games ADD COLUMN notes TEXT DEFAULT NULL"
        //         )
        //     }
        // }
    }
}
