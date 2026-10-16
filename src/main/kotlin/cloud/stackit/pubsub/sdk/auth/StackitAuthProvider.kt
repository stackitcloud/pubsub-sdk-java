package cloud.stackit.pubsub.sdk.auth

import cloud.stackit.sdk.core.KeyFlowAuthenticator
import cloud.stackit.sdk.core.config.CoreConfiguration
import com.auth0.jwt.JWT
import okhttp3.OkHttpClient
import cloud.stackit.pubsub.sdk.errors.PubSubError
import java.security.spec.InvalidKeySpecException
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class StackitAuthProvider internal constructor(
    private val config: AuthConfig,
    private val authenticatorFactory: (CoreConfiguration) -> KeyFlowAuthenticator
) : AuthProvider {
    constructor(config: AuthConfig) : this(
        config,
        { coreConfig -> KeyFlowAuthenticator(OkHttpClient.Builder().build(), coreConfig) }
    )

    private val tokenRef = AtomicReference<TokenRecord>(EMPTY_TOKEN)

    // Long-lived authenticator, created once per provider so its refresh token is reused.
    private var authenticator: KeyFlowAuthenticator? = null

    private companion object {
        val EMPTY_TOKEN = TokenRecord(null, Instant.EPOCH)
        val TOKEN_EXPIRY_BUFFER: java.time.Duration = java.time.Duration.ofSeconds(60)
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

    override fun getAuthHeader(): Map<String, String> =
        mapOf("Authorization" to "Bearer ${getToken()}")

    override fun getToken(): String {
        val cached = tokenRef.get()
        val now = Instant.now()

        if (cached.token != null && now.isBefore(cached.expiresAt.minus(TOKEN_EXPIRY_BUFFER))) {
            return cached.token
        }

        synchronized(tokenRef) {
            val current = tokenRef.get()
            if (current.token != null && now.isBefore(current.expiresAt.minus(TOKEN_EXPIRY_BUFFER))) {
                return current.token
            }
            val (token, expiresAt) = fetchTokenFromStackit()
            tokenRef.set(TokenRecord(token, expiresAt))
            return token
        }
    }

    private fun fetchTokenFromStackit(): Pair<String, Instant> {
        return try {
            // Same leeway as our cache buffer, so the authenticator refreshes exactly when we ask it to.
            val auth = authenticator ?: authenticatorFactory(
                config.toCoreConfiguration().tokenExpirationLeeway(TOKEN_EXPIRY_BUFFER.seconds)
            ).also { authenticator = it }

            val accessToken = auth.accessToken
            val expiresAt = JWT.decode(accessToken).expiresAtAsInstant
            accessToken to expiresAt
        } catch (e: InvalidKeySpecException) {
            throw PubSubError.AuthError.InvalidKey("Invalid private key in service account: ${e.message}", e)
        } catch (e: Exception) {
            throw PubSubError.AuthError.TokenAcquisitionFailed("Failed to obtain STACKIT token: ${e.message}", e)
        }
    }
}
