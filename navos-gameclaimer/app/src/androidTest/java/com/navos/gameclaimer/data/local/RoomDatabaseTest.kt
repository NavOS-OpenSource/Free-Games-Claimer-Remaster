package com.navos.gameclaimer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.navos.gameclaimer.data.local.dao.*
import com.navos.gameclaimer.data.local.entity.*
import com.navos.gameclaimer.data.local.export.DataExportManager
import com.navos.gameclaimer.data.local.export.ImportMode
import com.navos.gameclaimer.data.local.export.ExportResult
import com.navos.gameclaimer.data.local.export.ImportResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented tests for Room database operations.
 * 
 * These tests run on an Android device/emulator to verify:
 * - Insert and retrieve operations
 * - Duplicate handling
 * - Export/import with real transactions
 * - Rollback on failure
 * - Data integrity
 */
@RunWith(AndroidJUnit4::class)
class RoomDatabaseTest {
    
    private lateinit var db: GameClaimerDatabase
    private lateinit var claimedGameDao: ClaimedGameDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var storeSessionDao: StoreSessionDao
    private lateinit var availableGameDao: AvailableGameDao
    private lateinit var context: Context
    
    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GameClaimerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        claimedGameDao = db.claimedGameDao()
        settingsDao = db.settingsDao()
        storeSessionDao = db.storeSessionDao()
        availableGameDao = db.availableGameDao()
    }
    
    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }
    
    // ===========================================
    // CLAIMED GAMES TESTS
    // ===========================================
    
    @Test
    fun insertAndRetrieveClaimedGame() = runBlocking {
        val game = ClaimedGameEntity(
            id = 0,
            gameId = "game123",
            title = "Test Game",
            store = "epic",
            claimedAt = System.currentTimeMillis(),
            imageUrl = "https://example.com/thumb.jpg",
            description = "A test game"
        )
        
        claimedGameDao.insert(game)
        
        val retrieved = claimedGameDao.getClaimedGame("epic", "game123")
        assertNotNull(retrieved)
        assertEquals("Test Game", retrieved?.title)
        assertEquals("A test game", retrieved?.description)
    }
    
    @Test
    fun duplicateClaimedGameIsIgnored() = runBlocking {
        val game1 = ClaimedGameEntity(
            id = 0,
            gameId = "game123",
            title = "Original Title",
            store = "epic",
            claimedAt = 1000L,
            imageUrl = null,
            description = null
        )
        val game2 = ClaimedGameEntity(
            id = 0,
            gameId = "game123",
            title = "Updated Title",
            store = "epic",
            claimedAt = 2000L,
            imageUrl = null,
            description = null
        )
        
        claimedGameDao.insert(game1)
        claimedGameDao.insert(game2) // Should be ignored due to IGNORE conflict
        
        val all = claimedGameDao.getAllForExport()
        assertEquals(1, all.size)
        assertEquals("Original Title", all[0].title) // Original preserved
        assertEquals(1000L, all[0].claimedAt) // Original timestamp preserved
    }
    
    @Test
    fun insertAllClaimedGamesWithDuplicates() = runBlocking {
        val games = listOf(
            ClaimedGameEntity(0, "game1", "Game 1", "epic", 1000L, null, null),
            ClaimedGameEntity(0, "game2", "Game 2", "epic", 2000L, null, null),
            ClaimedGameEntity(0, "game1", "Game 1 Dup", "epic", 3000L, null, null) // Duplicate
        )
        
        claimedGameDao.insertAll(games)
        
        val all = claimedGameDao.getAllForExport()
        assertEquals(2, all.size) // Duplicate ignored
    }
    
    // ===========================================
    // SETTINGS TESTS
    // ===========================================
    
    @Test
    fun insertAndRetrieveSetting() = runBlocking {
        val setting = SettingsEntity(
            key = "theme",
            value = "dark",
            updatedAt = System.currentTimeMillis()
        )
        
        settingsDao.upsert(setting)
        
        val retrieved = settingsDao.getSetting("theme")
        assertNotNull(retrieved)
        assertEquals("dark", retrieved?.value)
    }
    
    @Test
    fun upsertSettingUpdatesExisting() = runBlocking {
        settingsDao.upsert(SettingsEntity("theme", "light", 1000L))
        settingsDao.upsert(SettingsEntity("theme", "dark", 2000L))
        
        val retrieved = settingsDao.getSetting("theme")
        assertEquals("dark", retrieved?.value) // Updated value
    }
    
    @Test
    fun secretSettingsAreNotExported() = runBlocking {
        // Insert both regular and secret settings
        settingsDao.upsert(SettingsEntity("theme", "dark", 1000L))
        settingsDao.upsert(SettingsEntity("session_epic", "secret123", 1000L))
        settingsDao.upsert(SettingsEntity("token_steam", "abc123", 1000L))
        settingsDao.upsert(SettingsEntity("auth_gog", "xyz789", 1000L))
        settingsDao.upsert(SettingsEntity("my_secret_key", "hidden", 1000L))
        settingsDao.upsert(SettingsEntity("password_hint", "hint", 1000L))
        settingsDao.upsert(SettingsEntity("credential_id", "cred", 1000L))
        settingsDao.upsert(SettingsEntity("notification_enabled", "true", 1000L))
        
        val all = settingsDao.getAllSettings()
        assertEquals(8, all.size) // All stored
        
        // Export manager should filter these
        val exportManager = DataExportManager(context, db)
        val result = exportManager.exportToJson()
        
        assertTrue(result is ExportResult.Success)
        val json = (result as ExportResult.Success).json
        
        // Regular settings should be present
        assertTrue(json.contains("theme"))
        assertTrue(json.contains("notification_enabled"))
        
        // Secret settings should NOT be present
        assertFalse(json.contains("session_epic"))
        assertFalse(json.contains("token_steam"))
        assertFalse(json.contains("auth_gog"))
        assertFalse(json.contains("my_secret_key"))
        assertFalse(json.contains("password_hint"))
        assertFalse(json.contains("credential_id"))
    }
    
    // ===========================================
    // STORE SESSIONS TESTS (NEVER EXPORTED)
    // ===========================================
    
    @Test
    fun storeSessionsNeverExported() = runBlocking {
        // Insert a session
        val session = StoreSessionEntity(
            id = 0,
            store = "epic",
            displayName = "TestUser",
            lastVerified = System.currentTimeMillis(),
            isAuthenticated = true,
            isSecret = true
        )
        storeSessionDao.upsert(session)
        
        // Verify it's stored
        val stored = storeSessionDao.getSession("epic")
        assertNotNull(stored)
        
        // Export should NOT include sessions (isSecret = true)
        val exportManager = DataExportManager(context, db)
        val result = exportManager.exportToJson()
        
        assertTrue(result is ExportResult.Success)
        val json = (result as ExportResult.Success).json
        
        // Sessions should never appear in export
        assertFalse("Export should not contain store_sessions", json.contains("store_sessions"))
    }
    
    // ===========================================
    // AVAILABLE GAMES TESTS
    // ===========================================
    
    @Test
    fun insertAndRetrieveAvailableGame() = runBlocking {
        val now = System.currentTimeMillis()
        val game = AvailableGameEntity(
            id = 0,
            gameId = "free123",
            title = "Free Game",
            store = "epic",
            imageUrl = "https://example.com/thumb.jpg",
            description = "A free game",
            offerEndTime = now + 86400000,
            claimUrl = "https://store.epicgames.com/free123",
            discoveredAt = now,
            lastChecked = now
        )
        
        availableGameDao.upsert(game)
        
        val all = availableGameDao.getAllForExport()
        assertEquals(1, all.size)
        assertEquals("Free Game", all[0].title)
    }
    
    @Test
    fun upsertAvailableGameUpdatesExisting() = runBlocking {
        val now = System.currentTimeMillis()
        
        availableGameDao.upsert(AvailableGameEntity(
            0, "game1", "Original", "epic", null, null, now + 1000, "https://epic.com/1", now, now
        ))
        availableGameDao.upsert(AvailableGameEntity(
            0, "game1", "Updated", "epic", null, "Updated desc", now + 2000, "https://epic.com/1", now, now
        ))
        
        val all = availableGameDao.getAllForExport()
        assertEquals(1, all.size)
        assertEquals("Updated", all[0].title)
        assertEquals("Updated desc", all[0].description)
    }
    
    // ===========================================
    // EXPORT/IMPORT ROUND-TRIP TESTS
    // ===========================================
    
    @Test
    fun exportImportRoundTrip() = runBlocking {
        val now = System.currentTimeMillis()
        
        // Populate database
        claimedGameDao.insert(ClaimedGameEntity(0, "claimed1", "Claimed Game 1", "epic", 1000L, null, null))
        claimedGameDao.insert(ClaimedGameEntity(0, "claimed2", "Claimed Game 2", "steam", 2000L, null, null))
        settingsDao.upsert(SettingsEntity("theme", "dark", 1000L))
        settingsDao.upsert(SettingsEntity("auto_claim", "true", 1000L))
        availableGameDao.upsert(AvailableGameEntity(0, "avail1", "Available 1", "epic", null, null, 9000L, "https://epic.com/1", now, now))
        
        // Export
        val exportManager = DataExportManager(context, db)
        val exportResult = exportManager.exportToJson()
        assertTrue(exportResult is ExportResult.Success)
        val json = (exportResult as ExportResult.Success).json
        
        // Create fresh database
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, GameClaimerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        // Import into fresh database
        val importManager = DataExportManager(context, db)
        val importResult = importManager.importFromJson(json, ImportMode.MERGE)
        
        assertTrue(importResult is ImportResult.Success)
        
        // Verify data was imported
        val claimedGames = db.claimedGameDao().getAllForExport()
        assertEquals(2, claimedGames.size)
        
        val settings = db.settingsDao().getAllSettings()
        assertEquals(2, settings.size)
        
        val availableGames = db.availableGameDao().getAllForExport()
        assertEquals(1, availableGames.size)
    }
    
    @Test
    fun mergeModePreservesExistingRecords() = runBlocking {
        // Pre-existing data
        claimedGameDao.insert(ClaimedGameEntity(0, "existing", "Existing Game", "epic", 1000L, null, null))
        settingsDao.upsert(SettingsEntity("existing_setting", "value", 1000L))
        
        // Create export from another "database"
        val exportJson = """
        {
            "version": 1,
            "exportedAt": ${System.currentTimeMillis()},
            "appVersion": "0.1.0",
            "claimedGames": [
                {"gameId": "new1", "title": "New Game", "store": "steam", "claimedAt": 2000}
            ],
            "settings": [
                {"key": "new_setting", "value": "new_value"}
            ],
            "availableGames": []
        }
        """.trimIndent()
        
        val exportManager = DataExportManager(context, db)
        val result = exportManager.importFromJson(exportJson, ImportMode.MERGE)
        
        assertTrue(result is ImportResult.Success)
        
        // Existing records should be preserved
        val existingGame = claimedGameDao.getClaimedGame("epic", "existing")
        assertNotNull(existingGame)
        assertEquals("Existing Game", existingGame?.title)
        
        // New records should be added
        val newGame = claimedGameDao.getClaimedGame("steam", "new1")
        assertNotNull(newGame)
        assertEquals("New Game", newGame?.title)
        
        // Both settings should exist
        assertEquals("value", settingsDao.getSetting("existing_setting")?.value)
        assertEquals("new_value", settingsDao.getSetting("new_setting")?.value)
    }
    
    @Test
    fun replaceModeRemovesExistingRecords() = runBlocking {
        // Pre-existing data
        claimedGameDao.insert(ClaimedGameEntity(0, "old", "Old Game", "epic", 1000L, null, null))
        settingsDao.upsert(SettingsEntity("old_setting", "old_value", 1000L))
        
        // Create export
        val exportJson = """
        {
            "version": 1,
            "exportedAt": ${System.currentTimeMillis()},
            "appVersion": "0.1.0",
            "claimedGames": [
                {"gameId": "new1", "title": "New Game", "store": "steam", "claimedAt": 2000}
            ],
            "settings": [
                {"key": "new_setting", "value": "new_value"}
            ],
            "availableGames": []
        }
        """.trimIndent()
        
        val exportManager = DataExportManager(context, db)
        val result = exportManager.importFromJson(exportJson, ImportMode.REPLACE)
        
        assertTrue(result is ImportResult.Success)
        
        // Old records should be gone
        val oldGame = claimedGameDao.getClaimedGame("epic", "old")
        assertNull(oldGame)
        assertNull(settingsDao.getSetting("old_setting"))
        
        // New records should exist
        val newGame = claimedGameDao.getClaimedGame("steam", "new1")
        assertNotNull(newGame)
        assertEquals("new_value", settingsDao.getSetting("new_setting")?.value)
    }
    
    // ===========================================
    // VALIDATION TESTS
    // ===========================================
    
    @Test
    fun importRejectsInvalidVersion() = runBlocking {
        val futureVersionJson = """
        {
            "version": 999,
            "exportedAt": ${System.currentTimeMillis()},
            "appVersion": "0.1.0",
            "claimedGames": [],
            "settings": [],
            "availableGames": []
        }
        """.trimIndent()
        
        val exportManager = DataExportManager(context, db)
        val result = exportManager.importFromJson(futureVersionJson, ImportMode.MERGE)
        
        assertTrue(result is ImportResult.Error)
        assertTrue((result as ImportResult.Error).message.contains("newer"))
    }
    
    @Test
    fun importRejectsHttpUrls() = runBlocking {
        val httpUrlJson = """
        {
            "version": 1,
            "exportedAt": ${System.currentTimeMillis()},
            "appVersion": "0.1.0",
            "claimedGames": [],
            "settings": [],
            "availableGames": [
                {"gameId": "game1", "title": "Game", "store": "epic", "claimUrl": "http://insecure.com/game"}
            ]
        }
        """.trimIndent()
        
        val exportManager = DataExportManager(context, db)
        val result = exportManager.importFromJson(httpUrlJson, ImportMode.MERGE)
        
        assertTrue(result is ImportResult.Error)
        assertTrue((result as ImportResult.Error).message.contains("HTTPS"))
    }
    
    @Test
    fun importRejectsSecretSettings() = runBlocking {
        val secretSettingJson = """
        {
            "version": 1,
            "exportedAt": ${System.currentTimeMillis()},
            "appVersion": "0.1.0",
            "claimedGames": [],
            "settings": [
                {"key": "session_epic", "value": "should_be_rejected"}
            ],
            "availableGames": []
        }
        """.trimIndent()
        
        val exportManager = DataExportManager(context, db)
        val result = exportManager.importFromJson(secretSettingJson, ImportMode.MERGE)
        
        assertTrue(result is ImportResult.Error)
        assertTrue((result as ImportResult.Error).message.contains("secret"))
    }
    
    @Test
    fun importRejectsOversizedData() = runBlocking {
        // Create a JSON that would exceed limits (simulate with max games check)
        val tooManyGames = (1..10001).map {
            """{"gameId": "game$it", "title": "Game $it", "store": "epic", "claimedAt": $it}"""
        }.joinToString(",")
        
        val oversizedJson = """
        {
            "version": 1,
            "exportedAt": ${System.currentTimeMillis()},
            "appVersion": "0.1.0",
            "claimedGames": [$tooManyGames],
            "settings": [],
            "availableGames": []
        }
        """.trimIndent()
        
        val exportManager = DataExportManager(context, db)
        val result = exportManager.importFromJson(oversizedJson, ImportMode.MERGE)
        
        assertTrue(result is ImportResult.Error)
        assertTrue((result as ImportResult.Error).message.contains("Too many"))
    }
    
    // ===========================================
    // CLAIMED GAMES CANNOT TRIGGER ACTIONS
    // ===========================================
    
    @Test
    fun importedClaimedGamesAreHistoryOnly() = runBlocking {
        // Import claimed games
        val exportJson = """
        {
            "version": 1,
            "exportedAt": ${System.currentTimeMillis()},
            "appVersion": "0.1.0",
            "claimedGames": [
                {"gameId": "imported1", "title": "Imported Game", "store": "epic", "claimedAt": 1000}
            ],
            "settings": [],
            "availableGames": []
        }
        """.trimIndent()
        
        val exportManager = DataExportManager(context, db)
        val result = exportManager.importFromJson(exportJson, ImportMode.MERGE)
        assertTrue(result is ImportResult.Success)
        
        // Verify the game is stored as history
        val game = claimedGameDao.getClaimedGame("epic", "imported1")
        assertNotNull(game)
        assertEquals("Imported Game", game?.title)
        assertEquals(1000L, game?.claimedAt)
        
        // The ClaimedGameEntity has no methods to trigger claims
        // It's a pure data class - claiming happens in a separate service
        // This test documents the design: claimed_games table is read-only history
    }
}
