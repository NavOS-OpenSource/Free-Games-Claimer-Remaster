package com.navos.gameclaimer.browser

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Phase 1 Multi-Account Prototype: Session Backup Manager
 * 
 * DEBUG-ONLY: This class handles backup and restoration of browser session state
 * for Steam authentication isolation testing.
 * 
 * SECURITY ARCHITECTURE:
 * - All session data is encrypted using Android Keystore AES-GCM
 * - Session files are stored in app-private directory with restrictive permissions
 * - No raw session data is ever logged or exposed
 * - Mutex ensures atomic backup/restore operations
 * 
 * BROWSER STATE CAPTURED:
 * 1. Cookies (CookieManager) - Primary authentication state
 * 2. LocalStorage - Some stores use this for session tokens
 * 3. IndexedDB - Some stores persist data here
 * 4. Cache identification only (not backed up - rebuilt on use)
 * 
 * LIMITATIONS:
 * - IndexedDB backup requires JavaScript execution in a WebView
 * - localStorage backup requires JavaScript execution in a WebView
 * - Some stores may have additional stateful tokens we can't capture
 * - Process death may corrupt partial backups (mitigated via temp files)
 */
class SessionBackupManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "SessionBackupManager"
        private const val KEYSTORE_ALIAS = "NavOS_SessionBackup_Key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val BACKUP_DIR = "session_backups"
        private const val FILE_EXTENSION = ".enc"
        
        // Steam domains we need to capture cookies for
        private val STEAM_COOKIE_DOMAINS = listOf(
            "https://store.steampowered.com",
            "https://steamcommunity.com",
            "https://steampowered.com",
            "https://help.steampowered.com",
            "https://login.steampowered.com"
        )
        
        // Version for backup format compatibility
        private const val BACKUP_VERSION = 1
    }
    
    // Mutex to prevent concurrent backup/restore operations
    private val operationMutex = Mutex()
    
    // Track if a switch is in progress (for navigation blocking)
    @Volatile
    var isSwitchInProgress: Boolean = false
        private set
    
    /**
     * Result of a backup operation.
     */
    sealed class BackupResult {
        data class Success(
            val accountId: String,
            val timestamp: Long,
            val cookieCount: Int,
            val hasLocalStorage: Boolean,
            val hasIndexedDb: Boolean
        ) : BackupResult()
        
        data class Failed(val reason: String, val exception: Exception? = null) : BackupResult()
    }
    
    /**
     * Result of a restore operation.
     */
    sealed class RestoreResult {
        data class Success(
            val accountId: String,
            val cookiesRestored: Int,
            val localStorageRestored: Boolean,
            val indexedDbRestored: Boolean
        ) : RestoreResult()
        
        data class Failed(val reason: String, val exception: Exception? = null) : RestoreResult()
        data object NoBackupFound : RestoreResult()
        data object BackupExpired : RestoreResult()
        data object BackupCorrupted : RestoreResult()
    }
    
    /**
     * Result of session verification.
     */
    sealed class VerificationResult {
        data class Authenticated(
            val accountId: String,
            val displayName: String?,
            val steamId: String?
        ) : VerificationResult()
        
        data object NotAuthenticated : VerificationResult()
        data class Failed(val reason: String) : VerificationResult()
    }
    
    /**
     * Backup current Steam session for the given account ID.
     * 
     * @param accountId Unique identifier for this account's backup
     * @param webView Optional WebView to capture localStorage/IndexedDB from
     * @return BackupResult indicating success or failure
     */
    suspend fun backupSteamSession(
        accountId: String,
        webView: WebView? = null
    ): BackupResult = operationMutex.withLock {
        return@withLock withContext(Dispatchers.IO) {
            try {
                isSwitchInProgress = true
                Log.d(TAG, "Starting backup for account: ${accountId.take(8)}...")
                
                // 1. Capture cookies for all Steam domains
                val cookies = captureSteamCookies()
                if (cookies.isEmpty()) {
                    return@withContext BackupResult.Failed("No Steam cookies found")
                }
                
                // 2. Capture localStorage if WebView provided
                val localStorage = webView?.let { captureLocalStorage(it) } ?: emptyMap()
                
                // 3. Capture IndexedDB metadata (full backup not practical)
                val indexedDbInfo = webView?.let { captureIndexedDbInfo(it) }
                
                // 4. Create backup JSON structure
                val backupJson = JSONObject().apply {
                    put("version", BACKUP_VERSION)
                    put("accountId", accountId)
                    put("timestamp", System.currentTimeMillis())
                    put("store", "STEAM")
                    
                    // Cookies array
                    put("cookies", JSONArray().apply {
                        cookies.forEach { (domain, cookieString) ->
                            put(JSONObject().apply {
                                put("domain", domain)
                                put("cookies", cookieString)
                            })
                        }
                    })
                    
                    // LocalStorage
                    if (localStorage.isNotEmpty()) {
                        put("localStorage", JSONObject().apply {
                            localStorage.forEach { (key, value) ->
                                put(key, value)
                            }
                        })
                    }
                    
                    // IndexedDB info (not full data)
                    indexedDbInfo?.let {
                        put("indexedDbInfo", it)
                    }
                }
                
                // 5. Encrypt and save
                val encryptedData = encryptData(backupJson.toString())
                val backupFile = getBackupFile(accountId)
                
                // Write to temp file first, then rename (atomic)
                val tempFile = File(backupFile.parent, "${backupFile.name}.tmp")
                tempFile.writeBytes(encryptedData)
                tempFile.renameTo(backupFile)
                
                Log.d(TAG, "Backup complete: ${cookies.size} cookie domains, localStorage=${localStorage.isNotEmpty()}")
                
                BackupResult.Success(
                    accountId = accountId,
                    timestamp = System.currentTimeMillis(),
                    cookieCount = cookies.values.sumOf { it.split(";").size },
                    hasLocalStorage = localStorage.isNotEmpty(),
                    hasIndexedDb = indexedDbInfo != null
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                BackupResult.Failed("Backup failed: ${e.message}", e)
            } finally {
                isSwitchInProgress = false
            }
        }
    }
    
    /**
     * Restore a previously backed up Steam session.
     * 
     * @param accountId The account ID to restore
     * @param webView Optional WebView to restore localStorage to
     * @return RestoreResult indicating success or failure
     */
    suspend fun restoreSteamSession(
        accountId: String,
        webView: WebView? = null
    ): RestoreResult = operationMutex.withLock {
        return@withLock withContext(Dispatchers.IO) {
            try {
                isSwitchInProgress = true
                Log.d(TAG, "Starting restore for account: ${accountId.take(8)}...")
                
                val backupFile = getBackupFile(accountId)
                if (!backupFile.exists()) {
                    return@withContext RestoreResult.NoBackupFound
                }
                
                // 1. Read and decrypt backup
                val encryptedData = backupFile.readBytes()
                val decryptedJson = try {
                    decryptData(encryptedData)
                } catch (e: Exception) {
                    Log.e(TAG, "Decryption failed", e)
                    return@withContext RestoreResult.BackupCorrupted
                }
                
                val backup = try {
                    JSONObject(decryptedJson)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parse failed", e)
                    return@withContext RestoreResult.BackupCorrupted
                }
                
                // 2. Validate backup version
                val version = backup.optInt("version", 0)
                if (version != BACKUP_VERSION) {
                    Log.w(TAG, "Backup version mismatch: $version != $BACKUP_VERSION")
                    // For now, reject incompatible versions
                    return@withContext RestoreResult.BackupCorrupted
                }
                
                // 3. Check if backup is expired (24 hours for prototype)
                val timestamp = backup.optLong("timestamp", 0)
                val maxAge = 24 * 60 * 60 * 1000L // 24 hours
                if (System.currentTimeMillis() - timestamp > maxAge) {
                    Log.w(TAG, "Backup expired: ${System.currentTimeMillis() - timestamp}ms old")
                    return@withContext RestoreResult.BackupExpired
                }
                
                // 4. Clear current Steam cookies first
                clearSteamCookies()
                
                // 5. Restore cookies
                val cookiesArray = backup.optJSONArray("cookies") ?: JSONArray()
                var cookiesRestored = 0
                
                withContext(Dispatchers.Main) {
                    val cookieManager = CookieManager.getInstance()
                    for (i in 0 until cookiesArray.length()) {
                        val domainEntry = cookiesArray.getJSONObject(i)
                        val domain = domainEntry.getString("domain")
                        val cookieString = domainEntry.getString("cookies")
                        
                        // Set each cookie individually
                        cookieString.split(";").forEach { cookie ->
                            val trimmed = cookie.trim()
                            if (trimmed.isNotEmpty()) {
                                cookieManager.setCookie(domain, trimmed)
                                cookiesRestored++
                            }
                        }
                    }
                    cookieManager.flush()
                }
                
                // 6. Restore localStorage if WebView provided
                var localStorageRestored = false
                val localStorageObj = backup.optJSONObject("localStorage")
                if (webView != null && localStorageObj != null) {
                    localStorageRestored = restoreLocalStorage(webView, localStorageObj)
                }
                
                Log.d(TAG, "Restore complete: $cookiesRestored cookies, localStorage=$localStorageRestored")
                
                RestoreResult.Success(
                    accountId = accountId,
                    cookiesRestored = cookiesRestored,
                    localStorageRestored = localStorageRestored,
                    indexedDbRestored = false // Not implemented in prototype
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                RestoreResult.Failed("Restore failed: ${e.message}", e)
            } finally {
                isSwitchInProgress = false
            }
        }
    }
    
    /**
     * Clear all Steam session data without restoring anything.
     */
    suspend fun clearSteamSession(): Boolean = operationMutex.withLock {
        return@withLock withContext(Dispatchers.Main) {
            try {
                isSwitchInProgress = true
                clearSteamCookies()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Clear failed", e)
                false
            } finally {
                isSwitchInProgress = false
            }
        }
    }
    
    /**
     * List all available backups for Steam.
     */
    fun listBackups(): List<BackupInfo> {
        val backupDir = getBackupDir()
        if (!backupDir.exists()) return emptyList()
        
        return backupDir.listFiles()
            ?.filter { it.name.endsWith(FILE_EXTENSION) }
            ?.mapNotNull { file ->
                try {
                    val accountId = file.nameWithoutExtension.removeSuffix("_steam")
                    BackupInfo(
                        accountId = accountId,
                        timestamp = file.lastModified(),
                        sizeBytes = file.length()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }
    
    /**
     * Delete a specific backup.
     */
    fun deleteBackup(accountId: String): Boolean {
        val file = getBackupFile(accountId)
        return file.delete()
    }
    
    /**
     * Delete all backups.
     */
    fun deleteAllBackups(): Int {
        val backupDir = getBackupDir()
        if (!backupDir.exists()) return 0
        
        var deleted = 0
        backupDir.listFiles()?.forEach { file ->
            if (file.delete()) deleted++
        }
        return deleted
    }
    
    // ===== Cookie Capture/Restore =====
    
    private fun captureSteamCookies(): Map<String, String> {
        val cookieManager = CookieManager.getInstance()
        val result = mutableMapOf<String, String>()
        
        STEAM_COOKIE_DOMAINS.forEach { domain ->
            val cookies = cookieManager.getCookie(domain)
            if (!cookies.isNullOrEmpty()) {
                result[domain] = cookies
            }
        }
        
        return result
    }
    
    private suspend fun clearSteamCookies() {
        withContext(Dispatchers.Main) {
            val cookieManager = CookieManager.getInstance()
            
            // Unfortunately, CookieManager doesn't support per-domain removal in older APIs
            // We need to remove all cookies and restore non-Steam ones
            // For the prototype, we'll accept this limitation and document it
            
            // NOTE: This affects ALL stores - documented limitation
            cookieManager.removeAllCookies { }
            cookieManager.flush()
        }
    }
    
    // ===== LocalStorage Capture/Restore =====
    
    private suspend fun captureLocalStorage(webView: WebView): Map<String, String> {
        return withContext(Dispatchers.Main) {
            val result = mutableMapOf<String, String>()
            
            // This requires the WebView to be on a Steam page
            // We execute JavaScript to extract localStorage
            val js = """
                (function() {
                    var data = {};
                    for (var i = 0; i < localStorage.length; i++) {
                        var key = localStorage.key(i);
                        data[key] = localStorage.getItem(key);
                    }
                    return JSON.stringify(data);
                })();
            """.trimIndent()
            
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                webView.evaluateJavascript(js) { jsonResult ->
                    try {
                        if (jsonResult != null && jsonResult != "null") {
                            val cleaned = jsonResult.trim('"').replace("\\\"", "\"")
                            val obj = JSONObject(cleaned)
                            obj.keys().forEach { key ->
                                result[key] = obj.getString(key)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "localStorage capture failed: ${e.message}")
                    }
                    continuation.resumeWith(Result.success(result))
                }
            }
        }
    }
    
    private suspend fun restoreLocalStorage(webView: WebView, data: JSONObject): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                // Build JavaScript to restore localStorage
                val jsBuilder = StringBuilder("(function() { ")
                data.keys().forEach { key ->
                    val value = data.getString(key)
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                    jsBuilder.append("localStorage.setItem('$key', '$value'); ")
                }
                jsBuilder.append("return true; })();")
                
                webView.evaluateJavascript(jsBuilder.toString()) { }
                true
            } catch (e: Exception) {
                Log.w(TAG, "localStorage restore failed: ${e.message}")
                false
            }
        }
    }
    
    // ===== IndexedDB Info =====
    
    private suspend fun captureIndexedDbInfo(webView: WebView): String? {
        return withContext(Dispatchers.Main) {
            // Just capture database names - full backup is complex
            val js = """
                (function() {
                    if (!window.indexedDB) return null;
                    return indexedDB.databases ? 'available' : 'present';
                })();
            """.trimIndent()
            
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                webView.evaluateJavascript(js) { result ->
                    val cleaned = result?.trim('"')?.takeIf { it != "null" }
                    continuation.resumeWith(Result.success(cleaned))
                }
            }
        }
    }
    
    // ===== Encryption =====
    
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        
        return if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            entry.secretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            
            val builder = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            
            // Require user authentication on Android 11+ (optional for prototype)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationRequired(false) // For prototype simplicity
            }
            
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
    }
    
    private fun encryptData(plaintext: String): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Prepend IV to encrypted data
        return iv + encrypted
    }
    
    private fun decryptData(encryptedData: ByteArray): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        
        // Extract IV (GCM uses 12-byte IV)
        val iv = encryptedData.copyOfRange(0, 12)
        val data = encryptedData.copyOfRange(12, encryptedData.size)
        
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(data)
        
        return String(decrypted, Charsets.UTF_8)
    }
    
    // ===== File Management =====
    
    private fun getBackupDir(): File {
        val dir = File(context.filesDir, BACKUP_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun getBackupFile(accountId: String): File {
        // Sanitize account ID for filename safety
        val safeId = accountId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(getBackupDir(), "${safeId}_steam$FILE_EXTENSION")
    }
    
    /**
     * Info about a stored backup.
     */
    data class BackupInfo(
        val accountId: String,
        val timestamp: Long,
        val sizeBytes: Long
    )
}
