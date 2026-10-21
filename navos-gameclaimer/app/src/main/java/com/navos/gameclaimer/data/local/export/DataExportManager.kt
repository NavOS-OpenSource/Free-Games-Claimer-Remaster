package com.navos.gameclaimer.data.local.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manages data export and import operations.
 * 
 * SECURITY DESIGN:
 * - Export EXCLUDES all session data and tokens
 * - Import validates data before writing
 * - Import uses INSERT OR IGNORE to prevent duplicates
 * - Invalid data is rejected, not partially imported
 * 
 * PORTABILITY:
 * - JSON format for human readability and broad compatibility
 * - Version field for future format changes
 * - Backwards-compatible import (ignores unknown fields)
 */
class DataExportManager(
    private val context: Context,
    private val database: GameClaimerDatabase
) {
    
    /**
     * Export all user data to JSON string.
     * 
     * SECURITY: Sessions and credentials are NOT included.
     */
    suspend fun exportToJson(): ExportResult = withContext(Dispatchers.IO) {
        try {
            val claimedGames = database.claimedGameDao().getAllForExport().map { it.toExport() }
            val settings = database.settingsDao().getAllSettings()
                .filter { !isSecretSetting(it.key) }
                .map { it.toExport() }
            val availableGames = database.availableGameDao().getAllForExport().map { it.toExport() }
            
            val exportData = ExportData(
                claimedGames = claimedGames,
                settings = settings,
                availableGames = availableGames
            )
            
            val json = ExportData.toJson(exportData)
            
            ExportResult.Success(
                json = json,
                stats = ExportStats(
                    claimedGamesCount = claimedGames.size,
                    settingsCount = settings.size,
                    availableGamesCount = availableGames.size
                )
            )
        } catch (e: Exception) {
            ExportResult.Error("Export failed: ${e.message}")
        }
    }
    
    /**
     * Export data to a file URI (for sharing via system picker).
     */
    suspend fun exportToUri(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        when (val result = exportToJson()) {
            is ExportResult.Success -> {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(result.json.toByteArray(Charsets.UTF_8))
                    } ?: return@withContext ExportResult.Error("Could not open output stream")
                    result
                } catch (e: IOException) {
                    ExportResult.Error("Failed to write file: ${e.message}")
                }
            }
            is ExportResult.Error -> result
        }
    }
    
    /**
     * Import data from JSON string.
     * 
     * SECURITY:
     * - Validates data before import
     * - Uses INSERT OR IGNORE for duplicates
     * - Rolls back on error
     * 
     * @param json The exported JSON data
     * @param mergeMode How to handle existing data (MERGE or REPLACE)
     */
    suspend fun importFromJson(
        json: String,
        mergeMode: ImportMode = ImportMode.MERGE
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            // Parse and validate
            val exportData = try {
                ExportData.fromJson(json)
            } catch (e: Exception) {
                return@withContext ImportResult.Error("Invalid JSON format: ${e.message}")
            }
            
            // Version check
            if (exportData.version < ExportData.MIN_SUPPORTED_VERSION) {
                return@withContext ImportResult.Error(
                    "Export version ${exportData.version} is too old. Minimum supported: ${ExportData.MIN_SUPPORTED_VERSION}"
                )
            }
            
            if (exportData.version > ExportData.CURRENT_VERSION) {
                return@withContext ImportResult.Error(
                    "Export version ${exportData.version} is newer than app version ${ExportData.CURRENT_VERSION}. Please update the app."
                )
            }
            
            // Validate data
            val validationResult = validateImportData(exportData)
            if (validationResult != null) {
                return@withContext ImportResult.Error("Validation failed: $validationResult")
            }
            
            // Perform import in transaction
            database.withTransaction {
                if (mergeMode == ImportMode.REPLACE) {
                    // Clear existing data (except sessions - those are never touched)
                    database.claimedGameDao().deleteAll()
                    database.settingsDao().deleteAll()
                    database.availableGameDao().deleteAll()
                }
                
                // Import claimed games (duplicates ignored)
                val games = exportData.claimedGames.map { it.toEntity() }
                database.claimedGameDao().insertAll(games)
                
                // Import settings (duplicates replaced in MERGE mode)
                val now = System.currentTimeMillis()
                exportData.settings.forEach { setting ->
                    if (!isSecretSetting(setting.key)) {
                        database.settingsDao().upsert(setting.toEntity(now))
                    }
                }
                
                // Import available games (duplicates replaced)
                val availableGames = exportData.availableGames.map { it.toEntity(now) }
                database.availableGameDao().upsertAll(availableGames)
            }
            
            ImportResult.Success(
                stats = ImportStats(
                    claimedGamesImported = exportData.claimedGames.size,
                    settingsImported = exportData.settings.size,
                    availableGamesImported = exportData.availableGames.size
                )
            )
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.message}")
        }
    }
    
    /**
     * Import from a file URI.
     */
    suspend fun importFromUri(
        uri: Uri,
        mergeMode: ImportMode = ImportMode.MERGE
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@withContext ImportResult.Error("Could not read file")
            
            // Size check (prevent DoS)
            if (json.length > MAX_IMPORT_SIZE_BYTES) {
                return@withContext ImportResult.Error(
                    "File too large. Maximum size: ${MAX_IMPORT_SIZE_BYTES / 1024}KB"
                )
            }
            
            importFromJson(json, mergeMode)
        } catch (e: IOException) {
            ImportResult.Error("Failed to read file: ${e.message}")
        }
    }
    
    /**
     * Validate import data for security and integrity.
     */
    private fun validateImportData(data: ExportData): String? {
        // Check for reasonable limits
        if (data.claimedGames.size > MAX_CLAIMED_GAMES) {
            return "Too many claimed games (max: $MAX_CLAIMED_GAMES)"
        }
        if (data.settings.size > MAX_SETTINGS) {
            return "Too many settings (max: $MAX_SETTINGS)"
        }
        if (data.availableGames.size > MAX_AVAILABLE_GAMES) {
            return "Too many available games (max: $MAX_AVAILABLE_GAMES)"
        }
        
        // Validate each game
        for (game in data.claimedGames) {
            if (game.gameId.isBlank()) return "Invalid game ID"
            if (game.title.isBlank()) return "Invalid game title"
            if (game.store.isBlank()) return "Invalid store"
            if (game.claimedAt <= 0) return "Invalid claimed timestamp"
        }
        
        // Validate settings keys
        for (setting in data.settings) {
            if (setting.key.isBlank()) return "Invalid setting key"
            if (isSecretSetting(setting.key)) return "Cannot import secret setting: ${setting.key}"
        }
        
        // Validate available games
        for (game in data.availableGames) {
            if (game.gameId.isBlank()) return "Invalid available game ID"
            if (game.claimUrl.isBlank()) return "Invalid claim URL"
            if (!game.claimUrl.startsWith("https://")) return "Claim URL must be HTTPS"
        }
        
        return null
    }
    
    /**
     * Settings keys that should never be exported/imported.
     */
    private fun isSecretSetting(key: String): Boolean {
        return key.startsWith("session_") ||
               key.startsWith("token_") ||
               key.startsWith("auth_") ||
               key.contains("secret") ||
               key.contains("password") ||
               key.contains("credential")
    }
    
    companion object {
        const val MAX_IMPORT_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
        const val MAX_CLAIMED_GAMES = 10_000
        const val MAX_SETTINGS = 100
        const val MAX_AVAILABLE_GAMES = 1_000
    }
}

enum class ImportMode {
    /** Merge with existing data, keeping both */
    MERGE,
    /** Replace existing data (except sessions) */
    REPLACE
}

sealed class ExportResult {
    data class Success(val json: String, val stats: ExportStats) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

data class ExportStats(
    val claimedGamesCount: Int,
    val settingsCount: Int,
    val availableGamesCount: Int
)

sealed class ImportResult {
    data class Success(val stats: ImportStats) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

data class ImportStats(
    val claimedGamesImported: Int,
    val settingsImported: Int,
    val availableGamesImported: Int
)
