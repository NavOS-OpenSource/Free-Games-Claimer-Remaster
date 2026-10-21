package com.navos.gameclaimer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navos.gameclaimer.data.local.dao.GiveawayDao
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.Giveaway
import com.navos.gameclaimer.domain.model.GiveawayType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Phase 14: ViewModel for the Discover screen.
 * 
 * Exposes giveaway feed with filtering and search capabilities.
 * Uses StateFlow for lifecycle-aware UI state.
 */
class DiscoverViewModel(
    private val giveawayDao: GiveawayDao
) : ViewModel() {
    
    // ===== Filter State =====
    
    private val _selectedStore = MutableStateFlow<GameStore?>(null)
    val selectedStore: StateFlow<GameStore?> = _selectedStore.asStateFlow()
    
    private val _selectedType = MutableStateFlow<GiveawayType?>(null)
    val selectedType: StateFlow<GiveawayType?> = _selectedType.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _showExpired = MutableStateFlow(false)
    val showExpired: StateFlow<Boolean> = _showExpired.asStateFlow()
    
    // ===== UI State =====
    
    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()
    
    // ===== Giveaway Feed =====
    
    /**
     * Active giveaways (not expired, not dismissed).
     * Filtered by store, type, and search query.
     */
    val activeGiveaways: StateFlow<List<Giveaway>> = combine(
        giveawayDao.getActiveGiveaways(),
        _selectedStore,
        _selectedType,
        _searchQuery
    ) { giveaways, store, type, query ->
        giveaways
            .map { it.toDomain() }
            .filter { giveaway ->
                (store == null || giveaway.store == store) &&
                (type == null || giveaway.type == type) &&
                (query.isBlank() || giveaway.title.contains(query, ignoreCase = true))
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    /**
     * Count of available (not claimed, not expired) giveaways.
     */
    val availableCount: StateFlow<Int> = giveawayDao.getActiveGiveaways()
        .map { it.count { entity -> !entity.isClaimed } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    /**
     * Count of giveaways by store for filter chips.
     */
    val storeCountsFlow: StateFlow<Map<GameStore, Int>> = giveawayDao.getActiveGiveaways()
        .map { giveaways ->
            giveaways
                .map { it.toDomain() }
                .groupBy { it.store }
                .mapValues { it.value.size }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    init {
        // Update UI state based on data loading
        viewModelScope.launch {
            giveawayDao.getActiveGiveaways().collect { giveaways ->
                _uiState.value = if (giveaways.isEmpty()) {
                    DiscoverUiState.Empty
                } else {
                    DiscoverUiState.Success(giveaways.size)
                }
            }
        }
    }
    
    // ===== Actions =====
    
    fun setStoreFilter(store: GameStore?) {
        _selectedStore.value = store
    }
    
    fun setTypeFilter(type: GiveawayType?) {
        _selectedType.value = type
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun toggleShowExpired() {
        _showExpired.value = !_showExpired.value
    }
    
    fun clearFilters() {
        _selectedStore.value = null
        _selectedType.value = null
        _searchQuery.value = ""
    }
    
    fun dismissGiveaway(giveawayId: String) {
        viewModelScope.launch {
            giveawayDao.markDismissed(giveawayId)
        }
    }
    
    fun refresh() {
        _uiState.value = DiscoverUiState.Loading
        // Refresh triggers discovery workers - handled by AutomationManager
    }
    
    // ===== Factory =====
    
    class Factory(
        private val giveawayDao: GiveawayDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DiscoverViewModel::class.java)) {
                return DiscoverViewModel(giveawayDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

/**
 * UI state for the Discover screen.
 */
sealed class DiscoverUiState {
    data object Loading : DiscoverUiState()
    data object Empty : DiscoverUiState()
    data class Success(val totalCount: Int) : DiscoverUiState()
    data class Error(val message: String) : DiscoverUiState()
}
