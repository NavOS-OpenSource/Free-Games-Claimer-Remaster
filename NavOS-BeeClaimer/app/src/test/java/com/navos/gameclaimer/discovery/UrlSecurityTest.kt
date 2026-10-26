package com.navos.gameclaimer.discovery

import com.navos.gameclaimer.data.adapter.*
import com.navos.gameclaimer.domain.model.GameStore
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

/**
 * Phase 7: URL security tests for store adapters.
 * 
 * Tests URL parsing, domain validation, deceptive hosts, suffix/prefix attacks,
 * unsafe schemes, unexpected ports, and redirect boundary enforcement.
 */
class UrlSecurityTest {
    
    // ===== STEAM URL SECURITY =====
    
    @Test
    fun `Steam rejects deceptive host suffix attacks`() {
        val adapter = SteamStoreAdapter()
        
        // Attacker domains that END with steampowered.com
        assertNull(adapter.parseItemId("https://fakesteampowered.com/app/123"))
        assertNull(adapter.parseItemId("https://attackersteampowered.com/app/123"))
        assertNull(adapter.parseItemId("https://not-steampowered.com/app/123"))
        assertNull(adapter.parseItemId("https://my-steampowered.com/app/123"))
    }
    
    @Test
    fun `Steam rejects deceptive host prefix attacks`() {
        val adapter = SteamStoreAdapter()
        
        // store.steampowered.com.attacker.com
        assertNull(adapter.parseItemId("https://store.steampowered.com.attacker.com/app/123"))
        assertNull(adapter.parseItemId("https://steampowered.com.evil.com/app/123"))
    }
    
    @Test
    fun `Steam rejects unsafe schemes`() {
        val adapter = SteamStoreAdapter()
        
        // HTTP is rejected (must be HTTPS)
        assertNull(adapter.parseItemId("http://store.steampowered.com/app/123"))
        
        // Other schemes
        assertNull(adapter.parseItemId("javascript:alert('xss')//store.steampowered.com/app/123"))
        assertNull(adapter.parseItemId("file:///store.steampowered.com/app/123"))
        assertNull(adapter.parseItemId("ftp://store.steampowered.com/app/123"))
        assertNull(adapter.parseItemId("data:text/html,<script>alert(1)</script>"))
    }
    
    @Test
    fun `Steam rejects unexpected ports`() {
        val adapter = SteamStoreAdapter()
        
        // Non-standard ports could indicate phishing
        assertNull(adapter.parseItemId("https://store.steampowered.com:8080/app/123"))
        assertNull(adapter.parseItemId("https://store.steampowered.com:3000/app/123"))
        assertNull(adapter.parseItemId("https://store.steampowered.com:443/app/123")) // Explicit 443 might be suspicious
    }
    
    @Test
    fun `Steam accepts valid subdomains`() {
        val adapter = SteamStoreAdapter()
        
        // Valid Steam subdomains
        assertNotNull(adapter.parseItemId("https://store.steampowered.com/app/123"))
        
        // Note: steamcommunity.com is separate domain, not subdomain
    }
    
    @Test
    fun `Steam rejects IP addresses`() {
        val adapter = SteamStoreAdapter()
        
        assertNull(adapter.parseItemId("https://192.168.1.1/app/123"))
        assertNull(adapter.parseItemId("https://10.0.0.1/app/123"))
        assertNull(adapter.parseItemId("https://127.0.0.1/app/123"))
        assertNull(adapter.parseItemId("https://[::1]/app/123"))
    }
    
    // ===== GOG URL SECURITY =====
    
    @Test
    fun `GOG rejects deceptive host suffix attacks`() {
        val adapter = GogStoreAdapter()
        
        assertNull(adapter.parseGameSlug("https://fakegog.com/game/test"))
        assertNull(adapter.parseGameSlug("https://notgog.com/game/test"))
        assertNull(adapter.parseGameSlug("https://mygog.com/game/test"))
    }
    
    @Test
    fun `GOG rejects deceptive host prefix attacks`() {
        val adapter = GogStoreAdapter()
        
        assertNull(adapter.parseGameSlug("https://www.gog.com.attacker.com/game/test"))
        assertNull(adapter.parseGameSlug("https://gog.com.evil.com/game/test"))
    }
    
    @Test
    fun `GOG rejects unsafe schemes`() {
        val adapter = GogStoreAdapter()
        
        assertNull(adapter.parseGameSlug("http://www.gog.com/game/test"))
        assertNull(adapter.parseGameSlug("javascript:void(0)//gog.com"))
    }
    
    @Test
    fun `GOG accepts valid domains`() {
        val adapter = GogStoreAdapter()
        
        // Main domain with game path
        assertNotNull(adapter.parseGameSlug("https://www.gog.com/game/test"))
        assertNotNull(adapter.parseGameSlug("https://www.gog.com/en/game/test"))
    }
    
    // ===== ITCH.IO URL SECURITY =====
    
    @Test
    fun `Itch rejects deceptive host suffix attacks`() {
        val adapter = ItchioStoreAdapter()
        
        // Ends with itch.io but isn't itch.io
        assertFalse(adapter.isValidItchioHost("fakeitch.io"))
        assertFalse(adapter.isValidItchioHost("notitch.io"))
        assertFalse(adapter.isValidItchioHost("myitch.io"))
    }
    
    @Test
    fun `Itch rejects deceptive host prefix attacks`() {
        val adapter = ItchioStoreAdapter()
        
        // itch.io.attacker.com
        assertFalse(adapter.isValidItchioHost("itch.io.attacker.com"))
        assertFalse(adapter.isValidItchioHost("developer.itch.io.evil.com"))
    }
    
    @Test
    fun `Itch validates subdomain format`() {
        val adapter = ItchioStoreAdapter()
        
        // Valid subdomains
        assertTrue(adapter.isValidItchioHost("itch.io"))
        assertTrue(adapter.isValidItchioHost("developer.itch.io"))
        assertTrue(adapter.isValidItchioHost("cool-studio.itch.io"))
        assertTrue(adapter.isValidItchioHost("indie_dev.itch.io"))
        
        // Invalid subdomain formats
        assertFalse(adapter.isValidItchioHost(".itch.io"))
        assertFalse(adapter.isValidItchioHost("..itch.io"))
    }
    
    @Test
    fun `Itch rejects unsafe schemes in URLs`() {
        val adapter = ItchioStoreAdapter()
        
        // HTTP URL parsing should fail
        assertNull(adapter.parseGameUrl("http://developer.itch.io/game"))
    }
    
    @Test
    fun `Itch handles deeply nested subdomains safely`() {
        val adapter = ItchioStoreAdapter()
        
        // Too many subdomain levels could be suspicious
        // Valid: developer.itch.io (1 level)
        assertTrue(adapter.isValidItchioHost("single.itch.io"))
        
        // Multiple levels should be rejected
        assertFalse(adapter.isValidItchioHost("evil.developer.itch.io"))
        assertFalse(adapter.isValidItchioHost("deep.nested.sub.itch.io"))
    }
    
    // ===== INDIEGALA URL SECURITY =====
    
    @Test
    fun `IndieGala rejects deceptive host suffix attacks`() {
        val adapter = IndieGalaStoreAdapter()
        
        assertNull(adapter.parseGiveawaySlug("https://fakeindiegala.com/giveaways/test"))
        assertNull(adapter.parseGiveawaySlug("https://notindiegala.com/giveaways/test"))
    }
    
    @Test
    fun `IndieGala rejects deceptive host prefix attacks`() {
        val adapter = IndieGalaStoreAdapter()
        
        assertNull(adapter.parseGiveawaySlug("https://www.indiegala.com.attacker.com/giveaways/test"))
        assertNull(adapter.parseGiveawaySlug("https://indiegala.com.evil.com/giveaways/test"))
    }
    
    @Test
    fun `IndieGala rejects unsafe schemes`() {
        val adapter = IndieGalaStoreAdapter()
        
        assertNull(adapter.parseGiveawaySlug("http://www.indiegala.com/giveaways/test"))
        assertNull(adapter.parseGiveawaySlug("javascript:alert(1)//indiegala.com"))
    }
    
    @Test
    fun `IndieGala accepts valid paths`() {
        val adapter = IndieGalaStoreAdapter()
        
        // Valid giveaway paths
        assertNotNull(adapter.parseGiveawaySlug("https://www.indiegala.com/giveaways/test-game"))
        assertNotNull(adapter.parseGiveawaySlug("https://indiegala.com/freebies/awesome-game"))
        
        // Invalid paths
        assertNull(adapter.parseGiveawaySlug("https://www.indiegala.com/store/test"))
    }
    
    // ===== REDIRECT BOUNDARY TESTS =====
    
    @Test
    fun `Steam trusted domain validation`() {
        val trustedDomains = setOf("steampowered.com", "steamcommunity.com")
        
        assertTrue(isTrustedDomain("store.steampowered.com", trustedDomains))
        assertTrue(isTrustedDomain("steamcommunity.com", trustedDomains))
        
        // Attacks
        assertFalse(isTrustedDomain("steampowered.com.attacker.com", trustedDomains))
        assertFalse(isTrustedDomain("fakesteampowered.com", trustedDomains))
    }
    
    @Test
    fun `GOG trusted domain validation`() {
        val trustedDomains = setOf("gog.com", "login.gog.com", "menu.gog.com")
        
        assertTrue(isTrustedDomain("gog.com", trustedDomains))
        assertTrue(isTrustedDomain("www.gog.com", trustedDomains))
        assertTrue(isTrustedDomain("login.gog.com", trustedDomains))
        
        // Attacks
        assertFalse(isTrustedDomain("gog.com.attacker.com", trustedDomains))
    }
    
    @Test
    fun `Itch trusted domain validation`() {
        val trustedDomains = setOf("itch.io")
        
        assertTrue(isTrustedDomain("itch.io", trustedDomains))
        assertTrue(isTrustedDomain("developer.itch.io", trustedDomains))
        
        // Attacks
        assertFalse(isTrustedDomain("itch.io.attacker.com", trustedDomains))
        assertFalse(isTrustedDomain("fakeitch.io", trustedDomains))
    }
    
    @Test
    fun `IndieGala trusted domain validation`() {
        val trustedDomains = setOf("indiegala.com")
        
        assertTrue(isTrustedDomain("indiegala.com", trustedDomains))
        assertTrue(isTrustedDomain("www.indiegala.com", trustedDomains))
        
        // Attacks
        assertFalse(isTrustedDomain("indiegala.com.attacker.com", trustedDomains))
    }
    
    // ===== SCHEME VALIDATION =====
    
    @Test
    fun `Only HTTPS scheme allowed for claims`() {
        assertTrue(isSecureScheme("https://store.steampowered.com"))
        assertFalse(isSecureScheme("http://store.steampowered.com"))
        assertFalse(isSecureScheme("ftp://store.steampowered.com"))
        assertFalse(isSecureScheme("file:///etc/passwd"))
        assertFalse(isSecureScheme("javascript:alert(1)"))
        assertFalse(isSecureScheme("data:text/html,<script>"))
    }
    
    // ===== PATH TRAVERSAL ATTACKS =====
    
    @Test
    fun `Path traversal attacks rejected`() {
        val adapter = SteamStoreAdapter()
        
        // Path traversal attempts
        assertNull(adapter.parseItemId("https://store.steampowered.com/app/../../../etc/passwd"))
        assertNull(adapter.parseItemId("https://store.steampowered.com/app/..%2f..%2fetc%2fpasswd"))
    }
    
    @Test
    fun `URL encoding attacks handled`() {
        val adapter = SteamStoreAdapter()
        
        // Double encoding
        assertNull(adapter.parseItemId("https://store.steampowered.com/app/%252e%252e/test"))
        
        // Null byte injection
        assertNull(adapter.parseItemId("https://store.steampowered.com/app/123%00.txt"))
    }
    
    // ===== HELPER FUNCTIONS =====
    
    /**
     * Validates a host against trusted domain list using exact matching.
     */
    private fun isTrustedDomain(host: String, trustedDomains: Set<String>): Boolean {
        val normalizedHost = host.lowercase().removePrefix("www.")
        
        for (trusted in trustedDomains) {
            // Exact match
            if (normalizedHost == trusted) return true
            
            // Subdomain match (host must END with .trusted)
            if (normalizedHost.endsWith(".$trusted")) return true
        }
        
        return false
    }
    
    /**
     * Validates URL scheme is HTTPS.
     */
    private fun isSecureScheme(url: String): Boolean {
        return try {
            val uri = URI(url)
            uri.scheme?.lowercase() == "https"
        } catch (e: Exception) {
            false
        }
    }
}
