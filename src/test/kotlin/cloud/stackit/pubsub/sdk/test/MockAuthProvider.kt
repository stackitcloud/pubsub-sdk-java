package cloud.stackit.pubsub.sdk.test

import cloud.stackit.pubsub.sdk.auth.AuthProvider
import java.util.concurrent.atomic.AtomicReference

class MockAuthProvider : AuthProvider {
    private val tokenRef = AtomicReference<String?>(null)

    fun setToken(token: String) {
        tokenRef.set(token)
    }

    override fun getToken(): String {
        return tokenRef.get() ?: "mock-fake-token-12345"
    }

    override fun getAuthHeader(): Map<String, String> {
        return mapOf("Authorization" to "Bearer ${getToken()}")
    }
}
