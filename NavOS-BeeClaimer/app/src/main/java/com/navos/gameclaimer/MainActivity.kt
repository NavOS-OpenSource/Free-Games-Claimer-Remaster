package com.navos.gameclaimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.navos.gameclaimer.ui.navigation.GameClaimerNavHost
import com.navos.gameclaimer.ui.theme.GameClaimerTheme
import com.navos.gameclaimer.ui.theme.ThemePreferences
import com.navos.gameclaimer.ui.theme.ThemeState

/**
 * NavOS GameClaimer Main Activity.
 * 
 * Phase 22: Brutalist UI with custom theme support
 * 
 * Single-activity architecture with Compose navigation.
 * All screens are managed through the NavHost.
 * Theme state is persisted via SharedPreferences.
 */
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            // Load saved theme preferences
            val themePrefs = remember { ThemePreferences(this) }
            val themeState = remember {
                ThemeState(
                    initialTheme = themePrefs.selectedTheme,
                    initialMode = themePrefs.themeMode,
                    initialCornerRadius = themePrefs.cornerRadius
                )
            }
            
            GameClaimerTheme(themeState = themeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameClaimerNavHost()
                }
            }
        }
    }
}
