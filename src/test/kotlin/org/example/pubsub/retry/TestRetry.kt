package org.example.pubsub.retry

import org.example.pubsub.errors.PubSubAuthError
import org.junit.jupiter.api.Test
import java.io.IOException

class TestRetryConfig {
    @Test
    fun `has correct default values`() {
        val config = RetryConfig.Default
        assert(config.maxRetries == 3)
        assert(config.baseDelay == 500L)
        assert(config.maxDelay == 30_000L)
        assert(config.multiplier == 2.0)
        assert(config.jitterFactor == 0.25)
    }

    @Test
    fun `accepts custom values`() {
        val config = RetryConfig(
            maxRetries = 5,
            baseDelay = 1000,
            maxDelay = 60_000,
            multiplier = 3.0,
            jitterFactor = 0.5
        )
        assert(config.maxRetries == 5)
        assert(config.baseDelay == 1000L)
        assert(config.maxDelay == 60_000L)
        assert(config.multiplier == 3.0)
        assert(config.jitterFactor == 0.5)
    }
}

class TestRetryableStatus {
    @Test
    fun `500 is retryable`() {
        assert(isRetryableStatusCode(500))
    }

    @Test
    fun `502 is retryable`() {
        assert(isRetryableStatusCode(502))
    }

    @Test
    fun `503 is retryable`() {
        assert(isRetryableStatusCode(503))
    }

    @Test
    fun `504 is retryable`() {
        assert(isRetryableStatusCode(504))
    }

    @Test
    fun `200 is not retryable`() {
        assert(!isRetryableStatusCode(200))
    }

    @Test
    fun `400 is not retryable`() {
        assert(!isRetryableStatusCode(400))
    }

    @Test
    fun `401 is not retryable`() {
        assert(!isRetryableStatusCode(401))
    }

    @Test
    fun `404 is not retryable`() {
        assert(!isRetryableStatusCode(404))
    }
}

class TestRetryableException {
    @Test
    fun `ConnectException is retryable`() {
        assert(isRetryableException(IOException("connection refused")))
    }

    @Test
    fun `SocketTimeoutException is retryable`() {
        val socketTimeout = java.net.SocketTimeoutException("timeout")
        assert(isRetryableException(socketTimeout))
    }

    @Test
    fun `IOException is retryable`() {
        assert(isRetryableException(IOException("network error")))
    }

    @Test
    fun `TimeoutException is retryable`() {
        assert(isRetryableException(java.util.concurrent.TimeoutException()))
    }

    @Test
    fun `PubSubAuthError is not retryable`() {
        assert(!isRetryableException(PubSubAuthError.TokenAcquisitionFailed("token error")))
    }

    @Test
    fun `nested retryable exception is retryable`() {
        val nested = RuntimeException(IOException("nested"))
        assert(isRetryable(nested))
    }
}

class TestCalculateDelay {
    @Test
    fun `first attempt returns at least base delay`() {
        val config = RetryConfig(baseDelay = 500L)
        val delay = getRetryDelay(config, 0)
        assert(delay >= 500L) { "Delay should be at least base delay, got $delay" }
    }

    @Test
    fun `second attempt returns increased delay`() {
        val config = RetryConfig(baseDelay = 500L, multiplier = 2.0)
        val delay0 = getRetryDelay(config, 0)
        val delay1 = getRetryDelay(config, 1)
        assert(delay1 > delay0) { "Delay should increase with attempt count" }
    }

    @Test
    fun `respects max delay cap`() {
        val config = RetryConfig(baseDelay = 500L, maxDelay = 1000L, multiplier = 10.0)
        val delay = getRetryDelay(config, 10)
        assert(delay <= 1000L + (1000L * 0.25)) { "Delay should respect max cap, got $delay" }
    }

    @Test
    fun `delays increase monotonically`() {
        val config = RetryConfig(baseDelay = 500L, multiplier = 2.0)
        val delays = (0..5).map { getRetryDelay(config, it) }
        for (i in 1 until delays.size) {
            assert(delays[i] > delays[i - 1]) { "Delay should increase: ${delays[i - 1]} -> ${delays[i]}" }
        }
    }
}

class TestRetryFunction {
    @Test
    fun `succeeds on first try`() {
        var called = false
        runCatching {
            retry<RuntimeException> {
                called = true
            }
        }
        assert(called)
    }

    @Test
    fun `succeeds after retries`() {
        var attempts = 0
        runCatching {
            retry<RuntimeException> {
                attempts++
                if (attempts < 3) {
                    throw RuntimeException("transient")
                }
            }
        }
        assert(attempts == 3)
    }

    @Test
    fun `does not retry non-retryable exception`() {
        var attempts = 0
        val result = runCatching {
            retry<IOException> {
                attempts++
                throw PubSubAuthError.TokenAcquisitionFailed("validation failed")
            }
        }
        assert(result.isFailure)
        assert(attempts == 1)
    }

    @Test
    fun `throws after exhausting retries`() {
        var attempts = 0
        val config = RetryConfig(maxRetries = 2)
        val result = runCatching {
            retry<IOException>(config = config) {
                attempts++
                throw IOException("connection failed")
            }
        }
        assert(result.isFailure)
        assert(attempts == 3) // 1 initial + 2 retries
    }
}
