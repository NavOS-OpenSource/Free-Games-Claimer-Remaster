package com.navos.gameclaimer.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.navos.gameclaimer.domain.model.GameStore

/**
 * Phase 14: Notification manager for GameClaimer.
 * 
 * Creates idempotent channels and sends notifications with proper permissions.
 * 
 * GPT REQUIREMENTS:
 * - Stable notification IDs for deduplication
 * - Respect Android 13+ notification permission
 * - Never execute claims or resolve uncertain items from notifications
 * - No sensitive content on lock screen
 */
class GameClaimerNotificationManager(
    private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    
    companion object {
        // Channel IDs (stable, idempotent)
        const val CHANNEL_CLAIM_SUCCESS = "claim_success"
        const val CHANNEL_ACTION_REQUIRED = "action_required"
        const val CHANNEL_EXPIRING = "expiring_offers"
        const val CHANNEL_FAILURES = "automation_failures"
        
        // Notification ID ranges (stable for deduplication)
        private const val ID_CLAIM_SUCCESS_BASE = 1000
        private const val ID_ACTION_REQUIRED_BASE = 2000
        private const val ID_EXPIRING_BASE = 3000
        private const val ID_FAILURES_BASE = 4000
        
        // Singleton
        @Volatile
        private var INSTANCE: GameClaimerNotificationManager? = null
        
        fun getInstance(context: Context): GameClaimerNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GameClaimerNotificationManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * Initialize notification channels.
     * Safe to call multiple times - channels are idempotent.
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Channel 1: Claim Success
        val successChannel = NotificationChannel(
            CHANNEL_CLAIM_SUCCESS,
            "Claim Success",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when games are successfully claimed"
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        
        // Channel 2: Action Required
        val actionChannel = NotificationChannel(
            CHANNEL_ACTION_REQUIRED,
            "Action Required",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when your action is needed (login, verification)"
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        
        // Channel 3: Expiring Offers
        val expiringChannel = NotificationChannel(
            CHANNEL_EXPIRING,
            "Expiring Offers",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders about free games expiring soon"
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        
        // Channel 4: Automation Failures
        val failuresChannel = NotificationChannel(
            CHANNEL_FAILURES,
            "Automation Issues",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications about repeated automation failures"
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        
        systemManager.createNotificationChannels(listOf(
            successChannel,
            actionChannel,
            expiringChannel,
            failuresChannel
        ))
    }
    
    /**
     * Check if notification permission is granted.
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * Check if a specific channel is enabled by user.
     */
    fun isChannelEnabled(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = systemManager.getNotificationChannel(channelId)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
    
    // ===== Notification Methods =====
    
    /**
     * Notify about a successful claim.
     * 
     * ONLY call after confirmed success from store.
     */
    fun notifyClaimSuccess(
        giveawayId: String,
        gameTitle: String,
        store: GameStore
    ) {
        if (!hasNotificationPermission()) return
        
        val notificationId = ID_CLAIM_SUCCESS_BASE + giveawayId.hashCode().mod(1000)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_CLAIM_SUCCESS)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("Game Claimed!")
            .setContentText("$gameTitle from ${store.displayName}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(CHANNEL_CLAIM_SUCCESS)
            .build()
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission denied - silently fail
        }
    }
    
    /**
     * Notify that user action is required.
     * 
     * Does NOT include sensitive details on lock screen.
     */
    fun notifyActionRequired(
        giveawayId: String,
        gameTitle: String,
        reason: String
    ) {
        if (!hasNotificationPermission()) return
        
        val notificationId = ID_ACTION_REQUIRED_BASE + giveawayId.hashCode().mod(1000)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ACTION_REQUIRED)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Action Required")
            .setContentText("$gameTitle needs your attention")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$gameTitle: $reason"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup(CHANNEL_ACTION_REQUIRED)
            .build()
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission denied
        }
    }
    
    /**
     * Notify about an expiring offer.
     */
    fun notifyExpiringSoon(
        giveawayId: String,
        gameTitle: String,
        store: GameStore,
        hoursRemaining: Int
    ) {
        if (!hasNotificationPermission()) return
        
        val notificationId = ID_EXPIRING_BASE + giveawayId.hashCode().mod(1000)
        
        val timeText = when {
            hoursRemaining <= 1 -> "less than an hour"
            hoursRemaining < 24 -> "$hoursRemaining hours"
            else -> "${hoursRemaining / 24} days"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_EXPIRING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Expiring Soon")
            .setContentText("$gameTitle expires in $timeText")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(CHANNEL_EXPIRING)
            .build()
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission denied
        }
    }
    
    /**
     * Notify about repeated automation failures.
     */
    fun notifyAutomationFailure(
        failureCount: Int,
        lastError: String
    ) {
        if (!hasNotificationPermission()) return
        
        val notificationId = ID_FAILURES_BASE
        
        val notification = NotificationCompat.Builder(context, CHANNEL_FAILURES)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Automation Issues")
            .setContentText("$failureCount operations need attention")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Recent issue: $lastError"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true) // Don't spam
            .build()
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission denied
        }
    }
    
    /**
     * Cancel a specific notification.
     */
    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
    
    /**
     * Cancel all notifications from this app.
     */
    fun cancelAll() {
        notificationManager.cancelAll()
    }
}
