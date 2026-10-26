package com.navos.gameclaimer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navos.gameclaimer.ui.theme.BrutalistDesign
import com.navos.gameclaimer.ui.theme.LocalThemeState

/**
 * NavOS BeeClaimer Brutalist UI Components
 * Phase 22: Boxy, strong-bordered components with consistent styling
 * 
 * Design principles:
 * - Strong visible borders
 * - Minimal corner rounding (boxy appearance)
 * - Flat design with minimal shadows
 * - High contrast
 */

// ============================================================================
// SHAPES
// ============================================================================

/**
 * Brutalist shapes - corner radius controlled by user preference
 */
object BrutalistShapes {
    val None = RoundedCornerShape(0.dp)
    val Small = RoundedCornerShape(BrutalistDesign.CornerSmall.dp)
    val Medium = RoundedCornerShape(BrutalistDesign.CornerMedium.dp)
}

/**
 * Get the current shape based on user's corner radius preference
 */
@Composable
fun currentBrutalistShape(): Shape {
    val themeState = LocalThemeState.current
    return RoundedCornerShape(themeState.cornerRadius.dp)
}

// ============================================================================
// BRUTALIST CARD
// ============================================================================

/**
 * A brutalist-styled card with strong borders
 */
@Composable
fun BrutalistCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = currentBrutalistShape(),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = BrutalistDesign.BorderMedium.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .clip(shape)
        .border(
            width = borderWidth,
            color = borderColor,
            shape = shape
        )
        .background(containerColor)
    
    if (onClick != null) {
        Column(
            modifier = cardModifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(color = contentColor.copy(alpha = 0.1f)),
                    onClick = onClick
                )
                .padding(BrutalistDesign.SpacingMedium.dp),
            content = content
        )
    } else {
        Column(
            modifier = cardModifier.padding(BrutalistDesign.SpacingMedium.dp),
            content = content
        )
    }
}

/**
 * A brutalist card with an icon header
 */
@Composable
fun BrutalistIconCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val iconShape = currentBrutalistShape()
    BrutalistCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon container with border
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        width = BrutalistDesign.BorderThin.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = iconShape
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = iconShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(BrutalistDesign.SpacingMedium.dp))
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Trailing content
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(BrutalistDesign.SpacingSmall.dp))
                trailingContent()
            }
        }
    }
}

// ============================================================================
// BRUTALIST BUTTON
// ============================================================================

/**
 * A brutalist-styled button with strong borders
 */
@Composable
fun BrutalistButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable RowScope.() -> Unit
) {
    val shape = currentBrutalistShape()
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        border = BorderStroke(BrutalistDesign.BorderMedium.dp, borderColor),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        content = content
    )
}

/**
 * A brutalist outlined button
 */
@Composable
fun BrutalistOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable RowScope.() -> Unit
) {
    val shape = currentBrutalistShape()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(BrutalistDesign.BorderMedium.dp, borderColor),
        content = content
    )
}

// ============================================================================
// BRUTALIST SECTION HEADER
// ============================================================================

/**
 * A section header with brutalist styling
 */
@Composable
fun BrutalistSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = BrutalistDesign.SpacingSmall.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title with bottom border accent
        Column {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(BrutalistDesign.BorderThick.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        if (action != null) {
            action()
        }
    }
}

// ============================================================================
// BRUTALIST DIVIDER
// ============================================================================

/**
 * A strong brutalist divider
 */
@Composable
fun BrutalistDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
    thickness: Dp = BrutalistDesign.BorderThin.dp
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

// ============================================================================
// BRUTALIST STATUS BADGE
// ============================================================================

/**
 * A status badge with brutalist styling
 */
@Composable
fun BrutalistBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    borderColor: Color = MaterialTheme.colorScheme.outline
) {
    val shape = currentBrutalistShape()
    Box(
        modifier = modifier
            .border(
                width = BrutalistDesign.BorderThin.dp,
                color = borderColor,
                shape = shape
            )
            .background(
                color = containerColor,
                shape = shape
            )
            .padding(
                horizontal = BrutalistDesign.SpacingSmall.dp,
                vertical = BrutalistDesign.SpacingXSmall.dp
            )
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = contentColor
        )
    }
}

// ============================================================================
// BRUTALIST CONTAINER
// ============================================================================

/**
 * A simple bordered container
 */
@Composable
fun BrutalistContainer(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = BrutalistDesign.BorderMedium.dp,
    shape: Shape? = null,  // null means use currentBrutalistShape()
    content: @Composable BoxScope.() -> Unit
) {
    val effectiveShape = shape ?: currentBrutalistShape()
    Box(
        modifier = modifier
            .clip(effectiveShape)
            .border(borderWidth, borderColor, effectiveShape)
            .background(containerColor)
            .padding(BrutalistDesign.SpacingMedium.dp),
        content = content
    )
}

// ============================================================================
// BRUTALIST TOP APP BAR
// ============================================================================

/**
 * A brutalist-styled top app bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalistTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = navigationIcon ?: {},
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
            // Use default windowInsets to handle status bar
        )
        // Strong bottom border
        BrutalistDivider(thickness = BrutalistDesign.BorderMedium.dp)
    }
}

// ============================================================================
// BRUTALIST SEARCH FIELD
// ============================================================================

/**
 * A brutalist-styled search field
 */
@Composable
fun BrutalistSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search..."
) {
    val shape = currentBrutalistShape()
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BrutalistDesign.SpacingMedium.dp, vertical = BrutalistDesign.SpacingSmall.dp)
            .border(
                width = BrutalistDesign.BorderMedium.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = shape
            ),
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear"
                    )
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        shape = shape
    )
}

// ============================================================================
// BRUTALIST FILTER CHIP
// ============================================================================

/**
 * A brutalist-styled filter chip
 */
@Composable
fun BrutalistFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val shape = currentBrutalistShape()
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        modifier = modifier,
        shape = shape,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = BrutalistDesign.BorderThin.dp,
            selectedBorderWidth = BrutalistDesign.BorderMedium.dp
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

// ============================================================================
// BRUTALIST STORE CARD
// ============================================================================

/**
 * A brutalist-styled store card for the stores list
 */
@Composable
fun BrutalistStoreCard(
    store: com.navos.gameclaimer.domain.model.GameStore,
    isAuthenticated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconShape = currentBrutalistShape()
    BrutalistCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Store icon placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        width = BrutalistDesign.BorderThin.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = iconShape
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = iconShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = store.displayName.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(BrutalistDesign.SpacingMedium.dp))
            
            // Store info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = store.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isAuthenticated) "Connected" else "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAuthenticated)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Status badge
            if (isAuthenticated) {
                BrutalistBadge(
                    text = "✓",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
