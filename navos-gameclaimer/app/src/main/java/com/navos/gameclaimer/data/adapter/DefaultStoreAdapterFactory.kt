package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterFactory
import com.navos.gameclaimer.domain.model.GameStore

/**
 * Phase 9: Concrete implementation of StoreAdapterFactory.
 * 
 * Provides access to all registered store adapters.
 * 
 * DESIGN:
 * - Lazy initialization of adapters
 * - Only returns adapters that support discovery
 * - Future: Configuration-based adapter enabling/disabling
 * 
 * PHASE 7: Steam, GOG, Itch.io, IndieGala
 * PHASE 8: Epic Games Store (PC + mobile offers)
 * PHASE 9: Prime Gaming (discovery only, external code support)
 */
class DefaultStoreAdapterFactory : StoreAdapterFactory {
    
    // Lazily initialized adapters
    private val adapters: Map<GameStore, StoreAdapter> by lazy {
        mapOf(
            GameStore.STEAM to SteamStoreAdapter(),
            GameStore.GOG to GogStoreAdapter(),
            GameStore.ITCH_IO to ItchioStoreAdapter(),
            GameStore.INDIEGALA to IndieGalaStoreAdapter(),
            // Phase 8: Epic Games Store
            GameStore.EPIC_GAMES to EpicStoreAdapter(),
            // Phase 9: Prime Gaming (Amazon Luna)
            GameStore.AMAZON_PRIME to PrimeGamingAdapter()
        )
    }
    
    /**
     * Get an adapter for a specific store.
     */
    override fun getAdapter(store: GameStore): StoreAdapter? {
        return adapters[store]
    }
    
    /**
     * Get all registered adapters.
     */
    override fun getAllAdapters(): List<StoreAdapter> {
        return adapters.values.toList()
    }
    
    /**
     * Get adapters that support discovery.
     * Used by DiscoveryPipeline to fetch giveaways.
     */
    override fun getDiscoveryAdapters(): List<StoreAdapter> {
        return adapters.values.filter { it.supportsDiscovery }
    }
    
    /**
     * Get adapters that support claiming.
     * Used by claim workflows to process giveaways.
     */
    fun getClaimingAdapters(): List<StoreAdapter> {
        return adapters.values.filter { it.supportsClaiming }
    }
    
    /**
     * Check if a store is supported.
     */
    fun isStoreSupported(store: GameStore): Boolean {
        return store in adapters
    }
    
    /**
     * Get the list of supported stores.
     */
    fun getSupportedStores(): Set<GameStore> {
        return adapters.keys
    }
}
