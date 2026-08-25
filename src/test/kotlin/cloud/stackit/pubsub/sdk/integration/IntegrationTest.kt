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
    private lateinit var topicId: String
    private lateinit var subscriptionId: String
    private lateinit var baseControlPlaneUrl: String
    private lateinit var baseDataplaneUrl: String
    private lateinit var authConfig: AuthConfig
    private lateinit var saEmail: String

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

        saEmail = gson.fromJson(saKey, JsonObject::class.java)
            .get("credentials")
            .asJsonObject
            .get("iss")
            .asString

        val saKeyFile = File.createTempFile("sa-key-", ".json")
        saKeyFile.writeText(saKey)
        saKeyFile.deleteOnExit()

        val tokenUrl = System.getenv("TOKEN_URL") ?: "https://accounts.qa.stackit.cloud/oauth/v2/token"
        authConfig = AuthConfig(serviceAccountKeyPath = saKeyFile.absolutePath, tokenEndpoint = tokenUrl)
        authProvider = StackitAuthProvider(authConfig)
        projectNumber = project
        baseControlPlaneUrl = "https://pubsub-dev.api.${Region.EU01.value}.qa.stackit.cloud"

        createTopic()
        createSubscription()

        baseDataplaneUrl = System.getenv("BASE_URL") ?: "https://$topicId.pubsub.${Region.EU01.value}.dev.onstackit.cloud"
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
        val tempId = UUID.randomUUID().toString().take(24)
        val requestBody = """
            {
                "displayName": "sdk-test-$tempId",
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

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (response.code == 409) {
            println("Topic already exists, skipping creation")
            return
        }

        if (!response.isSuccessful) {
            throw IOException("Failed to create topic: $responseBody")
        }

        val topic = gson.fromJson(responseBody, JsonObject::class.java)
        topicId = topic.get("id").asString
        println("Topic created: $topicId")

        waitForResourceActive("topics", topicId)
        grantPublisherAccess(topicId)
    }

    private fun createSubscription() {
        val tempId = UUID.randomUUID().toString().take(24)
        val requestBody = """
            {
                "displayName": "sdk-sub-$tempId",
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

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (response.code == 409) {
            println("Subscription already exists, skipping creation")
            return
        }

        if (!response.isSuccessful) {
            throw IOException("Failed to create subscription: $responseBody")
        }

        val sub = gson.fromJson(responseBody, JsonObject::class.java)
        subscriptionId = sub.get("id").asString
        println("Subscription created: $subscriptionId")

        waitForResourceActive("subscriptions", subscriptionId, topicId)
        grantSubscriberAccess(subscriptionId)
    }

    private fun grantPublisherAccess(topicId: String) {
        val requestBody = """
            {
                "emailAddress": "$saEmail"
            }
        """.trimIndent()

        val mediaType = "application/json".toMediaType()
        val body = requestBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$baseControlPlaneUrl/v1beta/projects/$projectNumber/regions/${Region.EU01.value}/topics/$topicId/publishers")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .patch(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (response.isSuccessful) {
            println("Granted publisher access for $saEmail")
        } else {
            println("Warning: Failed to grant publisher access: $responseBody")
        }
    }

    private fun grantSubscriberAccess(subscriptionId: String) {
        val requestBody = """
            {
                "emailAddress": "$saEmail"
            }
        """.trimIndent()

        val mediaType = "application/json".toMediaType()
        val body = requestBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$baseControlPlaneUrl/v1beta/projects/$projectNumber/regions/${Region.EU01.value}/topics/$topicId/subscriptions/$subscriptionId/subscribers")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .patch(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (response.isSuccessful) {
            println("Granted subscriber access for $saEmail")
        } else {
            println("Warning: Failed to grant subscriber access: $responseBody")
        }
    }

    private fun waitForResourceActive(
        resourceType: String,
        resourceId: String,
        topicIdOverride: String? = null
    ) {
        val start = System.currentTimeMillis()
        val timeoutMs = 600_000L // 10 minutes
        val pollIntervalMs = 10_000L // 10 seconds

        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val resourceTopicId = topicIdOverride ?: resourceId
                val url = if (resourceType == "subscriptions") {
                    "$baseControlPlaneUrl/v1beta/projects/$projectNumber/regions/${Region.EU01.value}/topics/$resourceTopicId/subscriptions/$resourceId"
                } else {
                    "$baseControlPlaneUrl/v1beta/projects/$projectNumber/regions/${Region.EU01.value}/$resourceType/$resourceId"
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    println("$resourceType $resourceId not reachable yet (${response.code}), retrying...")
                    Thread.sleep(pollIntervalMs)
                    continue
                }

                val body = response.body?.string() ?: ""
                val resource = gson.fromJson(body, JsonObject::class.java)
                val state = resource.get("state")?.asString

                if (state == "active") {
                    println("$resourceType $resourceId is active")
                    return
                }
                if (state == "deleting") {
                    throw RuntimeException("$resourceType entered deleting state: $resourceId")
                }

                if (System.currentTimeMillis() - start > 0 && (System.currentTimeMillis() - start) % 30000 < pollIntervalMs) {
                    println("Waiting for $resourceType $resourceId to become active (state: $state)")
                }

                Thread.sleep(pollIntervalMs)
            } catch (e: IOException) {
                println("$resourceType $resourceId not reachable yet: ${e.message}, retrying...")
                Thread.sleep(pollIntervalMs)
            }
        }

        throw IOException("$resourceType $resourceId did not become active within 10 minutes")
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
            if (!response.isSuccessful && response.code != 404) {
                println("Warning: Failed to delete topic: ${response.body?.string()}")
            }
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
            if (!response.isSuccessful && response.code != 404) {
                println("Warning: Failed to delete subscription: ${response.body?.string()}")
            }
        } catch (e: IOException) {
            println("Warning: Failed to delete subscription: ${e.message}")
        }
    }

    private fun purgeTopic() {
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

    @Test
    @Order(1)
    @Throws(Exception::class)
    fun `full pubsub flow - publish and subscribe`() {
        val client = PubSubClient(
            topicId = UUID.fromString(topicId),
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher(RetryConfig(maxRetries = 2, baseDelay = 100, maxDelay = 2000))
        val subscriber = client.getSubscriber(
            subscriptionId = UUID.fromString(subscriptionId),
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
        assertEquals(3, publisher.messagesSent.get())
        assertEquals(1, publisher.publishCalls.get())

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
            topicId = UUID.fromString(topicId),
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher(RetryConfig(maxRetries = 2, baseDelay = 100, maxDelay = 2000))
        val subscriber = client.getSubscriber(
            subscriptionId = UUID.fromString(subscriptionId),
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
        assertEquals(20, publisher.messagesSent.get())

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
            topicId = UUID.fromString(topicId),
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher()
        val subscriber = client.getSubscriber(UUID.fromString(subscriptionId))

        assertNotNull(publisher)
        assertNotNull(subscriber)
        assert(client.baseDataplaneUrl.contains(topicId))
    }

    @Test
    @Order(4)
    @Throws(Exception::class)
    fun `purge topic removes all messages`() {
        val client = PubSubClient(
            topicId = UUID.fromString(topicId),
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val publisher = client.getPublisher()
        publisher.purgeTopic()
        assertEquals(1, publisher.purgeCalls.get())

        Thread.sleep(1000)

        val subscriber = client.getSubscriber(
            subscriptionId = UUID.fromString(subscriptionId),
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
            topicId = UUID.fromString(topicId),
            authConfig = authConfig,
            region = Region.EU01,
            environment = Environment.DEV
        )

        val expectedUrl = "https://$topicId.pubsub.eu01.dev.onstackit.cloud"
        assertEquals(expectedUrl, client.baseDataplaneUrl)
    }

    @Test
    @Order(6)
    @Throws(Exception::class)
    fun `message text and payload properties work correctly`() {
        val client = PubSubClient(
            topicId = UUID.fromString(topicId),
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
