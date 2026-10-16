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
