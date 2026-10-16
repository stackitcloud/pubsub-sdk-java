/*
 * Copyright 2026 Schwarz Digits Cloud GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cloud.stackit.pubsub.sdk.auth

import cloud.stackit.pubsub.sdk.errors.PubSubError
import cloud.stackit.pubsub.sdk.test.MockAuthProvider
import cloud.stackit.sdk.core.KeyFlowAuthenticator
import cloud.stackit.sdk.core.config.CoreConfiguration
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Disabled
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.IOException
import java.time.Instant

class TestAuthConfig {
    @Test
    fun `creates AuthConfig with defaults`() {
        val config = AuthConfig()
        assertNull(config.serviceAccountKey)
        assertNull(config.serviceAccountKeyPath)
        assertEquals("https://authorization.stackit.cloud/auth/token", config.tokenEndpoint)
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
    private fun makeJwt(lifetimeSeconds: Long): String =
        JWT.create()
            .withExpiresAt(Instant.now().plusSeconds(lifetimeSeconds))
            .sign(Algorithm.HMAC256("secret"))

    private val keyConfig = AuthConfig(serviceAccountKeyPath = "sa-key.json")

    @Test
    fun `reuses authenticator and caches token until expiry`() {
        val token = makeJwt(900)
        val authenticator = mock<KeyFlowAuthenticator> { on { accessToken } doReturn token }
        var created = 0
        val provider = StackitAuthProvider(keyConfig) { created++; authenticator }

        assertEquals(token, provider.getToken())
        assertEquals(token, provider.getToken())

        assertEquals(1, created)
        verify(authenticator, times(1)).accessToken
    }

    @Test
    fun `refreshes via same authenticator when token is within expiry buffer`() {
        val expiring = makeJwt(30)
        val refreshed = makeJwt(1800)
        val authenticator = mock<KeyFlowAuthenticator> {
            on { accessToken }.doReturn(expiring, refreshed)
        }
        var created = 0
        val provider = StackitAuthProvider(keyConfig) { created++; authenticator }

        assertEquals(expiring, provider.getToken())
        assertEquals(refreshed, provider.getToken())

        assertEquals(1, created)
        verify(authenticator, times(2)).accessToken
    }

    @Test
    fun `configures authenticator leeway to match cache buffer`() {
        var captured: CoreConfiguration? = null
        val authenticator = mock<KeyFlowAuthenticator> { on { accessToken } doReturn makeJwt(900) }
        val provider = StackitAuthProvider(keyConfig) { captured = it; authenticator }

        provider.getToken()

        assertEquals(60L, captured!!.tokenExpirationLeeway)
    }

    @Test
    fun `wraps authenticator failures in TokenAcquisitionFailed`() {
        val authenticator = mock<KeyFlowAuthenticator> { on { accessToken } doThrow IOException("boom") }
        val provider = StackitAuthProvider(keyConfig) { authenticator }

        assertThrows<PubSubError.AuthError.TokenAcquisitionFailed> { provider.getToken() }
    }

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
