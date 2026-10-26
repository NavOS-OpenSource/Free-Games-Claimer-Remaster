package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 6: Giveaway Model Unit Tests
 */
class GiveawayModelTest {
    
    @Test
    fun `giveaway is available when within time window`() {
        val now = System.currentTimeMillis()
        val giveaway = createTestGiveaway(
            startTime = now - 10000,
            endTime = now + 10000
        )
        
        assertTrue(giveaway.isAvailable(now))
    }
    
    @Test
    fun `giveaway is not available when before start time`() {
        val now = System.currentTimeMillis()
        val giveaway = createTestGiveaway(
            startTime = now + 10000,
            endTime = now + 20000
        )
        
        assertFalse(giveaway.isAvailable(now))
    }
    
    @Test
    fun `giveaway is not available when after end time`() {
        val now = System.currentTimeMillis()
        val giveaway = createTestGiveaway(
            startTime = now - 20000,
            endTime = now - 10000
        )
        
        assertFalse(giveaway.isAvailable(now))
    }
    
    @Test
    fun `giveaway is available when no time bounds set`() {
        val giveaway = createTestGiveaway(
            startTime = null,
            endTime = null
        )
        
        assertTrue(giveaway.isAvailable())
    }
    
    @Test
    fun `FREE_TO_KEEP is permanently free`() {
        val giveaway = createTestGiveaway(type = GiveawayType.FREE_TO_KEEP)
        assertTrue(giveaway.isPermanentlyFree())
    }
    
    @Test
    fun `FREE_WEEKEND is not permanently free`() {
        val giveaway = createTestGiveaway(type = GiveawayType.FREE_WEEKEND)
        assertFalse(giveaway.isPermanentlyFree())
    }
    
    @Test
    fun `DEMO_OR_TRIAL is not permanently free`() {
        val giveaway = createTestGiveaway(type = GiveawayType.DEMO_OR_TRIAL)
        assertFalse(giveaway.isPermanentlyFree())
    }
    
    @Test
    fun `giveaway type enum covers all expected types`() {
        val types = GiveawayType.entries
        
        assertTrue(types.contains(GiveawayType.FREE_TO_KEEP))
        assertTrue(types.contains(GiveawayType.FREE_WEEKEND))
        assertTrue(types.contains(GiveawayType.DEMO_OR_TRIAL))
        assertTrue(types.contains(GiveawayType.FREE_TO_PLAY))
        assertTrue(types.contains(GiveawayType.DLC_OR_ADDON))
        assertTrue(types.contains(GiveawayType.SUBSCRIPTION_PERK))
        assertTrue(types.contains(GiveawayType.UNKNOWN))
    }
    
    @Test
    fun `claim eligibility enum covers all expected states`() {
        val states = ClaimEligibility.entries
        
        assertTrue(states.contains(ClaimEligibility.ELIGIBLE))
        assertTrue(states.contains(ClaimEligibility.ALREADY_OWNED))
        assertTrue(states.contains(ClaimEligibility.REQUIRES_AUTH))
        assertTrue(states.contains(ClaimEligibility.REGION_LOCKED))
        assertTrue(states.contains(ClaimEligibility.EXPIRED))
        assertTrue(states.contains(ClaimEligibility.UNKNOWN))
    }
    
    @Test
    fun `discovery source enum covers expected sources`() {
        val sources = DiscoverySource.entries
        
        assertTrue(sources.contains(DiscoverySource.GAMER_POWER))
        assertTrue(sources.contains(DiscoverySource.STORE_DIRECT))
        assertTrue(sources.contains(DiscoverySource.USER_ADDED))
        assertTrue(sources.contains(DiscoverySource.OTHER))
    }
    
    @Test
    fun `claim result Success contains required fields`() {
        val result = ClaimResult.Success(
            giveawayId = "test-123",
            claimedAt = 1234567890L
        )
        
        assertEquals("test-123", result.giveawayId)
        assertEquals(1234567890L, result.claimedAt)
    }
    
    @Test
    fun `claim result Failed contains error info`() {
        val result = ClaimResult.Failed(
            giveawayId = "test-123",
            reason = "Network error",
            isRetryable = true
        )
        
        assertEquals("test-123", result.giveawayId)
        assertEquals("Network error", result.reason)
        assertTrue(result.isRetryable)
    }
    
    @Test
    fun `discovery result Success contains giveaways`() {
        val giveaways = listOf(createTestGiveaway())
        val result = DiscoveryResult.Success(
            giveaways = giveaways,
            source = DiscoverySource.GAMER_POWER,
            durationMs = 100
        )
        
        assertEquals(1, result.giveaways.size)
        assertEquals(DiscoverySource.GAMER_POWER, result.source)
        assertEquals(100, result.durationMs)
    }
    
    @Test
    fun `discovery result Failed contains error details`() {
        val result = DiscoveryResult.Failed(
            source = DiscoverySource.STORE_DIRECT,
            error = "Timeout",
            isRetryable = true
        )
        
        assertEquals(DiscoverySource.STORE_DIRECT, result.source)
        assertEquals("Timeout", result.error)
        assertTrue(result.isRetryable)
    }
    
    @Test
    fun `giveaway ID format is correct`() {
        val giveaway = createTestGiveaway(
            id = "gamerpower:epic_games:12345",
            store = GameStore.EPIC_GAMES
        )
        
        assertTrue(giveaway.id.contains("gamerpower"))
        assertTrue(giveaway.id.contains("epic_games"))
        assertTrue(giveaway.id.contains("12345"))
    }
    
    private fun createTestGiveaway(
        id: String = "test:epic_games:1",
        title: String = "Test Game",
        store: GameStore = GameStore.EPIC_GAMES,
        type: GiveawayType = GiveawayType.FREE_TO_KEEP,
        claimUrl: String = "https://store.epicgames.com/test",
        startTime: Long? = null,
        endTime: Long? = null
    ): Giveaway {
        return Giveaway(
            id = id,
            title = title,
            store = store,
            type = type,
            claimUrl = claimUrl,
            startTime = startTime,
            endTime = endTime,
            source = DiscoverySource.GAMER_POWER
        )
    }
}
