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
 * - Version 2: Remove unused sessionTokenEncrypted column from store_sessions
 *              (Phase 5 hardening - we don't store tokens, only metadata)
 * 
 * SECURITY NOTES:
 * - Database file is stored in app-private storage
 * - No WAL mode to prevent data leakage in journal files
 * - Backup is disabled at app level (AndroidManifest)
 * - store_sessions table stores ONLY metadata (displayName, lastVerified, isAuthenticated)
 * - NO session tokens, cookies, or credentials are stored in this database
 * - WebView cookies are managed by Android's CookieManager (system-level storage)
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
    version = 2,
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
         * Migration from version 1 to 2:
         * - Remove sessionTokenEncrypted column from store_sessions
         * - This column was never used and was misleading
         * - We store metadata only, not actual tokens
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite doesn't support DROP COLUMN directly in older versions
                // We recreate the table without the column
                database.execSQL("""
                    CREATE TABLE store_sessions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        store TEXT NOT NULL,
                        displayName TEXT,
                        lastVerified INTEGER NOT NULL,
                        isAuthenticated INTEGER NOT NULL,
                        isSecret INTEGER NOT NULL
                    )
                """)
                database.execSQL("""
                    INSERT INTO store_sessions_new (id, store, displayName, lastVerified, isAuthenticated, isSecret)
                    SELECT id, store, displayName, lastVerified, isAuthenticated, isSecret FROM store_sessions
                """)
                database.execSQL("DROP TABLE store_sessions")
                database.execSQL("ALTER TABLE store_sessions_new RENAME TO store_sessions")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_store_sessions_store ON store_sessions (store)")
            }
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
            MIGRATION_1_2
        )
    }
}
