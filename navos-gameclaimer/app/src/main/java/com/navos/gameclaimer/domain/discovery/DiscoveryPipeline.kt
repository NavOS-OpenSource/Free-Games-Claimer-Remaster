package com.navos.gameclaimer.domain.discovery

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterFactory
import com.navos.gameclaimer.domain.model.DiscoveryResult
import com.navos.gameclaimer.domain.model.DiscoverySource
import com.navos.gameclaimer.domain.model.DiscoveryStatus
import com.navos.gameclaimer.domain.model.Giveaway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase 6: Discovery Pipeline
 * 
 * Orchestrates giveaway discovery across multiple sources with:
 * - Parallel discovery from multiple adapters
 * - Per-source error isolation (one failure doesn't stop others)
 * - Deduplication of discovered giveaways
 * - Bounded retries with exponential backoff
 * - Timeout enforcement
 * - Cancellation support
 * 
 * SECURITY:
 * - Discovery is READ-ONLY - never triggers claims
 * - All URLs are validated before storage
 * - External data is treated as untrusted
 */
class DiscoveryPipeline(
    private val adapterFactory: StoreAdapterFactory,
    private val urlValidator: UrlValidator = DefaultUrlValidator()
) {
    private val mutex = Mutex()
    private var currentJob: Job? = null
    
    private val _status = MutableStateFlow(
        DiscoveryStatus(
            isRunning = false,
            lastRunAt = null,
            successfulSources = emptyList(),
            failedSources = emptyList(),
            totalGiveawaysFound = 0
        )
    )
    val status: StateFlow<DiscoveryStatus> = _status
    
    private val _giveaways = MutableStateFlow<List<Giveaway>>(emptyList())
    val giveaways: StateFlow<List<Giveaway>> = _giveaways
    
    /**
     * Configuration for discovery operations.
     */
    data class Config(
        /** Timeout per adapter (ms) */
        val perAdapterTimeoutMs: Long = 30_000L,
        
        /** Maximum total runtime (ms) */
        val totalTimeoutMs: Long = 120_000L,
        
        /** Maximum retries per adapter */
        val maxRetries: Int = 2,
        
        /** Base delay for retry backoff (ms) */
        val retryBaseDelayMs: Long = 1_000L,
        
        /** Run adapters in parallel */
        val parallelDiscovery: Boolean = true
    )
    
    /**
     * Run discovery across all enabled adapters.
     * 
     * This is a READ-ONLY operation that discovers available giveaways.
     * It NEVER claims any offers.
     * 
     * @param config Discovery configuration
     * @return List of deduplicated, validated giveaways
     */
    suspend fun discoverAll(config: Config = Config()): List<Giveaway> = mutex.withLock {
        // Cancel any existing discovery
        currentJob?.cancelAndJoin()
        
        val startTime = System.currentTimeMillis()
        val adapters = adapterFactory.getDiscoveryAdapters()
        
        if (adapters.isEmpty()) {
            _status.value = DiscoveryStatus(
                isRunning = false,
                lastRunAt = startTime,
                successfulSources = emptyList(),
                failedSources = emptyList(),
                totalGiveawaysFound = 0
            )
            return emptyList()
        }
        
        _status.value = _status.value.copy(isRunning = true)
        
        val results = mutableListOf<DiscoveryResult>()
        
        currentJob = coroutineScope {
            if (config.parallelDiscovery) {
                // Run adapters in parallel with per-adapter timeout
                val deferreds = adapters.map { adapter ->
                    async {
                        discoverWithRetry(adapter, config)
                    }
                }
                
                // Collect results, enforcing total timeout
                withTimeoutOrNull(config.totalTimeoutMs) {
                    deferreds.map { it.await() }
                }?.let { results.addAll(it) }
            } else {
                // Run adapters sequentially
                for (adapter in adapters) {
                    if (!isActive) break
                    results.add(discoverWithRetry(adapter, config))
                }
            }
            coroutineContext[Job]
        }
        
        // Process results
        val allGiveaways = mutableListOf<Giveaway>()
        val successSources = mutableListOf<DiscoverySource>()
        val failedSources = mutableListOf<Pair<DiscoverySource, String>>()
        
        for (result in results) {
            when (result) {
                is DiscoveryResult.Success -> {
                    successSources.add(result.source)
                    // Validate and filter giveaways
                    val validated = result.giveaways.filter { validateGiveaway(it) }
                    allGiveaways.addAll(validated)
                }
                is DiscoveryResult.Empty -> {
                    successSources.add(result.source)
                }
                is DiscoveryResult.Failed -> {
                    failedSources.add(result.source to result.error)
                }
                is DiscoveryResult.Cancelled -> {
                    failedSources.add(result.source to "Cancelled")
                }
            }
        }
        
        // Deduplicate giveaways
        val deduplicated = deduplicateGiveaways(allGiveaways)
        
        _giveaways.value = deduplicated
        _status.value = DiscoveryStatus(
            isRunning = false,
            lastRunAt = startTime,
            successfulSources = successSources,
            failedSources = failedSources,
            totalGiveawaysFound = deduplicated.size
        )
        
        return deduplicated
    }
    
    /**
     * Cancel any running discovery.
     */
    suspend fun cancel() {
        currentJob?.cancelAndJoin()
        _status.value = _status.value.copy(isRunning = false)
    }
    
    /**
     * Run discovery with retry logic.
     */
    private suspend fun discoverWithRetry(
        adapter: StoreAdapter,
        config: Config
    ): DiscoveryResult {
        var lastResult: DiscoveryResult = DiscoveryResult.Failed(
            source = DiscoverySource.STORE_DIRECT,
            error = "No attempts made",
            isRetryable = false
        )
        
        repeat(config.maxRetries + 1) { attempt ->
            try {
                val result = withTimeoutOrNull(config.perAdapterTimeoutMs) {
                    adapter.discoverGiveaways(config.perAdapterTimeoutMs)
                }
                
                if (result != null) {
                    when (result) {
                        is DiscoveryResult.Success, is DiscoveryResult.Empty -> return result
                        is DiscoveryResult.Failed -> {
                            if (!result.isRetryable) return result
                            lastResult = result
                        }
                        is DiscoveryResult.Cancelled -> return result
                    }
                } else {
                    lastResult = DiscoveryResult.Failed(
                        source = DiscoverySource.STORE_DIRECT,
                        error = "Timeout after ${config.perAdapterTimeoutMs}ms",
                        isRetryable = true
                    )
                }
                
                // Exponential backoff before retry
                if (attempt < config.maxRetries) {
                    val delay = config.retryBaseDelayMs * (1 shl attempt)
                    delay(delay)
                }
            } catch (e: CancellationException) {
                return DiscoveryResult.Cancelled(source = DiscoverySource.STORE_DIRECT)
            } catch (e: Exception) {
                lastResult = DiscoveryResult.Failed(
                    source = DiscoverySource.STORE_DIRECT,
                    error = e.message ?: "Unknown error",
                    isRetryable = true
                )
            }
        }
        
        return lastResult
    }
    
    /**
     * Validate a giveaway entry.
     * 
     * SECURITY: Rejects invalid URLs, missing required fields.
     */
    private fun validateGiveaway(giveaway: Giveaway): Boolean {
        // Required fields
        if (giveaway.id.isBlank()) return false
        if (giveaway.title.isBlank()) return false
        if (giveaway.claimUrl.isBlank()) return false
        
        // URL validation
        if (!urlValidator.isValidClaimUrl(giveaway.claimUrl)) return false
        giveaway.imageUrl?.let { 
            if (it.isNotBlank() && !urlValidator.isValidImageUrl(it)) return false
        }
        
        return true
    }
    
    /**
     * Deduplicate giveaways by ID, preferring newer entries.
     */
    private fun deduplicateGiveaways(giveaways: List<Giveaway>): List<Giveaway> {
        val byId = mutableMapOf<String, Giveaway>()
        
        for (giveaway in giveaways) {
            val existing = byId[giveaway.id]
            if (existing == null || giveaway.lastCheckedAt > existing.lastCheckedAt) {
                byId[giveaway.id] = giveaway
            }
        }
        
        return byId.values.toList()
    }
}

/**
 * URL validation interface.
 */
interface UrlValidator {
    fun isValidClaimUrl(url: String): Boolean
    fun isValidImageUrl(url: String): Boolean
}

/**
 * Default URL validator implementation.
 * 
 * SECURITY:
 * - Only HTTPS allowed for claim URLs
 * - Blocks dangerous schemes (javascript:, file:, data:, etc.)
 * - Validates URL structure and host
 * - Rejects deceptive hostnames (IP addresses, localhost, private ranges)
 * - Rejects user info in URLs (potential credential leak)
 * - Trusted domain allowlist with SAFE suffix matching (requires leading dot)
 * 
 * REDIRECT SECURITY BOUNDARY:
 * This validator checks the INITIAL URL from discovery sources. It does NOT
 * validate redirect destinations. This is intentional:
 * 
 * 1. Phase 6 (Discovery) is READ-ONLY - it stores giveaway metadata but
 *    NEVER navigates to URLs or triggers claims automatically.
 * 
 * 2. A trusted starting URL (e.g., gamerpower.com) does NOT guarantee the
 *    final destination after redirects is also trusted.
 * 
 * 3. MANDATORY FOR FUTURE CLAIM PHASES: When implementing store-specific
 *    claim flows (Phase 7+), the WebView/browser navigation handler MUST:
 *    - Intercept ALL navigation events (shouldOverrideUrlLoading)
 *    - Re-validate each redirect destination against trusted domains
 *    - Block navigation to untrusted destinations
 *    - Never auto-submit forms or trigger purchases
 *    - Require explicit user confirmation before any claim action
 * 
 * 4. The claim workflow MUST re-validate the final URL before executing
 *    any authenticated action, treating discovery URLs as untrusted hints.
 */
class DefaultUrlValidator : UrlValidator {
    
    private val blockedSchemes = setOf(
        "javascript", "file", "content", "intent", "data", "blob"
    )
    
    /**
     * Known trusted store domains for claim URLs.
     * 
     * MATCHING LOGIC: Uses exact match OR safe subdomain match with leading dot.
     * - "epicgames.com" matches: epicgames.com, store.epicgames.com, www.epicgames.com
     * - "epicgames.com" does NOT match: epicgames.com.attacker.example (no leading dot)
     * - "epicgames.com" does NOT match: fakeepicgames.com (different domain)
     */
    private val trustedClaimDomains = setOf(
        "store.epicgames.com",
        "epicgames.com",
        "store.steampowered.com",
        "steampowered.com",
        "gog.com",
        "amazon.com",
        "gaming.amazon.com",
        "gamerpower.com",  // Redirect page - final destination must be re-validated
        "indiegala.com",
        "itch.io",
        "humblebundle.com"
    )
    
    override fun isValidClaimUrl(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return false
            
            // Must be HTTPS
            if (scheme != "https") return false
            
            // Must have a host
            val host = uri.host?.lowercase() ?: return false
            if (host.isBlank()) return false
            
            // No user info (username:password@host) - potential credential leak
            if (uri.userInfo != null) return false
            
            // Block IP addresses (IPv4 and IPv6)
            if (isIpAddress(host)) return false
            
            // Block localhost and private network
            if (isLocalOrPrivate(host)) return false
            
            // Block non-standard ports (phishing indicator)
            val port = uri.port
            if (port != -1 && port != 443) return false
            
            // For claim URLs, verify against trusted domains
            // This prevents phishing via look-alike domains
            if (!isTrustedDomain(host)) return false
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun isValidImageUrl(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return false
            
            // Allow HTTP and HTTPS for images (many CDNs still use HTTP)
            if (scheme !in setOf("http", "https")) return false
            
            // Block dangerous schemes
            if (scheme in blockedSchemes) return false
            
            // Must have a host
            val host = uri.host?.lowercase() ?: return false
            if (host.isBlank()) return false
            
            // Block IP addresses for images too
            if (isIpAddress(host)) return false
            
            // Block localhost and private network
            if (isLocalOrPrivate(host)) return false
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if host is an IP address.
     */
    private fun isIpAddress(host: String): Boolean {
        // IPv4 pattern
        val ipv4Regex = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
        if (ipv4Regex.matches(host)) return true
        
        // IPv6 pattern (simplified check for bracket notation)
        if (host.startsWith("[") || host.contains("::") || 
            host.count { it == ':' } > 1) return true
        
        return false
    }
    
    /**
     * Check if host is localhost or private network.
     */
    private fun isLocalOrPrivate(host: String): Boolean {
        val lower = host.lowercase()
        return lower == "localhost" ||
               lower.endsWith(".local") ||
               lower.endsWith(".localhost") ||
               lower.startsWith("10.") ||
               lower.startsWith("192.168.") ||
               lower.startsWith("172.") ||  // Simplified private range check
               lower == "127.0.0.1" ||
               lower == "0.0.0.0" ||
               lower == "::1"
    }
    
    /**
     * Check if host is a trusted store domain.
     * 
     * SAFE MATCHING: Uses exact match OR subdomain match with LEADING DOT.
     * This prevents suffix attacks:
     * - "gog.com" matches: gog.com, www.gog.com, store.gog.com
     * - "gog.com" rejects: gog.com.attacker.example (no leading dot before "gog.com")
     * - "gog.com" rejects: fakegog.com (different domain entirely)
     */
    private fun isTrustedDomain(host: String): Boolean {
        val lower = host.lowercase()
        return trustedClaimDomains.any { domain ->
            // Exact match: host == "gog.com"
            // Subdomain match: host == "www.gog.com" ends with ".gog.com"
            // Leading dot prevents: "gog.com.evil.com" matching (it ends with "m.evil.com")
            lower == domain || lower.endsWith(".$domain")
        }
    }
}
