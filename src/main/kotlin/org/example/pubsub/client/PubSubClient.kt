package org.example.pubsub.client

import org.example.pubsub.auth.AuthConfig
import org.example.pubsub.auth.StackitAuthProvider
import org.example.pubsub.publisher.PubSubPublisher
import org.example.pubsub.retry.RetryConfig
import org.example.pubsub.subscriber.PubSubSubscriber

/**
 * Main entry point for the STACKIT PubSub SDK.
 *
 * Provides a unified client for both publishing and subscribing,
 * sharing authentication and HTTP client resources.
 *
 * @param topicId The topic identifier.
 * @param authConfig Authentication configuration.
 * @param region API region (default: eu01).
 * @param environment STACKIT environment (default: prod).
 */
data class PubSubClient(
    val topicId: String,
    val authConfig: AuthConfig? = null,
    private val region: String = "eu01",
    private val environment: String = "prod"
) {
    val authProvider: StackitAuthProvider = StackitAuthProvider(authConfig ?: AuthConfig())

    private val envHost = when (environment.lowercase()) {
        "dev" -> "dev."
        "qa" -> "qa."
        else -> ""
    }

    val baseDataplaneUrl: String = "https://$topicId.pubsub.$envHost$region.onstackit.cloud"

    /**
     * Create a Publisher for this topic.
     *
     * @param retryConfig Optional retry configuration.
     * @return Configured PubSubPublisher.
     */
    fun getPublisher(retryConfig: RetryConfig? = null): PubSubPublisher {
        return PubSubPublisher(
            topicId = topicId,
            baseDataplaneUrl = baseDataplaneUrl,
            authProvider = authProvider,
            retryConfig = retryConfig ?: RetryConfig.Default
        )
    }

    /**
     * Create a Subscriber for a subscription.
     *
     * @param subscriptionId The subscription UUID.
     * @param retryConfig Optional retry configuration.
     * @param maxMessages Maximum messages per pull (1-32).
     * @param longPullDurationMs Long pull timeout in ms (100-5000).
     * @return Configured PubSubSubscriber.
     */
    fun getSubscriber(
        subscriptionId: String,
        retryConfig: RetryConfig? = null,
        maxMessages: Int = 16,
        longPullDurationMs: Int = 5000
    ): PubSubSubscriber {
        return PubSubSubscriber(
            subscriptionId = subscriptionId,
            baseDataplaneUrl = baseDataplaneUrl,
            authProvider = authProvider,
            retryConfig = retryConfig ?: RetryConfig.Default,
            maxMessages = maxMessages,
            longPullDurationMs = longPullDurationMs
        )
    }
}
