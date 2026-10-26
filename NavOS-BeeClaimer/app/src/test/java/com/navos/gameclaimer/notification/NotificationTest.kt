package com.navos.gameclaimer.notification

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 14: Notification preferences and manager tests.
 * 
 * Tests cover:
 * - Default preferences
 * - Channel configuration
 * - Safety: no sensitive content in channel IDs
 */
class NotificationPreferencesTest {
    
    @Test
    fun `claim success enabled by default`() {
        val prefs = NotificationPreferences.DEFAULT
        
        assertTrue(prefs.claimSuccessEnabled)
    }
    
    @Test
    fun `action required enabled by default`() {
        val prefs = NotificationPreferences.DEFAULT
        
        assertTrue(prefs.actionRequiredEnabled)
    }
    
    @Test
    fun `expiring offers enabled by default`() {
        val prefs = NotificationPreferences.DEFAULT
        
        assertTrue(prefs.expiringOffersEnabled)
    }
    
    @Test
    fun `automation failures enabled by default`() {
        val prefs = NotificationPreferences.DEFAULT
        
        assertTrue(prefs.automationFailuresEnabled)
    }
    
    @Test
    fun `expiring hours threshold defaults to 24`() {
        val prefs = NotificationPreferences.DEFAULT
        
        assertEquals(24, prefs.expiringHoursThreshold)
    }
    
    @Test
    fun `failure count threshold defaults to 3`() {
        val prefs = NotificationPreferences.DEFAULT
        
        assertEquals(3, prefs.failureCountThreshold)
    }
    
    @Test
    fun `group notifications enabled by default`() {
        val prefs = NotificationPreferences.DEFAULT
        
        assertTrue(prefs.groupNotifications)
    }
    
    @Test
    fun `custom preferences are respected`() {
        val prefs = NotificationPreferences(
            claimSuccessEnabled = false,
            actionRequiredEnabled = true,
            expiringOffersEnabled = false,
            automationFailuresEnabled = true,
            expiringHoursThreshold = 48,
            failureCountThreshold = 5,
            groupNotifications = false
        )
        
        assertFalse(prefs.claimSuccessEnabled)
        assertTrue(prefs.actionRequiredEnabled)
        assertFalse(prefs.expiringOffersEnabled)
        assertTrue(prefs.automationFailuresEnabled)
        assertEquals(48, prefs.expiringHoursThreshold)
        assertEquals(5, prefs.failureCountThreshold)
        assertFalse(prefs.groupNotifications)
    }
    
    @Test
    fun `all channels can be disabled`() {
        val prefs = NotificationPreferences(
            claimSuccessEnabled = false,
            actionRequiredEnabled = false,
            expiringOffersEnabled = false,
            automationFailuresEnabled = false
        )
        
        assertFalse(prefs.claimSuccessEnabled)
        assertFalse(prefs.actionRequiredEnabled)
        assertFalse(prefs.expiringOffersEnabled)
        assertFalse(prefs.automationFailuresEnabled)
    }
    
    @Test
    fun `notification channel IDs are correct`() {
        // Verify channel ID constants match the spec
        assertEquals("claim_success", GameClaimerNotificationManager.CHANNEL_CLAIM_SUCCESS)
        assertEquals("action_required", GameClaimerNotificationManager.CHANNEL_ACTION_REQUIRED)
        assertEquals("expiring_offers", GameClaimerNotificationManager.CHANNEL_EXPIRING)
        assertEquals("automation_failures", GameClaimerNotificationManager.CHANNEL_FAILURES)
    }
}

/**
 * Tests for notification event model.
 */
class NotificationEventTest {
    
    @Test
    fun `notification event has correct type`() {
        val event = NotificationEvent(
            id = "event-1",
            type = NotificationType.CLAIM_SUCCESS,
            giveawayId = "giveaway-1"
        )
        
        assertEquals(NotificationType.CLAIM_SUCCESS, event.type)
    }
    
    @Test
    fun `notification event not dismissed by default`() {
        val event = NotificationEvent(
            id = "event-1",
            type = NotificationType.ACTION_REQUIRED,
            giveawayId = "giveaway-1"
        )
        
        assertFalse(event.dismissed)
    }
    
    @Test
    fun `notification event has timestamp`() {
        val before = System.currentTimeMillis()
        val event = NotificationEvent(
            id = "event-1",
            type = NotificationType.EXPIRING_SOON,
            giveawayId = null
        )
        val after = System.currentTimeMillis()
        
        assertTrue(event.timestamp >= before)
        assertTrue(event.timestamp <= after)
    }
    
    @Test
    fun `notification types cover all channels`() {
        val types = NotificationType.values()
        
        assertEquals(4, types.size)
        assertTrue(types.contains(NotificationType.CLAIM_SUCCESS))
        assertTrue(types.contains(NotificationType.ACTION_REQUIRED))
        assertTrue(types.contains(NotificationType.EXPIRING_SOON))
        assertTrue(types.contains(NotificationType.AUTOMATION_FAILURE))
    }
}

/**
 * Tests for notification safety requirements.
 */
class NotificationSafetyTest {
    
    @Test
    fun `channel IDs do not contain sensitive keywords`() {
        val channelIds = listOf(
            GameClaimerNotificationManager.CHANNEL_CLAIM_SUCCESS,
            GameClaimerNotificationManager.CHANNEL_ACTION_REQUIRED,
            GameClaimerNotificationManager.CHANNEL_EXPIRING,
            GameClaimerNotificationManager.CHANNEL_FAILURES
        )
        
        val sensitiveKeywords = listOf("password", "token", "secret", "auth", "credential")
        
        for (channelId in channelIds) {
            for (keyword in sensitiveKeywords) {
                assertFalse(
                    "Channel ID '$channelId' contains sensitive keyword '$keyword'",
                    channelId.lowercase().contains(keyword)
                )
            }
        }
    }
    
    @Test
    fun `failure count threshold prevents spam`() {
        // Default threshold is 3 - prevents noisy notifications
        val defaults = NotificationPreferences.DEFAULT
        
        assertTrue(
            "Failure count threshold should be at least 2 to prevent spam",
            defaults.failureCountThreshold >= 2
        )
    }
}
