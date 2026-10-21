package com.navos.gameclaimer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.navos.gameclaimer.domain.model.DiscoverySource
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.Giveaway
import com.navos.gameclaimer.domain.model.GiveawayType

/**
 * Phase 6: Room entity for persisted giveaways.
 * 
 * DESIGN:
 * - Uses source-prefixed ID for deterministic deduplication
 * - Stores normalized fields, not raw API responses
 * - Tracks discovery metadata for refresh logic
 * 
 * SECURITY:
 * - No credentials or session data stored
 * - URLs validated before insertion
 */
@Entity(
    tableName = "giveaways",
    indices = [
        Index(value = ["store"]),
        Index(value = ["source"]),
        Index(value = ["externalId", "source"], unique = true),
        Index(value = ["endTime"])
    ]
)
data class GiveawayEntity(
    /** Unique ID: format source:store:external_id */
    @PrimaryKey
    val id: String,
    
    /** Display title */
    val title: String,
    
    /** Store name (enum name) */
    val store: String,
    
    /** Giveaway type (enum name) */
    val type: String,
    
    /** HTTPS claim URL (validated before storage) */
    val claimUrl: String,
    
    /** Original price string (e.g., "$29.99") */
    val originalPrice: String? = null,
    
    /** Image URL */
    val imageUrl: String? = null,
    
    /** Description text */
    val description: String? = null,
    
    /** Giveaway start time (epoch millis) */
    val startTime: Long? = null,
    
    /** Giveaway end time (epoch millis) */
    val endTime: Long? = null,
    
    /** Discovery source (enum name) */
    val source: String,
    
    /** External ID from source for deduplication */
    val externalId: String? = null,
    
    /** When first discovered */
    val discoveredAt: Long = System.currentTimeMillis(),
    
    /** When last refreshed/verified */
    val lastCheckedAt: Long = System.currentTimeMillis(),
    
    /** Whether user has dismissed this giveaway */
    val isDismissed: Boolean = false,
    
    /** Whether user has claimed this giveaway */
    val isClaimed: Boolean = false,
    
    /** When claimed (if applicable) */
    val claimedAt: Long? = null
) {
    /**
     * Convert to domain model.
     */
    fun toDomain(): Giveaway {
        return Giveaway(
            id = id,
            title = title,
            store = GameStore.valueOf(store),
            type = GiveawayType.valueOf(type),
            claimUrl = claimUrl,
            originalPrice = originalPrice,
            imageUrl = imageUrl,
            description = description,
            startTime = startTime,
            endTime = endTime,
            source = DiscoverySource.valueOf(source),
            lastCheckedAt = lastCheckedAt,
            externalId = externalId
        )
    }
    
    companion object {
        /**
         * Create entity from domain model.
         */
        fun fromDomain(giveaway: Giveaway): GiveawayEntity {
            return GiveawayEntity(
                id = giveaway.id,
                title = giveaway.title,
                store = giveaway.store.name,
                type = giveaway.type.name,
                claimUrl = giveaway.claimUrl,
                originalPrice = giveaway.originalPrice,
                imageUrl = giveaway.imageUrl,
                description = giveaway.description,
                startTime = giveaway.startTime,
                endTime = giveaway.endTime,
                source = giveaway.source.name,
                externalId = giveaway.externalId,
                lastCheckedAt = giveaway.lastCheckedAt
            )
        }
    }
}
