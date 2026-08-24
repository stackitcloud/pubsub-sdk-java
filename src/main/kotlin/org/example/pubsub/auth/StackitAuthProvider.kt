package org.example.pubsub.auth

import cloud.stackit.sdk.core.KeyFlowAuthenticator
import cloud.stackit.sdk.core.config.CoreConfiguration
import okhttp3.OkHttpClient
import org.example.pubsub.errors.PubSubAuthError
import java.security.spec.InvalidKeySpecException
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class StackitAuthProvider(
    private val config: AuthConfig
) {
    private val tokenRef = AtomicReference<TokenRecord>(EMPTY_TOKEN)

    private companion object {
        val EMPTY_TOKEN = TokenRecord(null, Instant.EPOCH)
        val TOKEN_EXPIRY_BUFFER: java.time.Duration = java.time.Duration.ofSeconds(60)
        val TOKEN_LIFETIME: java.time.Duration = java.time.Duration.ofSeconds(3600)
    }

    private data class TokenRecord(
        val token: String?,
        val expiresAt: Instant
    )

    init {
        val hasKey = config.serviceAccountKey?.isNotBlank() == true
        val hasKeyPath = config.serviceAccountKeyPath?.isNotBlank() == true
        require(hasKey || hasKeyPath) {
            """
            |No service account key configured. Provide one of:
            |  - serviceAccountKey: inline JSON string or file path
            |  - serviceAccountKeyPath: path to service account key file
            |  - STACKIT_SERVICE_ACCOUNT_KEY environment variable
            """.trimMargin()
        }
    }

    fun getAuthHeader(): Map<String, String> =
        mapOf("Authorization" to "Bearer ${getToken()}")

    fun getToken(): String {
        val cached = tokenRef.get()
        val now = Instant.now()

        if (cached.token != null && now.isBefore(cached.expiresAt.minus(TOKEN_EXPIRY_BUFFER))) {
            return cached.token
        }

        val (token, expiresAt) = fetchTokenFromStackit()
        tokenRef.set(TokenRecord(token, expiresAt))
        return token
    }

    @Suppress("SwallowedException")
    private fun fetchTokenFromStackit(): Pair<String, Instant> {
        return try {
            val coreConfig: CoreConfiguration = config.toCoreConfiguration()
            val httpClient = OkHttpClient.Builder().build()

            val authenticator = KeyFlowAuthenticator(httpClient, coreConfig)
            val accessToken = authenticator.accessToken
            val expiresAt = Instant.now().plus(TOKEN_LIFETIME)
            accessToken to expiresAt
        } catch (e: PubSubAuthError) {
            throw e
        } catch (e: InvalidKeySpecException) {
            throw PubSubAuthError.InvalidKey("Invalid private key in service account: ${e.message}", e)
        } catch (e: Exception) {
            throw PubSubAuthError.TokenAcquisitionFailed("Failed to obtain STACKIT token: ${e.message}", e)
        }
    }
}
