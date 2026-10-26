package com.navos.gameclaimer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.entity.GiveawayEntity
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Full page giveaway detail screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiveawayDetailScreen(
    giveawayId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { GameClaimerDatabase.getInstance(context) }
    
    // Load giveaway by ID
    val giveaway by database.giveawayDao().getGiveawayById(giveawayId)
        .collectAsState(initial = null)
    
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }
    
    // Dynamic ambient color
    var dominantColor by remember { mutableStateOf(Color(0xFF1a1a1a)) }
    val surfaceColor = Color(0xFF0d0d0d)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // Black status bar background
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight)
                .background(Color.Black)
                .zIndex(10f)
        )
        
        if (giveaway == null) {
            // Loading or not found
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val item = giveaway!!
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero image - full width, natural height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    // Full HD image
                    item.imageUrl?.let { url ->
                        val fullImageUrl = url.replace("/offers/1/", "/offers/1b/")
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fullImageUrl)
                                .crossfade(true)
                                .allowHardware(false)
                                .listener(
                                    onSuccess = { _, result ->
                                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                        bitmap?.let {
                                            try {
                                                androidx.palette.graphics.Palette.from(it).generate { palette ->
                                                    palette?.let { p ->
                                                        val extracted = p.darkMutedSwatch?.rgb
                                                            ?: p.mutedSwatch?.rgb
                                                            ?: p.dominantSwatch?.rgb
                                                        extracted?.let { color ->
                                                            dominantColor = Color(color)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) { }
                                        }
                                    }
                                )
                                .build(),
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    } ?: Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Bottom gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        surfaceColor
                                    )
                                )
                            )
                    )
                }
                
                // Content section with ambient gradient
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    dominantColor.copy(alpha = 0.4f),
                                    surfaceColor
                                ),
                                startY = 0f,
                                endY = 400f
                            )
                        )
                        .padding(BrutalistDesign.SpacingMedium.dp)
                ) {
                    // Title
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Store badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = item.store,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Info cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Type
                        InfoChip(
                            icon = Icons.Outlined.Category,
                            label = item.type.ifEmpty { "Game" },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Price
                        InfoChip(
                            icon = Icons.Outlined.LocalOffer,
                            label = item.originalPrice?.let { "Worth $it" } ?: "Free",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Expiry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoChip(
                            icon = Icons.Outlined.Schedule,
                            label = formatEndTime(item.endTime, dateFormat),
                            modifier = Modifier.weight(1f),
                            tint = getEndTimeColor(item.endTime)
                        )
                        
                        // Status
                        InfoChip(
                            icon = if (item.isClaimed) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            label = if (item.isClaimed) "Claimed" else "Not claimed",
                            modifier = Modifier.weight(1f),
                            tint = if (item.isClaimed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Description
                    item.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Text(
                                text = "Description",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    
                    // Claim button
                    Button(
                        onClick = {
                            item.claimUrl?.let { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Claim Now",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        
        // Floating back button below status bar
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(top = statusBarHeight + 4.dp)
                .padding(start = 4.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = tint
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

private fun formatEndTime(endTime: Long?, dateFormat: SimpleDateFormat): String {
    if (endTime == null) return "No expiry"
    
    val now = System.currentTimeMillis()
    val remaining = endTime - now
    
    return when {
        remaining < 0 -> "Expired"
        remaining < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(remaining)
            "Ends in ${hours}h"
        }
        remaining < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(remaining)
            "Ends in ${days}d"
        }
        else -> dateFormat.format(Date(endTime))
    }
}

@Composable
private fun getEndTimeColor(endTime: Long?): Color {
    if (endTime == null) return MaterialTheme.colorScheme.onSurfaceVariant
    
    val remaining = endTime - System.currentTimeMillis()
    return when {
        remaining < 0 -> MaterialTheme.colorScheme.error
        remaining < TimeUnit.DAYS.toMillis(1) -> MaterialTheme.colorScheme.error
        remaining < TimeUnit.DAYS.toMillis(3) -> Color(0xFFFF9800) // Orange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
