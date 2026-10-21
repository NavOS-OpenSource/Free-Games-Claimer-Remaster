package com.navos.gameclaimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.navos.gameclaimer.ui.navigation.GameClaimerNavHost
import com.navos.gameclaimer.ui.theme.GameClaimerTheme

/**
 * NavOS GameClaimer Main Activity.
 * 
 * Single-activity architecture with Compose navigation.
 * All screens are managed through the NavHost.
 */
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            GameClaimerTheme {
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
