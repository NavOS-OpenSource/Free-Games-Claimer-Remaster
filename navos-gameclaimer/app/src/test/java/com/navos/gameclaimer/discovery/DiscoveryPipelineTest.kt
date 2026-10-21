package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.domain.adapter.StoreAdapter
import com.navos.gameclaimer.domain.adapter.StoreAdapterFactory
import com.navos.gameclaimer.domain.discovery.DiscoveryPipeline
import com.navos.gameclaimer.domain.discovery.UrlValidator
import com.navos.gameclaimer.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 6: Discovery Pipeline Unit Tests
 * 
 * Uses fake adapters to test pipeline behavior without network calls.
 */
class DiscoveryPipelineTest {
    
    private lateinit var fakeAdapterFactory: FakeStoreAdapterFactory
    private lateinit var fakeUrlValidator: FakeUrlValidator
    private lateinit var pipeline: DiscoveryPipeline
    
    @Before
    fun setup() {
        fakeAdapterFactory = FakeStoreAdapterFactory()
        fakeUrlValidator = FakeUrlValidator()
        pipeline = DiscoveryPipeline(fakeAdapterFactory, fakeUrlValidator)
    }
    
    @Test
    fun `discoverAll returns empty when no adapters`() = runTest {
        fakeAdapterFactory.adapters.clear()
        
        val result = pipeline.discoverAll()
        
        assertTrue(result.isEmpty())
        assertFalse(pipeline.status.value.isRunning)
    }
    
    @Test
    fun `discoverAll returns giveaways from successful adapter`() = runTest {
        val giveaway = createTestGiveaway()
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.EPIC_GAMES,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(giveaway),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        
        val result = pipeline.discoverAll()
        
        assertEquals(1, result.size)
        assertEquals(giveaway.title, result[0].title)
    }
    
    @Test
    fun `discoverAll aggregates from multiple adapters`() = runTest {
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.EPIC_GAMES,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(createTestGiveaway(id = "epic:1")),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.STEAM,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(createTestGiveaway(id = "steam:1")),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        
        val result = pipeline.discoverAll()
        
        assertEquals(2, result.size)
    }
    
    @Test
    fun `discoverAll handles adapter failure gracefully`() = runTest {
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.EPIC_GAMES,
            discoveryResult = DiscoveryResult.Failed(
                source = DiscoverySource.STORE_DIRECT,
                error = "Network error",
                isRetryable = true
            )
        ))
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.STEAM,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(createTestGiveaway(id = "steam:1")),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        
        val result = pipeline.discoverAll()
        
        // Steam adapter should still succeed
        assertEquals(1, result.size)
        
        // Status should show failure
        val status = pipeline.status.value
        assertTrue(status.failedSources.isNotEmpty())
        assertTrue(status.successfulSources.isNotEmpty())
    }
    
    @Test
    fun `discoverAll deduplicates giveaways by ID`() = runTest {
        val sameId = "shared:game:1"
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.EPIC_GAMES,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(createTestGiveaway(id = sameId, lastCheckedAt = 1000)),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.STEAM,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(createTestGiveaway(id = sameId, lastCheckedAt = 2000)),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        
        val result = pipeline.discoverAll()
        
        // Should deduplicate, preferring newer entry
        assertEquals(1, result.size)
        assertEquals(2000, result[0].lastCheckedAt)
    }
    
    @Test
    fun `discoverAll rejects giveaways with invalid URLs`() = runTest {
        // Configure validator to reject non-HTTPS
        fakeUrlValidator.validClaimUrls.add("https://store.epicgames.com/test")
        
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.EPIC_GAMES,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(
                    createTestGiveaway(id = "valid:1", claimUrl = "https://store.epicgames.com/test"),
                    createTestGiveaway(id = "invalid:1", claimUrl = "http://insecure.com/test"),
                    createTestGiveaway(id = "invalid:2", claimUrl = "javascript:alert(1)")
                ),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        
        val result = pipeline.discoverAll()
        
        // Only valid URL should pass
        assertEquals(1, result.size)
        assertEquals("valid:1", result[0].id)
    }
    
    @Test
    fun `discoverAll rejects giveaways with blank required fields`() = runTest {
        // Allow all URLs in this test
        fakeUrlValidator.allowAll = true
        
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.EPIC_GAMES,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(
                    createTestGiveaway(id = "valid:1"),
                    createTestGiveaway(id = "", title = "No ID"),
                    createTestGiveaway(id = "noclaimurl:1", claimUrl = "")
                ),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        
        val result = pipeline.discoverAll()
        
        assertEquals(1, result.size)
        assertEquals("valid:1", result[0].id)
    }
    
    @Test
    fun `status reflects discovery state`() = runTest {
        fakeAdapterFactory.addAdapter(FakeStoreAdapter(
            store = GameStore.EPIC_GAMES,
            discoveryResult = DiscoveryResult.Success(
                giveaways = listOf(createTestGiveaway()),
                source = DiscoverySource.STORE_DIRECT,
                durationMs = 100
            )
        ))
        
        // Allow all URLs
        fakeUrlValidator.allowAll = true
        
        // Before discovery
        assertFalse(pipeline.status.value.isRunning)
        assertNull(pipeline.status.value.lastRunAt)
        
        // Run discovery
        pipeline.discoverAll()
        
        // After discovery
        assertFalse(pipeline.status.value.isRunning)
        assertNotNull(pipeline.status.value.lastRunAt)
        assertEquals(1, pipeline.status.value.totalGiveawaysFound)
    }
    
    @Test
    fun `URL validator integration - HTTPS required for claims`() {
        // Test the fake validator logic
        val validator = FakeUrlValidator()
        validator.validClaimUrls.add("https://store.epicgames.com/test")
        
        assertTrue(validator.isValidClaimUrl("https://store.epicgames.com/test"))
        assertFalse(validator.isValidClaimUrl("http://insecure.com/test"))
    }
    
    @Test
    fun `URL validator integration - HTTP allowed for images`() {
        val validator = FakeUrlValidator()
        validator.validImageUrls.addAll(listOf(
            "https://cdn.example.com/image.jpg",
            "http://cdn.example.com/image.jpg"
        ))
        
        assertTrue(validator.isValidImageUrl("https://cdn.example.com/image.jpg"))
        assertTrue(validator.isValidImageUrl("http://cdn.example.com/image.jpg"))
        assertFalse(validator.isValidImageUrl("javascript:void(0)"))
    }
    
    private fun createTestGiveaway(
        id: String = "test:epic_games:1",
        title: String = "Test Game",
        store: GameStore = GameStore.EPIC_GAMES,
        claimUrl: String = "https://store.epicgames.com/test",
        lastCheckedAt: Long = System.currentTimeMillis()
    ): Giveaway {
        return Giveaway(
            id = id,
            title = title,
            store = store,
            type = GiveawayType.FREE_TO_KEEP,
            claimUrl = claimUrl,
            source = DiscoverySource.GAMER_POWER,
            lastCheckedAt = lastCheckedAt
        )
    }
}

/**
 * Fake URL validator for unit tests (no Android dependencies).
 */
class FakeUrlValidator : UrlValidator {
    var allowAll = false
    val validClaimUrls = mutableSetOf<String>()
    val validImageUrls = mutableSetOf<String>()
    
    override fun isValidClaimUrl(url: String): Boolean {
        if (allowAll) return url.isNotBlank()
        return url in validClaimUrls || url.startsWith("https://")
    }
    
    override fun isValidImageUrl(url: String): Boolean {
        if (allowAll) return url.isNotBlank()
        return url in validImageUrls || url.startsWith("https://") || url.startsWith("http://")
    }
}

/**
 * Fake store adapter for testing.
 */
class FakeStoreAdapter(
    override val store: GameStore,
    private val discoveryResult: DiscoveryResult = DiscoveryResult.Empty(
        source = DiscoverySource.STORE_DIRECT,
        durationMs = 0
    ),
    override val displayName: String = store.displayName,
    override val supportsDiscovery: Boolean = true,
    override val supportsClaiming: Boolean = false,
    override val requiresAuth: Boolean = true
) : StoreAdapter {
    
    override suspend fun discoverGiveaways(timeoutMs: Long): DiscoveryResult = discoveryResult
    
    override suspend fun verifyGiveaway(giveaway: Giveaway): Giveaway? = giveaway
    
    override suspend fun checkEligibility(giveaway: Giveaway): ClaimEligibility = 
        ClaimEligibility.UNKNOWN
    
    override suspend fun claim(giveaway: Giveaway): ClaimResult = 
        ClaimResult.Failed(giveaway.id, "Fake adapter", false)
    
    override fun getAuthUrl(): String = "https://example.com/auth"
    
    override suspend fun isAuthenticated(): Boolean = false
}

/**
 * Fake adapter factory for testing.
 */
class FakeStoreAdapterFactory : StoreAdapterFactory {
    val adapters = mutableListOf<StoreAdapter>()
    
    fun addAdapter(adapter: StoreAdapter) {
        adapters.add(adapter)
    }
    
    override fun getAdapter(store: GameStore): StoreAdapter? = 
        adapters.find { it.store == store }
    
    override fun getAllAdapters(): List<StoreAdapter> = adapters.toList()
}
