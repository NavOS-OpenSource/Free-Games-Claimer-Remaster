package com.navos.gameclaimer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.navos.gameclaimer.domain.model.GameStore

/**
 * Room entity for storing claimed game history.
 * 
 * Security note: This stores only game metadata, no credentials.
 * Export-safe: All fields can be exported without security risk.
 */
@Entity(
    tableName = "claimed_games",
    indices = [
        Index(value = ["store", "gameId"], unique = true),
        Index(value = ["claimedAt"])
    ]
)
data class ClaimedGameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: String,
    val title: String,
    val store: String, // GameStore.name
    val claimedAt: Long, // Unix timestamp
    val imageUrl: String?,
    val description: String?
)

/**
 * Room entity for storing user settings.
 * 
 * Security note: Contains only preferences, no secrets.
 * Export-safe: All fields can be exported.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long
)

/**
 * Room entity for store authentication state.
 * 
 * SECURITY ARCHITECTURE (Phase 5):
 * This entity stores METADATA about authentication state only:
 * - Whether user is authenticated (boolean)
 * - Display name for UI
 * - Last verification timestamp
 * 
 * WHAT IS NOT STORED HERE:
 * - No session tokens
 * - No cookies (managed by Android CookieManager)
 * - No credentials
 * 
 * WebView cookies are stored by Android's system CookieManager in a
 * WebView-specific database. This is SEPARATE from this Room table.
 * 
 * EXPORT: This table is EXCLUDED from exports (isSecret=true).
 * Even though we don't store actual tokens, authentication metadata
 * should not be portable between devices.
 */
@Entity(
    tableName = "store_sessions",
    indices = [Index(value = ["store"], unique = true)]
)
data class StoreSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val store: String, // GameStore.name
    val displayName: String?,
    val lastVerified: Long,
    val isAuthenticated: Boolean,
    // Flag for export filtering - always true for this table
    val isSecret: Boolean = true
)

/**
 * Room entity for tracking available free games.
 * 
 * Export-safe: Contains only public game information.
 */
@Entity(
    tableName = "available_games",
    indices = [
        Index(value = ["store", "gameId"], unique = true),
        Index(value = ["offerEndTime"])
    ]
)
data class AvailableGameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: String,
    val title: String,
    val store: String,
    val imageUrl: String?,
    val description: String?,
    val offerEndTime: Long?, // Unix timestamp, null if permanent
    val claimUrl: String,
    val discoveredAt: Long,
    val lastChecked: Long
)
