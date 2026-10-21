package com.navos.gameclaimer

import android.app.Application
import com.navos.gameclaimer.data.adapter.DefaultStoreAdapterFactory
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.domain.adapter.StoreAdapterFactory
import com.navos.gameclaimer.domain.automation.AutomationManager

/**
 * NavOS GameClaimer Application class.
 * 
 * Handles app-wide initialization:
 * - Store adapter factory (Phase 7+)
 * - Automation manager (Phase 13)
 * - WorkManager configuration
 * - Security initialization
 * 
 * DEPENDENCY NOTE: This provides basic DI until we add Hilt/Dagger.
 * In production, these would be injected via proper DI.
 * 
 * Security note: This is where we'll initialize:
 * - Encrypted storage for credentials
 * - Network security policies
 * - Certificate pinning (if needed)
 */
class GameClaimerApplication : Application() {
    
    /**
     * Factory for creating store adapters.
     * Used by ClaimWorker and other components.
     */
    val adapterFactory: StoreAdapterFactory by lazy {
        DefaultStoreAdapterFactory()
    }
    
    /**
     * Central automation orchestration.
     * Manages discovery and claim scheduling.
     */
    val automationManager: AutomationManager by lazy {
        AutomationManager(
            context = this,
            database = GameClaimerDatabase.getInstance(this),
            adapterFactory = adapterFactory
        )
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize app-wide components
        initializeSecurity()
        initializeAutomation()
    }
    
    private fun initializeSecurity() {
        // Future: Initialize EncryptedSharedPreferences
        // Future: Configure certificate pinning for store APIs
        // Current: Basic setup only, detailed implementation in Phase 5+
    }
    
    /**
     * Initialize automation system.
     * 
     * Recovers any interrupted queue items from previous run.
     * Does NOT automatically start scheduling - that requires user opt-in.
     */
    private fun initializeAutomation() {
        // Note: We don't call recoverFromCrash() here because it's a suspend function.
        // The ClaimWorker handles recovery at the start of its doWork().
        // This is intentional - recovery happens when work actually starts,
        // not at app launch (which could be just to check notifications).
    }
}
