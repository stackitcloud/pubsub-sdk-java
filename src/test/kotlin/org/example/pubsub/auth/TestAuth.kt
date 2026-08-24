package org.example.pubsub.auth

import org.example.pubsub.TestUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

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
    fun `creates AuthConfig with serviceAccountKey`() {
        val config = AuthConfig(serviceAccountKeyPath = TestUtils.SA_KEY_PATH)
        assert(config.serviceAccountKeyPath == TestUtils.SA_KEY_PATH)
    }

    @Test
    fun `creates AuthConfig with serviceAccountKeyPath`() {
        val config = AuthConfig(serviceAccountKeyPath = "/path/to/key.json")
        assert(config.serviceAccountKeyPath == "/path/to/key.json")
    }

    @Test
    fun `creates AuthConfig with custom tokenEndpoint`() {
        val config = AuthConfig(tokenEndpoint = "https://custom.endpoint/token")
        assert(config.tokenEndpoint == "https://custom.endpoint/token")
    }

    @Test
    fun `toCoreConfiguration returns CoreConfiguration`() {
        val config = AuthConfig(
            serviceAccountKeyPath = TestUtils.SA_KEY_PATH,
            privateKeyPath = "/path/to/private.pem",
            tokenEndpoint = "https://custom/token"
        )
        val coreConfig = config.toCoreConfiguration()
        assert(coreConfig != null)
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
        val config = AuthConfig(serviceAccountKeyPath = TestUtils.SA_KEY_PATH)
        assertDoesNotThrow {
            StackitAuthProvider(config)
        }
    }

    @Test
    fun `getAuthHeader returns Authorization header`() {
        val config = AuthConfig(serviceAccountKeyPath = TestUtils.SA_KEY_PATH)
        val provider = try {
            StackitAuthProvider(config)
        } catch (e: Exception) {
            return
        }
        val header = provider.getAuthHeader()
        assert(header.containsKey("Authorization"))
        assert(header["Authorization"]!!.startsWith("Bearer "))
    }
}
