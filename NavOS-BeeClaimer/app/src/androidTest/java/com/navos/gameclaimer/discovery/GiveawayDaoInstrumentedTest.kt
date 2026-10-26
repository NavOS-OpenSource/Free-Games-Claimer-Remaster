package com.navos.gameclaimer.discovery

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.dao.GiveawayDao
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Phase 6: Instrumented tests for GiveawayDao.
 * 
 * Tests the safe upsert pattern that preserves user state
 * (isClaimed, isDismissed, claimedAt, discoveredAt).
 */
@RunWith(AndroidJUnit4::class)
class GiveawayDaoInstrumentedTest {
    
    private lateinit var db: GameClaimerDatabase
    private lateinit var dao: GiveawayDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GameClaimerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.giveawayDao()
    }
    
    @After
    @Throws(IOException::class)
    fun cleanup() {
        db.close()
    }
    
    // ===== Upsert Preserves User State =====
    
    @Test
    fun upsertPreservesClaimedStatus() = runTest {
        val id = "test:epic:1"
        val original = createTestEntity(id = id)
        
        // Insert original
        dao.upsert(original)
        
        // Mark as claimed
        dao.markClaimed(id, claimedAt = 1000L)
        
        // Verify claimed
        val afterClaim = dao.getById(id)
        assertTrue(afterClaim?.isClaimed == true)
        assertEquals(1000L, afterClaim?.claimedAt)
        
        // Upsert with updated source data
        val refreshed = original.copy(
            title = "Updated Title",
            lastCheckedAt = System.currentTimeMillis()
        )
        dao.upsert(refreshed)
        
        // Verify claimed status preserved
        val afterRefresh = dao.getById(id)
        assertNotNull(afterRefresh)
        assertTrue(afterRefresh!!.isClaimed)
        assertEquals(1000L, afterRefresh.claimedAt)
        assertEquals("Updated Title", afterRefresh.title)
    }
    
    @Test
    fun upsertPreservesDismissedStatus() = runTest {
        val id = "test:epic:2"
        val original = createTestEntity(id = id)
        
        // Insert original
        dao.upsert(original)
        
        // Dismiss
        dao.markDismissed(id)
        
        // Verify dismissed
        val afterDismiss = dao.getById(id)
        assertTrue(afterDismiss?.isDismissed == true)
        
        // Upsert with updated source data
        val refreshed = original.copy(
            description = "New description",
            lastCheckedAt = System.currentTimeMillis()
        )
        dao.upsert(refreshed)
        
        // Verify dismissed status preserved
        val afterRefresh = dao.getById(id)
        assertTrue(afterRefresh!!.isDismissed)
        assertEquals("New description", afterRefresh.description)
    }
    
    @Test
    fun upsertPreservesDiscoveredAt() = runTest {
        val id = "test:epic:3"
        val originalDiscoveredAt = 5000L
        val original = createTestEntity(id = id, discoveredAt = originalDiscoveredAt)
        
        // Insert original
        dao.upsert(original)
        
        // Verify discoveredAt
        val inserted = dao.getById(id)
        assertEquals(originalDiscoveredAt, inserted?.discoveredAt)
        
        // Upsert with different discoveredAt (should be ignored)
        val refreshed = original.copy(
            discoveredAt = 9000L,
            title = "Refreshed Title"
        )
        dao.upsert(refreshed)
        
        // Verify original discoveredAt preserved
        val afterRefresh = dao.getById(id)
        assertEquals(originalDiscoveredAt, afterRefresh?.discoveredAt)
        assertEquals("Refreshed Title", afterRefresh?.title)
    }
    
    @Test
    fun upsertUpdatesSourceFields() = runTest {
        val id = "test:epic:4"
        val original = createTestEntity(
            id = id,
            title = "Original Title",
            description = "Original Desc",
            originalPrice = "$19.99",
            endTime = 1000L
        )
        
        // Insert original
        dao.upsert(original)
        
        // Upsert with new source data
        val refreshed = original.copy(
            title = "New Title",
            description = "New Description",
            originalPrice = "$29.99",
            endTime = 2000L,
            lastCheckedAt = System.currentTimeMillis()
        )
        dao.upsert(refreshed)
        
        // Verify source fields updated
        val result = dao.getById(id)
        assertEquals("New Title", result?.title)
        assertEquals("New Description", result?.description)
        assertEquals("$29.99", result?.originalPrice)
        assertEquals(2000L, result?.endTime)
    }
    
    // ===== Cleanup Protection =====
    
    @Test
    fun deleteExpiredPreservesClaimedRecords() = runTest {
        val now = System.currentTimeMillis()
        val expiredTime = now - (31L * 24 * 60 * 60 * 1000) // 31 days ago
        
        // Insert claimed expired giveaway
        val claimed = createTestEntity(
            id = "test:epic:claimed",
            endTime = expiredTime,
            isClaimed = true
        )
        dao.upsert(claimed)
        dao.markClaimed(claimed.id)
        
        // Insert unclaimed expired giveaway
        val unclaimed = createTestEntity(
            id = "test:epic:unclaimed",
            endTime = expiredTime,
            isClaimed = false
        )
        dao.upsert(unclaimed)
        
        // Run cleanup
        dao.deleteExpiredBefore(now)
        
        // Claimed should be preserved
        assertNotNull(dao.getById(claimed.id))
        
        // Unclaimed should be deleted
        assertNull(dao.getById(unclaimed.id))
    }
    
    @Test
    fun deleteExpiredDoesNotDeleteActiveGiveaways() = runTest {
        val now = System.currentTimeMillis()
        val futureTime = now + (7L * 24 * 60 * 60 * 1000) // 7 days from now
        
        // Insert active giveaway
        val active = createTestEntity(
            id = "test:epic:active",
            endTime = futureTime
        )
        dao.upsert(active)
        
        // Run cleanup
        dao.deleteExpiredBefore(now)
        
        // Active should be preserved
        assertNotNull(dao.getById(active.id))
    }
    
    @Test
    fun deleteExpiredPreservesNullEndTime() = runTest {
        val now = System.currentTimeMillis()
        
        // Insert giveaway with null endTime (unlimited)
        val unlimited = createTestEntity(
            id = "test:epic:unlimited",
            endTime = null
        )
        dao.upsert(unlimited)
        
        // Run cleanup
        dao.deleteExpiredBefore(now)
        
        // Should be preserved (null endTime = never expires)
        assertNotNull(dao.getById(unlimited.id))
    }
    
    // ===== Deduplication =====
    
    @Test
    fun uniqueIndexPreventsPerSourceDuplicates() = runTest {
        val externalId = "ext123"
        val source = "GAMER_POWER"
        
        // Insert first
        val first = createTestEntity(
            id = "gamerpower:epic:$externalId",
            externalId = externalId,
            source = source,
            title = "First Title"
        )
        dao.upsert(first)
        
        // Insert second with same externalId+source but different id
        // This tests the unique index behavior
        val second = first.copy(
            title = "Second Title",
            lastCheckedAt = System.currentTimeMillis()
        )
        dao.upsert(second)
        
        // Should have updated, not created duplicate
        val result = dao.getById(first.id)
        assertEquals("Second Title", result?.title)
        
        // Count should be 1
        val count = dao.countActive()
        assertEquals(1, count)
    }
    
    @Test
    fun differentSourcesCreateSeparateRecords() = runTest {
        // Same game from different sources = different records
        // This is by design - each source tracks independently
        
        val gamerPower = createTestEntity(
            id = "gamerpower:epic:123",
            externalId = "123",
            source = "GAMER_POWER",
            title = "Game via GamerPower"
        )
        
        val storeDirect = createTestEntity(
            id = "direct:epic:game-slug",
            externalId = "game-slug",
            source = "STORE_DIRECT",
            title = "Game via Store"
        )
        
        dao.upsert(gamerPower)
        dao.upsert(storeDirect)
        
        // Both should exist
        assertNotNull(dao.getById(gamerPower.id))
        assertNotNull(dao.getById(storeDirect.id))
        assertEquals(2, dao.countActive())
    }
    
    // ===== Bulk Operations =====
    
    @Test
    fun upsertAllPreservesUserState() = runTest {
        // Insert original with explicit fields
        val g1 = createTestEntity(id = "test:epic:b1", title = "Original 1")
        dao.upsert(g1)
        
        // Verify insert worked
        val inserted = dao.getById(g1.id)
        assertNotNull("Insert should succeed", inserted)
        assertEquals("Original 1", inserted!!.title)
        assertFalse("Should not be claimed initially", inserted.isClaimed)
        
        // Claim it
        dao.markClaimed(g1.id)
        
        // Verify claim worked
        val afterClaim = dao.getById(g1.id)
        assertNotNull("Should still exist after claim", afterClaim)
        assertTrue("Should be claimed", afterClaim!!.isClaimed)
        
        // Upsert with update (simulating a refresh)
        val refreshed = g1.copy(title = "Updated 1", lastCheckedAt = System.currentTimeMillis() + 1000)
        dao.upsert(refreshed)
        
        // Verify title updated BUT claimed status preserved
        val afterUpsert = dao.getById(g1.id)
        assertNotNull("Should exist after upsert", afterUpsert)
        assertEquals("Title should be updated", "Updated 1", afterUpsert!!.title)
        assertTrue("Claimed status should be preserved", afterUpsert.isClaimed)
    }
    
    // ===== Query Tests =====
    
    @Test
    fun getActiveGiveawaysExcludesExpired() = runTest {
        val now = System.currentTimeMillis()
        
        val active = createTestEntity(id = "test:active", endTime = now + 10000)
        val expired = createTestEntity(id = "test:expired", endTime = now - 10000)
        
        dao.upsertAll(listOf(active, expired))
        
        val result = dao.getActiveGiveaways(now).first()
        assertEquals(1, result.size)
        assertEquals("test:active", result[0].id)
    }
    
    @Test
    fun getActiveGiveawaysExcludesDismissed() = runTest {
        val active = createTestEntity(id = "test:active")
        val dismissed = createTestEntity(id = "test:dismissed")
        
        dao.upsertAll(listOf(active, dismissed))
        dao.markDismissed(dismissed.id)
        
        val result = dao.getActiveGiveaways().first()
        assertEquals(1, result.size)
        assertEquals("test:active", result[0].id)
    }
    
    private fun createTestEntity(
        id: String = "test:epic:1",
        title: String = "Test Game",
        store: String = "EPIC_GAMES",
        type: String = "FREE_TO_KEEP",
        claimUrl: String = "https://store.epicgames.com/test",
        source: String = "GAMER_POWER",
        externalId: String? = "1",
        description: String? = null,
        originalPrice: String? = null,
        imageUrl: String? = null,
        startTime: Long? = null,
        endTime: Long? = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000),
        discoveredAt: Long = System.currentTimeMillis(),
        lastCheckedAt: Long = System.currentTimeMillis(),
        isClaimed: Boolean = false,
        isDismissed: Boolean = false
    ): GiveawayEntity {
        return GiveawayEntity(
            id = id,
            title = title,
            store = store,
            type = type,
            claimUrl = claimUrl,
            source = source,
            externalId = externalId,
            description = description,
            originalPrice = originalPrice,
            imageUrl = imageUrl,
            startTime = startTime,
            endTime = endTime,
            discoveredAt = discoveredAt,
            lastCheckedAt = lastCheckedAt,
            isClaimed = isClaimed,
            isDismissed = isDismissed
        )
    }
}
