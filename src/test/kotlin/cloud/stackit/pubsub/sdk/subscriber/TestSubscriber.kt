package cloud.stackit.pubsub.sdk.subscriber

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import cloud.stackit.pubsub.sdk.errors.PubSubError
import cloud.stackit.pubsub.sdk.test.MockAuthProvider
import cloud.stackit.pubsub.sdk.retry.RetryConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class TestPubSubSubscriberInit {
    @Test
    fun `creates subscriber with defaults`() {
        val server = MockWebServer()
        server.start()
        try {
            val authProvider = MockAuthProvider()
            val subscriber = PubSubSubscriber(
                subscriptionId = UUID.randomUUID(),
                baseDataplaneUrl = server.url("/").toString(),
                authProvider = authProvider
            )
            assertNotNull(subscriber)
        } finally {
            server.shutdown()
        }
    }
}

class TestSubscriberPull {
    private val server = MockWebServer()
    private lateinit var baseUrl: String
    private lateinit var authProvider: MockAuthProvider

    @BeforeEach
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString()
        authProvider = MockAuthProvider()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun createSubscriber(
        response: MockResponse,
        retryConfig: RetryConfig = RetryConfig.Default
    ): PubSubSubscriber {
        server.enqueue(response)
        return PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider,
            retryConfig = retryConfig
        )
    }

    @Test
    fun `pull returns messages`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messages":[{"id":1,"ackId":"ack-1","data":"SGVsbG8sIFdvcmxkIQ==","createTime":"2024-01-01T00:00:00Z","deliveryAttempts":1},{"id":2,"ackId":"ack-2","data":"U2Vjb25kIG1lc3NhZ2U=","createTime":"2024-01-01T00:01:00Z","deliveryAttempts":1}]}""")
            .addHeader("Content-Type", "application/json")

        val subscriber = createSubscriber(response)
        val messages = subscriber.pull()

        assertEquals(2, messages.size)
        assertEquals("ack-1", messages[0].ackId)
        assertEquals(1L, messages[0].id)
        assertEquals("Hello, World!", messages[0].text)
    }

    @Test
    fun `pull empty returns empty list`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messages":[]}""")

        val subscriber = createSubscriber(response)
        assertTrue(subscriber.pull().isEmpty())
    }

    @Test
    fun `pull sends maxMessages header`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messages":[]}""")

        val subscriber = createSubscriber(response)
        subscriber.pull(maxMessages = 5)

        val recordedRequest = server.takeRequest()
        assertEquals("5", recordedRequest.getHeader("PubSub-Max-Messages"))
    }

    @Test
    fun `pull sends longPullDurationMs header`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messages":[]}""")

        val subscriber = createSubscriber(response)
        subscriber.pull(longPullDurationMs = 3000)

        val recordedRequest = server.takeRequest()
        assertEquals("3000", recordedRequest.getHeader("PubSub-Long-Pull-Duration"))
    }

    @Test
    fun `pull invalid maxMessages throws`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messages":[]}""")

        val subscriber = createSubscriber(response)
        assertThrows(IllegalArgumentException::class.java) {
            subscriber.pull(maxMessages = 50)
        }
    }

    @Test
    fun `pull invalid maxMessages min throws`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messages":[]}""")

        val subscriber = createSubscriber(response)
        assertThrows(IllegalArgumentException::class.java) {
            subscriber.pull(maxMessages = 0)
        }
    }

    @Test
    fun `pull invalid longPullDurationMs in subscriber init throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PubSubSubscriber(
                subscriptionId = UUID.randomUUID(),
                baseDataplaneUrl = "https://test.pubsub.eu01.onstackit.cloud",
                authProvider = MockAuthProvider(),
                longPullDurationMs = 10000
            )
        }
    }

    @Test
    fun `pull 503 triggers retry then succeeds`() {
        val errorResponse = MockResponse()
            .setResponseCode(503)
            .setBody("Service Unavailable")
        val okResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messages":[]}""")

        server.enqueue(errorResponse)
        server.enqueue(errorResponse)
        server.enqueue(okResponse)

        val retryConfig = RetryConfig(
            maxRetries = 3,
            baseDelay = 10,
            maxDelay = 50,
            multiplier = 1.0
        )

        val subscriber = PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider,
            retryConfig = retryConfig
        )
        subscriber.pull()
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `pull exhausts retries`() {
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

        val subscriber = PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider,
            retryConfig = retryConfig
        )
        val thrown = assertThrows(PubSubError.HttpError.ServerError::class.java) {
            subscriber.pull()
        }
        assertTrue(thrown.message!!.contains("retries"))
        assertEquals(3, server.requestCount)
    }
}

class TestSubscriberAck {
    private val server = MockWebServer()
    private lateinit var baseUrl: String
    private lateinit var authProvider: MockAuthProvider

    @BeforeEach
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString()
        authProvider = MockAuthProvider()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun createSubscriber(response: MockResponse): PubSubSubscriber {
        server.enqueue(response)
        return PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider
        )
    }

    @Test
    fun `ack success`() {
        val response = MockResponse()
            .setResponseCode(204)
            .setBody("")

        val subscriber = createSubscriber(response)
        subscriber.ack(listOf("ack-1", "ack-2"))
    }

    @Test
    fun `ack empty list throws`() {
        val response = MockResponse()
            .setResponseCode(204)

        val subscriber = createSubscriber(response)
        assertThrows(IllegalArgumentException::class.java) {
            subscriber.ack(emptyList())
        }
    }

    @Test
    fun `ack sends correct payload`() {
        val response = MockResponse()
            .setResponseCode(204)
            .setBody("")

        val subscriber = createSubscriber(response)
        subscriber.ack(listOf("ack-1", "ack-2"))

        val recordedRequest = server.takeRequest()
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("ack-1"))
        assertTrue(body.contains("ack-2"))
    }
}

class TestSubscriberNack {
    private val server = MockWebServer()
    private lateinit var baseUrl: String
    private lateinit var authProvider: MockAuthProvider

    @BeforeEach
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString()
        authProvider = MockAuthProvider()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun createSubscriber(response: MockResponse): PubSubSubscriber {
        server.enqueue(response)
        return PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider
        )
    }

    @Test
    fun `nack success`() {
        val response = MockResponse()
            .setResponseCode(204)
            .setBody("")

        val subscriber = createSubscriber(response)
        subscriber.nack(listOf("nack-1", "nack-2"))
    }

    @Test
    fun `nack empty list throws`() {
        val response = MockResponse()
            .setResponseCode(204)

        val subscriber = createSubscriber(response)
        assertThrows(IllegalArgumentException::class.java) {
            subscriber.nack(emptyList())
        }
    }

    @Test
    fun `nack sends correct payload`() {
        val response = MockResponse()
            .setResponseCode(204)
            .setBody("")

        val subscriber = createSubscriber(response)
        subscriber.nack(listOf("nack-1", "nack-2"))

        val recordedRequest = server.takeRequest()
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("nack-1"))
        assertTrue(body.contains("nack-2"))
    }
}

class TestSubscriberErrorHandling {
    private val server = MockWebServer()
    private lateinit var baseUrl: String
    private lateinit var authProvider: MockAuthProvider

    @BeforeEach
    fun setUp() {
        server.start()
        baseUrl = server.url("/").toString()
        authProvider = MockAuthProvider()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun createSubscriber(response: MockResponse): PubSubSubscriber {
        server.enqueue(response)
        return PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider
        )
    }

    @Test
    fun `pull 404 throws not found`() {
        val response = MockResponse()
            .setResponseCode(404)
            .setBody("Not found")

        val subscriber = createSubscriber(response)
        val thrown = assertThrows(PubSubError.HttpError.NotFound::class.java) {
            subscriber.pull()
        }
        assertTrue(thrown.message!!.contains("Not found"))
    }

    @Test
    fun `pull 401 throws unauthorized`() {
        val response = MockResponse()
            .setResponseCode(401)
            .setBody("Unauthorized")

        val subscriber = createSubscriber(response)
        val thrown = assertThrows(PubSubError.HttpError.Unauthorized::class.java) {
            subscriber.pull()
        }
        assertTrue(thrown.message!!.contains("Unauthorized"))
    }

    @Test
    fun `ack 404 throws not found`() {
        val response = MockResponse()
            .setResponseCode(404)
            .setBody("Not found")

        val subscriber = createSubscriber(response)
        assertThrows(PubSubError.HttpError.NotFound::class.java) {
            subscriber.ack(listOf("ack-1"))
        }
    }

    @Test
    fun `nack 404 throws not found`() {
        val response = MockResponse()
            .setResponseCode(404)
            .setBody("Not found")

        val subscriber = createSubscriber(response)
        assertThrows(PubSubError.HttpError.NotFound::class.java) {
            subscriber.nack(listOf("nack-1"))
        }
    }

    @Test
    fun `pull 413 throws too large`() {
        val response = MockResponse()
            .setResponseCode(413)
            .setBody("Request body too large")

        val subscriber = createSubscriber(response)
        val thrown = assertThrows(PubSubError.HttpError.PayloadTooLarge::class.java) {
            subscriber.pull()
        }
        assertTrue(thrown.message!!.contains("too large"))
    }

    @Test
    fun `pull 500 throws after retries`() {
        val response = MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error")

        repeat(5) {
            server.enqueue(response)
        }

        val retryConfig = RetryConfig(
            maxRetries = 2,
            baseDelay = 10,
            maxDelay = 50,
            multiplier = 1.0
        )

        val subscriber = PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider,
            retryConfig = retryConfig
        )
        val thrown = assertThrows(PubSubError.HttpError.ServerError::class.java) {
            subscriber.pull()
        }
        assertTrue(thrown.message!!.contains("500"))
    }

    @Test
    fun `ack 413 throws too large`() {
        val response = MockResponse()
            .setResponseCode(413)
            .setBody("Request body too large")

        val subscriber = createSubscriber(response)
        val thrown = assertThrows(PubSubError.HttpError.PayloadTooLarge::class.java) {
            subscriber.ack(listOf("ack-1"))
        }
        assertTrue(thrown.message!!.contains("too large"))
    }

    @Test
    fun `nack 500 throws after retries`() {
        val response = MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error")

        repeat(5) {
            server.enqueue(response)
        }

        val retryConfig = RetryConfig(
            maxRetries = 2,
            baseDelay = 10,
            maxDelay = 50,
            multiplier = 1.0
        )

        val subscriber = PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider,
            retryConfig = retryConfig
        )
        val thrown = assertThrows(PubSubError.HttpError.ServerError::class.java) {
            subscriber.nack(listOf("nack-1"))
        }
        assertTrue(thrown.message!!.contains("500"))
    }

    @Test
    fun `pull IOException throws request failed`() {
        server.shutdown()

        val subscriber = PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider
        )
        val thrown = assertThrows(PubSubError.RequestFailed::class.java) {
            subscriber.pull()
        }
        assertTrue(thrown.message!!.contains("Request failed"))
    }
}
