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
    val freeGamesPath: String,
    val iconUrl: String
) {
    STEAM(
        displayName = "Steam",
        baseUrl = "https://store.steampowered.com",
        freeGamesPath = "/search/?maxprice=free&category1=998",
        iconUrl = "https://store.steampowered.com/favicon.ico"
    ),
    EPIC_GAMES(
        displayName = "Epic Games Store",
        baseUrl = "https://store.epicgames.com",
        freeGamesPath = "/en-US/free-games",
        iconUrl = "https://github.com/user-attachments/assets/82e9e9bf-b6ac-4f20-91db-36d2c8429cb6"
    ),
    GOG(
        displayName = "GOG.com",
        baseUrl = "https://www.gog.com",
        freeGamesPath = "/en/games?priceRange=0,0",
        iconUrl = "https://github.com/user-attachments/assets/49040b50-ee14-4439-8e3c-e93cafd7c3a5"
    ),
    MICROSOFT_STORE(
        displayName = "Microsoft Store",
        baseUrl = "https://www.xbox.com",
        freeGamesPath = "/en-US/games/store",
        iconUrl = "https://www.google.com/s2/favicons?domain=xbox.com&sz=64"
    ),
    UBISOFT(
        displayName = "Ubisoft",
        baseUrl = "https://www.ubisoft.com",
        freeGamesPath = "/en-us/games/free",
        iconUrl = "https://icon.horse/icon/ubisoft.com"
    ),
    AMAZON_PRIME(
        displayName = "Prime Gaming",
        baseUrl = "https://gaming.amazon.com",
        freeGamesPath = "/home",
        iconUrl = "https://github.com/user-attachments/assets/7627a108-20c6-4525-a1d8-5d221ee89d6e"
    ),
    ITCH_IO(
        displayName = "Itch.io",
        baseUrl = "https://itch.io",
        freeGamesPath = "/games/on-sale",
        iconUrl = "https://itch.io/favicon.ico"
    ),
    INDIEGALA(
        displayName = "IndieGala",
        baseUrl = "https://www.indiegala.com",
        freeGamesPath = "/giveaways",
        iconUrl = "https://www.indiegala.com/favicon.ico"
    ),
    FANATICAL(
        displayName = "Fanatical",
        baseUrl = "https://www.fanatical.com",
        freeGamesPath = "/en/pick-and-mix",
        iconUrl = "https://www.fanatical.com/favicon.ico"
    ),
    ALIENWARE_ARENA(
        displayName = "Alienware Arena",
        baseUrl = "https://www.alienwarearena.com",
        freeGamesPath = "/ucf/show/giveaways",
        iconUrl = "https://www.alienwarearena.com/favicon.ico"
    ),
    // Asset stores at bottom
    FAB(
        displayName = "Fab",
        baseUrl = "https://www.fab.com",
        freeGamesPath = "/limited-time-free",
        iconUrl = "https://www.google.com/s2/favicons?domain=fab.com&sz=64"
    ),
    UNITY_ASSET_STORE(
        displayName = "Unity Asset Store",
        baseUrl = "https://assetstore.unity.com",
        freeGamesPath = "/publisher-sale",
        iconUrl = "https://www.google.com/s2/favicons?domain=unity.com&sz=64"
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
