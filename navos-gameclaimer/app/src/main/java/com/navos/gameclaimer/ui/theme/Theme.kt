package com.navos.gameclaimer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// NavOS GameClaimer brand colors
private val PrimaryLight = Color(0xFF6750A4)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFE9DDFF)
private val OnPrimaryContainerLight = Color(0xFF22005D)

private val SecondaryLight = Color(0xFF625B71)
private val OnSecondaryLight = Color(0xFFFFFFFF)
private val SecondaryContainerLight = Color(0xFFE8DEF8)
private val OnSecondaryContainerLight = Color(0xFF1E192B)

private val TertiaryLight = Color(0xFF7E5260)
private val OnTertiaryLight = Color(0xFFFFFFFF)
private val TertiaryContainerLight = Color(0xFFFFD9E3)
private val OnTertiaryContainerLight = Color(0xFF31101D)

private val BackgroundLight = Color(0xFFFFFBFF)
private val OnBackgroundLight = Color(0xFF1C1B1E)
private val SurfaceLight = Color(0xFFFFFBFF)
private val OnSurfaceLight = Color(0xFF1C1B1E)

private val PrimaryDark = Color(0xFFCFBCFF)
private val OnPrimaryDark = Color(0xFF381E72)
private val PrimaryContainerDark = Color(0xFF4F378A)
private val OnPrimaryContainerDark = Color(0xFFE9DDFF)

private val SecondaryDark = Color(0xFFCBC2DB)
private val OnSecondaryDark = Color(0xFF332D41)
private val SecondaryContainerDark = Color(0xFF4A4458)
private val OnSecondaryContainerDark = Color(0xFFE8DEF8)

private val TertiaryDark = Color(0xFFEFB8C8)
private val OnTertiaryDark = Color(0xFF4A2532)
private val TertiaryContainerDark = Color(0xFF633B48)
private val OnTertiaryContainerDark = Color(0xFFFFD9E3)

private val BackgroundDark = Color(0xFF1C1B1E)
private val OnBackgroundDark = Color(0xFFE6E1E6)
private val SurfaceDark = Color(0xFF1C1B1E)
private val OnSurfaceDark = Color(0xFFE6E1E6)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark
)

@Composable
fun GameClaimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primaryContainer.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
