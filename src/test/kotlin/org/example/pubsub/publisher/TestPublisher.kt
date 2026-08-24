package org.example.pubsub.publisher

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.example.pubsub.TestUtils
import org.example.pubsub.auth.AuthConfig
import org.example.pubsub.auth.StackitAuthProvider
import org.example.pubsub.errors.PubSubAuthError
import org.example.pubsub.models.PublishMessage
import org.example.pubsub.retry.RetryConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class TestPubSubPublisherInit {
    @Test
    fun `creates publisher with valid topicId`() {
        val server = MockWebServer()
        server.start()
        try {
            val authProvider = createAuthProvider()
            val publisher = PubSubPublisher(
                topicId = UUID.randomUUID(),
                baseDataplaneUrl = server.url("/").toString(),
                authProvider = authProvider
            )
            assertNotNull(publisher)
        } finally {
            server.shutdown()
        }
    }

    private fun createAuthProvider(): StackitAuthProvider {
        return StackitAuthProvider(AuthConfig(serviceAccountKeyPath = TestUtils.SA_KEY_PATH))
    }
}

class TestPublisherPublish {
    private val server = MockWebServer()
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun createAuthProvider(): StackitAuthProvider {
        return StackitAuthProvider(AuthConfig(serviceAccountKeyPath = TestUtils.SA_KEY_PATH))
    }

    private fun createPublisher(
        response: MockResponse,
        retryConfig: RetryConfig = RetryConfig.Default
    ): PubSubPublisher {
        server.enqueue(response)
        return PubSubPublisher(
            topicId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = createAuthProvider(),
            retryConfig = retryConfig
        )
    }

    @Test
    fun `publish returns response with messageIds`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messageIds":[1,2,3]}""")
            .addHeader("Content-Type", "application/json")

        val publisher = createPublisher(response)
        val messages = listOf(PublishMessage("aGVsbG8="), PublishMessage("d29ybGQ="))
        val result = publisher.publish(messages)

        assertTrue(result.messageIds == listOf(1L, 2L, 3L))
        assertEquals(2, publisher.messagesSent)
        assertEquals(1, publisher.publishCalls)
    }

    @Test
    fun `publish sends correct payload`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messageIds":[1]}""")
            .addHeader("Content-Type", "application/json")

        val publisher = createPublisher(response)
        publisher.publish(listOf(PublishMessage("dGVzdA==")))

        val recordedRequest = server.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(requestBody.contains("messages"))
    }

    @Test
    fun `publish empty list throws`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messageIds":[]}""")

        val publisher = createPublisher(response)
        assertThrows(IllegalArgumentException::class.java) {
            publisher.publish(emptyList())
        }
    }

    @Test
    fun `publish too many messages throws`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messageIds":[]}""")

        val publisher = createPublisher(response)
        val messages = List(101) { PublishMessage("dGVzdA==") }
        assertThrows(IllegalArgumentException::class.java) {
            publisher.publish(messages)
        }
    }

    @Test
    fun `publish batch splits messages`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messageIds":[1]}""")

        val publisher = createPublisher(response)
        val messages = List(5) { PublishMessage("dGVzdA==") }
        val results = publisher.publishBatch(messages, batchSize = 2)

        assertEquals(3, results.size)
        assertEquals(3, publisher.publishCalls)
    }

    @Test
    fun `publish batch empty returns empty list`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messageIds":[]}""")

        val publisher = createPublisher(response)
        val results = publisher.publishBatch(emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `publish 503 triggers retry then succeeds`() {
        val errorResponse = MockResponse()
            .setResponseCode(503)
            .setBody("Service Unavailable")
        val okResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messageIds":[42]}""")
            .addHeader("Content-Type", "application/json")

        server.enqueue(errorResponse)
        server.enqueue(errorResponse)
        server.enqueue(okResponse)

        val retryConfig = RetryConfig(
            maxRetries = 3,
            baseDelay = 10,
            maxDelay = 50,
            multiplier = 1.0
        )

        val publisher = PubSubPublisher(
            topicId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = createAuthProvider(),
            retryConfig = retryConfig
        )
        val result = publisher.publish(listOf(PublishMessage("dGVzdA==")))

        assertEquals(listOf(42L), result.messageIds)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `publish exhausts retries`() {
        val errorResponse = MockResponse()
            .setResponseCode(503)
            .setBody("Service Unavailable")

        repeat(10) {
            server.enqueue(errorResponse)
        }

        val retryConfig = RetryConfig(
            maxRetries = 2,
            baseDelay = 10,
            maxDelay = 50,
            multiplier = 1.0
        )

        val publisher = PubSubPublisher(
            topicId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = createAuthProvider(),
            retryConfig = retryConfig
        )
        val thrown = assertThrows(PubSubAuthError::class.java) {
            publisher.publish(listOf(PublishMessage("dGVzdA==")))
        }
        assertTrue(thrown.message!!.contains("retries"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `publish 400 throws bad request`() {
        val errorResponse = MockResponse()
            .setResponseCode(400)
            .setBody("Bad request")

        val publisher = createPublisher(errorResponse)
        val thrown = assertThrows(PubSubAuthError::class.java) {
            publisher.publish(listOf(PublishMessage("dGVzdA==")))
        }
        assertTrue(thrown.message!!.contains("Bad request"))
    }

    @Test
    fun `publish 404 throws not found`() {
        val errorResponse = MockResponse()
            .setResponseCode(404)
            .setBody("Not found")

        val publisher = createPublisher(errorResponse)
        val thrown = assertThrows(PubSubAuthError::class.java) {
            publisher.publish(listOf(PublishMessage("dGVzdA==")))
        }
        assertTrue(thrown.message!!.contains("Not found"))
    }

    @Test
    fun `publish 401 throws auth error`() {
        val errorResponse = MockResponse()
            .setResponseCode(401)
            .setBody("Unauthorized")

        val publisher = createPublisher(errorResponse)
        assertThrows(PubSubAuthError::class.java) {
            publisher.publish(listOf(PublishMessage("dGVzdA==")))
        }
    }
}

class TestPublisherPurge {
    private val server = MockWebServer()
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun createAuthProvider(): StackitAuthProvider {
        return StackitAuthProvider(AuthConfig(serviceAccountKeyPath = TestUtils.SA_KEY_PATH))
    }

    private fun createPublisher(response: MockResponse): PubSubPublisher {
        server.enqueue(response)
        return PubSubPublisher(
            topicId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = createAuthProvider()
        )
    }

    @Test
    fun `purge topic succeeds`() {
        val response = MockResponse()
            .setResponseCode(204)
            .setBody("")

        val publisher = createPublisher(response)
        publisher.purgeTopic()
        assertEquals(1, publisher.purgeCalls)
    }

    @Test
    fun `purge 404 throws not found`() {
        val response = MockResponse()
            .setResponseCode(404)
            .setBody("Not found")

        val publisher = createPublisher(response)
        val thrown = assertThrows(PubSubAuthError::class.java) {
            publisher.purgeTopic()
        }
        assertTrue(thrown.message!!.contains("Not found"))
    }
}
