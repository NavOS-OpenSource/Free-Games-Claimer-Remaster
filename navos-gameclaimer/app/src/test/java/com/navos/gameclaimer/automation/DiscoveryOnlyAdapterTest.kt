package com.navos.gameclaimer.automation

import com.navos.gameclaimer.data.adapter.DefaultStoreAdapterFactory
import com.navos.gameclaimer.domain.model.GameStore
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 13: Tests verifying discovery-only adapters cannot claim.
 * 
 * GPT REQUIREMENT (CRITICAL):
 * "Adapters with supportsClaiming=false must remain discovery-only.
 *  Do not enable claims for them in this phase."
 * 
 * This test ensures the safety invariant that NO adapter currently supports
 * automated claiming. This is by design - claiming will only be enabled
 * after a separate security review per GPT's roadmap.
 */
class DiscoveryOnlyAdapterTest {
    
    private val adapterFactory = DefaultStoreAdapterFactory()
    
    // ===== CRITICAL: All Adapters Are Discovery-Only =====
    
    @Test
    fun `ALL adapters have supportsClaiming false - CRITICAL SAFETY`() {
        val adapters = adapterFactory.getAllAdapters()
        
        assertTrue("Should have adapters registered", adapters.isNotEmpty())
        
        for (adapter in adapters) {
            assertFalse(
                "CRITICAL: ${adapter.store.displayName} MUST have supportsClaiming=false",
                adapter.supportsClaiming
            )
        }
    }
    
    @Test
    fun `getClaimAdapters returns empty list`() {
        val claimAdapters = adapterFactory.getClaimAdapters()
        
        assertTrue(
            "getClaimAdapters MUST return empty list when all adapters are discovery-only",
            claimAdapters.isEmpty()
        )
    }
    
    @Test
    fun `all 12 adapters are discovery-only`() {
        val adapters = adapterFactory.getAllAdapters()
        val discoveryAdapters = adapterFactory.getDiscoveryAdapters()
        
        assertEquals("Should have 12 adapters", 12, adapters.size)
        assertEquals("All 12 should support discovery", 12, discoveryAdapters.size)
        assertEquals("Claim adapters should be 0", 0, adapterFactory.getClaimAdapters().size)
    }
    
    // ===== Per-Store Verification =====
    
    @Test
    fun `Steam adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.STEAM)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Steam must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `GOG adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.GOG)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: GOG must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Itch_io adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.ITCH_IO)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Itch.io must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `IndieGala adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.INDIEGALA)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: IndieGala must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Epic Games adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.EPIC_GAMES)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Epic must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Prime Gaming adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.AMAZON_PRIME)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Prime must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Microsoft Store adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.MICROSOFT_STORE)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Microsoft must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Ubisoft adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.UBISOFT)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Ubisoft must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Fab adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.FAB)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Fab must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Unity Asset Store adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.UNITY_ASSET_STORE)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Unity must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Fanatical adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.FANATICAL)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Fanatical must be discovery-only", adapter.supportsClaiming)
    }
    
    @Test
    fun `Alienware Arena adapter is discovery-only`() {
        val adapter = adapterFactory.getAdapter(GameStore.ALIENWARE_ARENA)
        assertNotNull(adapter)
        assertTrue(adapter!!.supportsDiscovery)
        assertFalse("CRITICAL: Alienware Arena must be discovery-only", adapter.supportsClaiming)
    }
    
    // ===== Documentation Check =====
    
    @Test
    fun `adapter count matches GameStore enum count`() {
        // All stores defined in GameStore should have adapters
        val stores = GameStore.values()
        val adapters = adapterFactory.getAllAdapters()
        
        assertEquals(
            "All stores should have adapters implemented",
            stores.size,
            adapters.size
        )
    }
}
