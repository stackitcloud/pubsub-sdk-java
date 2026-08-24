package org.example.pubsub.retry

import org.example.pubsub.errors.PubSubAuthError
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
        is PubSubAuthError -> false
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
    var result = 1.0
    for (i in 0 until exp) result *= base
    return result
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
                throw lastException as T
            }
            val delay = getRetryDelay(config, attempt)
            Thread.sleep(delay)
        }
    }
    throw lastException as T
}
