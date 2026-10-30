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

package cloud.stackit.pubsub.sdk.client

import cloud.stackit.pubsub.sdk.auth.AuthConfig
import cloud.stackit.pubsub.sdk.auth.AuthProvider
import cloud.stackit.pubsub.sdk.auth.StackitAuthProvider
import cloud.stackit.pubsub.sdk.config.Environment
import cloud.stackit.pubsub.sdk.config.Region
import cloud.stackit.pubsub.sdk.publisher.PubSubPublisher
import cloud.stackit.pubsub.sdk.retry.RetryConfig
import cloud.stackit.pubsub.sdk.subscriber.PubSubSubscriber
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.UUID

/**
 * Main entry point for the STACKIT PubSub SDK.
 *
 * Provides a unified client for both publishing and subscribing,
 * sharing authentication and HTTP client resources.
 *
 * @param topicId The topic UUID.
 * @param authConfig Authentication configuration.
 * @param region API region (default: EU01).
 * @param environment STACKIT environment (default: PROD).
 */
class PubSubClient @JvmOverloads constructor(
    val topicId: UUID,
    val authConfig: AuthConfig? = null,
    val region: Region = Region.EU01,
    val environment: Environment = Environment.PROD
) {
    val authProvider: StackitAuthProvider = StackitAuthProvider(authConfig ?: AuthConfig())

    val baseDataplaneUrl: String = "https://$topicId.pubsub.${region.value}.${environment.hostnamePrefix}onstackit.cloud"

    private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(10))
        .build()

    /**
     * Create a Publisher for this topic.
     *
     * @param retryConfig Optional retry configuration.
     * @return Configured PubSubPublisher.
     */
    @JvmOverloads
    fun getPublisher(retryConfig: RetryConfig? = null): PubSubPublisher {
        return PubSubPublisher(
            topicId = topicId,
            baseDataplaneUrl = baseDataplaneUrl,
            authProvider = authProvider,
            retryConfig = retryConfig ?: RetryConfig.Default,
            httpClient = sharedHttpClient
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
    @JvmOverloads
    fun getSubscriber(
        subscriptionId: UUID,
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
            longPullDurationMs = longPullDurationMs,
            httpClient = sharedHttpClient
        )
    }
}
