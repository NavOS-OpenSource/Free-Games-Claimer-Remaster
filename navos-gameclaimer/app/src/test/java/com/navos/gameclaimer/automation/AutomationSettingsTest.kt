package com.navos.gameclaimer.automation

import com.navos.gameclaimer.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 13: Tests for AutomationSettings configuration.
 * 
 * GPT REQUIREMENTS TESTED:
 * - Configurable automation scheduling
 * - Default interval is 12 hours for claiming
 * - Respect user's enabled/disabled settings
 * - Per-store enable/disable
 */
class AutomationSettingsTest {
    
    // ===== Default Settings Tests =====
    
    @Test
    fun `default settings have automation disabled`() {
        val settings = AutomationSettings()
        
        assertFalse("Automation should be disabled by default", settings.automationEnabled)
    }
    
    @Test
    fun `default claim interval is 12 hours`() {
        val settings = ClaimAutomationSettings()
        
        assertEquals(12L, settings.intervalHours)
        assertEquals(ClaimAutomationSettings.DEFAULT_CLAIM_INTERVAL_HOURS, settings.intervalHours)
    }
    
    @Test
    fun `default discovery interval is 6 hours`() {
        val settings = DiscoveryScheduleSettings()
        
        assertEquals(6L, settings.intervalHours)
        assertEquals(DiscoveryScheduleSettings.DEFAULT_DISCOVERY_INTERVAL_HOURS, settings.intervalHours)
    }
    
    @Test
    fun `auto-claiming is disabled by default`() {
        val settings = ClaimAutomationSettings()
        
        assertFalse("Auto-claiming MUST be disabled by default", settings.autoClaimEnabled)
    }
    
    // ===== Store Enable/Disable Tests =====
    
    @Test
    fun `isStoreEnabled returns false when automation disabled`() {
        val settings = AutomationSettings(automationEnabled = false)
        
        for (store in GameStore.values()) {
            assertFalse("Store should be disabled when automation off", 
                settings.isStoreEnabled(store))
        }
    }
    
    @Test
    fun `isStoreEnabled returns true when automation enabled and store not explicitly disabled`() {
        val settings = AutomationSettings(automationEnabled = true)
        
        // By default, stores are enabled if not explicitly set
        assertTrue(settings.isStoreEnabled(GameStore.EPIC_GAMES))
        assertTrue(settings.isStoreEnabled(GameStore.STEAM))
    }
    
    @Test
    fun `isStoreEnabled returns false when store explicitly disabled`() {
        val settings = AutomationSettings(
            automationEnabled = true,
            storeSettings = mapOf(
                GameStore.EPIC_GAMES.name to StoreAutomationSettings(enabled = false)
            )
        )
        
        assertFalse(settings.isStoreEnabled(GameStore.EPIC_GAMES))
        assertTrue(settings.isStoreEnabled(GameStore.STEAM)) // Not explicitly set
    }
    
    // ===== Claiming Enable/Disable Tests =====
    
    @Test
    fun `isClaimingEnabled returns false when automation disabled`() {
        val settings = AutomationSettings(
            automationEnabled = false,
            claimSettings = ClaimAutomationSettings(autoClaimEnabled = true)
        )
        
        assertFalse(settings.isClaimingEnabled(GameStore.EPIC_GAMES))
    }
    
    @Test
    fun `isClaimingEnabled returns false when global claiming disabled`() {
        val settings = AutomationSettings(
            automationEnabled = true,
            claimSettings = ClaimAutomationSettings(autoClaimEnabled = false)
        )
        
        assertFalse(settings.isClaimingEnabled(GameStore.EPIC_GAMES))
    }
    
    @Test
    fun `isClaimingEnabled returns false by default for stores`() {
        val settings = AutomationSettings(
            automationEnabled = true,
            claimSettings = ClaimAutomationSettings(autoClaimEnabled = true)
        )
        
        // Even with global claiming on, per-store default is OFF for safety
        assertFalse("Per-store claiming should be OFF by default",
            settings.isClaimingEnabled(GameStore.EPIC_GAMES))
    }
    
    @Test
    fun `isClaimingEnabled returns true only when fully enabled`() {
        val settings = AutomationSettings(
            automationEnabled = true,
            claimSettings = ClaimAutomationSettings(autoClaimEnabled = true),
            storeSettings = mapOf(
                GameStore.EPIC_GAMES.name to StoreAutomationSettings(
                    enabled = true,
                    autoClaimEnabled = true
                )
            )
        )
        
        assertTrue(settings.isClaimingEnabled(GameStore.EPIC_GAMES))
        assertFalse(settings.isClaimingEnabled(GameStore.STEAM)) // Not explicitly enabled
    }
    
    // ===== Interval Validation Tests =====
    
    @Test
    fun `discovery interval is validated to minimum`() {
        val settings = DiscoveryScheduleSettings(intervalHours = 0)
        
        assertEquals(DiscoveryScheduleSettings.MIN_INTERVAL_HOURS, settings.validatedInterval())
    }
    
    @Test
    fun `discovery interval is validated to maximum`() {
        val settings = DiscoveryScheduleSettings(intervalHours = 1000)
        
        assertEquals(DiscoveryScheduleSettings.MAX_INTERVAL_HOURS, settings.validatedInterval())
    }
    
    @Test
    fun `discovery interval within range is unchanged`() {
        val settings = DiscoveryScheduleSettings(intervalHours = 24)
        
        assertEquals(24L, settings.validatedInterval())
    }
    
    // ===== Time Window Tests =====
    
    @Test
    fun `time window contains hour within range`() {
        val window = TimeWindow(startHour = 9, endHour = 17)
        
        assertTrue(window.contains(9))
        assertTrue(window.contains(12))
        assertTrue(window.contains(16))
    }
    
    @Test
    fun `time window excludes hour outside range`() {
        val window = TimeWindow(startHour = 9, endHour = 17)
        
        assertFalse(window.contains(8))
        assertFalse(window.contains(17))
        assertFalse(window.contains(23))
    }
    
    @Test
    fun `time window handles midnight crossing`() {
        // Window from 22:00 to 06:00 (overnight)
        val window = TimeWindow(startHour = 22, endHour = 6)
        
        assertTrue(window.contains(22))
        assertTrue(window.contains(23))
        assertTrue(window.contains(0))
        assertTrue(window.contains(3))
        assertTrue(window.contains(5))
        assertFalse(window.contains(6))
        assertFalse(window.contains(12))
        assertFalse(window.contains(21))
    }
    
    // ===== Notification Settings Tests =====
    
    @Test
    fun `notification defaults are user-friendly`() {
        val settings = NotificationSettings()
        
        assertTrue("Should notify new giveaways by default", settings.notifyNewGiveaways)
        assertTrue("Should notify expiring soon by default", settings.notifyExpiringSoon)
        assertTrue("Should notify claim success by default", settings.notifyClaimSuccess)
        assertTrue("Should notify claim failure by default", settings.notifyClaimFailure)
        assertTrue("Should notify user action required by default", settings.notifyUserActionRequired)
        assertEquals(24, settings.expiryWarningHours)
    }
    
    // ===== Claim Settings Safety Tests =====
    
    @Test
    fun `claim settings have safe defaults`() {
        val settings = ClaimAutomationSettings()
        
        assertFalse("Auto-claim MUST be disabled by default", settings.autoClaimEnabled)
        assertTrue("WiFi-only should be enabled for claims", settings.wifiOnly)
        assertTrue("Max claims per run should be reasonable", settings.maxClaimsPerRun <= 10)
        assertTrue("Delay between claims should be at least 1 second", 
            settings.delayBetweenClaimsMs >= 1000)
    }
    
    @Test
    fun `store automation settings have safe defaults`() {
        val settings = StoreAutomationSettings()
        
        assertTrue("Store should be enabled by default (for discovery)", settings.enabled)
        assertFalse("Auto-claim MUST be disabled by default for stores", settings.autoClaimEnabled)
    }
    
    // ===== Constants Validation =====
    
    @Test
    fun `interval constants are reasonable`() {
        // Discovery
        assertTrue(DiscoveryScheduleSettings.MIN_INTERVAL_HOURS >= 1)
        assertTrue(DiscoveryScheduleSettings.MAX_INTERVAL_HOURS <= 168) // 1 week
        
        // Claiming
        assertTrue(ClaimAutomationSettings.MIN_INTERVAL_HOURS >= 1)
        assertTrue(ClaimAutomationSettings.MAX_INTERVAL_HOURS <= 168)
    }
}
