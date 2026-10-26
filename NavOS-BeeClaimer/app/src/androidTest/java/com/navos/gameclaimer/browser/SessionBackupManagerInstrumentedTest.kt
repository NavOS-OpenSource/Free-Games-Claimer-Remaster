package com.navos.gameclaimer.browser

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 1 Multi-Account Prototype: Instrumented Tests for Session Backup Manager
 * 
 * These tests run on an Android device/emulator and verify actual cookie
 * backup/restore functionality with CookieManager.
 * 
 * IMPORTANT: These tests manipulate cookies which affects global WebView state.
 * Tests should be run in isolation.
 */
@RunWith(AndroidJUnit4::class)
class SessionBackupManagerInstrumentedTest {
    
    private lateinit var sessionManager: SessionBackupManager
    private val testAccountA = "test_account_a"
    private val testAccountB = "test_account_b"
    
    private val testDomain = "https://store.steampowered.com"
    private val testCookieA = "steamLoginSecure=test_token_account_a"
    private val testCookieB = "steamLoginSecure=test_token_account_b"
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        sessionManager = SessionBackupManager(context)
        
        // Ensure CookieManager is initialized on main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        
        // Clean up any existing test backups
        sessionManager.deleteBackup(testAccountA)
        sessionManager.deleteBackup(testAccountB)
    }
    
    @After
    fun teardown() {
        // Clean up cookies
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        
        // Clean up test backups
        sessionManager.deleteBackup(testAccountA)
        sessionManager.deleteBackup(testAccountB)
    }
    
    // ===== Basic Backup Tests =====
    
    @Test
    fun backupWithNoCookies_returnsFailed() = runBlocking {
        // Ensure no cookies exist
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        
        val result = sessionManager.backupSteamSession(testAccountA)
        
        assertTrue("Backup with no cookies should fail", result is SessionBackupManager.BackupResult.Failed)
        val failed = result as SessionBackupManager.BackupResult.Failed
        assertTrue("Reason should mention no cookies", failed.reason.contains("No Steam cookies"))
    }
    
    @Test
    fun backupWithCookies_returnsSuccess() = runBlocking {
        // Set a test cookie
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(testDomain, testCookieA)
            CookieManager.getInstance().flush()
        }
        
        val result = sessionManager.backupSteamSession(testAccountA)
        
        assertTrue("Backup with cookies should succeed", result is SessionBackupManager.BackupResult.Success)
        val success = result as SessionBackupManager.BackupResult.Success
        assertEquals(testAccountA, success.accountId)
        assertTrue("Cookie count should be positive", success.cookieCount > 0)
    }
    
    // ===== Restore Tests =====
    
    @Test
    fun restoreNonExistentBackup_returnsNoBackupFound() = runBlocking {
        val result = sessionManager.restoreSteamSession("nonexistent_account")
        
        assertTrue("Restoring non-existent backup should return NoBackupFound", 
            result is SessionBackupManager.RestoreResult.NoBackupFound)
    }
    
    @Test
    fun restoreAfterBackup_restoresCookies() = runBlocking {
        // 1. Set cookie and backup
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(testDomain, testCookieA)
            CookieManager.getInstance().flush()
        }
        
        val backupResult = sessionManager.backupSteamSession(testAccountA)
        assertTrue("Backup should succeed", backupResult is SessionBackupManager.BackupResult.Success)
        
        // 2. Clear cookies
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        
        // Verify cookies are cleared
        var cookiesAfterClear: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookiesAfterClear = CookieManager.getInstance().getCookie(testDomain)
        }
        assertTrue("Cookies should be cleared", cookiesAfterClear.isNullOrEmpty())
        
        // 3. Restore backup
        val restoreResult = sessionManager.restoreSteamSession(testAccountA)
        assertTrue("Restore should succeed", restoreResult is SessionBackupManager.RestoreResult.Success)
        
        // 4. Verify cookies are restored
        var cookiesAfterRestore: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookiesAfterRestore = CookieManager.getInstance().getCookie(testDomain)
        }
        assertNotNull("Cookies should be restored", cookiesAfterRestore)
        assertTrue("Restored cookies should contain original value", 
            cookiesAfterRestore?.contains("steamLoginSecure") == true)
    }
    
    // ===== Account Switching Tests =====
    
    @Test
    fun switchBetweenAccounts_restoresCorrectSession() = runBlocking {
        // 1. Login as Account A and backup
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(testDomain, testCookieA)
            CookieManager.getInstance().flush()
        }
        val backupA = sessionManager.backupSteamSession(testAccountA)
        assertTrue("Backup A should succeed", backupA is SessionBackupManager.BackupResult.Success)
        
        // 2. Clear and login as Account B, then backup
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().setCookie(testDomain, testCookieB)
            CookieManager.getInstance().flush()
        }
        val backupB = sessionManager.backupSteamSession(testAccountB)
        assertTrue("Backup B should succeed", backupB is SessionBackupManager.BackupResult.Success)
        
        // 3. Restore Account A
        val restoreA = sessionManager.restoreSteamSession(testAccountA)
        assertTrue("Restore A should succeed", restoreA is SessionBackupManager.RestoreResult.Success)
        
        // 4. Verify Account A cookies
        var cookiesAfterRestoreA: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookiesAfterRestoreA = CookieManager.getInstance().getCookie(testDomain)
        }
        assertTrue("Should have Account A cookies", 
            cookiesAfterRestoreA?.contains("test_token_account_a") == true)
        assertFalse("Should NOT have Account B cookies",
            cookiesAfterRestoreA?.contains("test_token_account_b") == true)
        
        // 5. Restore Account B
        val restoreB = sessionManager.restoreSteamSession(testAccountB)
        assertTrue("Restore B should succeed", restoreB is SessionBackupManager.RestoreResult.Success)
        
        // 6. Verify Account B cookies
        var cookiesAfterRestoreB: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookiesAfterRestoreB = CookieManager.getInstance().getCookie(testDomain)
        }
        assertTrue("Should have Account B cookies",
            cookiesAfterRestoreB?.contains("test_token_account_b") == true)
        assertFalse("Should NOT have Account A cookies",
            cookiesAfterRestoreB?.contains("test_token_account_a") == true)
    }
    
    // ===== Backup Listing Tests =====
    
    @Test
    fun listBackups_returnsCreatedBackups() = runBlocking {
        // Create backup
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(testDomain, testCookieA)
            CookieManager.getInstance().flush()
        }
        sessionManager.backupSteamSession(testAccountA)
        
        // List backups
        val backups = sessionManager.listBackups()
        
        assertTrue("Should have at least one backup", backups.isNotEmpty())
        assertTrue("Should contain test account backup", 
            backups.any { it.accountId == testAccountA })
    }
    
    // ===== Backup Deletion Tests =====
    
    @Test
    fun deleteBackup_removesBackup() = runBlocking {
        // Create backup
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(testDomain, testCookieA)
            CookieManager.getInstance().flush()
        }
        sessionManager.backupSteamSession(testAccountA)
        
        // Verify backup exists
        var backups = sessionManager.listBackups()
        assertTrue("Backup should exist", backups.any { it.accountId == testAccountA })
        
        // Delete backup
        val deleted = sessionManager.deleteBackup(testAccountA)
        assertTrue("Delete should succeed", deleted)
        
        // Verify backup is gone
        backups = sessionManager.listBackups()
        assertFalse("Backup should be deleted", backups.any { it.accountId == testAccountA })
    }
    
    // ===== Clear Session Tests =====
    
    @Test
    fun clearSteamSession_removesCookies() = runBlocking {
        // Set cookie
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(testDomain, testCookieA)
            CookieManager.getInstance().flush()
        }
        
        // Clear session
        val cleared = sessionManager.clearSteamSession()
        assertTrue("Clear should succeed", cleared)
        
        // Verify cookies are gone
        var cookies: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookies = CookieManager.getInstance().getCookie(testDomain)
        }
        assertTrue("Cookies should be cleared", cookies.isNullOrEmpty())
    }
    
    // ===== Concurrent Operation Tests =====
    
    @Test
    fun concurrentBackupAttempts_areSerializedByMutex() = runBlocking {
        // Set cookie
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(testDomain, testCookieA)
            CookieManager.getInstance().flush()
        }
        
        // Launch concurrent backups
        val jobs = (1..3).map { i ->
            kotlinx.coroutines.async {
                sessionManager.backupSteamSession("concurrent_test_$i")
            }
        }
        
        // All should complete (mutex serializes them)
        val results = jobs.map { it.await() }
        
        // At least one should succeed (mutex ensures non-interference)
        assertTrue("At least one backup should succeed",
            results.any { it is SessionBackupManager.BackupResult.Success })
        
        // Clean up
        (1..3).forEach { i ->
            sessionManager.deleteBackup("concurrent_test_$i")
        }
    }
    
    // ===== Switch In Progress Flag Tests =====
    
    @Test
    fun switchInProgress_isFalseWhenIdle() {
        assertFalse("Switch should not be in progress when idle",
            sessionManager.isSwitchInProgress)
    }
}
