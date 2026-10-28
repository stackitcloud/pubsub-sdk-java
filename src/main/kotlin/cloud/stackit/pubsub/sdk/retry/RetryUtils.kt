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

package cloud.stackit.pubsub.sdk.retry

import cloud.stackit.pubsub.sdk.errors.PubSubError
import java.util.concurrent.ThreadLocalRandom

/**
 * HTTP status codes that are considered transient and worth retrying.
 */
val TRANSIENT_STATUS_CODES = setOf(500, 502, 503, 504)

/**
 * HTTP status codes that are never retried (permanent failures).
 */
val NON_RETRYABLE_STATUS_CODES = setOf(400, 401, 403, 404, 413)

/**
 * Determine whether an HTTP status code is retryable.
 */
fun isRetryableStatusCode(code: Int, config: RetryConfig = RetryConfig.Default): Boolean {
    return code in TRANSIENT_STATUS_CODES
}

/**
 * Determine whether an exception is retryable.
 */
fun isRetryableException(exc: Throwable, config: RetryConfig = RetryConfig.Default): Boolean {
    return when (exc) {
        is PubSubError -> false
        is java.net.ConnectException -> true
        is java.net.SocketTimeoutException -> true
        is java.io.IOException -> true
        is java.util.concurrent.TimeoutException -> true
        else -> false
    }
}

/**
 * Calculate the delay for a given retry attempt using exponential backoff with jitter.
 */
fun getRetryDelay(config: RetryConfig, attempt: Int): Long {
    val delay = minOf(
        (config.baseDelay * pow(config.multiplier, attempt)).toLong(),
        config.maxDelay
    )
    val jitter = (delay * config.jitterFactor * (ThreadLocalRandom.current().nextDouble(0.0, 1.0))).toLong()
    return delay + jitter
}

private fun pow(base: Double, exp: Int): Double {
    if (exp <= 0) return 1.0
    return Math.pow(base, exp.toDouble())
}

/**
 * Check if an exception and its causes are retryable.
 */
fun isRetryable(exc: Throwable): Boolean {
    var current: Throwable? = exc
    while (current != null) {
        if (isRetryableException(current)) return true
        current = current.cause
    }
    return false
}

/**
 * Retry a block of code with exponential backoff.
 *
 * @param config Retry configuration (uses [RetryConfig.Default] if not specified).
 * @param block The code block to execute and potentially retry.
 * @throws T The last exception if all retries are exhausted.
 */
inline fun <reified T : Throwable> retry(
    config: RetryConfig = RetryConfig.Default,
    noinline block: () -> Unit
) {
    var lastException: Throwable? = null
    for (attempt in 0..config.maxRetries) {
        try {
            block()
            return
        } catch (e: Throwable) {
            lastException = e
            if (!isRetryable(e) || attempt >= config.maxRetries) {
                @Suppress("UNCHECKED_CAST")
                throw lastException as? T ?: throw lastException
            }
            val delay = getRetryDelay(config, attempt)
            Thread.sleep(delay)
        }
    }
    @Suppress("UNCHECKED_CAST")
    throw lastException as? T ?: throw lastException!!
}
