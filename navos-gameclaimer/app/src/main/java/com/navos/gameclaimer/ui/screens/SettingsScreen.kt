package com.navos.gameclaimer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.navos.gameclaimer.data.local.GameClaimerDatabase
import com.navos.gameclaimer.data.local.export.DataExportManager
import com.navos.gameclaimer.data.local.export.ExportResult
import com.navos.gameclaimer.data.local.export.ImportMode
import com.navos.gameclaimer.data.local.export.ImportResult
import kotlinx.coroutines.launch

/**
 * Settings screen for app configuration.
 * 
 * Phase 3: Basic UI structure
 * Phase 4: Export/Import data functionality
 * Phase 7+: Will integrate with WorkManager for background claiming
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
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
            title = { Text("Import Data") },
            text = { 
                Text(
                    "How would you like to import this data?\n\n" +
                    "• Merge: Add new data while keeping existing records\n" +
                    "• Replace: Clear existing data and import fresh"
                )
            },
            confirmButton = {
                TextButton(
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
                    TextButton(
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
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Claiming Section
            SettingsSection(title = "Claiming") {
                SwitchPreference(
                    title = "Auto-claim free games",
                    description = "Automatically claim games when available (requires authentication)",
                    checked = autoClaimEnabled,
                    onCheckedChange = { autoClaimEnabled = it },
                    enabled = false // Disabled until Phase 7
                )
            }
            
            // Notifications Section
            SettingsSection(title = "Notifications") {
                SwitchPreference(
                    title = "Game alerts",
                    description = "Notify when new free games are available",
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }
            
            // Data Management Section (Phase 4)
            SettingsSection(title = "Data Management") {
                // Export button
                DataActionButton(
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
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Import button
                DataActionButton(
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
            
            // Security Section
            SettingsSection(title = "Security & Privacy") {
                Text(
                    text = "• Credentials stored using Android Keystore encryption\n" +
                           "• Sessions are isolated per store\n" +
                           "• No data is sent to third parties\n" +
                           "• All network traffic uses HTTPS\n" +
                           "• Backups exclude sensitive credentials",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // About Section
            SettingsSection(title = "About") {
                Text(
                    text = "NavOS GameClaimer v0.1.0-alpha\n" +
                           "Phase 4: Local Data Portability\n" +
                           "© 2026 NavOS Project",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface 
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun DataActionButton(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = { if (!isLoading) onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
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
