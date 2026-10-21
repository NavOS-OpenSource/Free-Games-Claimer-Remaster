package com.navos.gameclaimer.domain.model

/**
 * Represents a supported game store platform.
 * 
 * Each store has different claiming mechanisms and authentication requirements.
 * This enum defines the stores we plan to support (implementation in later phases).
 */
enum class GameStore(
    val displayName: String,
    val baseUrl: String,
    val freeGamesPath: String
) {
    EPIC_GAMES(
        displayName = "Epic Games Store",
        baseUrl = "https://store.epicgames.com",
        freeGamesPath = "/en-US/free-games"
    ),
    GOG(
        displayName = "GOG.com",
        baseUrl = "https://www.gog.com",
        freeGamesPath = "/en/games?priceRange=0,0"
    ),
    STEAM(
        displayName = "Steam",
        baseUrl = "https://store.steampowered.com",
        freeGamesPath = "/search/?maxprice=free&category1=998"
    ),
    AMAZON_PRIME(
        displayName = "Prime Gaming",
        baseUrl = "https://gaming.amazon.com",
        freeGamesPath = "/home"
    ),
    // Phase 7 additions
    ITCH_IO(
        displayName = "Itch.io",
        baseUrl = "https://itch.io",
        freeGamesPath = "/games/on-sale"  // No dedicated free games page
    ),
    INDIEGALA(
        displayName = "IndieGala",
        baseUrl = "https://www.indiegala.com",
        freeGamesPath = "/giveaways"
    ),
    // Phase 10 additions
    MICROSOFT_STORE(
        displayName = "Microsoft Store",
        baseUrl = "https://www.xbox.com",
        freeGamesPath = "/en-US/games/store"
    ),
    UBISOFT(
        displayName = "Ubisoft",
        baseUrl = "https://www.ubisoft.com",
        freeGamesPath = "/en-us/games/free"
    );

    val freeGamesUrl: String
        get() = "$baseUrl$freeGamesPath"
}

/**
 * Represents a free game available for claiming.
 */
data class FreeGame(
    val id: String,
    val title: String,
    val store: GameStore,
    val imageUrl: String?,
    val description: String?,
    val offerEndTime: Long?, // Unix timestamp, null if permanent
    val claimUrl: String,
    val isClaimed: Boolean = false
)

/**
 * Represents the authentication state for a game store.
 */
sealed class StoreAuthState {
    data object NotAuthenticated : StoreAuthState()
    data object Authenticating : StoreAuthState()
    data class Authenticated(
        val displayName: String?,
        val lastVerified: Long // Unix timestamp
    ) : StoreAuthState()
    data class AuthError(val message: String) : StoreAuthState()
}

// ClaimResult moved to GiveawayModels.kt in Phase 6
