package org.example.pubsub.client

import org.example.pubsub.auth.AuthConfig
import org.example.pubsub.auth.StackitAuthProvider
import org.example.pubsub.config.Environment
import org.example.pubsub.config.Region
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
 * @param region API region (default: EU01).
 * @param environment STACKIT environment (default: PROD).
 */
data class PubSubClient(
    val topicId: String,
    val authConfig: AuthConfig? = null,
    val region: Region = Region.EU01,
    val environment: Environment = Environment.PROD
) {
    val authProvider: StackitAuthProvider = StackitAuthProvider(authConfig ?: AuthConfig())

    val baseDataplaneUrl: String = "https://$topicId.pubsub.${environment.hostnamePrefix}${region.value}.onstackit.cloud"

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
