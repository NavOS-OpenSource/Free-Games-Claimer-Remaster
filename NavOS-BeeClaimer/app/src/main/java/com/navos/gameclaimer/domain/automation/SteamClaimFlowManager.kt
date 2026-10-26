package com.navos.gameclaimer.domain.automation

import android.util.Log
import android.webkit.WebView
import com.navos.gameclaimer.data.adapter.SteamClaimResult
import com.navos.gameclaimer.data.adapter.SteamOwnershipState
import com.navos.gameclaimer.data.adapter.SteamStoreAdapter
import com.navos.gameclaimer.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Steam Claim Flow Manager
 * 
 * Orchestrates the WebView-based claim flow for Steam games:
 * 1. Navigate to game page
 * 2. Handle age gate if present
 * 3. Detect ownership state
 * 4. If not owned and free, click claim button
 * 5. Verify ownership after claim
 * 
 * SECURITY:
 * - All operations are user-initiated
 * - Price verification before any claim attempt
 * - Only claims items with -100% discount
 * - Never auto-submits paid content
 */
class SteamClaimFlowManager(
    private val adapter: SteamStoreAdapter = SteamStoreAdapter()
) {
    companion object {
        private const val TAG = "SteamClaimFlow"
        private const val PAGE_LOAD_DELAY_MS = 3000L
        private const val CLAIM_VERIFY_DELAY_MS = 5000L
        private const val MAX_AGE_GATE_RETRIES = 2
    }
    
    /**
     * Current state of the claim flow.
     */
    sealed class ClaimFlowState {
        object Idle : ClaimFlowState()
        data class Loading(val url: String, val gameTitle: String?) : ClaimFlowState()
        data class DetectingOwnership(val appId: String) : ClaimFlowState()
        data class AlreadyOwned(val appId: String, val title: String) : ClaimFlowState()
        data class ReadyToClaim(val appId: String, val title: String, val action: String) : ClaimFlowState()
        data class Claiming(val appId: String, val title: String) : ClaimFlowState()
        data class VerifyingClaim(val appId: String, val title: String) : ClaimFlowState()
        data class ClaimSuccess(val appId: String, val title: String, val method: String) : ClaimFlowState()
        data class ClaimFailed(val appId: String, val title: String, val reason: String) : ClaimFlowState()
        data class RequiresLogin(val appId: String) : ClaimFlowState()
        data class RequiresAgeVerification(val appId: String) : ClaimFlowState()
        data class RequiresManualAction(val appId: String, val actionType: String) : ClaimFlowState()
        data class Skipped(val appId: String, val reason: String) : ClaimFlowState()
        data class Error(val message: String) : ClaimFlowState()
    }
    
    private val _state = MutableStateFlow<ClaimFlowState>(ClaimFlowState.Idle)
    val state: StateFlow<ClaimFlowState> = _state
    
    private val _currentGame = MutableStateFlow<Giveaway?>(null)
    val currentGame: StateFlow<Giveaway?> = _currentGame
    
    /**
     * Start claim flow for a giveaway.
     * 
     * This prepares the flow - actual WebView interaction happens via callbacks.
     */
    fun startClaimFlow(giveaway: Giveaway) {
        Log.d(TAG, "Starting claim flow for: ${giveaway.title}")
        _currentGame.value = giveaway
        _state.value = ClaimFlowState.Loading(giveaway.claimUrl, giveaway.title)
    }
    
    /**
     * Called when the page has finished loading.
     * Returns the appropriate JavaScript to execute.
     */
    suspend fun onPageLoaded(webView: WebView, url: String): String? {
        Log.d(TAG, "Page loaded: $url")
        
        val appIdPair = adapter.parseItemId(url)
        val appId = appIdPair?.second
        
        if (appId == null) {
            Log.w(TAG, "Could not parse app ID from URL: $url")
            _state.value = ClaimFlowState.Error("Invalid Steam URL")
            return null
        }
        
        // Check for age gate first
        if (url.contains("agecheck/")) {
            _state.value = ClaimFlowState.RequiresAgeVerification(appId)
            return SteamStoreAdapter.JS_HANDLE_AGE_GATE
        }
        
        // Wait for page to fully render
        delay(PAGE_LOAD_DELAY_MS)
        
        // Start ownership detection
        _state.value = ClaimFlowState.DetectingOwnership(appId)
        return SteamStoreAdapter.JS_DETECT_OWNERSHIP
    }
    
    /**
     * Process the ownership detection result.
     * Returns the next JavaScript to execute, or null if done.
     */
    fun onOwnershipDetected(appId: String, jsResult: String): String? {
        Log.d(TAG, "Ownership detected for $appId: ${jsResult.take(200)}")
        
        val ownershipState = adapter.parseOwnershipResult(appId, jsResult)
        val title = _currentGame.value?.title ?: "Unknown"
        
        when (ownershipState) {
            is SteamOwnershipState.Owned -> {
                Log.i(TAG, "Already owned: $title")
                _state.value = ClaimFlowState.AlreadyOwned(appId, title)
                return null
            }
            
            is SteamOwnershipState.NotOwned -> {
                if (ownershipState.isFreeToKeep && ownershipState.availableAction != null) {
                    Log.i(TAG, "Ready to claim: $title via ${ownershipState.availableAction}")
                    _state.value = ClaimFlowState.ReadyToClaim(appId, title, ownershipState.availableAction)
                    // Return claim script - actual execution is up to caller
                    return SteamStoreAdapter.JS_CLICK_CLAIM
                } else if (!ownershipState.isFreeToKeep) {
                    Log.w(TAG, "Not a giveaway (no -100% discount): $title")
                    _state.value = ClaimFlowState.Skipped(appId, "Not a giveaway - no 100% discount")
                    return null
                } else {
                    Log.w(TAG, "No claim button found for: $title")
                    _state.value = ClaimFlowState.ClaimFailed(appId, title, "No claim button available")
                    return null
                }
            }
            
            is SteamOwnershipState.RequiresLogin -> {
                Log.w(TAG, "Login required for: $appId")
                _state.value = ClaimFlowState.RequiresLogin(appId)
                return null
            }
            
            is SteamOwnershipState.RequiresAgeVerification -> {
                Log.w(TAG, "Age verification required for: $appId")
                _state.value = ClaimFlowState.RequiresAgeVerification(appId)
                return SteamStoreAdapter.JS_HANDLE_AGE_GATE
            }
            
            is SteamOwnershipState.FreeToPlay -> {
                Log.i(TAG, "Skipping Free-to-Play (not a giveaway): $title")
                _state.value = ClaimFlowState.Skipped(appId, "Free-to-Play game (nothing to claim)")
                return null
            }
            
            is SteamOwnershipState.Demo -> {
                Log.i(TAG, "Skipping demo: $title")
                _state.value = ClaimFlowState.Skipped(appId, "This is a demo, not the full game")
                return null
            }
            
            is SteamOwnershipState.RequiresBaseGame -> {
                Log.w(TAG, "DLC requires base game: $title")
                _state.value = ClaimFlowState.Skipped(appId, "DLC requires base game you don't own")
                return null
            }
            
            is SteamOwnershipState.Uncertain -> {
                Log.w(TAG, "Uncertain state for $appId: ${ownershipState.reason}")
                _state.value = ClaimFlowState.Error("Could not determine ownership: ${ownershipState.reason}")
                return null
            }
            
            is SteamOwnershipState.Error -> {
                Log.e(TAG, "Detection error for $appId: ${ownershipState.error}")
                _state.value = ClaimFlowState.Error(ownershipState.error)
                return null
            }
        }
    }
    
    /**
     * Process the claim button click result.
     */
    fun onClaimClicked(appId: String, jsResult: String): String? {
        Log.d(TAG, "Claim clicked for $appId: $jsResult")
        
        val claimResult = adapter.parseClaimResult(appId, jsResult)
        val title = _currentGame.value?.title ?: "Unknown"
        
        when (claimResult) {
            is SteamClaimResult.Success -> {
                Log.i(TAG, "Claim button clicked: $title via ${claimResult.method}")
                _state.value = ClaimFlowState.VerifyingClaim(appId, title)
                // Return verification script after delay
                return SteamStoreAdapter.JS_VERIFY_OWNED
            }
            
            is SteamClaimResult.Failed -> {
                Log.e(TAG, "Claim click failed for $title: ${claimResult.reason}")
                _state.value = ClaimFlowState.ClaimFailed(appId, title, claimResult.reason)
                return null
            }
            
            else -> {
                _state.value = ClaimFlowState.ClaimFailed(appId, title, "Unexpected claim result")
                return null
            }
        }
    }
    
    /**
     * Process ownership verification after claim.
     */
    fun onVerificationComplete(appId: String, jsResult: String) {
        val title = _currentGame.value?.title ?: "Unknown"
        val isOwned = jsResult.contains("true")
        
        if (isOwned) {
            Log.i(TAG, "✓ Successfully claimed: $title")
            _state.value = ClaimFlowState.ClaimSuccess(appId, title, "verified")
        } else {
            Log.w(TAG, "Claim not confirmed for: $title")
            _state.value = ClaimFlowState.ClaimFailed(appId, title, "Ownership not confirmed after claim")
        }
    }
    
    /**
     * Reset the flow state.
     */
    fun reset() {
        _state.value = ClaimFlowState.Idle
        _currentGame.value = null
    }
    
    /**
     * Execute JavaScript on WebView and return result.
     */
    suspend fun evaluateJs(webView: WebView, script: String): String = suspendCoroutine { cont ->
        webView.post {
            webView.evaluateJavascript(script) { result ->
                val cleaned = cleanJsResult(result)
                cont.resume(cleaned)
            }
        }
    }
    
    private fun cleanJsResult(result: String?): String {
        if (result == null || result == "null") return ""
        var cleaned = result
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        return cleaned.replace("\\\"", "\"").replace("\\\\", "\\")
    }
}

/**
 * Claim queue item for batch processing.
 */
data class SteamClaimQueueItem(
    val giveaway: Giveaway,
    var status: SteamClaimStatus = SteamClaimStatus.PENDING,
    var resultMessage: String? = null,
    var claimedAt: Long? = null
)

enum class SteamClaimStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    ALREADY_OWNED,
    SKIPPED,
    FAILED,
    REQUIRES_LOGIN,
    REQUIRES_MANUAL_ACTION
}

/**
 * Manages a queue of Steam games to claim.
 */
class SteamClaimQueueManager {
    companion object {
        private const val TAG = "SteamClaimQueue"
    }
    
    private val _queue = MutableStateFlow<List<SteamClaimQueueItem>>(emptyList())
    val queue: StateFlow<List<SteamClaimQueueItem>> = _queue
    
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing
    
    /**
     * Add giveaways to the claim queue.
     * Filters to only include Steam giveaways.
     */
    fun enqueue(giveaways: List<Giveaway>) {
        val steamGiveaways = giveaways.filter { it.store == GameStore.STEAM }
        val items = steamGiveaways.map { SteamClaimQueueItem(it) }
        _queue.value = items
        _currentIndex.value = 0
        Log.d(TAG, "Enqueued ${items.size} Steam games for claiming")
    }
    
    /**
     * Get the next item to process.
     */
    fun getNextItem(): SteamClaimQueueItem? {
        val items = _queue.value
        val index = _currentIndex.value
        
        if (index >= items.size) return null
        return items[index]
    }
    
    /**
     * Mark current item as complete and advance.
     */
    fun markComplete(status: SteamClaimStatus, message: String? = null) {
        val items = _queue.value.toMutableList()
        val index = _currentIndex.value
        
        if (index < items.size) {
            items[index] = items[index].copy(
                status = status,
                resultMessage = message,
                claimedAt = if (status == SteamClaimStatus.SUCCESS) System.currentTimeMillis() else null
            )
            _queue.value = items
        }
        
        _currentIndex.value = index + 1
    }
    
    /**
     * Start queue processing.
     */
    fun startProcessing() {
        _isProcessing.value = true
    }
    
    /**
     * Stop queue processing.
     */
    fun stopProcessing() {
        _isProcessing.value = false
    }
    
    /**
     * Get summary of queue results.
     */
    fun getSummary(): SteamClaimSummary {
        val items = _queue.value
        return SteamClaimSummary(
            total = items.size,
            pending = items.count { it.status == SteamClaimStatus.PENDING },
            success = items.count { it.status == SteamClaimStatus.SUCCESS },
            alreadyOwned = items.count { it.status == SteamClaimStatus.ALREADY_OWNED },
            skipped = items.count { it.status == SteamClaimStatus.SKIPPED },
            failed = items.count { it.status == SteamClaimStatus.FAILED },
            requiresLogin = items.count { it.status == SteamClaimStatus.REQUIRES_LOGIN },
            requiresManualAction = items.count { it.status == SteamClaimStatus.REQUIRES_MANUAL_ACTION }
        )
    }
    
    /**
     * Reset the queue.
     */
    fun reset() {
        _queue.value = emptyList()
        _currentIndex.value = 0
        _isProcessing.value = false
    }
}

/**
 * Summary of Steam claim queue results.
 */
data class SteamClaimSummary(
    val total: Int,
    val pending: Int,
    val success: Int,
    val alreadyOwned: Int,
    val skipped: Int,
    val failed: Int,
    val requiresLogin: Int,
    val requiresManualAction: Int
) {
    val completed: Int get() = total - pending
    val effectivelyClaimed: Int get() = success + alreadyOwned
}
