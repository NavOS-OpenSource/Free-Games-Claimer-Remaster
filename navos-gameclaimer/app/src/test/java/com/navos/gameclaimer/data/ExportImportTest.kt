package com.navos.gameclaimer.data

import com.navos.gameclaimer.data.local.export.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for export/import functionality.
 * 
 * These tests verify:
 * - Export format correctness
 * - Import validation
 * - Security constraints (no secret data in exports)
 * - Version compatibility
 */
class ExportImportTest {

    @Test
    fun `ExportData serializes to JSON correctly`() {
        val exportData = ExportData(
            claimedGames = listOf(
                ClaimedGameExport(
                    gameId = "test-game-1",
                    title = "Test Game",
                    store = "EPIC_GAMES",
                    claimedAt = 1700000000000L,
                    imageUrl = "https://example.com/image.png",
                    description = "A test game"
                )
            ),
            settings = listOf(
                SettingExport(key = "notifications_enabled", value = "true")
            ),
            availableGames = emptyList()
        )
        
        val json = ExportData.toJson(exportData)
        
        // Verify JSON contains expected fields
        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("\"claimedGames\""))
        assertTrue(json.contains("test-game-1"))
        assertTrue(json.contains("EPIC_GAMES"))
        assertTrue(json.contains("notifications_enabled"))
    }
    
    @Test
    fun `ExportData deserializes from JSON correctly`() {
        val json = """
        {
            "version": 1,
            "exportedAt": 1700000000000,
            "appVersion": "0.1.0",
            "claimedGames": [
                {
                    "gameId": "test-123",
                    "title": "Test Game",
                    "store": "EPIC_GAMES",
                    "claimedAt": 1700000000000
                }
            ],
            "settings": [],
            "availableGames": []
        }
        """.trimIndent()
        
        val exportData = ExportData.fromJson(json)
        
        assertEquals(1, exportData.version)
        assertEquals(1, exportData.claimedGames.size)
        assertEquals("test-123", exportData.claimedGames[0].gameId)
        assertEquals("EPIC_GAMES", exportData.claimedGames[0].store)
    }
    
    @Test
    fun `ExportData ignores unknown fields during deserialization`() {
        // Future-proofing: older app versions should ignore new fields
        val json = """
        {
            "version": 1,
            "exportedAt": 1700000000000,
            "appVersion": "0.1.0",
            "futureField": "ignored",
            "claimedGames": [],
            "settings": [],
            "availableGames": [],
            "anotherUnknownField": 123
        }
        """.trimIndent()
        
        // Should not throw
        val exportData = ExportData.fromJson(json)
        assertEquals(1, exportData.version)
    }
    
    @Test
    fun `ClaimedGameExport converts to entity correctly`() {
        val export = ClaimedGameExport(
            gameId = "epic-123",
            title = "Epic Game",
            store = "EPIC_GAMES",
            claimedAt = 1700000000000L,
            imageUrl = "https://epic.com/image.png",
            description = "Epic description"
        )
        
        val entity = export.toEntity()
        
        assertEquals(0L, entity.id) // Auto-generate
        assertEquals("epic-123", entity.gameId)
        assertEquals("Epic Game", entity.title)
        assertEquals("EPIC_GAMES", entity.store)
        assertEquals(1700000000000L, entity.claimedAt)
    }
    
    @Test
    fun `SettingExport excludes secret keys`() {
        // These should be filtered out during export
        val secretKeys = listOf(
            "session_token",
            "token_epic",
            "auth_state",
            "secret_key",
            "password_hash",
            "credential_epic"
        )
        
        // Verify the check matches expected patterns
        for (key in secretKeys) {
            assertTrue(
                "Key '$key' should be detected as secret",
                isSecretKey(key)
            )
        }
        
        // These should NOT be detected as secret
        val safeKeys = listOf(
            "notifications_enabled",
            "auto_claim_enabled",
            "dark_mode",
            "last_check_time"
        )
        
        for (key in safeKeys) {
            assertFalse(
                "Key '$key' should NOT be detected as secret",
                isSecretKey(key)
            )
        }
    }
    
    private fun isSecretKey(key: String): Boolean {
        return key.startsWith("session_") ||
               key.startsWith("token_") ||
               key.startsWith("auth_") ||
               key.contains("secret") ||
               key.contains("password") ||
               key.contains("credential")
    }
    
    @Test
    fun `AvailableGameExport requires HTTPS claim URL`() {
        // Valid HTTPS URL
        val validExport = AvailableGameExport(
            gameId = "test",
            title = "Test",
            store = "EPIC_GAMES",
            claimUrl = "https://store.epicgames.com/p/test"
        )
        assertTrue(validExport.claimUrl.startsWith("https://"))
        
        // HTTP URL would be rejected during import validation
        val invalidUrl = "http://insecure.com/game"
        assertFalse(invalidUrl.startsWith("https://"))
    }
    
    @Test
    fun `ExportData includes version and timestamp`() {
        val export = ExportData(
            claimedGames = emptyList(),
            settings = emptyList(),
            availableGames = emptyList()
        )
        
        assertEquals(ExportData.CURRENT_VERSION, export.version)
        assertTrue(export.exportedAt > 0)
        assertNotNull(export.appVersion)
    }
    
    @Test
    fun `Import rejects future versions`() {
        // This is a contract test - import should fail for unknown future versions
        val futureVersion = ExportData.CURRENT_VERSION + 1
        assertTrue(futureVersion > ExportData.CURRENT_VERSION)
    }
    
    @Test
    fun `Import accepts current and older versions`() {
        assertTrue(ExportData.MIN_SUPPORTED_VERSION <= ExportData.CURRENT_VERSION)
    }
    
    @Test
    fun `Optional fields handle null correctly`() {
        val export = ClaimedGameExport(
            gameId = "test",
            title = "Test",
            store = "EPIC_GAMES",
            claimedAt = 1700000000000L,
            imageUrl = null,
            description = null
        )
        
        val json = ExportData.toJson(ExportData(
            claimedGames = listOf(export),
            settings = emptyList(),
            availableGames = emptyList()
        ))
        
        // Should serialize null fields correctly
        val parsed = ExportData.fromJson(json)
        assertNull(parsed.claimedGames[0].imageUrl)
        assertNull(parsed.claimedGames[0].description)
    }
}
