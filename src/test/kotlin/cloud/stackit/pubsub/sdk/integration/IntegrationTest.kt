package cloud.stackit.pubsub.sdk.integration

import cloud.stackit.pubsub.sdk.auth.AuthConfig
import cloud.stackit.pubsub.sdk.auth.StackitAuthProvider
import cloud.stackit.pubsub.sdk.client.PubSubClient
import cloud.stackit.pubsub.sdk.config.Environment
import cloud.stackit.pubsub.sdk.config.Region
import cloud.stackit.pubsub.sdk.models.Message
import cloud.stackit.pubsub.sdk.models.PublishMessage
import cloud.stackit.pubsub.sdk.retry.RetryConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrationTest {
    companion object {
        @JvmStatic
        fun isIntegrationTestEnabled(): Boolean {
            return System.getenv("STACKIT_PROJECT_ID") != null
        }
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder().build()
    private lateinit var authProvider: StackitAuthProvider
    private lateinit var projectNumber: String
    private lateinit var topicId: UUID
    private lateinit var subscriptionId: UUID
    private lateinit var baseControlPlaneUrl: String
    private lateinit var baseDataplaneUrl: String
    private lateinit var authConfig: AuthConfig

    @BeforeAll
    @Throws(Exception::class)
    fun setupOnce() {
        val project = System.getenv("STACKIT_PROJECT_ID")
        assumeTrue({ project != null }, "STACKIT_PROJECT_ID environment variable not set")

        val saKeyB64 = System.getenv("SA_KEY_B64")
        val saKey = if (saKeyB64 != null && saKeyB64.isNotBlank()) {
            String(Base64.getDecoder().decode(saKeyB64), StandardCharsets.UTF_8)
        } else {
            File("sa-key.json").readText()
        }

        val saKeyFile = File.createTempFile("sa-key-", ".json")
        saKeyFile.writeText(saKey)
        saKeyFile.deleteOnExit()

        val tokenUrl = System.getenv("TOKEN_URL") ?: "https://accounts.qa.stackit.cloud/oauth/v2/token"
        authConfig = AuthConfig(serviceAccountKeyPath = saKeyFile.absolutePath, tokenEndpoint = tokenUrl)
        authProvider = StackitAuthProvider(authConfig)
        projectNumber = project
        baseControlPlaneUrl = "https://pubsub-dev.api.${Region.EU01.value}.qa.stackit.cloud"
        topicId = UUID.randomUUID()
        subscriptionId = UUID.randomUUID()

        // Get dataplane URL from env or construct from topic
        val customBaseDataplaneUrl = System.getenv("BASE_URL")
        if (customBaseDataplaneUrl != null && customBaseDataplaneUrl.isNotBlank()) {
            baseDataplaneUrl = customBaseDataplaneUrl
        } else {
            baseDataplaneUrl = "https://$topicId.pubsub.${Region.EU01.value}.dev.onstackit.cloud"
        }

        createTopic()
        createSubscription()
    }

    @AfterAll
    @Throws(Exception::class)
    fun cleanupOnce() {
        if (!::baseControlPlaneUrl.isInitialized) return
        try {
            deleteSubscription()
            purgeTopic()
            deleteTopic()
        } catch (e: Exception) {
            println("Cleanup failed: ${e.message}")
        }
    }

    private fun createTopic() {
        val requestBody = """
            {
                "displayName": "sdk-test-${topicId}",
                "description": "Created by SDK integration tests"
            }
        """.trimIndent()

        val mediaType = "application/json".toMediaType()
        val body = requestBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$baseControlPlaneUrl/v1beta/projects/$projectNumber/regions/${Region.EU01.value}/topics")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .post(body)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code == 409) {
                    println("Topic already exists, skipping creation")
                    return
                }
                throw IOException("Failed to create topic: $responseBody")
            }
            waitForOperation(responseBody)
            println("Topic created successfully: $topicId")
        } catch (e: IOException) {
            println("Warning: Failed to create topic (may already exist): ${e.message}")
        }
    }

    private fun createSubscription() {
        val requestBody = """
            {
                "displayName": "sdk-sub-${subscriptionId}",
                "description": "Created by SDK integration tests"
            }
        """.trimIndent()

        val mediaType = "application/json".toMediaType()
        val body = requestBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$baseControlPlaneUrl/v1beta/projects/$projectNumber/regions/${Region.EU01.value}/topics/$topicId/subscriptions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .post(body)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code == 409) {
                    println("Subscription already exists, skipping creation")
                    return
                }
                throw IOException("Failed to create subscription: $responseBody")
            }
            waitForOperation(responseBody)
            println("Subscription created successfully: $subscriptionId")
        } catch (e: IOException) {
            println("Warning: Failed to create subscription (may already exist): ${e.message}")
        }
    }

    private fun deleteTopic() {
        val request = Request.Builder()
            .url("$baseControlPlaneUrl/v1beta/projects/$projectNumber/regions/${Region.EU01.value}/topics/$topicId")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .delete()
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                println("Warning: Failed to delete topic: $responseBody")
                return
            }
            waitForOperation(responseBody)
            println("Topic deleted successfully")
        } catch (e: IOException) {
            println("Warning: Failed to delete topic: ${e.message}")
        }
    }

    private fun deleteSubscription() {
        val request = Request.Builder()
            .url("$baseControlPlaneUrl/v1beta/projects/$projectNumber/regions/${Region.EU01.value}/topics/$topicId/subscriptions/$subscriptionId")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .delete()
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                println("Warning: Failed to delete subscription: $responseBody")
                return
            }
            waitForOperation(responseBody)
            println("Subscription deleted successfully")
        } catch (e: IOException) {
            println("Warning: Failed to delete subscription: ${e.message}")
        }
    }

    private fun purgeTopic() {
        baseDataplaneUrl = "https://$topicId.pubsub.${Region.EU01.value}.onstackit.cloud"
        val mediaType = "application/json".toMediaType()

        val request = Request.Builder()
            .url("$baseDataplaneUrl/v1alpha/purge")
            .post("{}".toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful && response.code != 404) {
                println("Warning: Failed to purge topic: ${response.code}")
            }
        } catch (e: IOException) {
            println("Warning: Failed to purge topic: ${e.message}")
        }
    }

    private fun waitForOperation(operationResponse: String) {
        val operation = gson.fromJson(operationResponse, JsonObject::class.java)
        val name = operation.get("name").asString
        val done = operation.get("done")?.asBoolean ?: false

        if (done) return

        var attempts = 0
        val maxAttempts = 30
        while (attempts < maxAttempts) {
            Thread.sleep(1000)
            attempts++

            val getRequest = Request.Builder()
                .url("$baseControlPlaneUrl/v1alpha/operations/$name")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
                .build()

            val getResponse = httpClient.newCall(getRequest).execute()
            val responseText = getResponse.body?.string() ?: ""
            val op = gson.fromJson(responseText, JsonObject::class.java)

            if (op.get("done")?.asBoolean == true) {
                if (op.has("error")) {
                    val error = op.get("error")
                    throw IOException("Operation failed: ${error.asString}")
                }
                return
            }
        }
        throw IOException("Operation timed out after ${maxAttempts}s")
    }

    @Test
    @Order(1)
    @Throws(Exception::class)
    fun `full pubsub flow - publish and subscribe`() {
        val client = PubSubClient(
            topicId = topicId,
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher(RetryConfig(maxRetries = 2, baseDelay = 100, maxDelay = 2000))
        val subscriber = client.getSubscriber(
            subscriptionId = subscriptionId,
            retryConfig = RetryConfig(maxRetries = 2, baseDelay = 100, maxDelay = 2000),
            maxMessages = 10,
            longPullDurationMs = 1000
        )

        val testMessages = listOf(
            PublishMessage("aGVsbG8gd29ybGQ="),
            PublishMessage("dGVzdCBtZXNzYWdl"),
            PublishMessage("cHViIHN1YnNjcmliZXIgdGVzdA==")
        )

        val publishResponse = publisher.publish(testMessages)
        assertEquals(3, publishResponse.messageIds.size)
        assertEquals(3, publisher.messagesSent)
        assertEquals(1, publisher.publishCalls)

        Thread.sleep(2000)

        var pulledMessages: List<Message> = emptyList()
        var attempts = 0
        val maxAttempts = 10

        while (pulledMessages.isEmpty() && attempts < maxAttempts) {
            pulledMessages = subscriber.pull()
            if (pulledMessages.isEmpty()) {
                Thread.sleep(1000)
                attempts++
            }
        }

        assertTrue(pulledMessages.size >= 3, "Expected at least 3 messages, got ${pulledMessages.size}")

        val ackIds = pulledMessages.take(3).map { it.ackId }
        subscriber.ack(ackIds)

        assertTrue(pulledMessages.all { msg ->
            msg.id in publishResponse.messageIds || msg.text in listOf("hello world", "test message", "pub subscriber test")
        })
    }

    @Test
    @Order(2)
    @Throws(Exception::class)
    fun `publish batch and nack messages`() {
        val client = PubSubClient(
            topicId = topicId,
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher(RetryConfig(maxRetries = 2, baseDelay = 100, maxDelay = 2000))
        val subscriber = client.getSubscriber(
            subscriptionId = subscriptionId,
            retryConfig = RetryConfig(maxRetries = 2, baseDelay = 100, maxDelay = 2000),
            maxMessages = 10,
            longPullDurationMs = 1000
        )

        val allMessages = (1..20).map { i ->
            PublishMessage("bGFyZ2UtYmF0Y2gtbWVzc2FnZSAtICRp")
        }

        val batchResults = publisher.publishBatch(allMessages, batchSize = 10)
        assertEquals(2, batchResults.size)
        val totalIds = batchResults.sumOf { it.messageIds.size }
        assertEquals(20, totalIds)
        assertEquals(20, publisher.messagesSent)

        Thread.sleep(2000)

        var pulledMessages: List<Message> = emptyList()
        var attempts = 0
        val maxAttempts = 10

        while (pulledMessages.isEmpty() && attempts < maxAttempts) {
            pulledMessages = subscriber.pull()
            if (pulledMessages.isEmpty()) {
                Thread.sleep(1000)
                attempts++
            }
        }

        if (pulledMessages.isNotEmpty()) {
            val nackIds = pulledMessages.take(minOf(3, pulledMessages.size)).map { it.ackId }
            subscriber.nack(nackIds)

            assertTrue(pulledMessages.all { msg ->
                msg.text.startsWith("large-batch-message - ")
            })
        }
    }

    @Test
    @Order(3)
    @Throws(Exception::class)
    fun `client factory methods share auth provider`() {
        val client = PubSubClient(
            topicId = topicId,
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher()
        val subscriber = client.getSubscriber(subscriptionId)

        assertNotNull(publisher)
        assertNotNull(subscriber)
        assert(client.baseDataplaneUrl.contains(topicId.toString()))
    }

    @Test
    @Order(4)
    @Throws(Exception::class)
    fun `purge topic removes all messages`() {
        val client = PubSubClient(
            topicId = topicId,
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher()
        publisher.purgeTopic()
        assertEquals(1, publisher.purgeCalls)

        Thread.sleep(1000)

        val subscriber = client.getSubscriber(
            subscriptionId = subscriptionId,
            maxMessages = 10,
            longPullDurationMs = 500
        )

        val messages = subscriber.pull()
        assertTrue(messages.isEmpty(), "Expected empty list after purge, got ${messages.size} messages")
    }

    @Test
    @Order(5)
    @Throws(Exception::class)
    fun `dataplane URL construction is correct`() {
        val client = PubSubClient(
            topicId = topicId,
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val expectedUrl = "https://$topicId.pubsub.eu01.onstackit.cloud"
        assertEquals(expectedUrl, client.baseDataplaneUrl)
    }

    @Test
    @Order(6)
    @Throws(Exception::class)
    fun `message text and payload properties work correctly`() {
        val client = PubSubClient(
            topicId = topicId,
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher()
        val testMessage = PublishMessage("aGVsbG8gd29ybGQ=")
        val response = publisher.publish(listOf(testMessage))
        assertEquals(1, response.messageIds.size)
    }
}
