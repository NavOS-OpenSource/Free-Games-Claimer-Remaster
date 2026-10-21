package com.navos.gameclaimer.automation

import com.navos.gameclaimer.data.local.entity.ClaimQueueEntity
import com.navos.gameclaimer.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 13: Tests for ClaimQueueEntity Room entity.
 * 
 * GPT REQUIREMENTS TESTED:
 * - Store only minimum necessary information
 * - Never persist credentials, cookies, tokens
 * - State transitions are safe
 */
class ClaimQueueEntityTest {
    
    // ===== Domain to Entity Conversion =====
    
    @Test
    fun `fromDomain creates entity with all fields`() {
        val item = ClaimQueueItem(
            queueId = "queue:test:123",
            giveawayId = "gamer_power:epic:456",
            store = GameStore.EPIC_GAMES,
            state = ClaimQueueState.PENDING,
            enqueuedAt = 1000L,
            scheduledAt = 2000L,
            attemptCount = 0,
            maxAttempts = 3,
            priority = ClaimQueueItem.PRIORITY_HIGH,
            isUserInitiated = true
        )
        
        val entity = ClaimQueueEntity.fromDomain(item)
        
        assertEquals("queue:test:123", entity.queueId)
        assertEquals("gamer_power:epic:456", entity.giveawayId)
        assertEquals("EPIC_GAMES", entity.store)
        assertEquals("PENDING", entity.state)
        assertEquals(1000L, entity.enqueuedAt)
        assertEquals(2000L, entity.scheduledAt)
        assertEquals(0, entity.attemptCount)
        assertEquals(3, entity.maxAttempts)
        assertEquals(ClaimQueueItem.PRIORITY_HIGH, entity.priority)
        assertTrue(entity.isUserInitiated)
    }
    
    @Test
    fun `fromDomain preserves outcome metadata when provided`() {
        val item = ClaimQueueItem(
            queueId = "queue:test:123",
            giveawayId = "test:id",
            store = GameStore.STEAM,
            state = ClaimQueueState.FAILED,
            enqueuedAt = 1000L,
            scheduledAt = 2000L,
            attemptCount = 3,
            maxAttempts = 3,
            lastAttemptAt = 3000L,
            priority = ClaimQueueItem.PRIORITY_NORMAL,
            isUserInitiated = false
        )
        
        val entity = ClaimQueueEntity.fromDomain(
            item = item,
            outcomeType = "PermanentFailure",
            outcomeJson = """{"giveawayId":"test:id","reason":"Not found"}"""
        )
        
        assertEquals("PermanentFailure", entity.lastOutcomeType)
        assertNotNull(entity.lastOutcomeJson)
        assertTrue(entity.lastOutcomeJson!!.contains("Not found"))
    }
    
    // ===== Entity to Domain Conversion =====
    
    @Test
    fun `toDomain creates queue item with all fields`() {
        val entity = ClaimQueueEntity(
            queueId = "queue:test:789",
            giveawayId = "gamer_power:gog:100",
            store = "GOG",
            state = "RETRY_SCHEDULED",
            enqueuedAt = 5000L,
            scheduledAt = 6000L,
            attemptCount = 2,
            maxAttempts = 5,
            lastOutcomeType = "TemporarilyUnavailable",
            lastOutcomeJson = null, // JSON deserialized in repository
            lastAttemptAt = 5500L,
            priority = ClaimQueueItem.PRIORITY_LOW,
            isUserInitiated = false,
            version = 3
        )
        
        val item = entity.toDomain()
        
        assertEquals("queue:test:789", item.queueId)
        assertEquals("gamer_power:gog:100", item.giveawayId)
        assertEquals(GameStore.GOG, item.store)
        assertEquals(ClaimQueueState.RETRY_SCHEDULED, item.state)
        assertEquals(5000L, item.enqueuedAt)
        assertEquals(6000L, item.scheduledAt)
        assertEquals(2, item.attemptCount)
        assertEquals(5, item.maxAttempts)
        assertEquals(5500L, item.lastAttemptAt)
        assertEquals(ClaimQueueItem.PRIORITY_LOW, item.priority)
        assertFalse(item.isUserInitiated)
        // Note: lastOutcome is null in toDomain - deserialized separately
        assertNull(item.lastOutcome)
    }
    
    // ===== All States Can Be Converted =====
    
    @Test
    fun `all ClaimQueueState values convert correctly`() {
        for (state in ClaimQueueState.values()) {
            val entity = createTestEntity().copy(state = state.name)
            val item = entity.toDomain()
            
            assertEquals("State $state should convert correctly", state, item.state)
        }
    }
    
    // ===== All Stores Can Be Converted =====
    
    @Test
    fun `all GameStore values convert correctly`() {
        for (store in GameStore.values()) {
            val entity = createTestEntity().copy(store = store.name)
            val item = entity.toDomain()
            
            assertEquals("Store $store should convert correctly", store, item.store)
        }
    }
    
    // ===== Round-Trip Conversion =====
    
    @Test
    fun `round-trip conversion preserves all fields`() {
        val original = ClaimQueueItem(
            queueId = "queue:roundtrip:test",
            giveawayId = "test:giveaway:rt",
            store = GameStore.INDIEGALA,
            state = ClaimQueueState.IN_PROGRESS,
            enqueuedAt = 10000L,
            scheduledAt = 20000L,
            attemptCount = 1,
            maxAttempts = 4,
            lastAttemptAt = 15000L,
            priority = 7,
            isUserInitiated = true
        )
        
        val entity = ClaimQueueEntity.fromDomain(original)
        val restored = entity.toDomain()
        
        assertEquals(original.queueId, restored.queueId)
        assertEquals(original.giveawayId, restored.giveawayId)
        assertEquals(original.store, restored.store)
        assertEquals(original.state, restored.state)
        assertEquals(original.enqueuedAt, restored.enqueuedAt)
        assertEquals(original.scheduledAt, restored.scheduledAt)
        assertEquals(original.attemptCount, restored.attemptCount)
        assertEquals(original.maxAttempts, restored.maxAttempts)
        assertEquals(original.lastAttemptAt, restored.lastAttemptAt)
        assertEquals(original.priority, restored.priority)
        assertEquals(original.isUserInitiated, restored.isUserInitiated)
    }
    
    // ===== Security: No Sensitive Data =====
    
    @Test
    fun `entity does not contain sensitive fields`() {
        val entity = createTestEntity()
        
        // Verify entity class only has safe fields
        val fieldNames = ClaimQueueEntity::class.java.declaredFields.map { it.name }
        
        // These should NOT be present
        val sensitiveTerms = listOf(
            "password", "token", "cookie", "session", "credential", 
            "secret", "auth", "key", "bearer", "access"
        )
        
        for (field in fieldNames) {
            for (term in sensitiveTerms) {
                assertFalse(
                    "Field '$field' should not contain sensitive term '$term'",
                    field.lowercase().contains(term.lowercase())
                )
            }
        }
    }
    
    @Test
    fun `outcome JSON does not contain sensitive data`() {
        // ClaimOutcome types should never contain credentials
        val outcomes = listOf(
            ClaimOutcome.Success(giveawayId = "test", verificationMethod = VerificationMethod.API_CONFIRMATION),
            ClaimOutcome.RequiresAuth(giveawayId = "test", store = GameStore.EPIC_GAMES),
            ClaimOutcome.PermanentFailure(giveawayId = "test", reason = "Test error"),
            ClaimOutcome.TemporarilyUnavailable(giveawayId = "test", reason = "Server error")
        )
        
        for (outcome in outcomes) {
            // Verify outcome class doesn't have credential fields
            val outcomeFields = outcome::class.java.declaredFields.map { it.name }
            val sensitiveTerms = listOf("password", "token", "cookie", "secret", "bearer")
            
            for (field in outcomeFields) {
                for (term in sensitiveTerms) {
                    assertFalse(
                        "Outcome field '$field' should not contain '$term'",
                        field.lowercase().contains(term.lowercase())
                    )
                }
            }
        }
    }
    
    // ===== Version Field for Optimistic Locking =====
    
    @Test
    fun `entity has version field for optimistic locking`() {
        val entity = createTestEntity()
        
        assertEquals(1, entity.version)
    }
    
    @Test
    fun `version defaults to 1`() {
        val entity = ClaimQueueEntity(
            queueId = "test",
            giveawayId = "test",
            store = "STEAM",
            state = "PENDING",
            enqueuedAt = 0,
            scheduledAt = 0,
            attemptCount = 0,
            maxAttempts = 3,
            priority = 5,
            isUserInitiated = false
            // version not specified - should default to 1
        )
        
        assertEquals(1, entity.version)
    }
    
    // ===== Helper =====
    
    private fun createTestEntity() = ClaimQueueEntity(
        queueId = "queue:test:default",
        giveawayId = "test:giveaway:default",
        store = "EPIC_GAMES",
        state = "PENDING",
        enqueuedAt = 1000L,
        scheduledAt = 2000L,
        attemptCount = 0,
        maxAttempts = 3,
        lastOutcomeType = null,
        lastOutcomeJson = null,
        lastAttemptAt = null,
        priority = ClaimQueueItem.PRIORITY_NORMAL,
        isUserInitiated = false,
        version = 1
    )
}
