package org.example.pubsub.errors

sealed class PubSubAuthError(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {
    class MissingCredentials(
        message: String = "No service account key configured"
    ) : PubSubAuthError(message)

    class TokenAcquisitionFailed(
        message: String,
        cause: Throwable? = null
    ) : PubSubAuthError(message, cause)

    class InvalidKey(
        message: String,
        cause: Throwable? = null
    ) : PubSubAuthError(message, cause)
}
