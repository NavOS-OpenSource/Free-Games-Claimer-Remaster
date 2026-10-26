package com.navos.gameclaimer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.export.DataExportManager
import com.navos.gameclaimer.data.local.export.ExportResult
import com.navos.gameclaimer.data.local.export.ImportMode
import com.navos.gameclaimer.data.local.export.ImportResult
import com.navos.gameclaimer.ui.components.*
import com.navos.gameclaimer.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Settings screen for app configuration.
 * 
 * Phase 22: Brutalist UI redesign with theme selection
 * Phase 4: Export/Import data functionality
 * Phase 7+: Will integrate with WorkManager for background claiming
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMultiAccountExperiment: () -> Unit = {},
    onNavigateToManualClaim: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Theme state
    val themeState = LocalThemeState.current
    val themePrefs = remember { ThemePreferences(context) }
    
    // Developer options state
    var developerOptionsEnabled by remember { mutableStateOf(themePrefs.developerOptionsEnabled) }
    var versionTapCount by remember { mutableStateOf(0) }
    val tapsToEnable = 7
    
    // Sync theme state with preferences
    LaunchedEffect(Unit) {
        themeState.currentTheme = themePrefs.selectedTheme
        themeState.themeMode = themePrefs.themeMode
    }
    
    var autoClaimEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    
    // Export/Import state
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    
    // Initialize export manager lazily
    val exportManager = remember {
        val database = GameClaimerDatabase.getInstance(context)
        DataExportManager(context, database)
    }
    
    // File picker for export - create JSON file
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                when (val result = exportManager.exportToUri(uri)) {
                    is ExportResult.Success -> {
                        statusMessage = "Exported ${result.stats.claimedGamesCount} games, ${result.stats.settingsCount} settings"
                        isError = false
                    }
                    is ExportResult.Error -> {
                        statusMessage = "Export failed: ${result.message}"
                        isError = true
                    }
                }
                isExporting = false
            }
        }
    }
    
    // File picker for import - open JSON file
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            showImportDialog = true
        }
    }
    
    // Import confirmation dialog
    if (showImportDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                pendingImportUri = null
            },
            title = { Text("Import Data", fontWeight = FontWeight.Bold) },
            text = { 
                Text(
                    "How would you like to import this data?\n\n" +
                    "• Merge: Add new data while keeping existing records\n" +
                    "• Replace: Clear existing data and import fresh"
                )
            },
            confirmButton = {
                BrutalistButton(
                    onClick = {
                        showImportDialog = false
                        val uri = pendingImportUri!!
                        pendingImportUri = null
                        isImporting = true
                        scope.launch {
                            when (val result = exportManager.importFromUri(uri, ImportMode.MERGE)) {
                                is ImportResult.Success -> {
                                    statusMessage = "Imported ${result.stats.claimedGamesImported} games, ${result.stats.settingsImported} settings (merged)"
                                    isError = false
                                }
                                is ImportResult.Error -> {
                                    statusMessage = "Import failed: ${result.message}"
                                    isError = true
                                }
                            }
                            isImporting = false
                        }
                    }
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showImportDialog = false
                            pendingImportUri = null
                        }
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    BrutalistOutlinedButton(
                        onClick = {
                            showImportDialog = false
                            val uri = pendingImportUri!!
                            pendingImportUri = null
                            isImporting = true
                            scope.launch {
                                when (val result = exportManager.importFromUri(uri, ImportMode.REPLACE)) {
                                    is ImportResult.Success -> {
                                        statusMessage = "Imported ${result.stats.claimedGamesImported} games, ${result.stats.settingsImported} settings (replaced)"
                                        isError = false
                                    }
                                    is ImportResult.Error -> {
                                        statusMessage = "Import failed: ${result.message}"
                                        isError = true
                                    }
                                }
                                isImporting = false
                            }
                        }
                    ) {
                        Text("Replace")
                    }
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            BrutalistTopAppBar(
                title = "Settings",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            // Status snackbar
            statusMessage?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                                     else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
                                   else MaterialTheme.colorScheme.onPrimaryContainer,
                    action = {
                        TextButton(onClick = { statusMessage = null }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance Section - NEW for Phase 22
            BrutalistSettingsSection(title = "Appearance") {
                // Theme Mode Selection
                BrutalistThemeModeSelector(
                    currentMode = themeState.themeMode,
                    onModeSelected = { mode ->
                        themeState.setMode(mode)
                        themePrefs.themeMode = mode
                    }
                )
                
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                
                // Theme Selection
                Text(
                    text = "Color Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                BrutalistThemeSelector(
                    currentTheme = themeState.currentTheme,
                    currentMode = themeState.themeMode,
                    onThemeSelected = { theme ->
                        themeState.setTheme(theme)
                        themePrefs.selectedTheme = theme
                    }
                )
                
                // Corner Radius Slider
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingMedium.dp))
                
                Text(
                    text = "UI Roundness",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                BrutalistCornerRadiusSlider(
                    currentRadius = themeState.cornerRadius,
                    onRadiusChange = { radius ->
                        themeState.cornerRadius = radius
                        themePrefs.cornerRadius = radius
                    }
                )
            }
            
            // Claiming Section
            BrutalistSettingsSection(title = "Claiming") {
                BrutalistSwitchPreference(
                    title = "Auto-claim free games",
                    description = "Automatically claim games when available (requires authentication)",
                    checked = autoClaimEnabled,
                    onCheckedChange = { autoClaimEnabled = it },
                    enabled = false // Disabled until Phase 7
                )
            }
            
            // Notifications Section
            BrutalistSettingsSection(title = "Notifications") {
                BrutalistSwitchPreference(
                    title = "Game alerts",
                    description = "Notify when new free games are available",
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }
            
            // Data Management Section
            BrutalistSettingsSection(title = "Data Management") {
                // Export button
                BrutalistDataActionButton(
                    title = "Export Data",
                    description = "Save claimed games and settings to a JSON file",
                    icon = Icons.Default.Upload,
                    isLoading = isExporting,
                    onClick = {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                            .format(java.util.Date())
                        exportLauncher.launch("gameclaimer_backup_$timestamp.json")
                    }
                )
                
                Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                
                // Import button
                BrutalistDataActionButton(
                    title = "Import Data",
                    description = "Restore data from a backup file",
                    icon = Icons.Default.Download,
                    isLoading = isImporting,
                    onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                )
                
                // Security note
                Text(
                    text = "Note: Session credentials are never included in exports for security.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // Developer Options Section (Debug builds only) - shown only when enabled
            if (com.navos.gameclaimer.BuildConfig.DEBUG && developerOptionsEnabled) {
                BrutalistSettingsSection(title = "Developer Options") {
                    BrutalistCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onClick = onNavigateToMultiAccountExperiment
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Multi-Account Experiment",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Test session backup/restore for Steam",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                    
                    // Manual Claim button
                    BrutalistCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onClick = onNavigateToManualClaim
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLink,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Manual Steam Claim",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Paste a Steam URL and claim free games",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
                    
                    // Disable Developer Options button
                    BrutalistCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onClick = {
                            developerOptionsEnabled = false
                            themePrefs.developerOptionsEnabled = false
                            statusMessage = "Developer options disabled"
                            isError = false
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Disable Developer Options",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            // Security & Privacy Section (MERGED)
            BrutalistSettingsSection(title = "Security & Privacy") {
                BrutalistCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "• Credentials stored using Android Keystore encryption\n" +
                               "• Sessions are isolated per store\n" +
                               "• No data is sent to third parties\n" +
                               "• All network traffic uses HTTPS\n" +
                               "• Backups exclude sensitive credentials",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // About Section - tap version info 7 times to enable developer options
            BrutalistSettingsSection(title = "About") {
                BrutalistCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = if (com.navos.gameclaimer.BuildConfig.DEBUG && !developerOptionsEnabled) {
                        {
                            versionTapCount++
                            val remaining = tapsToEnable - versionTapCount
                            if (remaining > 0 && remaining <= 3) {
                                statusMessage = "$remaining taps to enable developer options"
                                isError = false
                            } else if (versionTapCount >= tapsToEnable) {
                                developerOptionsEnabled = true
                                themePrefs.developerOptionsEnabled = true
                                versionTapCount = 0
                                statusMessage = "Developer options enabled!"
                                isError = false
                            }
                        }
                    } else null
                ) {
                    Text(
                        text = "NavOS GameClaimer v0.1.0-alpha\n" +
                               "Phase 22: Brutalist UI Redesign\n" +
                               "© 2026 NavOS Project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(BrutalistDesign.SpacingLarge.dp))
        }
    }
}

// ============================================================================
// BRUTALIST SETTINGS COMPONENTS
// ============================================================================

@Composable
private fun BrutalistSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = BrutalistDesign.SpacingSmall.dp)) {
        BrutalistSectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(BrutalistDesign.SpacingSmall.dp))
        content()
        BrutalistDivider(
            modifier = Modifier.padding(
                top = BrutalistDesign.SpacingMedium.dp,
                start = 16.dp,
                end = 16.dp
            )
        )
    }
}

@Composable
private fun BrutalistSwitchPreference(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    BrutalistCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun BrutalistDataActionButton(
    title: String,
    description: String,
    icon: ImageVector,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    BrutalistCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = if (!isLoading) onClick else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isLoading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// THEME SELECTION COMPONENTS
// ============================================================================

@Composable
private fun BrutalistThemeModeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Theme Mode",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                BrutalistModeChip(
                    label = when (mode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.SYSTEM -> "System"
                    },
                    icon = when (mode) {
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.DARK -> Icons.Default.DarkMode
                        ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                    },
                    isSelected = currentMode == mode,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BrutalistModeChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) 
        MaterialTheme.colorScheme.primaryContainer 
    else 
        MaterialTheme.colorScheme.surface
    
    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface
    
    val borderWidth = if (isSelected) 
        BrutalistDesign.BorderThick.dp 
    else 
        BrutalistDesign.BorderMedium.dp
    
    Box(
        modifier = modifier
            .border(
                width = borderWidth,
                color = MaterialTheme.colorScheme.outline,
                shape = currentBrutalistShape()
            )
            .background(containerColor, currentBrutalistShape())
            .clickable(onClick = onClick)
            .padding(BrutalistDesign.SpacingSmall.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
        }
    }
}

@Composable
private fun BrutalistThemeSelector(
    currentTheme: AppTheme,
    currentMode: ThemeMode,
    onThemeSelected: (AppTheme) -> Unit
) {
    // Filter themes based on mode - use ordered lists
    val availableThemes = when (currentMode) {
        ThemeMode.DARK -> AppTheme.darkThemes
        ThemeMode.LIGHT -> AppTheme.lightThemes
        ThemeMode.SYSTEM -> AppTheme.lightThemes + AppTheme.darkThemes
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(BrutalistDesign.SpacingSmall.dp)
    ) {
        availableThemes.forEach { theme ->
            BrutalistThemeCard(
                theme = theme,
                isSelected = currentTheme == theme,
                onClick = { onThemeSelected(theme) }
            )
        }
    }
}

@Composable
private fun BrutalistThemeCard(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = getColorScheme(theme)
    
    val borderWidth = if (isSelected) 
        BrutalistDesign.BorderThick.dp 
    else 
        BrutalistDesign.BorderMedium.dp
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = borderWidth,
                color = if (isSelected) colors.primary else MaterialTheme.colorScheme.outline,
                shape = currentBrutalistShape()
            )
            .background(MaterialTheme.colorScheme.surface, currentBrutalistShape())
            .clickable(onClick = onClick)
            .padding(BrutalistDesign.SpacingMedium.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color preview swatches
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ColorSwatch(color = colors.primary)
                ColorSwatch(color = colors.surface)
                ColorSwatch(color = colors.primaryContainer)
                ColorSwatch(color = colors.secondary)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Theme name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = if (theme.isDark) "Dark theme" else "Light theme",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    size: Int = 24
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = BrutalistShapes.None
            )
            .background(color)
    )
}

/**
 * Slider to control UI corner radius (boxy to rounded)
 */
@Composable
private fun BrutalistCornerRadiusSlider(
    currentRadius: Float,
    onRadiusChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Preview box showing current corner radius
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Boxy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Preview card
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(currentRadius.dp)
                    )
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(currentRadius.dp)
                    )
            )
            
            Text(
                text = "Rounded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Slider
        Slider(
            value = currentRadius,
            onValueChange = onRadiusChange,
            valueRange = 0f..24f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}
