package org.example.pubsub.publisher

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.example.pubsub.auth.StackitAuthProvider
import org.example.pubsub.errors.PubSubAuthError
import org.example.pubsub.models.PublishMessage
import org.example.pubsub.models.PublishMessagesRequest
import org.example.pubsub.models.PublishMessagesResponse
import org.example.pubsub.retry.RetryConfig
import org.example.pubsub.retry.isRetryableException
import org.example.pubsub.retry.isRetryableStatusCode
import java.io.IOException
import java.time.Duration
import java.util.UUID

/**
 * Publisher interface for sending messages to a STACKIT PubSub topic.
 *
 * @param topicId The topic UUID.
 * @param baseDataplaneUrl The dataplane base URL.
 * @param authProvider Provider for STACKIT authorization tokens.
 * @param retryConfig Configuration for retry behavior.
 * @param messagesSent Total count of messages sent.
 * @param publishCalls Total count of publish calls made.
 * @param purgeCalls Total count of purge calls made.
 */
class PubSubPublisher(
    private val topicId: UUID,
    private val baseDataplaneUrl: String,
    private val authProvider: StackitAuthProvider,
    private val retryConfig: RetryConfig = RetryConfig.Default
) {
    private val gson = Gson()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(10))
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    var messagesSent: Int = 0
        private set

    var publishCalls: Int = 0
        private set

    var purgeCalls: Int = 0
        private set

    /**
     * Publish messages to the topic.
     *
     * @param messages List of messages to publish (max 100).
     * @return Response with assigned message IDs.
     */
    fun publish(messages: List<PublishMessage>): PublishMessagesResponse {
        require(messages.isNotEmpty()) { "Cannot publish an empty list of messages" }
        require(messages.size <= 100) { "Maximum 100 messages per publish request" }

        publishCalls++
        messagesSent += messages.size

        val request = Request.Builder()
            .url("$baseDataplaneUrl/v1alpha/publish")
            .post(
                gson.toJson(PublishMessagesRequest(messages)).toRequestBody(jsonMediaType)
            )
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .build()

        val (statusCode, body) = executeWithRetry(request)

        if (statusCode == 200) {
            val response = gson.fromJson(body, PublishMessagesResponse::class.java)
            return response
        }

        handleErrorResponse(statusCode, body)
        throw PubSubAuthError.TokenAcquisitionFailed("Unexpected status: $statusCode")
    }

    /**
     * Publish messages in batches.
     *
     * @param messages All messages to publish.
     * @param batchSize Maximum messages per batch (default: 100).
     * @return List of responses, one per batch.
     */
    fun publishBatch(messages: List<PublishMessage>, batchSize: Int = 100): List<PublishMessagesResponse> {
        if (messages.isEmpty()) return emptyList()

        val responses = mutableListOf<PublishMessagesResponse>()
        messages.chunked(batchSize).forEach { batch ->
            responses.add(publish(batch))
        }
        return responses
    }

    /**
     * Purge all messages from the topic.
     */
    fun purgeTopic() {
        purgeCalls++

        val request = Request.Builder()
            .url("$baseDataplaneUrl/v1alpha/purge")
            .post("{}".toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .build()

        val (statusCode, _) = executeWithRetry(request)

        if (statusCode != 204) {
            throw PubSubAuthError.TokenAcquisitionFailed("Purge failed with status $statusCode")
        }
    }

    private fun executeWithRetry(request: Request): Pair<Int, String> {
        var lastException: Throwable? = null

        for (attempt in 0..retryConfig.maxRetries) {
            try {
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val statusCode = response.code

                if (statusCode == 200 || statusCode == 204) {
                    return statusCode to body
                }

                if (isRetryableStatusCode(statusCode)) {
                    if (attempt >= retryConfig.maxRetries) {
                        throw PubSubAuthError.TokenAcquisitionFailed("Server error $statusCode after $attempt retries: $body")
                    }
                    val delay = org.example.pubsub.retry.getRetryDelay(retryConfig, attempt)
                    Thread.sleep(delay)
                    continue
                }

                return statusCode to body
            } catch (e: IOException) {
                lastException = e
                if (!isRetryableException(e) || attempt >= retryConfig.maxRetries) {
                    throw PubSubAuthError.TokenAcquisitionFailed("Request failed: ${e.message}", e)
                }
                val delay = org.example.pubsub.retry.getRetryDelay(retryConfig, attempt)
                Thread.sleep(delay)
            }
        }

        throw PubSubAuthError.TokenAcquisitionFailed("Request failed after retries", lastException)
    }

    private fun handleErrorResponse(statusCode: Int, body: String) {
        when (statusCode) {
            400 -> throw PubSubAuthError.TokenAcquisitionFailed("Bad request: $body")
            401 -> throw PubSubAuthError.TokenAcquisitionFailed("Unauthorized: $body")
            403 -> throw PubSubAuthError.TokenAcquisitionFailed("Forbidden: $body")
            404 -> throw PubSubAuthError.TokenAcquisitionFailed("Not found: $body")
            413 -> throw PubSubAuthError.TokenAcquisitionFailed("Request body too large: $body")
            in 500..599 -> throw PubSubAuthError.TokenAcquisitionFailed("Server error $statusCode: $body")
            else -> throw PubSubAuthError.TokenAcquisitionFailed("Unexpected error $statusCode: $body")
        }
    }
}
