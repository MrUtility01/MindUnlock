package com.mindunlock.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncEndpointPolicyTest {
    @Test
    fun explicitHttpsIsPreserved() {
        assertEquals("https://192.168.1.20:8443", SyncEndpointPolicy.baseUrl("https://192.168.1.20", 8443))
    }

    @Test
    fun explicitHttpRemainsAvailableForPrivateLanServers() {
        assertEquals("http://mindunlock-pc.local:82", SyncEndpointPolicy.baseUrl("http://mindunlock-pc.local/", 82))
        assertEquals("http://192.168.1.20:82", SyncEndpointPolicy.baseUrl("http://192.168.1.20", 82))
    }

    @Test
    fun privateLanBareHostKeepsLegacyHttpDefault() {
        assertEquals("http://192.168.1.20:82", SyncEndpointPolicy.baseUrl("192.168.1.20", 82))
    }

    @Test
    fun publicBareHostDefaultsToHttps() {
        assertEquals("https://sync.example.com:443", SyncEndpointPolicy.baseUrl("sync.example.com", 443))
    }

    @Test
    fun rejectsPlainHttpForPublicEndpoints() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncEndpointPolicy.baseUrl("http://sync.example.com", 82)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncEndpointPolicy.baseUrl("http://8.8.8.8", 82)
        }
    }

    @Test
    fun rejectsUnsupportedSchemesAndCredentialsOrPaths() {
        assertThrows(IllegalArgumentException::class.java) { SyncEndpointPolicy.baseUrl("ftp://192.168.1.20", 82) }
        assertThrows(IllegalArgumentException::class.java) { SyncEndpointPolicy.baseUrl("https://user:pass@example.com", 443) }
        assertThrows(IllegalArgumentException::class.java) { SyncEndpointPolicy.baseUrl("https://example.com/api", 443) }
    }

    @Test
    fun rejectsInvalidPortsAndEmptyHosts() {
        assertThrows(IllegalArgumentException::class.java) { SyncEndpointPolicy.baseUrl("", 82) }
        assertThrows(IllegalArgumentException::class.java) { SyncEndpointPolicy.baseUrl("host", 0) }
        assertThrows(IllegalArgumentException::class.java) { SyncEndpointPolicy.baseUrl("host", 65536) }
    }
}
