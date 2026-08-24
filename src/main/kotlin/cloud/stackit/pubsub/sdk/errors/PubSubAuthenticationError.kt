package cloud.stackit.pubsub.sdk.errors

sealed class PubSubError(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    sealed class AuthError(
        message: String,
        cause: Throwable? = null
    ) : PubSubError(message, cause) {
        class MissingCredentials(
            message: String = "No service account key configured"
        ) : AuthError(message)

        class TokenAcquisitionFailed(
            message: String,
            cause: Throwable? = null
        ) : AuthError(message, cause)

        class InvalidKey(
            message: String,
            cause: Throwable? = null
        ) : AuthError(message, cause)
    }

    sealed class HttpError(
        message: String,
        cause: Throwable? = null
    ) : PubSubError(message, cause) {
        class BadRequest(
            message: String
        ) : HttpError(message)

        class Unauthorized(
            message: String
        ) : HttpError(message)

        class Forbidden(
            message: String
        ) : HttpError(message)

        class NotFound(
            message: String
        ) : HttpError(message)

        class PayloadTooLarge(
            message: String
        ) : HttpError(message)

        class ServerError(
            message: String
        ) : HttpError(message)

        class Unexpected(
            message: String
        ) : HttpError(message)
    }

    class RequestFailed(
        message: String,
        cause: Throwable? = null
    ) : PubSubError(message, cause)
}
