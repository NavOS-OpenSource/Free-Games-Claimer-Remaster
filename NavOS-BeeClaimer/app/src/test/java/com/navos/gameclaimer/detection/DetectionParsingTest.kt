package com.navos.gameclaimer.detection

import com.navos.gameclaimer.data.adapter.EpicPageState
import com.navos.gameclaimer.data.adapter.EpicStoreAdapter
import com.navos.gameclaimer.data.adapter.GogStoreAdapter
import com.navos.gameclaimer.data.adapter.SteamOwnershipState
import com.navos.gameclaimer.data.adapter.SteamStoreAdapter
import com.navos.gameclaimer.domain.model.DetectionResult
import com.navos.gameclaimer.domain.model.DetectionState
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 18E: Security tests for detection parsing.
 * 
 * CRITICAL REQUIREMENT (per GPT spec):
 * Ambiguous or incomplete responses must produce UNKNOWN or ERROR states,
 * NEVER false positives (OWNED, AVAILABLE, NEW_ADD, etc.).
 * 
 * These tests verify defensive parsing prevents false ownership/availability claims.
 */
class DetectionParsingTest {

    // ===== DetectionResult Factory Tests =====

    @Test
    fun `DetectionResult available creates correct state`() {
        val result = DetectionResult.available("test_method", "raw")
        assertEquals(DetectionState.AVAILABLE, result.state)
        assertEquals("test_method", result.detectionMethod)
    }

    @Test
    fun `DetectionResult owned creates correct state`() {
        val result = DetectionResult.owned("owned_method")
        assertEquals(DetectionState.OWNED, result.state)
    }

    @Test
    fun `DetectionResult authRequired creates correct state`() {
        val result = DetectionResult.authRequired("Login needed")
        assertEquals(DetectionState.AUTH_REQUIRED, result.state)
    }

    @Test
    fun `DetectionResult error creates correct state with message`() {
        val result = DetectionResult.error("Something failed", "raw_error")
        assertEquals(DetectionState.ERROR, result.state)
        assertEquals("Something failed", result.errorMessage)
    }

    @Test
    fun `DetectionResult unknown creates correct state with elements`() {
        val result = DetectionResult.unknown(listOf("elem1", "elem2"), "raw")
        assertEquals(DetectionState.UNKNOWN, result.state)
    }

    // ===== CRITICAL: Steam must not return false positives =====

    @Test
    fun `Steam empty string MUST NOT return Owned`() {
        val adapter = SteamStoreAdapter()
        val result = adapter.parseOwnershipResult("440", "")
        assertFalse("Empty string must not return Owned", result is SteamOwnershipState.Owned)
    }

    @Test
    fun `Steam empty string MUST NOT return NotOwned`() {
        val adapter = SteamStoreAdapter()
        val result = adapter.parseOwnershipResult("440", "")
        assertFalse("Empty string must not return NotOwned", result is SteamOwnershipState.NotOwned)
    }

    @Test
    fun `Steam null literal MUST NOT return Owned`() {
        val adapter = SteamStoreAdapter()
        val result = adapter.parseOwnershipResult("440", "null")
        assertFalse("Null literal must not return Owned", result is SteamOwnershipState.Owned)
    }

    @Test
    fun `Steam malformed JSON MUST NOT return Owned`() {
        val adapter = SteamStoreAdapter()
        val result = adapter.parseOwnershipResult("440", "not valid json")
        assertFalse("Malformed JSON must not return Owned", result is SteamOwnershipState.Owned)
    }

    @Test
    fun `Steam empty JSON object MUST NOT return Owned`() {
        val adapter = SteamStoreAdapter()
        val result = adapter.parseOwnershipResult("440", "{}")
        assertFalse("Empty JSON must not return Owned", result is SteamOwnershipState.Owned)
    }

    @Test
    fun `Steam empty JSON object MUST NOT return NotOwned`() {
        val adapter = SteamStoreAdapter()
        val result = adapter.parseOwnershipResult("440", "{}")
        assertFalse("Empty JSON must not return NotOwned", result is SteamOwnershipState.NotOwned)
    }

    @Test
    fun `Steam ambiguous JSON MUST return Uncertain or Error`() {
        val adapter = SteamStoreAdapter()
        val result = adapter.parseOwnershipResult("440", """{"owned": false, "notOwned": false}""")
        assertTrue(
            "Ambiguous JSON must return Uncertain or Error, got ${result::class.simpleName}",
            result is SteamOwnershipState.Uncertain || result is SteamOwnershipState.Error
        )
    }

    // ===== CRITICAL: Epic must not return false positives =====

    @Test
    fun `Epic empty string MUST NOT return OWNED`() {
        val adapter = EpicStoreAdapter()
        val (state, _) = adapter.parsePageState("")
        assertNotEquals("Empty string must not return OWNED", EpicPageState.OWNED, state)
    }

    @Test
    fun `Epic empty string MUST NOT return NEW_ADD`() {
        val adapter = EpicStoreAdapter()
        val (state, _) = adapter.parsePageState("")
        assertNotEquals("Empty string must not return NEW_ADD", EpicPageState.NEW_ADD, state)
    }

    @Test
    fun `Epic empty string MUST NOT return NEW_GET`() {
        val adapter = EpicStoreAdapter()
        val (state, _) = adapter.parsePageState("")
        assertNotEquals("Empty string must not return NEW_GET", EpicPageState.NEW_GET, state)
    }

    @Test
    fun `Epic malformed JSON MUST NOT return OWNED`() {
        val adapter = EpicStoreAdapter()
        val (state, _) = adapter.parsePageState("not json")
        assertNotEquals("Malformed JSON must not return OWNED", EpicPageState.OWNED, state)
    }

    @Test
    fun `Epic empty JSON MUST NOT return OWNED`() {
        val adapter = EpicStoreAdapter()
        val (state, _) = adapter.parsePageState("{}")
        assertNotEquals("Empty JSON must not return OWNED", EpicPageState.OWNED, state)
    }

    @Test
    fun `Epic empty JSON MUST return UNKNOWN`() {
        val adapter = EpicStoreAdapter()
        val (state, _) = adapter.parsePageState("{}")
        assertEquals("Empty JSON should return UNKNOWN", EpicPageState.UNKNOWN, state)
    }

    // ===== CRITICAL: GOG must not return false positives =====

    @Test
    fun `GOG empty string MUST NOT return AVAILABLE`() {
        val adapter = GogStoreAdapter()
        val (state, _) = adapter.parseGiveawayResult("")
        assertNotEquals("Empty string must not return AVAILABLE", DetectionState.AVAILABLE, state)
    }

    @Test
    fun `GOG malformed JSON MUST NOT return AVAILABLE`() {
        val adapter = GogStoreAdapter()
        val (state, _) = adapter.parseGiveawayResult("not json")
        assertNotEquals("Malformed JSON must not return AVAILABLE", DetectionState.AVAILABLE, state)
    }

    @Test
    fun `GOG found false MUST return NOT_FOUND not AVAILABLE`() {
        val adapter = GogStoreAdapter()
        val (state, _) = adapter.parseGiveawayResult("""{"found": false}""")
        assertEquals("found=false should return NOT_FOUND", DetectionState.NOT_FOUND, state)
    }

    @Test
    fun `GOG found true without details MUST NOT return AVAILABLE`() {
        val adapter = GogStoreAdapter()
        val (state, _) = adapter.parseGiveawayResult("""{"found": true}""")
        assertNotEquals("found=true without details must not return AVAILABLE", DetectionState.AVAILABLE, state)
    }

    @Test
    fun `GOG claim empty braces returns claimed status`() {
        val adapter = GogStoreAdapter()
        val (status, _, success) = adapter.parseClaimResult("{}")
        assertEquals("claimed", status)
        assertTrue("Empty braces should indicate success", success)
    }

    // ===== Null Safety Tests =====

    @Test
    fun `Steam handles JSON with null values safely`() {
        val adapter = SteamStoreAdapter()
        val json = """{"owned": null, "error": null}"""
        val result = adapter.parseOwnershipResult("440", json)
        // Must not crash, and must not be a false positive
        assertNotNull(result)
        assertFalse("Null values must not return Owned", result is SteamOwnershipState.Owned)
    }

    @Test
    fun `Epic handles JSON with null flow safely`() {
        val adapter = EpicStoreAdapter()
        val (state, _) = adapter.parsePageState("""{"flow": null}""")
        // Must return UNKNOWN, not a false positive
        assertEquals(EpicPageState.UNKNOWN, state)
    }

    @Test
    fun `GOG handles JSON with null found safely`() {
        val adapter = GogStoreAdapter()
        val (state, _) = adapter.parseGiveawayResult("""{"found": null}""")
        // null should be treated as false, returning NOT_FOUND
        assertNotEquals("Null found must not return AVAILABLE", DetectionState.AVAILABLE, state)
    }

    // ===== isOwned Logic Tests =====

    @Test
    fun `Epic isOwned returns true for OWNED state`() {
        val adapter = EpicStoreAdapter()
        assertTrue(adapter.isOwned(EpicPageState.OWNED, ""))
    }

    @Test
    fun `Epic isOwned returns true for in library text`() {
        val adapter = EpicStoreAdapter()
        assertTrue(adapter.isOwned(EpicPageState.UNKNOWN, "In Library"))
    }

    @Test
    fun `Epic isOwned returns false for NEW_ADD`() {
        val adapter = EpicStoreAdapter()
        assertFalse(adapter.isOwned(EpicPageState.NEW_ADD, "Add to Library"))
    }

    @Test
    fun `Epic isOwned returns false for REGION`() {
        val adapter = EpicStoreAdapter()
        assertFalse(adapter.isOwned(EpicPageState.REGION, ""))
    }
}
