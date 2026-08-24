package cloud.stackit.pubsub.sdk.auth

interface AuthProvider {
    fun getToken(): String
    fun getAuthHeader(): Map<String, String>
}
