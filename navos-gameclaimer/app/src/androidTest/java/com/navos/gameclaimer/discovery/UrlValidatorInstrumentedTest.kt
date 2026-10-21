package com.navos.gameclaimer.discovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.navos.gameclaimer.domain.discovery.DefaultUrlValidator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 6: Instrumented tests for URL validation.
 * 
 * These tests run on a real Android device to use android.net.Uri.
 * They cover security cases that the FakeUrlValidator cannot fully test.
 */
@RunWith(AndroidJUnit4::class)
class UrlValidatorInstrumentedTest {
    
    private lateinit var validator: DefaultUrlValidator
    
    @Before
    fun setup() {
        validator = DefaultUrlValidator()
    }
    
    // ===== Scheme Tests =====
    
    @Test
    fun httpsClaimUrlIsValid() {
        assertTrue(validator.isValidClaimUrl("https://store.epicgames.com/game"))
        assertTrue(validator.isValidClaimUrl("https://gog.com/game/test"))
        assertTrue(validator.isValidClaimUrl("https://store.steampowered.com/app/12345"))
    }
    
    @Test
    fun httpClaimUrlIsRejected() {
        assertFalse(validator.isValidClaimUrl("http://store.epicgames.com/game"))
        assertFalse(validator.isValidClaimUrl("http://gog.com/game"))
    }
    
    @Test
    fun dangerousSchemesAreRejected() {
        assertFalse(validator.isValidClaimUrl("javascript:alert(1)"))
        assertFalse(validator.isValidClaimUrl("file:///etc/passwd"))
        assertFalse(validator.isValidClaimUrl("data:text/html,<script>"))
        assertFalse(validator.isValidClaimUrl("content://media/external"))
        assertFalse(validator.isValidClaimUrl("intent://test"))
        assertFalse(validator.isValidClaimUrl("blob:https://example.com"))
    }
    
    // ===== IP Address Tests =====
    
    @Test
    fun ipv4AddressIsRejected() {
        assertFalse(validator.isValidClaimUrl("https://192.168.1.1/game"))
        assertFalse(validator.isValidClaimUrl("https://10.0.0.1/game"))
        assertFalse(validator.isValidClaimUrl("https://172.16.0.1/game"))
        assertFalse(validator.isValidClaimUrl("https://8.8.8.8/game"))
    }
    
    @Test
    fun localhostAndLoopbackAreRejected() {
        assertFalse(validator.isValidClaimUrl("https://localhost/game"))
        assertFalse(validator.isValidClaimUrl("https://127.0.0.1/game"))
        assertFalse(validator.isValidClaimUrl("https://0.0.0.0/game"))
        assertFalse(validator.isValidClaimUrl("https://test.localhost/game"))
        assertFalse(validator.isValidClaimUrl("https://machine.local/game"))
    }
    
    // ===== Port Tests =====
    
    @Test
    fun standardHttpsPortIsAllowed() {
        assertTrue(validator.isValidClaimUrl("https://store.epicgames.com:443/game"))
    }
    
    @Test
    fun nonStandardPortsAreRejected() {
        assertFalse(validator.isValidClaimUrl("https://store.epicgames.com:8080/game"))
        assertFalse(validator.isValidClaimUrl("https://gog.com:8443/game"))
        assertFalse(validator.isValidClaimUrl("https://store.epicgames.com:80/game"))
    }
    
    // ===== User Info Tests =====
    
    @Test
    fun userInfoInUrlIsRejected() {
        assertFalse(validator.isValidClaimUrl("https://user:pass@store.epicgames.com/game"))
        assertFalse(validator.isValidClaimUrl("https://admin@gog.com/game"))
    }
    
    // ===== Trusted Domain Tests =====
    
    @Test
    fun trustedDomainsAreAccepted() {
        assertTrue(validator.isValidClaimUrl("https://store.epicgames.com/game"))
        assertTrue(validator.isValidClaimUrl("https://epicgames.com/store/free-games"))
        assertTrue(validator.isValidClaimUrl("https://gog.com/game/test"))
        assertTrue(validator.isValidClaimUrl("https://store.steampowered.com/app/123"))
        assertTrue(validator.isValidClaimUrl("https://gaming.amazon.com/loot"))
        assertTrue(validator.isValidClaimUrl("https://gamerpower.com/giveaway/test"))
    }
    
    @Test
    fun untrustedDomainsAreRejected() {
        assertFalse(validator.isValidClaimUrl("https://phishing.com/game"))
        assertFalse(validator.isValidClaimUrl("https://epic-free-games.com/test"))
        assertFalse(validator.isValidClaimUrl("https://not-gog.com/game"))
        assertFalse(validator.isValidClaimUrl("https://epicgames.phishing.com/test"))
    }
    
    @Test
    fun subdomainsOfTrustedDomainsAreAccepted() {
        assertTrue(validator.isValidClaimUrl("https://store.epicgames.com/game"))
        assertTrue(validator.isValidClaimUrl("https://www.gog.com/game"))
        assertTrue(validator.isValidClaimUrl("https://cdn.gamerpower.com/redirect"))
    }
    
    // ===== Image URL Tests =====
    
    @Test
    fun httpImageUrlIsAllowed() {
        assertTrue(validator.isValidImageUrl("http://cdn.example.com/image.jpg"))
        assertTrue(validator.isValidImageUrl("http://images.gog.com/cover.png"))
    }
    
    @Test
    fun httpsImageUrlIsAllowed() {
        assertTrue(validator.isValidImageUrl("https://cdn.cloudflare.com/image.webp"))
    }
    
    @Test
    fun imageUrlRejectsIpAddresses() {
        assertFalse(validator.isValidImageUrl("https://192.168.1.1/image.jpg"))
        assertFalse(validator.isValidImageUrl("http://10.0.0.1/cover.png"))
    }
    
    @Test
    fun imageUrlRejectsLocalhost() {
        assertFalse(validator.isValidImageUrl("http://localhost/image.jpg"))
        assertFalse(validator.isValidImageUrl("https://127.0.0.1/cover.png"))
    }
    
    @Test
    fun imageUrlRejectsDangerousSchemes() {
        assertFalse(validator.isValidImageUrl("javascript:void(0)"))
        assertFalse(validator.isValidImageUrl("data:image/png;base64,abc"))
        assertFalse(validator.isValidImageUrl("file:///sdcard/image.jpg"))
    }
    
    // ===== Edge Cases =====
    
    @Test
    fun emptyUrlIsRejected() {
        assertFalse(validator.isValidClaimUrl(""))
        assertFalse(validator.isValidClaimUrl("   "))
        assertFalse(validator.isValidImageUrl(""))
    }
    
    @Test
    fun malformedUrlIsRejected() {
        assertFalse(validator.isValidClaimUrl("not a url"))
        assertFalse(validator.isValidClaimUrl("https://"))
        assertFalse(validator.isValidClaimUrl("://missing.scheme"))
    }
    
    @Test
    fun urlWithQueryParamsIsAccepted() {
        assertTrue(validator.isValidClaimUrl("https://store.epicgames.com/game?ref=homepage"))
        assertTrue(validator.isValidClaimUrl("https://gog.com/game/test?id=123&lang=en"))
    }
    
    @Test
    fun urlWithFragmentIsAccepted() {
        assertTrue(validator.isValidClaimUrl("https://store.epicgames.com/game#section"))
    }
}
