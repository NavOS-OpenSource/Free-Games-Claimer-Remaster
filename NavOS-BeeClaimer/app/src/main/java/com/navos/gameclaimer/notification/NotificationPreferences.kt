package com.navos.gameclaimer.notification

import kotlinx.serialization.Serializable

/**
 * Phase 14: User preferences for notifications.
 * 
 * Stored in DataStore, respects system channel settings.
 */
@Serializable
data class NotificationPreferences(
    /** Enable claim success notifications */
    val claimSuccessEnabled: Boolean = true,
    
    /** Enable action required notifications */
    val actionRequiredEnabled: Boolean = true,
    
    /** Enable expiring offer notifications */
    val expiringOffersEnabled: Boolean = true,
    
    /** Hours before expiration to notify (0 = disabled) */
    val expiringHoursThreshold: Int = 24,
    
    /** Enable automation failure notifications */
    val automationFailuresEnabled: Boolean = true,
    
    /** Minimum failures before notifying */
    val failureCountThreshold: Int = 3,
    
    /** Group similar notifications */
    val groupNotifications: Boolean = true
) {
    companion object {
        val DEFAULT = NotificationPreferences()
    }
}

/**
 * Notification event for tracking and deduplication.
 */
@Serializable
data class NotificationEvent(
    val id: String,
    val type: NotificationType,
    val giveawayId: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val dismissed: Boolean = false
)

/**
 * Type of notification.
 */
@Serializable
enum class NotificationType {
    CLAIM_SUCCESS,
    ACTION_REQUIRED,
    EXPIRING_SOON,
    AUTOMATION_FAILURE
}
