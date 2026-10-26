package com.navos.gameclaimer.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * NavOS BeeClaimer Theme System
 * Phase 22: Brutalist UI with custom themes
 * 
 * Features:
 * - 5 distinctive themes with attractive names
 * - Buttery Yellow as default light theme
 * - Midnight as default dark theme
 * - Strong borders and boxy aesthetic
 * - Theme persistence via SharedPreferences
 */

// ============================================================================
// THEME DEFINITIONS
// ============================================================================

/**
 * Available app themes with display names
 */
enum class AppTheme(val displayName: String, val isDark: Boolean) {
    // Light themes
    BUTTERY_YELLOW("Buttery Yellow", false),
    FOREST_MOSS("Forest Moss", false),
    ARCTIC_FROST("Arctic Frost", false),
    SUNSET_EMBER("Sunset Ember", false),
    // Dark themes
    MIDNIGHT("Midnight", true),                  // Pure dark (existing)
    BUTTERY_YELLOW_DARK("Buttery Yellow", true),
    FOREST_MOSS_DARK("Forest Moss", true),
    ARCTIC_FROST_DARK("Arctic Frost", true),
    SUNSET_EMBER_DARK("Sunset Ember", true);
    
    companion object {
        val defaultLight = BUTTERY_YELLOW
        val defaultDark = MIDNIGHT
        
        // Get all light themes for picker
        val lightThemes = listOf(BUTTERY_YELLOW, FOREST_MOSS, ARCTIC_FROST, SUNSET_EMBER)
        // Get all dark themes for picker - Midnight at bottom (pure dark)
        val darkThemes = listOf(BUTTERY_YELLOW_DARK, FOREST_MOSS_DARK, ARCTIC_FROST_DARK, SUNSET_EMBER_DARK, MIDNIGHT)
        
        fun fromString(value: String): AppTheme {
            return entries.find { it.name == value } ?: defaultLight
        }
        
        // Get the dark variant of a light theme
        fun getDarkVariant(lightTheme: AppTheme): AppTheme {
            return when (lightTheme) {
                BUTTERY_YELLOW -> BUTTERY_YELLOW_DARK
                FOREST_MOSS -> FOREST_MOSS_DARK
                ARCTIC_FROST -> ARCTIC_FROST_DARK
                SUNSET_EMBER -> SUNSET_EMBER_DARK
                else -> lightTheme // Already dark
            }
        }
        
        // Get the light variant of a dark theme
        fun getLightVariant(darkTheme: AppTheme): AppTheme {
            return when (darkTheme) {
                BUTTERY_YELLOW_DARK -> BUTTERY_YELLOW
                FOREST_MOSS_DARK -> FOREST_MOSS
                ARCTIC_FROST_DARK -> ARCTIC_FROST
                SUNSET_EMBER_DARK -> SUNSET_EMBER
                MIDNIGHT -> BUTTERY_YELLOW // Midnight maps to default light
                else -> darkTheme // Already light
            }
        }
    }
}

/**
 * Theme mode selection
 */
enum class ThemeMode {
    LIGHT,      // Always use light theme
    DARK,       // Always use dark theme
    SYSTEM;     // Follow system setting
    
    companion object {
        fun fromString(value: String): ThemeMode {
            return entries.find { it.name == value } ?: SYSTEM
        }
    }
}

// ============================================================================
// COLOR SCHEMES
// ============================================================================

private fun butteryYellowColorScheme() = lightColorScheme(
    primary = ButteryYellowColors.Primary,
    onPrimary = ButteryYellowColors.OnPrimary,
    primaryContainer = ButteryYellowColors.PrimaryContainer,
    onPrimaryContainer = ButteryYellowColors.OnPrimaryContainer,
    secondary = ButteryYellowColors.Secondary,
    onSecondary = ButteryYellowColors.OnSecondary,
    secondaryContainer = ButteryYellowColors.SecondaryContainer,
    onSecondaryContainer = ButteryYellowColors.OnSecondaryContainer,
    tertiary = ButteryYellowColors.Tertiary,
    onTertiary = ButteryYellowColors.OnTertiary,
    tertiaryContainer = ButteryYellowColors.TertiaryContainer,
    onTertiaryContainer = ButteryYellowColors.OnTertiaryContainer,
    background = ButteryYellowColors.Background,
    onBackground = ButteryYellowColors.OnBackground,
    surface = ButteryYellowColors.Surface,
    onSurface = ButteryYellowColors.OnSurface,
    surfaceVariant = ButteryYellowColors.SurfaceVariant,
    onSurfaceVariant = ButteryYellowColors.OnSurfaceVariant,
    outline = ButteryYellowColors.Outline,
    outlineVariant = ButteryYellowColors.OutlineVariant,
    error = ButteryYellowColors.Error,
    onError = ButteryYellowColors.OnError,
    errorContainer = ButteryYellowColors.ErrorContainer,
    onErrorContainer = ButteryYellowColors.OnErrorContainer,
    inverseSurface = ButteryYellowColors.InverseSurface,
    inverseOnSurface = ButteryYellowColors.InverseOnSurface,
    inversePrimary = ButteryYellowColors.InversePrimary,
    scrim = ButteryYellowColors.Scrim
)

private fun midnightColorScheme() = darkColorScheme(
    primary = MidnightColors.Primary,
    onPrimary = MidnightColors.OnPrimary,
    primaryContainer = MidnightColors.PrimaryContainer,
    onPrimaryContainer = MidnightColors.OnPrimaryContainer,
    secondary = MidnightColors.Secondary,
    onSecondary = MidnightColors.OnSecondary,
    secondaryContainer = MidnightColors.SecondaryContainer,
    onSecondaryContainer = MidnightColors.OnSecondaryContainer,
    tertiary = MidnightColors.Tertiary,
    onTertiary = MidnightColors.OnTertiary,
    tertiaryContainer = MidnightColors.TertiaryContainer,
    onTertiaryContainer = MidnightColors.OnTertiaryContainer,
    background = MidnightColors.Background,
    onBackground = MidnightColors.OnBackground,
    surface = MidnightColors.Surface,
    onSurface = MidnightColors.OnSurface,
    surfaceVariant = MidnightColors.SurfaceVariant,
    onSurfaceVariant = MidnightColors.OnSurfaceVariant,
    outline = MidnightColors.Outline,
    outlineVariant = MidnightColors.OutlineVariant,
    error = MidnightColors.Error,
    onError = MidnightColors.OnError,
    errorContainer = MidnightColors.ErrorContainer,
    onErrorContainer = MidnightColors.OnErrorContainer,
    inverseSurface = MidnightColors.InverseSurface,
    inverseOnSurface = MidnightColors.InverseOnSurface,
    inversePrimary = MidnightColors.InversePrimary,
    scrim = MidnightColors.Scrim
)

private fun forestMossColorScheme() = lightColorScheme(
    primary = ForestMossColors.Primary,
    onPrimary = ForestMossColors.OnPrimary,
    primaryContainer = ForestMossColors.PrimaryContainer,
    onPrimaryContainer = ForestMossColors.OnPrimaryContainer,
    secondary = ForestMossColors.Secondary,
    onSecondary = ForestMossColors.OnSecondary,
    secondaryContainer = ForestMossColors.SecondaryContainer,
    onSecondaryContainer = ForestMossColors.OnSecondaryContainer,
    tertiary = ForestMossColors.Tertiary,
    onTertiary = ForestMossColors.OnTertiary,
    tertiaryContainer = ForestMossColors.TertiaryContainer,
    onTertiaryContainer = ForestMossColors.OnTertiaryContainer,
    background = ForestMossColors.Background,
    onBackground = ForestMossColors.OnBackground,
    surface = ForestMossColors.Surface,
    onSurface = ForestMossColors.OnSurface,
    surfaceVariant = ForestMossColors.SurfaceVariant,
    onSurfaceVariant = ForestMossColors.OnSurfaceVariant,
    outline = ForestMossColors.Outline,
    outlineVariant = ForestMossColors.OutlineVariant,
    error = ForestMossColors.Error,
    onError = ForestMossColors.OnError,
    errorContainer = ForestMossColors.ErrorContainer,
    onErrorContainer = ForestMossColors.OnErrorContainer,
    inverseSurface = ForestMossColors.InverseSurface,
    inverseOnSurface = ForestMossColors.InverseOnSurface,
    inversePrimary = ForestMossColors.InversePrimary,
    scrim = ForestMossColors.Scrim
)

private fun arcticFrostColorScheme() = lightColorScheme(
    primary = ArcticFrostColors.Primary,
    onPrimary = ArcticFrostColors.OnPrimary,
    primaryContainer = ArcticFrostColors.PrimaryContainer,
    onPrimaryContainer = ArcticFrostColors.OnPrimaryContainer,
    secondary = ArcticFrostColors.Secondary,
    onSecondary = ArcticFrostColors.OnSecondary,
    secondaryContainer = ArcticFrostColors.SecondaryContainer,
    onSecondaryContainer = ArcticFrostColors.OnSecondaryContainer,
    tertiary = ArcticFrostColors.Tertiary,
    onTertiary = ArcticFrostColors.OnTertiary,
    tertiaryContainer = ArcticFrostColors.TertiaryContainer,
    onTertiaryContainer = ArcticFrostColors.OnTertiaryContainer,
    background = ArcticFrostColors.Background,
    onBackground = ArcticFrostColors.OnBackground,
    surface = ArcticFrostColors.Surface,
    onSurface = ArcticFrostColors.OnSurface,
    surfaceVariant = ArcticFrostColors.SurfaceVariant,
    onSurfaceVariant = ArcticFrostColors.OnSurfaceVariant,
    outline = ArcticFrostColors.Outline,
    outlineVariant = ArcticFrostColors.OutlineVariant,
    error = ArcticFrostColors.Error,
    onError = ArcticFrostColors.OnError,
    errorContainer = ArcticFrostColors.ErrorContainer,
    onErrorContainer = ArcticFrostColors.OnErrorContainer,
    inverseSurface = ArcticFrostColors.InverseSurface,
    inverseOnSurface = ArcticFrostColors.InverseOnSurface,
    inversePrimary = ArcticFrostColors.InversePrimary,
    scrim = ArcticFrostColors.Scrim
)

private fun sunsetEmberColorScheme() = lightColorScheme(
    primary = SunsetEmberColors.Primary,
    onPrimary = SunsetEmberColors.OnPrimary,
    primaryContainer = SunsetEmberColors.PrimaryContainer,
    onPrimaryContainer = SunsetEmberColors.OnPrimaryContainer,
    secondary = SunsetEmberColors.Secondary,
    onSecondary = SunsetEmberColors.OnSecondary,
    secondaryContainer = SunsetEmberColors.SecondaryContainer,
    onSecondaryContainer = SunsetEmberColors.OnSecondaryContainer,
    tertiary = SunsetEmberColors.Tertiary,
    onTertiary = SunsetEmberColors.OnTertiary,
    tertiaryContainer = SunsetEmberColors.TertiaryContainer,
    onTertiaryContainer = SunsetEmberColors.OnTertiaryContainer,
    background = SunsetEmberColors.Background,
    onBackground = SunsetEmberColors.OnBackground,
    surface = SunsetEmberColors.Surface,
    onSurface = SunsetEmberColors.OnSurface,
    surfaceVariant = SunsetEmberColors.SurfaceVariant,
    onSurfaceVariant = SunsetEmberColors.OnSurfaceVariant,
    outline = SunsetEmberColors.Outline,
    outlineVariant = SunsetEmberColors.OutlineVariant,
    error = SunsetEmberColors.Error,
    onError = SunsetEmberColors.OnError,
    errorContainer = SunsetEmberColors.ErrorContainer,
    onErrorContainer = SunsetEmberColors.OnErrorContainer,
    inverseSurface = SunsetEmberColors.InverseSurface,
    inverseOnSurface = SunsetEmberColors.InverseOnSurface,
    inversePrimary = SunsetEmberColors.InversePrimary,
    scrim = SunsetEmberColors.Scrim
)

// ============================================================================
// DARK COLOR SCHEMES
// ============================================================================

private fun butteryYellowDarkColorScheme() = darkColorScheme(
    primary = ButteryYellowDarkColors.Primary,
    onPrimary = ButteryYellowDarkColors.OnPrimary,
    primaryContainer = ButteryYellowDarkColors.PrimaryContainer,
    onPrimaryContainer = ButteryYellowDarkColors.OnPrimaryContainer,
    secondary = ButteryYellowDarkColors.Secondary,
    onSecondary = ButteryYellowDarkColors.OnSecondary,
    secondaryContainer = ButteryYellowDarkColors.SecondaryContainer,
    onSecondaryContainer = ButteryYellowDarkColors.OnSecondaryContainer,
    tertiary = ButteryYellowDarkColors.Tertiary,
    onTertiary = ButteryYellowDarkColors.OnTertiary,
    tertiaryContainer = ButteryYellowDarkColors.TertiaryContainer,
    onTertiaryContainer = ButteryYellowDarkColors.OnTertiaryContainer,
    background = ButteryYellowDarkColors.Background,
    onBackground = ButteryYellowDarkColors.OnBackground,
    surface = ButteryYellowDarkColors.Surface,
    onSurface = ButteryYellowDarkColors.OnSurface,
    surfaceVariant = ButteryYellowDarkColors.SurfaceVariant,
    onSurfaceVariant = ButteryYellowDarkColors.OnSurfaceVariant,
    outline = ButteryYellowDarkColors.Outline,
    outlineVariant = ButteryYellowDarkColors.OutlineVariant,
    error = ButteryYellowDarkColors.Error,
    onError = ButteryYellowDarkColors.OnError,
    errorContainer = ButteryYellowDarkColors.ErrorContainer,
    onErrorContainer = ButteryYellowDarkColors.OnErrorContainer,
    inverseSurface = ButteryYellowDarkColors.InverseSurface,
    inverseOnSurface = ButteryYellowDarkColors.InverseOnSurface,
    inversePrimary = ButteryYellowDarkColors.InversePrimary,
    scrim = ButteryYellowDarkColors.Scrim
)

private fun forestMossDarkColorScheme() = darkColorScheme(
    primary = ForestMossDarkColors.Primary,
    onPrimary = ForestMossDarkColors.OnPrimary,
    primaryContainer = ForestMossDarkColors.PrimaryContainer,
    onPrimaryContainer = ForestMossDarkColors.OnPrimaryContainer,
    secondary = ForestMossDarkColors.Secondary,
    onSecondary = ForestMossDarkColors.OnSecondary,
    secondaryContainer = ForestMossDarkColors.SecondaryContainer,
    onSecondaryContainer = ForestMossDarkColors.OnSecondaryContainer,
    tertiary = ForestMossDarkColors.Tertiary,
    onTertiary = ForestMossDarkColors.OnTertiary,
    tertiaryContainer = ForestMossDarkColors.TertiaryContainer,
    onTertiaryContainer = ForestMossDarkColors.OnTertiaryContainer,
    background = ForestMossDarkColors.Background,
    onBackground = ForestMossDarkColors.OnBackground,
    surface = ForestMossDarkColors.Surface,
    onSurface = ForestMossDarkColors.OnSurface,
    surfaceVariant = ForestMossDarkColors.SurfaceVariant,
    onSurfaceVariant = ForestMossDarkColors.OnSurfaceVariant,
    outline = ForestMossDarkColors.Outline,
    outlineVariant = ForestMossDarkColors.OutlineVariant,
    error = ForestMossDarkColors.Error,
    onError = ForestMossDarkColors.OnError,
    errorContainer = ForestMossDarkColors.ErrorContainer,
    onErrorContainer = ForestMossDarkColors.OnErrorContainer,
    inverseSurface = ForestMossDarkColors.InverseSurface,
    inverseOnSurface = ForestMossDarkColors.InverseOnSurface,
    inversePrimary = ForestMossDarkColors.InversePrimary,
    scrim = ForestMossDarkColors.Scrim
)

private fun arcticFrostDarkColorScheme() = darkColorScheme(
    primary = ArcticFrostDarkColors.Primary,
    onPrimary = ArcticFrostDarkColors.OnPrimary,
    primaryContainer = ArcticFrostDarkColors.PrimaryContainer,
    onPrimaryContainer = ArcticFrostDarkColors.OnPrimaryContainer,
    secondary = ArcticFrostDarkColors.Secondary,
    onSecondary = ArcticFrostDarkColors.OnSecondary,
    secondaryContainer = ArcticFrostDarkColors.SecondaryContainer,
    onSecondaryContainer = ArcticFrostDarkColors.OnSecondaryContainer,
    tertiary = ArcticFrostDarkColors.Tertiary,
    onTertiary = ArcticFrostDarkColors.OnTertiary,
    tertiaryContainer = ArcticFrostDarkColors.TertiaryContainer,
    onTertiaryContainer = ArcticFrostDarkColors.OnTertiaryContainer,
    background = ArcticFrostDarkColors.Background,
    onBackground = ArcticFrostDarkColors.OnBackground,
    surface = ArcticFrostDarkColors.Surface,
    onSurface = ArcticFrostDarkColors.OnSurface,
    surfaceVariant = ArcticFrostDarkColors.SurfaceVariant,
    onSurfaceVariant = ArcticFrostDarkColors.OnSurfaceVariant,
    outline = ArcticFrostDarkColors.Outline,
    outlineVariant = ArcticFrostDarkColors.OutlineVariant,
    error = ArcticFrostDarkColors.Error,
    onError = ArcticFrostDarkColors.OnError,
    errorContainer = ArcticFrostDarkColors.ErrorContainer,
    onErrorContainer = ArcticFrostDarkColors.OnErrorContainer,
    inverseSurface = ArcticFrostDarkColors.InverseSurface,
    inverseOnSurface = ArcticFrostDarkColors.InverseOnSurface,
    inversePrimary = ArcticFrostDarkColors.InversePrimary,
    scrim = ArcticFrostDarkColors.Scrim
)

private fun sunsetEmberDarkColorScheme() = darkColorScheme(
    primary = SunsetEmberDarkColors.Primary,
    onPrimary = SunsetEmberDarkColors.OnPrimary,
    primaryContainer = SunsetEmberDarkColors.PrimaryContainer,
    onPrimaryContainer = SunsetEmberDarkColors.OnPrimaryContainer,
    secondary = SunsetEmberDarkColors.Secondary,
    onSecondary = SunsetEmberDarkColors.OnSecondary,
    secondaryContainer = SunsetEmberDarkColors.SecondaryContainer,
    onSecondaryContainer = SunsetEmberDarkColors.OnSecondaryContainer,
    tertiary = SunsetEmberDarkColors.Tertiary,
    onTertiary = SunsetEmberDarkColors.OnTertiary,
    tertiaryContainer = SunsetEmberDarkColors.TertiaryContainer,
    onTertiaryContainer = SunsetEmberDarkColors.OnTertiaryContainer,
    background = SunsetEmberDarkColors.Background,
    onBackground = SunsetEmberDarkColors.OnBackground,
    surface = SunsetEmberDarkColors.Surface,
    onSurface = SunsetEmberDarkColors.OnSurface,
    surfaceVariant = SunsetEmberDarkColors.SurfaceVariant,
    onSurfaceVariant = SunsetEmberDarkColors.OnSurfaceVariant,
    outline = SunsetEmberDarkColors.Outline,
    outlineVariant = SunsetEmberDarkColors.OutlineVariant,
    error = SunsetEmberDarkColors.Error,
    onError = SunsetEmberDarkColors.OnError,
    errorContainer = SunsetEmberDarkColors.ErrorContainer,
    onErrorContainer = SunsetEmberDarkColors.OnErrorContainer,
    inverseSurface = SunsetEmberDarkColors.InverseSurface,
    inverseOnSurface = SunsetEmberDarkColors.InverseOnSurface,
    inversePrimary = SunsetEmberDarkColors.InversePrimary,
    scrim = SunsetEmberDarkColors.Scrim
)

/**
 * Get color scheme for a specific theme
 */
fun getColorScheme(theme: AppTheme): ColorScheme {
    return when (theme) {
        // Light themes
        AppTheme.BUTTERY_YELLOW -> butteryYellowColorScheme()
        AppTheme.FOREST_MOSS -> forestMossColorScheme()
        AppTheme.ARCTIC_FROST -> arcticFrostColorScheme()
        AppTheme.SUNSET_EMBER -> sunsetEmberColorScheme()
        // Dark themes
        AppTheme.MIDNIGHT -> midnightColorScheme()
        AppTheme.BUTTERY_YELLOW_DARK -> butteryYellowDarkColorScheme()
        AppTheme.FOREST_MOSS_DARK -> forestMossDarkColorScheme()
        AppTheme.ARCTIC_FROST_DARK -> arcticFrostDarkColorScheme()
        AppTheme.SUNSET_EMBER_DARK -> sunsetEmberDarkColorScheme()
    }
}

// ============================================================================
// THEME PREFERENCES
// ============================================================================

/**
 * Manages theme preferences persistence
 */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_THEME = "selected_theme"
        private const val KEY_MODE = "theme_mode"
        private const val KEY_CORNER_RADIUS = "corner_radius"
        private const val KEY_DEVELOPER_OPTIONS = "developer_options_enabled"
    }
    
    var selectedTheme: AppTheme
        get() = AppTheme.fromString(prefs.getString(KEY_THEME, AppTheme.defaultLight.name) ?: AppTheme.defaultLight.name)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()
    
    var themeMode: ThemeMode
        get() = ThemeMode.fromString(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()
    
    var cornerRadius: Float
        get() = prefs.getFloat(KEY_CORNER_RADIUS, 4f)  // Default to slightly boxy
        set(value) = prefs.edit().putFloat(KEY_CORNER_RADIUS, value).apply()
    
    var developerOptionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEVELOPER_OPTIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVELOPER_OPTIONS, value).apply()
}

// ============================================================================
// THEME STATE
// ============================================================================

/**
 * Theme state holder for composition
 */
class ThemeState(
    initialTheme: AppTheme = AppTheme.defaultLight,
    initialMode: ThemeMode = ThemeMode.SYSTEM,
    initialCornerRadius: Float = 4f
) {
    var currentTheme by mutableStateOf(initialTheme)
    var themeMode by mutableStateOf(initialMode)
    var cornerRadius by mutableStateOf(initialCornerRadius)
    
    fun setTheme(theme: AppTheme) {
        currentTheme = theme
    }
    
    fun setMode(mode: ThemeMode) {
        themeMode = mode
        // Convert theme to match the new mode
        when (mode) {
            ThemeMode.LIGHT -> {
                if (currentTheme.isDark) {
                    currentTheme = AppTheme.getLightVariant(currentTheme)
                }
            }
            ThemeMode.DARK -> {
                if (!currentTheme.isDark) {
                    currentTheme = AppTheme.getDarkVariant(currentTheme)
                }
            }
            ThemeMode.SYSTEM -> {
                // Keep current theme, system will handle light/dark automatically
            }
        }
    }
}

/**
 * Local composition for theme state access throughout the app
 */
val LocalThemeState = staticCompositionLocalOf<ThemeState> { 
    error("ThemeState not provided") 
}

// ============================================================================
// MAIN THEME COMPOSABLE
// ============================================================================

/**
 * Main theme wrapper for GameClaimer app
 * 
 * @param themeState The current theme state
 * @param content The app content
 */
@Composable
fun GameClaimerTheme(
    themeState: ThemeState = remember { ThemeState() },
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()
    
    // Determine if we should use dark mode
    val useDarkTheme = when (themeState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDarkTheme
    }
    
    // Select appropriate theme variant (light or dark)
    val effectiveTheme = if (useDarkTheme && !themeState.currentTheme.isDark) {
        // If dark mode is on but current theme is light, get its dark variant
        AppTheme.getDarkVariant(themeState.currentTheme)
    } else if (!useDarkTheme && themeState.currentTheme.isDark) {
        // If light mode is on but current theme is dark, get its light variant
        AppTheme.getLightVariant(themeState.currentTheme)
    } else {
        themeState.currentTheme
    }
    
    val colorScheme = getColorScheme(effectiveTheme)
    
    // Update status bar
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveTheme.isDark
        }
    }
    
    CompositionLocalProvider(LocalThemeState provides themeState) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Legacy theme function for backward compatibility
 */
@Composable
fun GameClaimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val themeState = remember {
        ThemeState(
            initialTheme = if (darkTheme) AppTheme.MIDNIGHT else AppTheme.BUTTERY_YELLOW,
            initialMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT
        )
    }
    GameClaimerTheme(themeState = themeState, content = content)
}
