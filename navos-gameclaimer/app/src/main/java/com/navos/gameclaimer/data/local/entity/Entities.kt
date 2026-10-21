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
 * SECURITY CRITICAL: This contains session data.
 * - sessionToken is encrypted via EncryptedSharedPreferences (Phase 5+)
 * - MUST be excluded from exports
 * - Marked with isSecret=true for export filtering
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
    // Security: This field is encrypted and MUST NOT be exported
    // In Phase 5+, this will use Android Keystore
    val sessionTokenEncrypted: String? = null,
    // Flag for export filtering
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
