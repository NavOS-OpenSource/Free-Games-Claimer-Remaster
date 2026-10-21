package com.navos.gameclaimer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navos.gameclaimer.data.local.dao.ClaimQueueDao
import com.navos.gameclaimer.data.local.dao.GiveawayDao
import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.domain.model.ClaimQueueState
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.Giveaway
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Phase 14: ViewModel for Needs Attention screen.
 * 
 * Handles uncertain claims, authentication requirements, and manual actions.
 * Uses StateFlow for lifecycle-aware UI state.
 * 
 * GPT SAFETY REQUIREMENT:
 * - Never auto-resolve uncertain claims
 * - User must explicitly verify ownership at the store
 * - Opening a store page is NOT proof of ownership
 */
class NeedsAttentionViewModel(
    private val claimQueueDao: ClaimQueueDao,
    private val giveawayDao: GiveawayDao
) : ViewModel() {
    
    // ===== UI State =====
    
    private val _uiState = MutableStateFlow<NeedsAttentionUiState>(NeedsAttentionUiState.Loading)
    val uiState: StateFlow<NeedsAttentionUiState> = _uiState.asStateFlow()
    
    // ===== Uncertain Items =====
    
    /**
     * Items in UNCERTAIN state requiring verification.
     */
    val uncertainItems: StateFlow<List<UncertainItem>> = claimQueueDao.getUncertainItems()
        .map { entities ->
            entities.map { entity ->
                val giveaway = giveawayDao.getById(entity.giveawayId)?.toDomain()
                UncertainItem(
                    queueEntity = entity,
                    giveaway = giveaway
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Items blocked waiting for user action (auth, CAPTCHA, etc).
     */
    val blockedItems: StateFlow<List<BlockedItem>> = claimQueueDao
        .getItemsByState(ClaimQueueState.BLOCKED_USER_ACTION.name)
        .map { entities ->
            entities.map { entity ->
                val giveaway = giveawayDao.getById(entity.giveawayId)?.toDomain()
                BlockedItem(
                    queueEntity = entity,
                    giveaway = giveaway,
                    reason = parseBlockedReason(entity)
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Total count of items requiring attention.
     */
    val totalAttentionCount: StateFlow<Int> = combine(
        uncertainItems,
        blockedItems
    ) { uncertain, blocked ->
        uncertain.size + blocked.size
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    
    init {
        viewModelScope.launch {
            combine(uncertainItems, blockedItems) { uncertain, blocked ->
                when {
                    uncertain.isEmpty() && blocked.isEmpty() -> NeedsAttentionUiState.Empty
                    else -> NeedsAttentionUiState.Success(
                        uncertainCount = uncertain.size,
                        blockedCount = blocked.size
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    // ===== Actions =====
    
    /**
     * Mark an uncertain item as verified owned by user.
     * 
     * ONLY call this after user has manually verified at the store.
     */
    fun markAsOwned(queueId: String, version: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val result = claimQueueDao.resolveUncertainItem(
                queueId = queueId,
                verified = true,
                scheduledAt = now,
                expectedVersion = version
            )
            if (result == 0) {
                _uiState.value = NeedsAttentionUiState.Error(
                    "Could not update item - it may have been modified. Please refresh."
                )
            }
        }
    }
    
    /**
     * Mark an uncertain item as not owned and allow retry.
     * 
     * ONLY call this when user has verified they do NOT own it.
     */
    fun markAsNotOwnedAndRetry(queueId: String, version: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val result = claimQueueDao.resolveUncertainItem(
                queueId = queueId,
                verified = false,
                scheduledAt = now,
                expectedVersion = version
            )
            if (result == 0) {
                _uiState.value = NeedsAttentionUiState.Error(
                    "Could not update item - it may have been modified. Please refresh."
                )
            }
        }
    }
    
    /**
     * Get the store URL for manual verification.
     * 
     * NOTE: Opening this URL is NOT proof of ownership.
     */
    fun getStoreVerificationUrl(item: UncertainItem): String? {
        return item.giveaway?.claimUrl
    }
    
    fun clearError() {
        if (_uiState.value is NeedsAttentionUiState.Error) {
            viewModelScope.launch {
                val uncertain = uncertainItems.first()
                val blocked = blockedItems.first()
                _uiState.value = if (uncertain.isEmpty() && blocked.isEmpty()) {
                    NeedsAttentionUiState.Empty
                } else {
                    NeedsAttentionUiState.Success(uncertain.size, blocked.size)
                }
            }
        }
    }
    
    // ===== Helpers =====
    
    private fun parseBlockedReason(entity: ClaimQueueEntity): BlockedReason {
        val outcomeType = entity.lastOutcomeType ?: return BlockedReason.Unknown
        return when {
            outcomeType.contains("Auth", ignoreCase = true) -> BlockedReason.RequiresAuth
            outcomeType.contains("Manual", ignoreCase = true) -> BlockedReason.RequiresManualAction
            outcomeType.contains("Captcha", ignoreCase = true) -> BlockedReason.Captcha
            outcomeType.contains("MFA", ignoreCase = true) -> BlockedReason.TwoFactorAuth
            else -> BlockedReason.Unknown
        }
    }
    
    // ===== Factory =====
    
    class Factory(
        private val claimQueueDao: ClaimQueueDao,
        private val giveawayDao: GiveawayDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NeedsAttentionViewModel::class.java)) {
                return NeedsAttentionViewModel(claimQueueDao, giveawayDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

/**
 * UI state for Needs Attention screen.
 */
sealed class NeedsAttentionUiState {
    data object Loading : NeedsAttentionUiState()
    data object Empty : NeedsAttentionUiState()
    data class Success(
        val uncertainCount: Int,
        val blockedCount: Int
    ) : NeedsAttentionUiState()
    data class Error(val message: String) : NeedsAttentionUiState()
}

/**
 * Item in UNCERTAIN state.
 */
data class UncertainItem(
    val queueEntity: ClaimQueueEntity,
    val giveaway: Giveaway?
) {
    val queueId: String get() = queueEntity.queueId
    val version: Int get() = queueEntity.version
    val store: GameStore get() = giveaway?.store ?: GameStore.valueOf(queueEntity.store)
    val title: String get() = giveaway?.title ?: "Unknown Giveaway"
    val attemptedAt: Long? get() = queueEntity.lastAttemptAt
}

/**
 * Item blocked on user action.
 */
data class BlockedItem(
    val queueEntity: ClaimQueueEntity,
    val giveaway: Giveaway?,
    val reason: BlockedReason
) {
    val queueId: String get() = queueEntity.queueId
    val store: GameStore get() = giveaway?.store ?: GameStore.valueOf(queueEntity.store)
    val title: String get() = giveaway?.title ?: "Unknown Giveaway"
}

/**
 * Reason why an item is blocked.
 */
enum class BlockedReason {
    RequiresAuth,
    RequiresManualAction,
    Captcha,
    TwoFactorAuth,
    Unknown
}
