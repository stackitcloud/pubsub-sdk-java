package org.example.pubsub.client

import org.example.pubsub.auth.AuthConfig
import org.example.pubsub.auth.StackitAuthProvider

data class PubSubClient(
    val topicId: String,
    val authConfig: AuthConfig? = null,
    private val baseUrl: String = "https://{topicId}.pubsub.eu01.onstackit.cloud"
) {
    val authProvider: StackitAuthProvider = StackitAuthProvider(authConfig ?: AuthConfig())
    val baseDataplaneUrl: String = baseUrl.replace("{topicId}", topicId)
}
