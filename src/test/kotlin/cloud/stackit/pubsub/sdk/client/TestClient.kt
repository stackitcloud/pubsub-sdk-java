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
import cloud.stackit.pubsub.sdk.config.Environment
import cloud.stackit.pubsub.sdk.config.Region
import cloud.stackit.pubsub.sdk.retry.RetryConfig
import cloud.stackit.pubsub.sdk.test.MockAuthProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class TestPubSubClientInit {
    @Test
    fun `creates client with topicId`() {
        val topicId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        assertEquals(topicId, client.topicId)
    }

    @Test
    fun `accepts custom AuthConfig`() {
        val topicId = UUID.randomUUID()
        val customConfig = AuthConfig(serviceAccountKey = "test-key")
        val client = PubSubClient(topicId = topicId, authConfig = customConfig)
        assertNotNull(client.authProvider)
    }

    @Test
    fun `sets default region to EU01`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config)
        assertEquals(Region.EU01, client.region)
    }

    @Test
    fun `sets default environment to PROD`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config)
        assertEquals(Environment.PROD, client.environment)
    }

    @Test
    fun `accepts custom region`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, region = Region.EU01)
        assertEquals(Region.EU01, client.region)
    }

    @Test
    fun `accepts custom environment`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, environment = Environment.QA)
        assertEquals(Environment.QA, client.environment)
    }

    @Test
    fun `builds dataplaneUrl with topicId`() {
        val topicId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        assertTrue(client.baseDataplaneUrl.contains(topicId.toString()))
    }

    @Test
    fun `builds correct url structure`() {
        val topicId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = topicId, authConfig = config, region = Region.EU01)
        assertEquals("https://$topicId.pubsub.eu01.onstackit.cloud", client.baseDataplaneUrl)
    }

    @Test
    fun `builds url without prefix for PROD environment`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, environment = Environment.PROD)
        assertFalse(client.baseDataplaneUrl.contains(".dev."))
        assertFalse(client.baseDataplaneUrl.contains(".qa."))
    }

    @Test
    fun `builds url with dev prefix for DEV environment`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, environment = Environment.DEV)
        assertTrue(client.baseDataplaneUrl.contains(".dev."))
    }

    @Test
    fun `builds url with qa prefix for QA environment`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, environment = Environment.QA)
        assertTrue(client.baseDataplaneUrl.contains(".qa."))
    }
}

class TestPubSubClientFactories {
    @Test
    fun `getPublisher returns configured publisher`() {
        val topicId = UUID.randomUUID()
        val authProvider = MockAuthProvider()
        val publisher = cloud.stackit.pubsub.sdk.publisher.PubSubPublisher(
            topicId = topicId,
            baseDataplaneUrl = "https://test.pubsub.eu01.onstackit.cloud",
            authProvider = authProvider
        )
        assertEquals(0, publisher.publishCalls.get())
    }

    @Test
    fun `getSubscriber returns configured subscriber`() {
        val subscriptionId = UUID.randomUUID()
        val authProvider = MockAuthProvider()
        val subscriber = cloud.stackit.pubsub.sdk.subscriber.PubSubSubscriber(
            subscriptionId = subscriptionId,
            baseDataplaneUrl = "https://test.pubsub.eu01.onstackit.cloud",
            authProvider = authProvider
        )
        assertNotNull(subscriber)
    }

    @Test
    fun `getPublisher uses shared auth provider`() {
        val topicId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKey = "test-key")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        val publisher = client.getPublisher()
        assertNotNull(publisher)
        assertEquals(0, publisher.publishCalls.get())
    }

    @Test
    fun `getSubscriber uses shared auth provider`() {
        val topicId = UUID.randomUUID()
        val subscriptionId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKey = "test-key")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        val subscriber = client.getSubscriber(subscriptionId)
        assertNotNull(subscriber)
    }

    @Test
    fun `getPublisher with null retryConfig uses default`() {
        val topicId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKey = "test-key")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        val publisher = client.getPublisher(retryConfig = null)
        assertNotNull(publisher)
    }

    @Test
    fun `getSubscriber with null retryConfig uses default`() {
        val topicId = UUID.randomUUID()
        val subscriptionId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKey = "test-key")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        val subscriber = client.getSubscriber(subscriptionId, retryConfig = null)
        assertNotNull(subscriber)
    }

    @Test
    fun `getSubscriber respects maxMessages and longPullDurationMs`() {
        val topicId = UUID.randomUUID()
        val subscriptionId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKey = "test-key")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        val subscriber = client.getSubscriber(
            subscriptionId,
            maxMessages = 8,
            longPullDurationMs = 3000
        )
        assertNotNull(subscriber)
    }
}
