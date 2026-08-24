package org.example.pubsub.retry

/**
 * Configuration for retry behavior with exponential backoff.
 *
 * @param maxRetries Maximum number of retry attempts (0 = no retries). Default: 3
 * @param baseDelay Base delay in milliseconds between retries. Default: 500
 * @param maxDelay Maximum delay in milliseconds between retries. Default: 30000
 * @param multiplier Multiplier applied to delay after each retry. Default: 2.0
 * @param jitterFactor Randomization factor (0.0-1.0) to avoid thundering herd. Default: 0.25
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val baseDelay: Long = 500,
    val maxDelay: Long = 30_000,
    val multiplier: Double = 2.0,
    val jitterFactor: Double = 0.25
) {
    companion object {
        val Default = RetryConfig()
    }
}
