package com.navos.gameclaimer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NavOS GameClaimer Color System
 * Phase 22: Brutalist UI with strong borders and distinctive themes
 * 
 * Design principles:
 * - High contrast borders for brutalist aesthetic
 * - Distinctive theme palettes with attractive names
 * - Consistent surface hierarchy
 */

// ============================================================================
// BUTTERY YELLOW THEME (Default Light Theme)
// A warm, inviting palette with creamy yellows and rich contrasts
// ============================================================================
object ButteryYellowColors {
    val Primary = Color(0xFF1A1A1A)           // Near-black for strong contrast
    val OnPrimary = Color(0xFFFFF8DC)         // Cornsilk text
    val PrimaryContainer = Color(0xFFFFF3B0)  // Soft buttery yellow
    val OnPrimaryContainer = Color(0xFF1A1A1A)
    
    val Secondary = Color(0xFF5D4E37)         // Warm brown
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFFFE4B5) // Moccasin
    val OnSecondaryContainer = Color(0xFF2D2416)
    
    val Tertiary = Color(0xFF6B5B4E)          // Taupe
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFD4C4A8)
    val OnTertiaryContainer = Color(0xFF241D13)
    
    val Background = Color(0xFFFFFBF0)        // Warm off-white
    val OnBackground = Color(0xFF1A1A1A)
    val Surface = Color(0xFFFFF8DC)           // Cornsilk - main cards
    val OnSurface = Color(0xFF1A1A1A)
    val SurfaceVariant = Color(0xFFFFEFB5)    // Slightly darker yellow
    val OnSurfaceVariant = Color(0xFF4A4236)
    
    val Outline = Color(0xFF1A1A1A)           // Strong black borders (brutalist)
    val OutlineVariant = Color(0xFF8B7355)    // Softer border for secondary elements
    
    val Error = Color(0xFFB00020)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFCD8DF)
    val OnErrorContainer = Color(0xFF370617)
    
    val InverseSurface = Color(0xFF1A1A1A)
    val InverseOnSurface = Color(0xFFFFF8DC)
    val InversePrimary = Color(0xFFFFD700)    // Gold
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// BUTTERY YELLOW DARK THEME
// Pure black with golden yellow + off-white only (3 color palette)
// ============================================================================
object ButteryYellowDarkColors {
    val Primary = Color(0xFFFFD54F)           // Golden yellow
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Color(0xFF3D3500)  // Dark golden
    val OnPrimaryContainer = Color(0xFFFFE082)
    
    val Secondary = Color(0xFFE0D6B8)         // Warm off-white
    val OnSecondary = Color(0xFF000000)
    val SecondaryContainer = Color(0xFF1A1A10)
    val OnSecondaryContainer = Color(0xFFE0D6B8)
    
    val Tertiary = Color(0xFFFFD54F)          // Same golden yellow
    val OnTertiary = Color(0xFF000000)
    val TertiaryContainer = Color(0xFF2A2500)
    val OnTertiaryContainer = Color(0xFFFFE082)
    
    val Background = Color(0xFF000000)        // Pure black
    val OnBackground = Color(0xFFE0D6B8)
    val Surface = Color(0xFF000000)           // Pure black
    val OnSurface = Color(0xFFE0D6B8)
    val SurfaceVariant = Color(0xFF0D0D08)    // Near black
    val OnSurfaceVariant = Color(0xFFB0A890)
    
    val Outline = Color(0xFFFFD54F)           // Golden borders
    val OutlineVariant = Color(0xFF5D5530)
    
    val Error = Color(0xFFFF6B6B)
    val OnError = Color(0xFF000000)
    val ErrorContainer = Color(0xFF4A1515)
    val OnErrorContainer = Color(0xFFFFCCCC)
    
    val InverseSurface = Color(0xFFE8E0D0)
    val InverseOnSurface = Color(0xFF1A1710)
    val InversePrimary = Color(0xFF5D4E37)
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// MIDNIGHT DARK THEME
// A deep, mysterious dark theme with subtle purple undertones
// ============================================================================
object MidnightColors {
    val Primary = Color(0xFFE0E0E0)           // Light gray for readability
    val OnPrimary = Color(0xFF1A1A2E)
    val PrimaryContainer = Color(0xFF16213E)  // Deep navy
    val OnPrimaryContainer = Color(0xFFE8E8E8)
    
    val Secondary = Color(0xFF7B68EE)         // Medium slate blue
    val OnSecondary = Color(0xFF0F0F1A)
    val SecondaryContainer = Color(0xFF252547)
    val OnSecondaryContainer = Color(0xFFD4CCFF)
    
    val Tertiary = Color(0xFF64B5F6)          // Light blue accent
    val OnTertiary = Color(0xFF0A1929)
    val TertiaryContainer = Color(0xFF1A3A5C)
    val OnTertiaryContainer = Color(0xFFB3E0FF)
    
    val Background = Color(0xFF0F0F1A)        // Very dark blue-black
    val OnBackground = Color(0xFFE0E0E0)
    val Surface = Color(0xFF1A1A2E)           // Dark purple-navy
    val OnSurface = Color(0xFFE0E0E0)
    val SurfaceVariant = Color(0xFF252547)    // Slightly lighter
    val OnSurfaceVariant = Color(0xFFB0B0C0)
    
    val Outline = Color(0xFFE0E0E0)           // Strong light borders (brutalist)
    val OutlineVariant = Color(0xFF4A4A6A)
    
    val Error = Color(0xFFFF6B6B)
    val OnError = Color(0xFF1A0A0A)
    val ErrorContainer = Color(0xFF4A1515)
    val OnErrorContainer = Color(0xFFFFCCCC)
    
    val InverseSurface = Color(0xFFE0E0E0)
    val InverseOnSurface = Color(0xFF1A1A2E)
    val InversePrimary = Color(0xFF4A4A8A)
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// FOREST MOSS THEME
// Earthy greens with organic warmth
// ============================================================================
object ForestMossColors {
    val Primary = Color(0xFF1B2E1B)           // Deep forest green
    val OnPrimary = Color(0xFFE8F5E8)
    val PrimaryContainer = Color(0xFFC8E6C9)  // Light green
    val OnPrimaryContainer = Color(0xFF0D1A0D)
    
    val Secondary = Color(0xFF5D4037)         // Brown bark
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFD7CCC8)
    val OnSecondaryContainer = Color(0xFF2E1F17)
    
    val Tertiary = Color(0xFF827717)          // Olive
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFF0F4C3)
    val OnTertiaryContainer = Color(0xFF3A3509)
    
    val Background = Color(0xFFF1F8E9)        // Very light green
    val OnBackground = Color(0xFF1B2E1B)
    val Surface = Color(0xFFE8F5E9)           // Light moss
    val OnSurface = Color(0xFF1B2E1B)
    val SurfaceVariant = Color(0xFFC8E6C9)
    val OnSurfaceVariant = Color(0xFF3E5040)
    
    val Outline = Color(0xFF1B2E1B)           // Strong borders
    val OutlineVariant = Color(0xFF6B8E6B)
    
    val Error = Color(0xFFB71C1C)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFCDD2)
    val OnErrorContainer = Color(0xFF4A0D0D)
    
    val InverseSurface = Color(0xFF1B2E1B)
    val InverseOnSurface = Color(0xFFE8F5E8)
    val InversePrimary = Color(0xFF81C784)
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// FOREST MOSS DARK THEME
// Pure black with green + light green only (3 color palette)
// ============================================================================
object ForestMossDarkColors {
    val Primary = Color(0xFF81C784)           // Soft green
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Color(0xFF1B3D1B)  // Dark forest
    val OnPrimaryContainer = Color(0xFFA5D6A7)
    
    val Secondary = Color(0xFFC8E6C9)         // Light green (no brown)
    val OnSecondary = Color(0xFF000000)
    val SecondaryContainer = Color(0xFF0D1A0D)
    val OnSecondaryContainer = Color(0xFFC8E6C9)
    
    val Tertiary = Color(0xFF81C784)          // Same soft green
    val OnTertiary = Color(0xFF000000)
    val TertiaryContainer = Color(0xFF1B2E1B)
    val OnTertiaryContainer = Color(0xFFA5D6A7)
    
    val Background = Color(0xFF000000)        // Pure black
    val OnBackground = Color(0xFFC8E6C9)
    val Surface = Color(0xFF000000)           // Pure black
    val OnSurface = Color(0xFFC8E6C9)
    val SurfaceVariant = Color(0xFF0A150A)    // Near black
    val OnSurfaceVariant = Color(0xFF90A890)
    
    val Outline = Color(0xFF81C784)           // Green borders
    val OutlineVariant = Color(0xFF4A6B4A)
    
    val Error = Color(0xFFFF6B6B)
    val OnError = Color(0xFF000000)
    val ErrorContainer = Color(0xFF4A1515)
    val OnErrorContainer = Color(0xFFFFCCCC)
    
    val InverseSurface = Color(0xFFD0E8D0)
    val InverseOnSurface = Color(0xFF0D1A0D)
    val InversePrimary = Color(0xFF2E7D32)
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// ARCTIC FROST THEME
// Cool, crisp blues with icy undertones
// ============================================================================
object ArcticFrostColors {
    val Primary = Color(0xFF0D47A1)           // Deep blue
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFBBDEFB)  // Light blue
    val OnPrimaryContainer = Color(0xFF062043)
    
    val Secondary = Color(0xFF455A64)         // Blue gray
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFCFD8DC)
    val OnSecondaryContainer = Color(0xFF1C2A30)
    
    val Tertiary = Color(0xFF00695C)          // Teal
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFB2DFDB)
    val OnTertiaryContainer = Color(0xFF00302B)
    
    val Background = Color(0xFFF5F9FC)        // Ice white
    val OnBackground = Color(0xFF0D1B2A)
    val Surface = Color(0xFFECF3F9)           // Light frost
    val OnSurface = Color(0xFF0D1B2A)
    val SurfaceVariant = Color(0xFFD6E5F0)
    val OnSurfaceVariant = Color(0xFF384854)
    
    val Outline = Color(0xFF0D1B2A)           // Strong borders
    val OutlineVariant = Color(0xFF607D8B)
    
    val Error = Color(0xFFC62828)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFCDD2)
    val OnErrorContainer = Color(0xFF4A1010)
    
    val InverseSurface = Color(0xFF0D1B2A)
    val InverseOnSurface = Color(0xFFECF3F9)
    val InversePrimary = Color(0xFF90CAF9)
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// ARCTIC FROST DARK THEME
// Pure black with teal + cool white only (3 color palette)
// ============================================================================
object ArcticFrostDarkColors {
    val Primary = Color(0xFF80CBC4)           // Teal accent
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Color(0xFF004D40)  // Dark teal
    val OnPrimaryContainer = Color(0xFFB2DFDB)
    
    val Secondary = Color(0xFFE0E8EC)         // Cool white
    val OnSecondary = Color(0xFF000000)
    val SecondaryContainer = Color(0xFF101418)
    val OnSecondaryContainer = Color(0xFFE0E8EC)
    
    val Tertiary = Color(0xFF80CBC4)          // Same teal
    val OnTertiary = Color(0xFF000000)
    val TertiaryContainer = Color(0xFF00332B)
    val OnTertiaryContainer = Color(0xFFB2DFDB)
    
    val Background = Color(0xFF000000)        // Pure black
    val OnBackground = Color(0xFFE0E8EC)
    val Surface = Color(0xFF000000)           // Pure black
    val OnSurface = Color(0xFFE0E8EC)
    val SurfaceVariant = Color(0xFF080C0E)    // Near black
    val OnSurfaceVariant = Color(0xFF90A0A8)
    
    val Outline = Color(0xFF80CBC4)           // Teal borders
    val OutlineVariant = Color(0xFF4A5B63)
    
    val Error = Color(0xFFFF6B6B)
    val OnError = Color(0xFF000000)
    val ErrorContainer = Color(0xFF4A1515)
    val OnErrorContainer = Color(0xFFFFCCCC)
    
    val InverseSurface = Color(0xFFD0D8DC)
    val InverseOnSurface = Color(0xFF0A1214)
    val InversePrimary = Color(0xFF00695C)
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// SUNSET EMBER THEME
// Warm oranges and deep reds
// ============================================================================
object SunsetEmberColors {
    val Primary = Color(0xFF3E2723)           // Deep brown
    val OnPrimary = Color(0xFFFFF3E0)
    val PrimaryContainer = Color(0xFFFFCC80)  // Light orange
    val OnPrimaryContainer = Color(0xFF1F1310)
    
    val Secondary = Color(0xFFBF360C)         // Deep orange
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFFFAB91)
    val OnSecondaryContainer = Color(0xFF4A1507)
    
    val Tertiary = Color(0xFF880E4F)          // Deep pink
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFF8BBD0)
    val OnTertiaryContainer = Color(0xFF3A0620)
    
    val Background = Color(0xFFFFF8F0)        // Warm white
    val OnBackground = Color(0xFF2D1810)
    val Surface = Color(0xFFFFF3E0)           // Light cream
    val OnSurface = Color(0xFF2D1810)
    val SurfaceVariant = Color(0xFFFFE0B2)
    val OnSurfaceVariant = Color(0xFF5D4037)
    
    val Outline = Color(0xFF2D1810)           // Strong borders
    val OutlineVariant = Color(0xFF8D6E63)
    
    val Error = Color(0xFFB71C1C)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFCDD2)
    val OnErrorContainer = Color(0xFF4A0D0D)
    
    val InverseSurface = Color(0xFF2D1810)
    val InverseOnSurface = Color(0xFFFFF3E0)
    val InversePrimary = Color(0xFFFFB74D)
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// SUNSET EMBER DARK THEME
// Pure black with amber + warm white only (3 color palette)
// ============================================================================
object SunsetEmberDarkColors {
    val Primary = Color(0xFFFFB74D)           // Warm amber
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Color(0xFF3D2500)  // Dark amber
    val OnPrimaryContainer = Color(0xFFFFCC80)
    
    val Secondary = Color(0xFFFFF3E0)         // Warm white
    val OnSecondary = Color(0xFF000000)
    val SecondaryContainer = Color(0xFF1A1510)
    val OnSecondaryContainer = Color(0xFFFFF3E0)
    
    val Tertiary = Color(0xFFFFB74D)          // Same amber
    val OnTertiary = Color(0xFF000000)
    val TertiaryContainer = Color(0xFF2A1D00)
    val OnTertiaryContainer = Color(0xFFFFCC80)
    
    val Background = Color(0xFF000000)        // Pure black
    val OnBackground = Color(0xFFFFF3E0)
    val Surface = Color(0xFF000000)           // Pure black
    val OnSurface = Color(0xFFFFF3E0)
    val SurfaceVariant = Color(0xFF0D0A05)    // Near black
    val OnSurfaceVariant = Color(0xFFB0A090)
    
    val Outline = Color(0xFFFFB74D)           // Amber borders
    val OutlineVariant = Color(0xFF5D4D30)
    
    val Error = Color(0xFFFF6B6B)
    val OnError = Color(0xFF000000)
    val ErrorContainer = Color(0xFF4A1515)
    val OnErrorContainer = Color(0xFFFFCCCC)
    
    val InverseSurface = Color(0xFFE8D8D0)
    val InverseOnSurface = Color(0xFF1A0D08)
    val InversePrimary = Color(0xFFBF360C)
    
    val Scrim = Color(0xFF000000)
}

// ============================================================================
// BRUTALIST DESIGN TOKENS
// Consistent spacing and border widths for the brutalist aesthetic
// ============================================================================
object BrutalistDesign {
    // Border widths - thicker for that brutalist feel
    val BorderThin = 1.5f     // dp - subtle borders
    val BorderMedium = 2.5f   // dp - standard borders
    val BorderThick = 4f      // dp - emphasis borders
    
    // Corner radii - minimal for boxy appearance
    val CornerNone = 0f       // dp - pure brutalist
    val CornerSmall = 4f      // dp - slight softening
    val CornerMedium = 8f     // dp - moderate
    
    // Elevation - brutalist prefers flat with borders over shadows
    val ElevationNone = 0f    // dp
    val ElevationSubtle = 1f  // dp
    val ElevationMedium = 2f  // dp
    
    // Spacing - consistent grid
    val SpacingXSmall = 4     // dp
    val SpacingSmall = 8      // dp
    val SpacingMedium = 16    // dp
    val SpacingLarge = 24     // dp
    val SpacingXLarge = 32    // dp
}
