package com.navos.gameclaimer.data.local.export

import com.navos.gameclaimer.data.local.entity.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data class representing the exportable app data.
 * 
 * SECURITY: This class explicitly EXCLUDES sensitive data:
 * - No session tokens
 * - No encrypted credentials
 * - No authentication state
 * 
 * What IS exported:
 * - Claimed games history
 * - User preferences (non-secret)
 * - Available games cache
 * 
 * What is NOT exported:
 * - Store sessions (sessionTokenEncrypted)
 * - Any authentication state
 */
@Serializable
data class ExportData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "0.1.0",
    val claimedGames: List<ClaimedGameExport>,
    val settings: List<SettingExport>,
    val availableGames: List<AvailableGameExport>
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val MIN_SUPPORTED_VERSION = 1
        
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        
        fun toJson(data: ExportData): String = json.encodeToString(data)
        
        fun fromJson(jsonString: String): ExportData = json.decodeFromString(jsonString)
    }
}

@Serializable
data class ClaimedGameExport(
    val gameId: String,
    val title: String,
    val store: String,
    val claimedAt: Long,
    val imageUrl: String? = null,
    val description: String? = null
)

@Serializable
data class SettingExport(
    val key: String,
    val value: String
)

@Serializable
data class AvailableGameExport(
    val gameId: String,
    val title: String,
    val store: String,
    val imageUrl: String? = null,
    val description: String? = null,
    val offerEndTime: Long? = null,
    val claimUrl: String
)

// Extension functions to convert entities to export models
fun ClaimedGameEntity.toExport() = ClaimedGameExport(
    gameId = gameId,
    title = title,
    store = store,
    claimedAt = claimedAt,
    imageUrl = imageUrl,
    description = description
)

fun SettingsEntity.toExport() = SettingExport(
    key = key,
    value = value
)

fun AvailableGameEntity.toExport() = AvailableGameExport(
    gameId = gameId,
    title = title,
    store = store,
    imageUrl = imageUrl,
    description = description,
    offerEndTime = offerEndTime,
    claimUrl = claimUrl
)

// Extension functions to convert export models back to entities
fun ClaimedGameExport.toEntity() = ClaimedGameEntity(
    id = 0, // Auto-generate new ID
    gameId = gameId,
    title = title,
    store = store,
    claimedAt = claimedAt,
    imageUrl = imageUrl,
    description = description
)

fun SettingExport.toEntity(updatedAt: Long = System.currentTimeMillis()) = SettingsEntity(
    key = key,
    value = value,
    updatedAt = updatedAt
)

fun AvailableGameExport.toEntity(now: Long = System.currentTimeMillis()) = AvailableGameEntity(
    id = 0, // Auto-generate new ID
    gameId = gameId,
    title = title,
    store = store,
    imageUrl = imageUrl,
    description = description,
    offerEndTime = offerEndTime,
    claimUrl = claimUrl,
    discoveredAt = now,
    lastChecked = now
)
