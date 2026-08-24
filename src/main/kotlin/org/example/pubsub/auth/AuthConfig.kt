package org.example.pubsub.auth

import cloud.stackit.sdk.core.config.CoreConfiguration

data class AuthConfig(
    val serviceAccountKey: String? = null,
    val serviceAccountKeyPath: String? = null,
    val privateKeyPath: String? = null,
    val tokenEndpoint: String? = "https://authorization.stackit.cloud/auth/token",
    val scope: String = "dataplane"
) {
    fun toCoreConfiguration(): CoreConfiguration {
        val config = CoreConfiguration()
        serviceAccountKey?.let { config.serviceAccountKey(it) }
        serviceAccountKeyPath?.let { config.serviceAccountKeyPath(it) }
        privateKeyPath?.let { config.privateKeyPath(it) }
        tokenEndpoint?.let { config.tokenCustomUrl(it) }
        return config
    }
}
