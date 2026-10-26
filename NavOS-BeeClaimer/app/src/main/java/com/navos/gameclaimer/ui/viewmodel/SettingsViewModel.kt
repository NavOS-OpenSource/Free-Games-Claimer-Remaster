package com.navos.gameclaimer.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.navos.gameclaimer.domain.automation.AutomationManager
import com.navos.gameclaimer.domain.model.AutomationSettings
import com.navos.gameclaimer.notification.GameClaimerNotificationManager
import com.navos.gameclaimer.notification.NotificationPreferences
import com.navos.gameclaimer.worker.ClaimWorker
import com.navos.gameclaimer.worker.DiscoveryWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Phase 14: ViewModel for Settings screen.
 * 
 * Manages automation, notifications, and app preferences.
 * Uses StateFlow for lifecycle-aware state management.
 */
class SettingsViewModel(
    private val context: Context,
    private val automationManager: AutomationManager
) : ViewModel() {
    
    private val workManager = WorkManager.getInstance(context)
    private val notificationManager = GameClaimerNotificationManager.getInstance(context)
    
    // ===== Automation Settings =====
    
    private val _automationEnabled = MutableStateFlow(false)
    val automationEnabled: StateFlow<Boolean> = _automationEnabled.asStateFlow()
    
    private val _discoveryIntervalHours = MutableStateFlow(6)
    val discoveryIntervalHours: StateFlow<Int> = _discoveryIntervalHours.asStateFlow()
    
    private val _requireWifi = MutableStateFlow(false)
    val requireWifi: StateFlow<Boolean> = _requireWifi.asStateFlow()
    
    // ===== Notification Preferences =====
    
    private val _notificationPrefs = MutableStateFlow(NotificationPreferences.DEFAULT)
    val notificationPrefs: StateFlow<NotificationPreferences> = _notificationPrefs.asStateFlow()
    
    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()
    
    // ===== Work Status =====
    
    private val _discoveryWorkState = MutableStateFlow<WorkState>(WorkState.Unknown)
    val discoveryWorkState: StateFlow<WorkState> = _discoveryWorkState.asStateFlow()
    
    private val _claimWorkState = MutableStateFlow<WorkState>(WorkState.Unknown)
    val claimWorkState: StateFlow<WorkState> = _claimWorkState.asStateFlow()
    
    // ===== UI State =====
    
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Ready)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Load saved settings
        val settings = automationManager.getSettings()
        _automationEnabled.value = settings.automationEnabled
        _discoveryIntervalHours.value = settings.discoveryIntervalHours
        _requireWifi.value = settings.requireWifi
        
        // Check notification permission
        _hasNotificationPermission.value = notificationManager.hasNotificationPermission()
        
        // Observe work status
        observeWorkStatus()
    }
    
    private fun observeWorkStatus() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(DiscoveryWorker.WORK_NAME)
                .collect { workInfos ->
                    _discoveryWorkState.value = workInfos.firstOrNull()?.toWorkState() 
                        ?: WorkState.NotScheduled
                }
        }
        
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ClaimWorker.WORK_NAME)
                .collect { workInfos ->
                    _claimWorkState.value = workInfos.firstOrNull()?.toWorkState()
                        ?: WorkState.NotScheduled
                }
        }
    }
    
    // ===== Automation Actions =====
    
    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _automationEnabled.value = enabled
            val currentSettings = automationManager.getSettings()
            val newSettings = currentSettings.copy(automationEnabled = enabled)
            automationManager.updateSettings(newSettings)
            
            if (enabled) {
                automationManager.scheduleDiscovery()
            }
        }
    }
    
    fun setDiscoveryInterval(hours: Int) {
        viewModelScope.launch {
            _discoveryIntervalHours.value = hours
            // Note: Need to update via nested settings copy
        }
    }
    
    fun triggerManualDiscovery() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading("Running discovery...")
            automationManager.runDiscoveryNow()
            _uiState.value = SettingsUiState.Ready
        }
    }
    
    // ===== Notification Actions =====
    
    fun updateNotificationPermissionStatus() {
        _hasNotificationPermission.value = notificationManager.hasNotificationPermission()
    }
    
    fun setClaimSuccessNotifications(enabled: Boolean) {
        _notificationPrefs.value = _notificationPrefs.value.copy(claimSuccessEnabled = enabled)
    }
    
    fun setActionRequiredNotifications(enabled: Boolean) {
        _notificationPrefs.value = _notificationPrefs.value.copy(actionRequiredEnabled = enabled)
    }
    
    fun setExpiringNotifications(enabled: Boolean) {
        _notificationPrefs.value = _notificationPrefs.value.copy(expiringOffersEnabled = enabled)
    }
    
    fun setExpiringThreshold(hours: Int) {
        _notificationPrefs.value = _notificationPrefs.value.copy(expiringHoursThreshold = hours)
    }
    
    fun setFailureNotifications(enabled: Boolean) {
        _notificationPrefs.value = _notificationPrefs.value.copy(automationFailuresEnabled = enabled)
    }
    
    // ===== Channel Status =====
    
    fun isChannelEnabled(channelId: String): Boolean {
        return notificationManager.isChannelEnabled(channelId)
    }
    
    // ===== Factory =====
    
    class Factory(
        private val context: Context,
        private val automationManager: AutomationManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(context, automationManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

/**
 * UI state for Settings screen.
 */
sealed class SettingsUiState {
    data object Ready : SettingsUiState()
    data class Loading(val message: String) : SettingsUiState()
    data class Error(val message: String) : SettingsUiState()
}

/**
 * State of a WorkManager work request.
 */
sealed class WorkState {
    data object Unknown : WorkState()
    data object NotScheduled : WorkState()
    data object Enqueued : WorkState()
    data object Running : WorkState()
    data class Completed(val success: Boolean) : WorkState()
    data object Cancelled : WorkState()
}

/**
 * Extension to convert WorkInfo to WorkState.
 */
private fun WorkInfo.toWorkState(): WorkState {
    return when (state) {
        WorkInfo.State.ENQUEUED -> WorkState.Enqueued
        WorkInfo.State.RUNNING -> WorkState.Running
        WorkInfo.State.SUCCEEDED -> WorkState.Completed(true)
        WorkInfo.State.FAILED -> WorkState.Completed(false)
        WorkInfo.State.CANCELLED -> WorkState.Cancelled
        WorkInfo.State.BLOCKED -> WorkState.Enqueued
    }
}
