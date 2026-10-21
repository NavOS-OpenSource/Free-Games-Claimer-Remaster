package com.navos.gameclaimer

import android.app.Application

/**
 * NavOS GameClaimer Application class.
 * 
 * Handles app-wide initialization:
 * - Dependency injection setup (Phase 4+)
 * - WorkManager configuration (Phase 7+)
 * - Security initialization
 * 
 * Security note: This is where we'll initialize:
 * - Encrypted storage for credentials
 * - Network security policies
 * - Certificate pinning (if needed)
 */
class GameClaimerApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize app-wide components
        initializeSecurity()
    }
    
    private fun initializeSecurity() {
        // Future: Initialize EncryptedSharedPreferences
        // Future: Configure certificate pinning for store APIs
        // Current: Basic setup only, detailed implementation in Phase 5+
    }
}
