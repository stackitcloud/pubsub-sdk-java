package org.example.pubsub.subscriber

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.example.pubsub.auth.AuthConfig
import org.example.pubsub.auth.StackitAuthProvider
import org.example.pubsub.errors.PubSubAuthError
import org.example.pubsub.retry.RetryConfig
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
            val authProvider = createAuthProvider()
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

    private fun createAuthProvider(): StackitAuthProvider {
        val key = """{"id":"test-key-id","keyAlgorithm":"RSA","keyOrigin":"USER_PROVIDED","keyType":"USER_MANAGED","publicKey":"fake-public-key","active":true,"createdAt":"2024-01-01T00:00:00Z","keySigningAlgorithm":"RS256","credentials":{"kid":"test-kid","iss":"sa-issuer","sub":"sa-subject","aud":"https://stackit.cloud","privateKey":"-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD3CA8a337KrBXt\nJxNsIN5u8Tw3Elb25/kkmpqwXSvRAUTZzFymsP6DAcz+Bj5O+bUSEujrP06M133n\n3CJyzHQCAUukZXHfilhg0jp7HXNKYlkb8tQxWpVmg3mCGAKozqo8XqZQkObd135N\nDecfTev2I5i4pkQd2TURxIgYKLIF+crklY6df24V2lHLwt1BW22AvfmSoEKzgwbM\nq4TwFMOH9Xavj/134nEgzD2Whi8FoaFLXJRSKILRAGz5xaqQR9i+NxU/dtQKtkj+\nQnFGUbkmdmGHSgrGsp1lcZCCjUZx7sIy4YevMvxnIk1UAzepxpdDU+HZjDYynUq2\nCMrB9g2HAgMBAAECggEAJRUyn0gZkncOu5D4G207ts/DyEiagWoFPndu7TavUyqr\nmT5SUjuTmTr1+NQbGmPlyJbKHDZepkna/qlPCoiiiYKERmNsGrB739zvJngfTHVD\nBy0GqXn9n2vxG5RbIdgUOgfDU6jM31RwA0UpAswBtetWC7H527k1fDtSar4BK2Au\nkbM213sknTTOIIZIktagxmbgUb/Ee852W0HSw/cxFTRmpM2fzn1WUf7qpRWzwyIg\n6LV+UFyim5HhtxjOwgJL0l7ev/JNBK0bC5LH2LmKlO3S/8IS7COlEXiepsc0c/sS\nnnaBblbeNnDlVXm9+KVixc/0DM+g4dPdBbFL1b1fgQKBgQD9/lifxWxR0TmzupRp\neyx4r+jv2ul42Lkar6l7qIdFOf50TA9i6GE92CpdmVT9KR8WN8ZEEUnqkVaI6rai\nxRxlBaOir7yIPw/PKFNU0bpclnzTf2KhHGzfwbgqQGTFBdciB1qdj1DX80jtw3Je\nTx1e1zqUDrZZpucTMitsngha5wKBgQD4+6IkiNA7iXGJKcmDGQE4ceomEJnzWBV8\nEmLwnddeBwsnaXr3nFRewc76youSU9j8WkBPye0kPUHFbpii3F5axmbD74MATr5e\n9rnINeUvHdVwUzwEYjriZ6yynw8TlJgavl0FyhvKsjhORtdEPZXtcvMbmlbvtjYr\nMUOXuaAEYQKBgQDQdJ4Ryrriz1+XJWtGwuO1zVHVAQqatuv8fe0QYzlZ1IYEtqPu\nHiAo3K+wj/ZTrBsqZe4f0Tyj+lJWHqR0JGfwdVdeXvLRs0frUgJKW7Ki6JNcFXFC\nHw0v+1KFwsGxfeCZugVhyMyl37KcCMtloXLOcsBN4WFBZRzDAWroQy9CQwKBgQDm\nd03L0ZQmOAAO/c4YenHFxLP/Tgii1lr0wLQyk3hCAfUSIzH+twbluzIStJ9i8Bf+\n5q9266uFiuoxjUDeJ2tzTQjcvvwcscbHckvfhfvBlDT4RyNbz21AGsUhhdfXwQjW\nYHUModr90eAKnMov0W3RIEDlwQRN2PTlIDG35Mu4oQKBgBq/OT0JJJxVFlA7F7DF\nbn5OH8p46myCEJHYG6Ro1YX3Z4dWUyYEVEH/9pzoorjqic5PFGkBE5Totw/iEBlO\nkNle073m8CmJ9SG5awoCjSWRPlbhQuTo1o3ucCX4kwLtTRbWOdafwBL02ZiGD7Et\nIJk10Be68f6e3OO2SE3YEJWE\n-----END PRIVATE KEY-----"}}"""
        return StackitAuthProvider(AuthConfig(serviceAccountKey = key))
    }
}

class TestSubscriberPull {
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
        val key = """{"id":"test-key-id","keyAlgorithm":"RSA","keyOrigin":"USER_PROVIDED","keyType":"USER_MANAGED","publicKey":"fake-public-key","active":true,"createdAt":"2024-01-01T00:00:00Z","keySigningAlgorithm":"RS256","credentials":{"kid":"test-kid","iss":"sa-issuer","sub":"sa-subject","aud":"https://stackit.cloud","privateKey":"-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD3CA8a337KrBXt\nJxNsIN5u8Tw3Elb25/kkmpqwXSvRAUTZzFymsP6DAcz+Bj5O+bUSEujrP06M133n\n3CJyzHQCAUukZXHfilhg0jp7HXNKYlkb8tQxWpVmg3mCGAKozqo8XqZQkObd135N\nDecfTev2I5i4pkQd2TURxIgYKLIF+crklY6df24V2lHLwt1BW22AvfmSoEKzgwbM\nq4TwFMOH9Xavj/134nEgzD2Whi8FoaFLXJRSKILRAGz5xaqQR9i+NxU/dtQKtkj+\nQnFGUbkmdmGHSgrGsp1lcZCCjUZx7sIy4YevMvxnIk1UAzepxpdDU+HZjDYynUq2\nCMrB9g2HAgMBAAECggEAJRUyn0gZkncOu5D4G207ts/DyEiagWoFPndu7TavUyqr\nmT5SUjuTmTr1+NQbGmPlyJbKHDZepkna/qlPCoiiiYKERmNsGrB739zvJngfTHVD\nBy0GqXn9n2vxG5RbIdgUOgfDU6jM31RwA0UpAswBtetWC7H527k1fDtSar4BK2Au\nkbM213sknTTOIIZIktagxmbgUb/Ee852W0HSw/cxFTRmpM2fzn1WUf7qpRWzwyIg\n6LV+UFyim5HhtxjOwgJL0l7ev/JNBK0bC5LH2LmKlO3S/8IS7COlEXiepsc0c/sS\nnnaBblbeNnDlVXm9+KVixc/0DM+g4dPdBbFL1b1fgQKBgQD9/lifxWxR0TmzupRp\neyx4r+jv2ul42Lkar6l7qIdFOf50TA9i6GE92CpdmVT9KR8WN8ZEEUnqkVaI6rai\nxRxlBaOir7yIPw/PKFNU0bpclnzTf2KhHGzfwbgqQGTFBdciB1qdj1DX80jtw3Je\nTx1e1zqUDrZZpucTMitsngha5wKBgQD4+6IkiNA7iXGJKcmDGQE4ceomEJnzWBV8\nEmLwnddeBwsnaXr3nFRewc76youSU9j8WkBPye0kPUHFbpii3F5axmbD74MATr5e\n9rnINeUvHdVwUzwEYjriZ6yynw8TlJgavl0FyhvKsjhORtdEPZXtcvMbmlbvtjYr\nMUOXuaAEYQKBgQDQdJ4Ryrriz1+XJWtGwuO1zVHVAQqatuv8fe0QYzlZ1IYEtqPu\nHiAo3K+wj/ZTrBsqZe4f0Tyj+lJWHqR0JGfwdVdeXvLRs0frUgJKW7Ki6JNcFXFC\nHw0v+1KFwsGxfeCZugVhyMyl37KcCMtloXLOcsBN4WFBZRzDAWroQy9CQwKBgQDm\nd03L0ZQmOAAO/c4YenHFxLP/Tgii1lr0wLQyk3hCAfUSIzH+twbluzIStJ9i8Bf+\n5q9266uFiuoxjUDeJ2tzTQjcvvwcscbHckvfhfvBlDT4RyNbz21AGsUhhdfXwQjW\nYHUModr90eAKnMov0W3RIEDlwQRN2PTlIDG35Mu4oQKBgBq/OT0JJJxVFlA7F7DF\nbn5OH8p46myCEJHYG6Ro1YX3Z4dWUyYEVEH/9pzoorjqic5PFGkBE5Totw/iEBlO\nkNle073m8CmJ9SG5awoCjSWRPlbhQuTo1o3ucCX4kwLtTRbWOdafwBL02ZiGD7Et\nIJk10Be68f6e3OO2SE3YEJWE\n-----END PRIVATE KEY-----"}}"""
        return StackitAuthProvider(AuthConfig(serviceAccountKey = key))
    }

    private fun createSubscriber(
        response: MockResponse,
        retryConfig: RetryConfig = RetryConfig.Default
    ): PubSubSubscriber {
        server.enqueue(response)
        return PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = createAuthProvider(),
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
    fun `pull invalid longPullDurationMs throws`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody("""{"messages":[]}""")

        val subscriber = createSubscriber(response)
        assertThrows(IllegalArgumentException::class.java) {
            subscriber.pull(longPullDurationMs = 10000)
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
            authProvider = createAuthProvider(),
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
            authProvider = createAuthProvider(),
            retryConfig = retryConfig
        )
        val thrown = assertThrows(PubSubAuthError::class.java) {
            subscriber.pull()
        }
        assertTrue(thrown.message!!.contains("retries"))
        assertEquals(3, server.requestCount)
    }
}

class TestSubscriberAck {
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
        val key = """{"id":"test-key-id","keyAlgorithm":"RSA","keyOrigin":"USER_PROVIDED","keyType":"USER_MANAGED","publicKey":"fake-public-key","active":true,"createdAt":"2024-01-01T00:00:00Z","keySigningAlgorithm":"RS256","credentials":{"kid":"test-kid","iss":"sa-issuer","sub":"sa-subject","aud":"https://stackit.cloud","privateKey":"-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD3CA8a337KrBXt\nJxNsIN5u8Tw3Elb25/kkmpqwXSvRAUTZzFymsP6DAcz+Bj5O+bUSEujrP06M133n\n3CJyzHQCAUukZXHfilhg0jp7HXNKYlkb8tQxWpVmg3mCGAKozqo8XqZQkObd135N\nDecfTev2I5i4pkQd2TURxIgYKLIF+crklY6df24V2lHLwt1BW22AvfmSoEKzgwbM\nq4TwFMOH9Xavj/134nEgzD2Whi8FoaFLXJRSKILRAGz5xaqQR9i+NxU/dtQKtkj+\nQnFGUbkmdmGHSgrGsp1lcZCCjUZx7sIy4YevMvxnIk1UAzepxpdDU+HZjDYynUq2\nCMrB9g2HAgMBAAECggEAJRUyn0gZkncOu5D4G207ts/DyEiagWoFPndu7TavUyqr\nmT5SUjuTmTr1+NQbGmPlyJbKHDZepkna/qlPCoiiiYKERmNsGrB739zvJngfTHVD\nBy0GqXn9n2vxG5RbIdgUOgfDU6jM31RwA0UpAswBtetWC7H527k1fDtSar4BK2Au\nkbM213sknTTOIIZIktagxmbgUb/Ee852W0HSw/cxFTRmpM2fzn1WUf7qpRWzwyIg\n6LV+UFyim5HhtxjOwgJL0l7ev/JNBK0bC5LH2LmKlO3S/8IS7COlEXiepsc0c/sS\nnnaBblbeNnDlVXm9+KVixc/0DM+g4dPdBbFL1b1fgQKBgQD9/lifxWxR0TmzupRp\neyx4r+jv2ul42Lkar6l7qIdFOf50TA9i6GE92CpdmVT9KR8WN8ZEEUnqkVaI6rai\nxRxlBaOir7yIPw/PKFNU0bpclnzTf2KhHGzfwbgqQGTFBdciB1qdj1DX80jtw3Je\nTx1e1zqUDrZZpucTMitsngha5wKBgQD4+6IkiNA7iXGJKcmDGQE4ceomEJnzWBV8\nEmLwnddeBwsnaXr3nFRewc76youSU9j8WkBPye0kPUHFbpii3F5axmbD74MATr5e\n9rnINeUvHdVwUzwEYjriZ6yynw8TlJgavl0FyhvKsjhORtdEPZXtcvMbmlbvtjYr\nMUOXuaAEYQKBgQDQdJ4Ryrriz1+XJWtGwuO1zVHVAQqatuv8fe0QYzlZ1IYEtqPu\nHiAo3K+wj/ZTrBsqZe4f0Tyj+lJWHqR0JGfwdVdeXvLRs0frUgJKW7Ki6JNcFXFC\nHw0v+1KFwsGxfeCZugVhyMyl37KcCMtloXLOcsBN4WFBZRzDAWroQy9CQwKBgQDm\nd03L0ZQmOAAO/c4YenHFxLP/Tgii1lr0wLQyk3hCAfUSIzH+twbluzIStJ9i8Bf+\n5q9266uFiuoxjUDeJ2tzTQjcvvwcscbHckvfhfvBlDT4RyNbz21AGsUhhdfXwQjW\nYHUModr90eAKnMov0W3RIEDlwQRN2PTlIDG35Mu4oQKBgBq/OT0JJJxVFlA7F7DF\nbn5OH8p46myCEJHYG6Ro1YX3Z4dWUyYEVEH/9pzoorjqic5PFGkBE5Totw/iEBlO\nkNle073m8CmJ9SG5awoCjSWRPlbhQuTo1o3ucCX4kwLtTRbWOdafwBL02ZiGD7Et\nIJk10Be68f6e3OO2SE3YEJWE\n-----END PRIVATE KEY-----"}}"""
        return StackitAuthProvider(AuthConfig(serviceAccountKey = key))
    }

    private fun createSubscriber(response: MockResponse): PubSubSubscriber {
        server.enqueue(response)
        return PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = createAuthProvider()
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
        val key = """{"id":"test-key-id","keyAlgorithm":"RSA","keyOrigin":"USER_PROVIDED","keyType":"USER_MANAGED","publicKey":"fake-public-key","active":true,"createdAt":"2024-01-01T00:00:00Z","keySigningAlgorithm":"RS256","credentials":{"kid":"test-kid","iss":"sa-issuer","sub":"sa-subject","aud":"https://stackit.cloud","privateKey":"-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD3CA8a337KrBXt\nJxNsIN5u8Tw3Elb25/kkmpqwXSvRAUTZzFymsP6DAcz+Bj5O+bUSEujrP06M133n\n3CJyzHQCAUukZXHfilhg0jp7HXNKYlkb8tQxWpVmg3mCGAKozqo8XqZQkObd135N\nDecfTev2I5i4pkQd2TURxIgYKLIF+crklY6df24V2lHLwt1BW22AvfmSoEKzgwbM\nq4TwFMOH9Xavj/134nEgzD2Whi8FoaFLXJRSKILRAGz5xaqQR9i+NxU/dtQKtkj+\nQnFGUbkmdmGHSgrGsp1lcZCCjUZx7sIy4YevMvxnIk1UAzepxpdDU+HZjDYynUq2\nCMrB9g2HAgMBAAECggEAJRUyn0gZkncOu5D4G207ts/DyEiagWoFPndu7TavUyqr\nmT5SUjuTmTr1+NQbGmPlyJbKHDZepkna/qlPCoiiiYKERmNsGrB739zvJngfTHVD\nBy0GqXn9n2vxG5RbIdgUOgfDU6jM31RwA0UpAswBtetWC7H527k1fDtSar4BK2Au\nkbM213sknTTOIIZIktagxmbgUb/Ee852W0HSw/cxFTRmpM2fzn1WUf7qpRWzwyIg\n6LV+UFyim5HhtxjOwgJL0l7ev/JNBK0bC5LH2LmKlO3S/8IS7COlEXiepsc0c/sS\nnnaBblbeNnDlVXm9+KVixc/0DM+g4dPdBbFL1b1fgQKBgQD9/lifxWxR0TmzupRp\neyx4r+jv2ul42Lkar6l7qIdFOf50TA9i6GE92CpdmVT9KR8WN8ZEEUnqkVaI6rai\nxRxlBaOir7yIPw/PKFNU0bpclnzTf2KhHGzfwbgqQGTFBdciB1qdj1DX80jtw3Je\nTx1e1zqUDrZZpucTMitsngha5wKBgQD4+6IkiNA7iXGJKcmDGQE4ceomEJnzWBV8\nEmLwnddeBwsnaXr3nFRewc76youSU9j8WkBPye0kPUHFbpii3F5axmbD74MATr5e\n9rnINeUvHdVwUzwEYjriZ6yynw8TlJgavl0FyhvKsjhORtdEPZXtcvMbmlbvtjYr\nMUOXuaAEYQKBgQDQdJ4Ryrriz1+XJWtGwuO1zVHVAQqatuv8fe0QYzlZ1IYEtqPu\nHiAo3K+wj/ZTrBsqZe4f0Tyj+lJWHqR0JGfwdVdeXvLRs0frUgJKW7Ki6JNcFXFC\nHw0v+1KFwsGxfeCZugVhyMyl37KcCMtloXLOcsBN4WFBZRzDAWroQy9CQwKBgQDm\nd03L0ZQmOAAO/c4YenHFxLP/Tgii1lr0wLQyk3hCAfUSIzH+twbluzIStJ9i8Bf+\n5q9266uFiuoxjUDeJ2tzTQjcvvwcscbHckvfhfvBlDT4RyNbz21AGsUhhdfXwQjW\nYHUModr90eAKnMov0W3RIEDlwQRN2PTlIDG35Mu4oQKBgBq/OT0JJJxVFlA7F7DF\nbn5OH8p46myCEJHYG6Ro1YX3Z4dWUyYEVEH/9pzoorjqic5PFGkBE5Totw/iEBlO\nkNle073m8CmJ9SG5awoCjSWRPlbhQuTo1o3ucCX4kwLtTRbWOdafwBL02ZiGD7Et\nIJk10Be68f6e3OO2SE3YEJWE\n-----END PRIVATE KEY-----"}}"""
        return StackitAuthProvider(AuthConfig(serviceAccountKey = key))
    }

    private fun createSubscriber(response: MockResponse): PubSubSubscriber {
        server.enqueue(response)
        return PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = createAuthProvider()
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
        val key = """{"id":"test-key-id","keyAlgorithm":"RSA","keyOrigin":"USER_PROVIDED","keyType":"USER_MANAGED","publicKey":"fake-public-key","active":true,"createdAt":"2024-01-01T00:00:00Z","keySigningAlgorithm":"RS256","credentials":{"kid":"test-kid","iss":"sa-issuer","sub":"sa-subject","aud":"https://stackit.cloud","privateKey":"-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD3CA8a337KrBXt\nJxNsIN5u8Tw3Elb25/kkmpqwXSvRAUTZzFymsP6DAcz+Bj5O+bUSEujrP06M133n\n3CJyzHQCAUukZXHfilhg0jp7HXNKYlkb8tQxWpVmg3mCGAKozqo8XqZQkObd135N\nDecfTev2I5i4pkQd2TURxIgYKLIF+crklY6df24V2lHLwt1BW22AvfmSoEKzgwbM\nq4TwFMOH9Xavj/134nEgzD2Whi8FoaFLXJRSKILRAGz5xaqQR9i+NxU/dtQKtkj+\nQnFGUbkmdmGHSgrGsp1lcZCCjUZx7sIy4YevMvxnIk1UAzepxpdDU+HZjDYynUq2\nCMrB9g2HAgMBAAECggEAJRUyn0gZkncOu5D4G207ts/DyEiagWoFPndu7TavUyqr\nmT5SUjuTmTr1+NQbGmPlyJbKHDZepkna/qlPCoiiiYKERmNsGrB739zvJngfTHVD\nBy0GqXn9n2vxG5RbIdgUOgfDU6jM31RwA0UpAswBtetWC7H527k1fDtSar4BK2Au\nkbM213sknTTOIIZIktagxmbgUb/Ee852W0HSw/cxFTRmpM2fzn1WUf7qpRWzwyIg\n6LV+UFyim5HhtxjOwgJL0l7ev/JNBK0bC5LH2LmKlO3S/8IS7COlEXiepsc0c/sS\nnnaBblbeNnDlVXm9+KVixc/0DM+g4dPdBbFL1b1fgQKBgQD9/lifxWxR0TmzupRp\neyx4r+jv2ul42Lkar6l7qIdFOf50TA9i6GE92CpdmVT9KR8WN8ZEEUnqkVaI6rai\nxRxlBaOir7yIPw/PKFNU0bpclnzTf2KhHGzfwbgqQGTFBdciB1qdj1DX80jtw3Je\nTx1e1zqUDrZZpucTMitsngha5wKBgQD4+6IkiNA7iXGJKcmDGQE4ceomEJnzWBV8\nEmLwnddeBwsnaXr3nFRewc76youSU9j8WkBPye0kPUHFbpii3F5axmbD74MATr5e\n9rnINeUvHdVwUzwEYjriZ6yynw8TlJgavl0FyhvKsjhORtdEPZXtcvMbmlbvtjYr\nMUOXuaAEYQKBgQDQdJ4Ryrriz1+XJWtGwuO1zVHVAQqatuv8fe0QYzlZ1IYEtqPu\nHiAo3K+wj/ZTrBsqZe4f0Tyj+lJWHqR0JGfwdVdeXvLRs0frUgJKW7Ki6JNcFXFC\nHw0v+1KFwsGxfeCZugVhyMyl37KcCMtloXLOcsBN4WFBZRzDAWroQy9CQwKBgQDm\nd03L0ZQmOAAO/c4YenHFxLP/Tgii1lr0wLQyk3hCAfUSIzH+twbluzIStJ9i8Bf+\n5q9266uFiuoxjUDeJ2tzTQjcvvwcscbHckvfhfvBlDT4RyNbz21AGsUhhdfXwQjW\nYHUModr90eAKnMov0W3RIEDlwQRN2PTlIDG35Mu4oQKBgBq/OT0JJJxVFlA7F7DF\nbn5OH8p46myCEJHYG6Ro1YX3Z4dWUyYEVEH/9pzoorjqic5PFGkBE5Totw/iEBlO\nkNle073m8CmJ9SG5awoCjSWRPlbhQuTo1o3ucCX4kwLtTRbWOdafwBL02ZiGD7Et\nIJk10Be68f6e3OO2SE3YEJWE\n-----END PRIVATE KEY-----"}}"""
        return StackitAuthProvider(AuthConfig(serviceAccountKey = key))
    }

    private fun createSubscriber(response: MockResponse): PubSubSubscriber {
        server.enqueue(response)
        return PubSubSubscriber(
            subscriptionId = UUID.randomUUID(),
            baseDataplaneUrl = baseUrl,
            authProvider = createAuthProvider()
        )
    }

    @Test
    fun `pull 404 throws not found`() {
        val response = MockResponse()
            .setResponseCode(404)
            .setBody("Not found")

        val subscriber = createSubscriber(response)
        val thrown = assertThrows(PubSubAuthError::class.java) {
            subscriber.pull()
        }
        assertTrue(thrown.message!!.contains("Not found"))
    }

    @Test
    fun `pull 401 throws auth error`() {
        val response = MockResponse()
            .setResponseCode(401)
            .setBody("Unauthorized")

        val subscriber = createSubscriber(response)
        assertThrows(PubSubAuthError::class.java) {
            subscriber.pull()
        }
    }

    @Test
    fun `ack 404 throws not found`() {
        val response = MockResponse()
            .setResponseCode(404)
            .setBody("Not found")

        val subscriber = createSubscriber(response)
        assertThrows(PubSubAuthError::class.java) {
            subscriber.ack(listOf("ack-1"))
        }
    }

    @Test
    fun `nack 404 throws not found`() {
        val response = MockResponse()
            .setResponseCode(404)
            .setBody("Not found")

        val subscriber = createSubscriber(response)
        assertThrows(PubSubAuthError::class.java) {
            subscriber.nack(listOf("nack-1"))
        }
    }
}
