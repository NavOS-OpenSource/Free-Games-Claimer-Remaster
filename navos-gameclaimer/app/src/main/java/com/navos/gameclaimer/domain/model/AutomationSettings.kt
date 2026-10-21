package com.navos.gameclaimer.domain.model

import kotlinx.serialization.Serializable

/**
 * Phase 13: Automation settings and preferences.
 * 
 * GPT REQUIREMENTS:
 * - Configurable automation scheduling; default interval is 12 hours
 * - Respect user's enabled/disabled settings
 * - Support cancellation and graceful process interruption
 * 
 * DESIGN:
 * - All settings have sensible defaults
 * - Per-store enable/disable
 * - Battery and network constraints respected via WorkManager
 */
@Serializable
data class AutomationSettings(
    /** Master switch for all automation */
    val automationEnabled: Boolean = false,
    
    /** Discovery scheduling settings */
    val discoverySettings: DiscoveryScheduleSettings = DiscoveryScheduleSettings(),
    
    /** Claim automation settings */
    val claimSettings: ClaimAutomationSettings = ClaimAutomationSettings(),
    
    /** Per-store settings */
    val storeSettings: Map<String, StoreAutomationSettings> = emptyMap(),
    
    /** Notification settings */
    val notificationSettings: NotificationSettings = NotificationSettings(),
    
    /** Last modified timestamp */
    val lastModifiedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Default settings instance */
        val DEFAULT = AutomationSettings()
    }
    
    // Phase 14: Convenience accessors
    
    /** Discovery interval in hours */
    val discoveryIntervalHours: Int
        get() = discoverySettings.intervalHours.toInt()
    
    /** Claim interval in hours */
    val claimIntervalHours: Int
        get() = claimSettings.intervalHours.toInt()
    
    /** Whether WiFi is required for operations */
    val requireWifi: Boolean
        get() = discoverySettings.wifiOnly || claimSettings.wifiOnly
    
    /** Whether to respect charging state */
    val requireCharging: Boolean
        get() = false // Could be added to settings if needed
    
    /**
     * Whether automation is effectively enabled for a specific store.
     */
    fun isStoreEnabled(store: GameStore): Boolean {
        if (!automationEnabled) return false
        val settings = storeSettings[store.name]
        return settings?.enabled ?: true // Default to enabled if not explicitly set
    }
    
    /**
     * Whether claiming is allowed for a store.
     * 
     * NOTE: All adapters currently have supportsClaiming=false.
     * This setting will take effect when claiming is enabled.
     */
    fun isClaimingEnabled(store: GameStore): Boolean {
        if (!automationEnabled) return false
        if (!claimSettings.autoClaimEnabled) return false
        val settings = storeSettings[store.name]
        return settings?.autoClaimEnabled ?: false // Default to disabled for safety
    }
}

/**
 * Discovery scheduling configuration.
 */
@Serializable
data class DiscoveryScheduleSettings(
    /** Whether automatic discovery is enabled */
    val enabled: Boolean = true,
    
    /** Interval in hours between discovery runs */
    val intervalHours: Long = DEFAULT_DISCOVERY_INTERVAL_HOURS,
    
    /** Whether to only run on WiFi */
    val wifiOnly: Boolean = false,
    
    /** Whether to respect battery saver mode */
    val respectBatterySaver: Boolean = true,
    
    /** Time window for preferred execution (null = any time) */
    val preferredTimeWindow: TimeWindow? = null
) {
    companion object {
        const val DEFAULT_DISCOVERY_INTERVAL_HOURS = 6L
        const val MIN_INTERVAL_HOURS = 1L
        const val MAX_INTERVAL_HOURS = 168L // 1 week
    }
    
    /**
     * Validate and coerce interval to valid range.
     */
    fun validatedInterval(): Long = intervalHours.coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)
}

/**
 * Claim automation configuration.
 * 
 * GPT REQUIREMENT: "Adapters with supportsClaiming=false must remain discovery-only.
 * Do not enable claims for them in this phase."
 * 
 * This settings structure exists for future use but auto-claiming is disabled.
 */
@Serializable
data class ClaimAutomationSettings(
    /** 
     * Whether automatic claiming is enabled.
     * 
     * SECURITY: Currently forced to false for all adapters.
     * Will be enabled per-store after security review.
     */
    val autoClaimEnabled: Boolean = false,
    
    /** Interval in hours between claim runs */
    val intervalHours: Long = DEFAULT_CLAIM_INTERVAL_HOURS,
    
    /** Whether to require WiFi for claim operations */
    val wifiOnly: Boolean = true,
    
    /** Maximum claims per run (to avoid abuse flags) */
    val maxClaimsPerRun: Int = 5,
    
    /** Delay between individual claims in milliseconds */
    val delayBetweenClaimsMs: Long = 5_000L
) {
    companion object {
        const val DEFAULT_CLAIM_INTERVAL_HOURS = 12L
        const val MIN_INTERVAL_HOURS = 1L
        const val MAX_INTERVAL_HOURS = 168L // 1 week
    }
}

/**
 * Per-store automation settings.
 */
@Serializable
data class StoreAutomationSettings(
    /** Whether this store is enabled for automation */
    val enabled: Boolean = true,
    
    /** 
     * Whether auto-claiming is enabled for this store.
     * 
     * SECURITY: Only effective if:
     * 1. Global automationEnabled is true
     * 2. Global autoClaimEnabled is true
     * 3. The adapter's supportsClaiming is true
     * 4. User is authenticated with the store
     */
    val autoClaimEnabled: Boolean = false,
    
    /** Store-specific interval override (null = use global) */
    val intervalHoursOverride: Long? = null,
    
    /** Priority for this store (higher = processed first) */
    val priority: Int = ClaimQueueItem.PRIORITY_NORMAL
)

/**
 * Notification preferences for automation events.
 */
@Serializable
data class NotificationSettings(
    /** Notify when new giveaways are discovered */
    val notifyNewGiveaways: Boolean = true,
    
    /** Notify when giveaways are about to expire */
    val notifyExpiringSoon: Boolean = true,
    
    /** Hours before expiry to send notification */
    val expiryWarningHours: Int = 24,
    
    /** Notify on successful claims */
    val notifyClaimSuccess: Boolean = true,
    
    /** Notify on claim failures */
    val notifyClaimFailure: Boolean = true,
    
    /** Notify when user action is required */
    val notifyUserActionRequired: Boolean = true,
    
    /** Whether to use sound for notifications */
    val soundEnabled: Boolean = true
)

/**
 * Time window for preferred execution.
 */
@Serializable
data class TimeWindow(
    /** Start hour (0-23) */
    val startHour: Int,
    
    /** End hour (0-23) */
    val endHour: Int
) {
    /**
     * Check if a given hour is within this window.
     */
    fun contains(hour: Int): Boolean {
        return if (startHour <= endHour) {
            hour in startHour until endHour
        } else {
            // Window crosses midnight
            hour >= startHour || hour < endHour
        }
    }
}
