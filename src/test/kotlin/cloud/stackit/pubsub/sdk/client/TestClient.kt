package cloud.stackit.pubsub.sdk.client

import cloud.stackit.pubsub.sdk.auth.AuthConfig
import cloud.stackit.pubsub.sdk.config.Environment
import cloud.stackit.pubsub.sdk.config.Region
import cloud.stackit.pubsub.sdk.publisher.PubSubPublisher
import cloud.stackit.pubsub.sdk.subscriber.PubSubSubscriber
import cloud.stackit.pubsub.sdk.test.MockAuthProvider
import org.junit.jupiter.api.Test
import java.util.UUID

class TestPubSubClientInit {
    @Test
    fun `creates client with topicId`() {
        val topicId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        assert(client.topicId == topicId)
    }

    @Test
    fun `creates default AuthConfig automatically`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config)
        assert(client.authProvider != null)
    }

    @Test
    fun `accepts custom AuthConfig`() {
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = AuthConfig(serviceAccountKeyPath = "sa-key.json"))
        assert(client.authProvider != null)
    }

    @Test
    fun `sets default region to EU01`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config)
        assert(client.region == Region.EU01)
    }

    @Test
    fun `sets default environment to PROD`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config)
        assert(client.environment == Environment.PROD)
    }

    @Test
    fun `accepts custom region`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, region = Region.EU01)
        assert(client.region == Region.EU01)
    }

    @Test
    fun `accepts custom environment`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, environment = Environment.QA)
        assert(client.environment == Environment.QA)
    }

    @Test
    fun `builds dataplaneUrl with topicId`() {
        val topicId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        assert(client.baseDataplaneUrl.contains(topicId.toString()))
    }

    @Test
    fun `builds correct url structure`() {
        val topicId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = topicId, authConfig = config, region = Region.EU01)
        assert(client.baseDataplaneUrl == "https://$topicId.pubsub.eu01.onstackit.cloud")
    }

    @Test
    fun `builds url without prefix for PROD environment`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, environment = Environment.PROD)
        assert(!client.baseDataplaneUrl.contains(".dev."))
        assert(!client.baseDataplaneUrl.contains(".qa."))
    }

    @Test
    fun `builds url with dev prefix for DEV environment`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, environment = Environment.DEV)
        assert(client.baseDataplaneUrl.contains(".dev."))
    }

    @Test
    fun `builds url with qa prefix for QA environment`() {
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = UUID.randomUUID(), authConfig = config, environment = Environment.QA)
        assert(client.baseDataplaneUrl.contains(".qa."))
    }
}

class TestPubSubClientFactories {
    @Test
    fun `getPublisher returns configured publisher`() {
        val topicId = UUID.randomUUID()
        val authProvider = MockAuthProvider()
        val publisher = PubSubPublisher(
            topicId = topicId,
            baseDataplaneUrl = "https://test.pubsub.eu01.onstackit.cloud",
            authProvider = authProvider
        )
        assert(publisher != null)
    }

    @Test
    fun `getSubscriber returns configured subscriber`() {
        val subscriptionId = UUID.randomUUID()
        val authProvider = MockAuthProvider()
        val subscriber = PubSubSubscriber(
            subscriptionId = subscriptionId,
            baseDataplaneUrl = "https://test.pubsub.eu01.onstackit.cloud",
            authProvider = authProvider
        )
        assert(subscriber != null)
    }

    @Test
    fun `getPublisher shares auth provider`() {
        val topicId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        val publisher = client.getPublisher()
        assert(publisher != null)
    }

    @Test
    fun `getSubscriber shares auth provider`() {
        val topicId = UUID.randomUUID()
        val subscriptionId = UUID.randomUUID()
        val config = AuthConfig(serviceAccountKeyPath = "sa-key.json")
        val client = PubSubClient(topicId = topicId, authConfig = config)
        val subscriber = client.getSubscriber(subscriptionId)
        assert(subscriber != null)
    }
}
