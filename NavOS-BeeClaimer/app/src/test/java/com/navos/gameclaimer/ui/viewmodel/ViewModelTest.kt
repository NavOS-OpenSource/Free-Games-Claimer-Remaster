package com.navos.gameclaimer.ui.viewmodel

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 14: UI state and model tests.
 * 
 * Tests cover:
 * - UI state model correctness
 * - Settings defaults
 * - Safety: state properties
 */

/**
 * Tests for DiscoverUiState.
 */
class DiscoverUiStateTest {
    
    @Test
    fun `Loading state is a singleton`() {
        val state1 = DiscoverUiState.Loading
        val state2 = DiscoverUiState.Loading
        
        assertSame(state1, state2)
    }
    
    @Test
    fun `Empty state is a singleton`() {
        val state1 = DiscoverUiState.Empty
        val state2 = DiscoverUiState.Empty
        
        assertSame(state1, state2)
    }
    
    @Test
    fun `Success state holds count`() {
        val state = DiscoverUiState.Success(totalCount = 5)
        
        assertEquals(5, state.totalCount)
    }
    
    @Test
    fun `Error state holds message`() {
        val state = DiscoverUiState.Error(message = "Network error")
        
        assertEquals("Network error", state.message)
    }
}

/**
 * Tests for NeedsAttentionUiState.
 */
class NeedsAttentionUiStateTest {
    
    @Test
    fun `Loading state is a singleton`() {
        val state1 = NeedsAttentionUiState.Loading
        val state2 = NeedsAttentionUiState.Loading
        
        assertSame(state1, state2)
    }
    
    @Test
    fun `Empty state is a singleton`() {
        val state1 = NeedsAttentionUiState.Empty
        val state2 = NeedsAttentionUiState.Empty
        
        assertSame(state1, state2)
    }
    
    @Test
    fun `Success state holds uncertain and blocked counts`() {
        val state = NeedsAttentionUiState.Success(
            uncertainCount = 3,
            blockedCount = 2
        )
        
        assertEquals(3, state.uncertainCount)
        assertEquals(2, state.blockedCount)
    }
    
    @Test
    fun `Error state holds message`() {
        val state = NeedsAttentionUiState.Error(message = "Failed to load")
        
        assertEquals("Failed to load", state.message)
    }
}

/**
 * Tests for automation settings defaults.
 */
class AutomationSettingsDefaultsTest {
    
    @Test
    fun `automation disabled by default`() {
        val defaults = com.navos.gameclaimer.domain.model.AutomationSettings.DEFAULT
        
        assertFalse(defaults.automationEnabled)
    }
    
    @Test
    fun `discovery interval defaults are reasonable`() {
        val defaults = com.navos.gameclaimer.domain.model.AutomationSettings.DEFAULT
        
        // Default is 6 hours from discoverySettings
        assertEquals(6, defaults.discoveryIntervalHours)
    }
    
    @Test
    fun `claim interval defaults are reasonable`() {
        val defaults = com.navos.gameclaimer.domain.model.AutomationSettings.DEFAULT
        
        // Default is 12 hours from claimSettings
        assertEquals(12, defaults.claimIntervalHours)
    }
    
    @Test
    fun `wifi required when claim settings require wifi`() {
        val defaults = com.navos.gameclaimer.domain.model.AutomationSettings.DEFAULT
        
        // claimSettings.wifiOnly is true by default
        assertTrue(defaults.requireWifi)
    }
    
    @Test
    fun `auto claim disabled by default for safety`() {
        val defaults = com.navos.gameclaimer.domain.model.AutomationSettings.DEFAULT
        
        // Safety: auto-claiming is disabled
        assertFalse(defaults.claimSettings.autoClaimEnabled)
    }
}

/**
 * Tests for notification preferences defaults.
 */
class NotificationPreferencesDefaultsTest {
    
    @Test
    fun `claim success enabled by default`() {
        val defaults = com.navos.gameclaimer.notification.NotificationPreferences.DEFAULT
        
        assertTrue(defaults.claimSuccessEnabled)
    }
    
    @Test
    fun `action required enabled by default`() {
        val defaults = com.navos.gameclaimer.notification.NotificationPreferences.DEFAULT
        
        assertTrue(defaults.actionRequiredEnabled)
    }
    
    @Test
    fun `expiring offers enabled by default`() {
        val defaults = com.navos.gameclaimer.notification.NotificationPreferences.DEFAULT
        
        assertTrue(defaults.expiringOffersEnabled)
    }
    
    @Test
    fun `automation failures enabled by default`() {
        val defaults = com.navos.gameclaimer.notification.NotificationPreferences.DEFAULT
        
        assertTrue(defaults.automationFailuresEnabled)
    }
    
    @Test
    fun `expiry threshold defaults to 24 hours`() {
        val defaults = com.navos.gameclaimer.notification.NotificationPreferences.DEFAULT
        
        assertEquals(24, defaults.expiringHoursThreshold)
    }
    
    @Test
    fun `failure count threshold defaults to 3`() {
        val defaults = com.navos.gameclaimer.notification.NotificationPreferences.DEFAULT
        
        assertEquals(3, defaults.failureCountThreshold)
    }
    
    @Test
    fun `group notifications enabled by default`() {
        val defaults = com.navos.gameclaimer.notification.NotificationPreferences.DEFAULT
        
        assertTrue(defaults.groupNotifications)
    }
}

/**
 * Tests for BlockedReason enum.
 */
class BlockedReasonTest {
    
    @Test
    fun `all blocked reasons are defined`() {
        val reasons = BlockedReason.values()
        
        assertEquals(5, reasons.size)
        assertTrue(reasons.contains(BlockedReason.RequiresAuth))
        assertTrue(reasons.contains(BlockedReason.RequiresManualAction))
        assertTrue(reasons.contains(BlockedReason.Captcha))
        assertTrue(reasons.contains(BlockedReason.TwoFactorAuth))
        assertTrue(reasons.contains(BlockedReason.Unknown))
    }
}

/**
 * Safety-critical tests for uncertain claim resolution.
 */
class UncertainClaimSafetyTest {
    
    @Test
    fun `UNCERTAIN state requires user action`() {
        val state = com.navos.gameclaimer.domain.model.ClaimQueueState.UNCERTAIN
        
        assertTrue(
            "UNCERTAIN state must require user action",
            state.requiresUserAction
        )
    }
    
    @Test
    fun `UNCERTAIN state cannot auto retry`() {
        val state = com.navos.gameclaimer.domain.model.ClaimQueueState.UNCERTAIN
        
        assertFalse(
            "UNCERTAIN state must not auto-retry (could duplicate side effects)",
            state.canAutoRetry
        )
    }
    
    @Test
    fun `UNCERTAIN state is not terminal`() {
        val state = com.navos.gameclaimer.domain.model.ClaimQueueState.UNCERTAIN
        
        assertFalse(
            "UNCERTAIN state is not terminal - can be resolved by user",
            state.isTerminal
        )
    }
    
    @Test
    fun `only RETRY_SCHEDULED can auto retry`() {
        val states = com.navos.gameclaimer.domain.model.ClaimQueueState.values()
        
        for (state in states) {
            if (state == com.navos.gameclaimer.domain.model.ClaimQueueState.RETRY_SCHEDULED) {
                assertTrue("RETRY_SCHEDULED should auto-retry", state.canAutoRetry)
            } else {
                assertFalse(
                    "Only RETRY_SCHEDULED should auto-retry, not $state",
                    state.canAutoRetry
                )
            }
        }
    }
    
    @Test
    fun `BLOCKED_USER_ACTION requires user action`() {
        val state = com.navos.gameclaimer.domain.model.ClaimQueueState.BLOCKED_USER_ACTION
        
        assertTrue(
            "BLOCKED_USER_ACTION must require user action",
            state.requiresUserAction
        )
    }
}
