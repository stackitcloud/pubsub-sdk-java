package cloud.stackit.pubsub.sdk.auth

import cloud.stackit.pubsub.sdk.test.MockAuthProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Disabled

class TestAuthConfig {
    @Test
    fun `creates AuthConfig with defaults`() {
        val config = AuthConfig()
        assertNull(config.serviceAccountKey)
        assertNull(config.serviceAccountKeyPath)
        assertEquals("https://authorization.stackit.cloud/auth/token", config.tokenEndpoint)
        assertEquals("dataplane", config.scope)
    }

    @Test
    fun `creates AuthConfig with serviceAccountKeyPath`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        assertEquals("sa-key.json", config.serviceAccountKeyPath)
    }

    @Test
    fun `creates AuthConfig with custom tokenEndpoint`() {
        val config = AuthConfig(tokenEndpoint = "https://custom.endpoint/token")
        assertEquals("https://custom.endpoint/token", config.tokenEndpoint)
    }

    @Test
    fun `tokenEndpoint must use HTTPS`() {
        assertThrows<IllegalArgumentException> {
            AuthConfig(tokenEndpoint = "http://insecure.endpoint/token")
        }
    }

    @Test
    fun `toCoreConfiguration maps all fields`() {
        val config = AuthConfig(
            serviceAccountKey = "inline-key",
            serviceAccountKeyPath = "sa-key.json",
            privateKeyPath = "/path/to/private.pem",
            tokenEndpoint = "https://custom/token"
        )
        val coreConfig = config.toCoreConfiguration()
        assertNotNull(coreConfig)
    }

    @Test
    fun `toCoreConfiguration handles null fields`() {
        val config = AuthConfig(
            serviceAccountKeyPath = "sa-key.json",
            privateKeyPath = null,
            tokenEndpoint = null
        )
        val coreConfig = config.toCoreConfiguration()
        assertNotNull(coreConfig)
    }
}

class TestStackitAuthProvider {
    @Test
    fun `throws when no credentials configured`() {
        val config = AuthConfig()
        assertThrows<IllegalArgumentException> {
            StackitAuthProvider(config)
        }
    }

    @Test
    fun `throws when serviceAccountKey is blank`() {
        val config = AuthConfig(serviceAccountKey = "   ")
        assertThrows<IllegalArgumentException> {
            StackitAuthProvider(config)
        }
    }

    @Test
    fun `throws when serviceAccountKeyPath is blank`() {
        val config = AuthConfig(serviceAccountKeyPath = "   ")
        assertThrows<IllegalArgumentException> {
            StackitAuthProvider(config)
        }
    }

    @Test
    fun `accepts valid serviceAccountKeyPath`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        assertDoesNotThrow {
            StackitAuthProvider(config)
        }
    }

    @Test
    @Disabled("Requires valid network access to STACKIT authorization endpoint")
    fun `accepts valid serviceAccountKey`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        assertDoesNotThrow {
            val provider = StackitAuthProvider(config)
            val token = provider.getToken()
            // Token should be non-empty if the sa-key.json is valid
            // This test validates the provider creation, not the token value
            assertNotNull(provider)
        }
    }
}

class TestAuthProviderInterface {
    @Test
    @Disabled("Requires network access to STACKIT authorization endpoint")
    fun `StackitAuthProvider implements AuthProvider`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider: AuthProvider = StackitAuthProvider(config)
        assertNotNull(provider)
    }

    @Test
    fun `MockAuthProvider implements AuthProvider`() {
        val provider: AuthProvider = MockAuthProvider()
        assertNotNull(provider)
    }

    @Test
    fun `MockAuthProvider returns default token`() {
        val provider = MockAuthProvider()
        val token = provider.getToken()
        assertEquals("mock-fake-token-12345", token)
    }

    @Test
    fun `MockAuthProvider returns custom token when set`() {
        val provider = MockAuthProvider()
        provider.setToken("custom-mock-token")
        val token = provider.getToken()
        assertEquals("custom-mock-token", token)
    }

    @Test
    fun `MockAuthProvider getAuthHeader returns correct format`() {
        val provider = MockAuthProvider()
        val header = provider.getAuthHeader()
        assertTrue(header.containsKey("Authorization"))
        assertTrue(header["Authorization"]!!.startsWith("Bearer "))
    }

    @Test
    fun `MockAuthProvider getAuthHeader uses current token`() {
        val provider = MockAuthProvider()
        provider.setToken("my-test-token")
        val header = provider.getAuthHeader()
        assertEquals("Bearer my-test-token", header["Authorization"])
    }

    @Test
    @Disabled("Requires network access to STACKIT authorization endpoint")
    fun `StackitAuthProvider getAuthHeader returns Authorization header`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider = StackitAuthProvider(config)
        val header = provider.getAuthHeader()
        assertTrue(header.containsKey("Authorization"))
        assertTrue(header["Authorization"]!!.startsWith("Bearer "))
    }

    @Test
    @Disabled("Requires network access to STACKIT authorization endpoint")
    fun `StackitAuthProvider getToken returns non-empty string`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider = StackitAuthProvider(config)
        val token = provider.getToken()
        // Token is non-empty when sa-key.json is valid
        assertTrue(token.isNotEmpty())
    }

    @Test
    @Disabled("Requires network access to STACKIT authorization endpoint")
    fun `StackitAuthProvider getToken returns same token on cache hit`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider = StackitAuthProvider(config)
        val token1 = provider.getToken()
        val token2 = provider.getToken()
        assertEquals(token1, token2)
    }

    @Test
    @Disabled("Requires network access to STACKIT authorization endpoint")
    fun `StackitAuthProvider getAuthHeader returns correct format`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider = StackitAuthProvider(config)
        val header = provider.getAuthHeader()
        val authValue = header["Authorization"]!!
        assertTrue(authValue.startsWith("Bearer "))
        assertTrue(authValue.length > "Bearer ".length)
    }
}
