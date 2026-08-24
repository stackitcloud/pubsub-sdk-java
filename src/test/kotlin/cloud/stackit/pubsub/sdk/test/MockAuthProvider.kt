package cloud.stackit.pubsub.sdk.test

import cloud.stackit.pubsub.sdk.auth.AuthProvider

class MockAuthProvider : AuthProvider {
    private var _token: String? = null

    fun setToken(token: String) {
        _token = token
    }

    override fun getToken(): String {
        return _token ?: "mock-fake-token-12345"
    }

    override fun getAuthHeader(): Map<String, String> {
        return mapOf("Authorization" to "Bearer ${getToken()}")
    }
}
