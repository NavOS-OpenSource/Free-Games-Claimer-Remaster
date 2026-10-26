package com.navos.gameclaimer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navos.gameclaimer.R
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import kotlinx.coroutines.delay

/**
 * Splash screen with rotating tips and tricks about the app.
 * Shows while the app initializes.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val tips = listOf(
        "Go to Stores tab to connect your Steam account",
        "Tap on a free game card to claim it instantly",
        "DLC items require owning the base game first",
        "Pull down on Games tab to refresh giveaways",
        "Green dot means your store account is connected",
        "Check expiry dates - some deals end in hours!",
        "Claimed games appear directly in your Steam library",
        "Use the browser button to login securely"
    )
    
    var currentTipIndex by remember { mutableIntStateOf((0..tips.lastIndex).random()) }
    var tipVisible by remember { mutableStateOf(true) }
    
    // Rotate tips every 3 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            tipVisible = false
            delay(300) // Fade out duration
            currentTipIndex = (currentTipIndex + 1) % tips.size
            tipVisible = true
        }
    }
    
    // Auto-navigate after minimum splash time (2.5 seconds)
    LaunchedEffect(Unit) {
        delay(2500)
        onSplashComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // App icon
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "NavOS BeeClaimer",
                modifier = Modifier.size(160.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App name
            Text(
                text = "NavOS BeeClaimer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFC107) // Yellow
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tagline
            Text(
                text = "Never miss a free game",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFFFFC107),
                strokeWidth = 2.dp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Rotating tip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = tipVisible,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TIP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFC107),
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tips[currentTipIndex],
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
