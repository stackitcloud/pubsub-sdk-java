package cloud.stackit.pubsub.sdk.auth

import cloud.stackit.pubsub.sdk.test.MockAuthProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Disabled

class TestAuthConfig {
    @Test
    fun `creates AuthConfig with defaults`() {
        val config = AuthConfig()
        assert(config.serviceAccountKey == null)
        assert(config.serviceAccountKeyPath == null)
        assert(config.tokenEndpoint == "https://authorization.stackit.cloud/auth/token")
        assert(config.scope == "dataplane")
    }

    @Test
    fun `creates AuthConfig with serviceAccountKeyPath`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        assert(config.serviceAccountKeyPath == "sa-key.json")
    }

    @Test
    fun `creates AuthConfig with custom tokenEndpoint`() {
        val config = AuthConfig(tokenEndpoint = "https://custom.endpoint/token")
        assert(config.tokenEndpoint == "https://custom.endpoint/token")
    }

    @Test
    fun `toCoreConfiguration returns CoreConfiguration`() {
        val config = AuthConfig(
            serviceAccountKeyPath = "sa-key.json",
            privateKeyPath = "/path/to/private.pem",
            tokenEndpoint = "https://custom/token"
        )
        val coreConfig = config.toCoreConfiguration()
    }

    @Test
    fun `toCoreConfiguration handles null privateKeyPath`() {
        val config = AuthConfig(
            serviceAccountKeyPath = "sa-key.json",
            privateKeyPath = null,
            tokenEndpoint = "https://custom/token"
        )
        val coreConfig = config.toCoreConfiguration()
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
}

class TestAuthProviderInterface {
    @Test
    fun `StackitAuthProvider implements AuthProvider`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider: AuthProvider = StackitAuthProvider(config)
    }

    @Test
    fun `MockAuthProvider implements AuthProvider`() {
        val provider: AuthProvider = MockAuthProvider()
    }

    @Test
    fun `MockAuthProvider returns default token`() {
        val provider = MockAuthProvider()
        val token = provider.getToken()
        assert(token == "mock-fake-token-12345")
    }

    @Test
    fun `MockAuthProvider returns custom token when set`() {
        val provider = MockAuthProvider()
        provider.setToken("custom-mock-token")
        val token = provider.getToken()
        assert(token == "custom-mock-token")
    }

    @Test
    fun `MockAuthProvider getAuthHeader returns correct format`() {
        val provider = MockAuthProvider()
        val header = provider.getAuthHeader()
        assert(header.containsKey("Authorization"))
        assert(header["Authorization"]!!.startsWith("Bearer "))
    }

    @Test
    fun `MockAuthProvider getAuthHeader uses current token`() {
        val provider = MockAuthProvider()
        provider.setToken("my-test-token")
        val header = provider.getAuthHeader()
        assert(header["Authorization"] == "Bearer my-test-token")
    }

    @Test
    @Disabled("Requires valid credentials and network access")
    fun `StackitAuthProvider getAuthHeader returns Authorization header`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider = StackitAuthProvider(config)
        val header = provider.getAuthHeader()
        assert(header.containsKey("Authorization"))
        assert(header["Authorization"]!!.startsWith("Bearer "))
    }

    @Test
    @Disabled("Requires valid credentials and network access")
    fun `StackitAuthProvider getToken returns non-empty string`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider = StackitAuthProvider(config)
        val token = provider.getToken()
        assert(token.isNotEmpty())
    }

    @Test
    @Disabled("Requires valid credentials and network access")
    fun `StackitAuthProvider getToken returns same token on cache hit`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider = StackitAuthProvider(config)
        val token1 = provider.getToken()
        val token2 = provider.getToken()
        assert(token1 == token2)
    }

    @Test
    @Disabled("Requires valid credentials and network access")
    fun `StackitAuthProvider getAuthHeader returns correct format`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val provider = StackitAuthProvider(config)
        val header = provider.getAuthHeader()
        val authValue = header["Authorization"]!!
        assert(authValue.startsWith("Bearer "))
        assert(authValue.length > "Bearer ".length)
    }
}
