package cloud.stackit.pubsub.sdk.publisher

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import cloud.stackit.pubsub.sdk.models.PublishMessage
import cloud.stackit.pubsub.sdk.test.MockAuthProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class TestPublisherConcurrency {
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

    @Test
    fun `concurrent publishes from multiple threads`() {
        val threadCount = 5
        val messagesPerThread = 2
        val totalMessages = threadCount * messagesPerThread
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(totalMessages) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .addHeader("Content-Type", "application/json")
            )
        }

        val executor: ExecutorService = Executors.newFixedThreadPool(threadCount)

        try {
            repeat(threadCount) { threadId ->
                executor.submit {
                    try {
                        val publisher = PubSubPublisher(
                            topicId = UUID.randomUUID(),
                            baseDataplaneUrl = baseUrl,
                            authProvider = authProvider
                        )
                        val messages = List(messagesPerThread) { PublishMessage("dGVzdA==") }
                        publisher.publish(messages)
                        successCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }

            val completed = latch.await(30, TimeUnit.SECONDS)
            assertTrue(completed, "All threads should complete within timeout")

            assertEquals(threadCount, successCount.get(), "All threads should succeed")
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `concurrent publishes share publisher safely`() {
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .addHeader("Content-Type", "application/json")
            )
        }

        val publisher = PubSubPublisher(
            topicId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = authProvider
        )

        val threads = List(threadCount) {
            thread {
                try {
                    publisher.publish(listOf(PublishMessage("dGVzdA==")))
                    successCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue(completed, "All threads should complete within timeout")

        threads.forEach { it.join() }

        assertEquals(threadCount, successCount.get())
        assertEquals(threadCount, publisher.publishCalls.get())
        assertEquals(threadCount, publisher.messagesSent.get())
    }

    @Test
    fun `concurrent ack and nack operations`() {
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) {
            server.enqueue(MockResponse().setResponseCode(204).setBody(""))
        }

        val subscriber = cloud.stackit.pubsub.sdk.subscriber.PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = MockAuthProvider()
        )

        val threads = List(threadCount) { id ->
            thread {
                try {
                    if (id % 2 == 0) {
                        subscriber.ack(listOf("ack-$id"))
                    } else {
                        subscriber.nack(listOf("nack-$id"))
                    }
                    successCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue(completed)

        assertEquals(threadCount, successCount.get())
        threads.forEach { it.join() }
    }
}

class TestClientConcurrency {
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

    @Test
    fun `multiple publishers from same client`() {
        try {
            val topicId = UUID.randomUUID()
            val authProvider = MockAuthProvider()

            repeat(5) {
                server.enqueue(
                    MockResponse()
                        .setResponseCode(204)
                        .addHeader("Content-Type", "application/json")
                )
            }

            val publishers = List(5) {
                PubSubPublisher(
                    topicId = topicId,
                    baseDataplaneUrl = baseUrl,
                    authProvider = authProvider
                )
            }
            publishers.forEach { it.publish(listOf(PublishMessage("dGVzdA=="))) }

            assertEquals(5, publishers.sumOf { it.publishCalls.get() })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `publisher and subscriber from same client`() {
        try {
            val topicId = UUID.randomUUID()
            val subscriptionId = UUID.randomUUID()
            val authProvider = MockAuthProvider()

            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .addHeader("Content-Type", "application/json")
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"messages":[]}""")
                    .addHeader("Content-Type", "application/json")
            )

            val publisher = PubSubPublisher(
                topicId = topicId,
                baseDataplaneUrl = baseUrl,
                authProvider = authProvider
            )
            publisher.publish(listOf(PublishMessage("dGVzdA==")))

            val subscriber = cloud.stackit.pubsub.sdk.subscriber.PubSubSubscriber(
                subscriptionId = subscriptionId,
                baseDataplaneUrl = baseUrl,
                authProvider = authProvider
            )
            subscriber.pull()

            assertEquals(1, publisher.publishCalls.get())
        } finally {
            server.shutdown()
        }
    }
}
