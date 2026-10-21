package com.navos.gameclaimer.data.adapter

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterFactory
import com.navos.gameclaimer.domain.model.GameStore

/**
 * Phase 12: Concrete implementation of StoreAdapterFactory.
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
 * PHASE 10: Microsoft Store, Ubisoft (discovery only)
 * PHASE 11: Fab, Unity Asset Store (discovery only)
 * PHASE 12: Fanatical, Alienware Arena (discovery only via GamerPower)
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
            GameStore.AMAZON_PRIME to PrimeGamingAdapter(),
            // Phase 10: Microsoft Store and Ubisoft
            GameStore.MICROSOFT_STORE to MicrosoftStoreAdapter(),
            GameStore.UBISOFT to UbisoftAdapter(),
            // Phase 11: Fab and Unity Asset Store
            GameStore.FAB to FabAdapter(),
            GameStore.UNITY_ASSET_STORE to UnityAssetStoreAdapter(),
            // Phase 12: Fanatical and Alienware Arena (via GamerPower)
            GameStore.FANATICAL to FanaticalAdapter(),
            GameStore.ALIENWARE_ARENA to AlienwareArenaAdapter()
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
