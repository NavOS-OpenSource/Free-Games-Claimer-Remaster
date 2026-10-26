package com.navos.gameclaimer

import com.navos.gameclaimer.domain.model.ClaimResult
import com.navos.gameclaimer.domain.model.FreeGame
import com.navos.gameclaimer.domain.model.GameStore
import com.navos.gameclaimer.domain.model.StoreAuthState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for domain models.
 * 
 * Phase 3: Basic model validation
 * Phase 5+: Will add tests for repository implementations
 */
class GameModelsTest {

    @Test
    fun `GameStore has correct URLs`() {
        assertEquals(
            "https://store.epicgames.com/en-US/free-games",
            GameStore.EPIC_GAMES.freeGamesUrl
        )
        assertEquals(
            "https://www.gog.com/en/games?priceRange=0,0",
            GameStore.GOG.freeGamesUrl
        )
        assertEquals(
            "https://store.steampowered.com/search/?maxprice=free&category1=998",
            GameStore.STEAM.freeGamesUrl
        )
    }

    @Test
    fun `GameStore URLs are HTTPS`() {
        GameStore.entries.forEach { store ->
            assertTrue(
                "Store ${store.name} URL should be HTTPS",
                store.freeGamesUrl.startsWith("https://")
            )
        }
    }

    @Test
    fun `FreeGame data class holds expected values`() {
        val game = FreeGame(
            id = "test-123",
            title = "Test Game",
            store = GameStore.EPIC_GAMES,
            imageUrl = "https://example.com/image.png",
            description = "A test game",
            offerEndTime = 1700000000L,
            claimUrl = "https://store.epicgames.com/p/test-game",
            isClaimed = false
        )

        assertEquals("test-123", game.id)
        assertEquals("Test Game", game.title)
        assertEquals(GameStore.EPIC_GAMES, game.store)
        assertFalse(game.isClaimed)
    }

    @Test
    fun `StoreAuthState sealed class works correctly`() {
        val notAuth: StoreAuthState = StoreAuthState.NotAuthenticated
        val authenticating: StoreAuthState = StoreAuthState.Authenticating
        val authenticated: StoreAuthState = StoreAuthState.Authenticated(
            displayName = "TestUser",
            lastVerified = System.currentTimeMillis()
        )
        val error: StoreAuthState = StoreAuthState.AuthError("Failed")

        assertTrue(notAuth is StoreAuthState.NotAuthenticated)
        assertTrue(authenticating is StoreAuthState.Authenticating)
        assertTrue(authenticated is StoreAuthState.Authenticated)
        assertEquals("TestUser", (authenticated as StoreAuthState.Authenticated).displayName)
        assertTrue(error is StoreAuthState.AuthError)
    }

    @Test
    fun `ClaimResult sealed class works correctly`() {
        val game = FreeGame(
            id = "test",
            title = "Test",
            store = GameStore.EPIC_GAMES,
            imageUrl = null,
            description = null,
            offerEndTime = null,
            claimUrl = "https://store.epicgames.com/p/test"
        )

        val success: ClaimResult = ClaimResult.Success(giveawayId = game.id)
        val alreadyOwned: ClaimResult = ClaimResult.AlreadyOwned(giveawayId = game.id)
        val failed: ClaimResult = ClaimResult.Failed(
            giveawayId = game.id, 
            reason = "Network error",
            isRetryable = true
        )
        val requiresInteraction: ClaimResult = ClaimResult.RequiresInteraction(
            giveawayId = game.id,
            interactionType = "captcha"
        )

        assertTrue(success is ClaimResult.Success)
        assertTrue(alreadyOwned is ClaimResult.AlreadyOwned)
        assertTrue(failed is ClaimResult.Failed)
        assertTrue(requiresInteraction is ClaimResult.RequiresInteraction)
        assertEquals("Network error", (failed as ClaimResult.Failed).reason)
    }
    
    @Test
    fun `All GameStore entries have valid configuration`() {
        GameStore.entries.forEach { store ->
            assertNotNull("Display name should not be null for ${store.name}", store.displayName)
            assertTrue("Display name should not be empty for ${store.name}", store.displayName.isNotEmpty())
            assertTrue("Base URL should start with https for ${store.name}", store.baseUrl.startsWith("https://"))
            assertTrue("Free games path should start with / for ${store.name}", store.freeGamesPath.startsWith("/"))
        }
    }
}
