package cloud.stackit.pubsub.sdk.publisher

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import cloud.stackit.pubsub.sdk.auth.AuthProvider
import cloud.stackit.pubsub.sdk.errors.PubSubError
import cloud.stackit.pubsub.sdk.models.PublishMessage
import cloud.stackit.pubsub.sdk.models.PublishMessagesRequest
import cloud.stackit.pubsub.sdk.retry.RetryConfig
import cloud.stackit.pubsub.sdk.retry.isRetryableException
import cloud.stackit.pubsub.sdk.retry.isRetryableStatusCode
import cloud.stackit.pubsub.sdk.retry.getRetryDelay
import java.io.IOException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Publisher interface for sending messages to a STACKIT PubSub topic.
 *
 * @param topicId The topic UUID.
 * @param baseDataplaneUrl The dataplane base URL.
 * @param authProvider Provider for STACKIT authorization tokens.
 * @param retryConfig Configuration for retry behavior.
 * @param httpClient Shared HTTP client for connection reuse.
 * @param messagesSent Total count of messages sent.
 * @param publishCalls Total count of publish calls made.
 * @param purgeCalls Total count of purge calls made.
 */
class PubSubPublisher(
    private val topicId: UUID,
    private val baseDataplaneUrl: String,
    private val authProvider: AuthProvider,
    private val retryConfig: RetryConfig = RetryConfig.Default,
    httpClient: OkHttpClient? = null
) {
    private val gson = Gson()
    private val httpClient: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(10))
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    val messagesSent: AtomicInteger = AtomicInteger(0)

    val publishCalls: AtomicInteger = AtomicInteger(0)

    val purgeCalls: AtomicInteger = AtomicInteger(0)

    /**
     * Publish messages to the topic.
     *
     * @param messages List of messages to publish (max 100).
     */
    fun publish(messages: List<PublishMessage>) {
        require(messages.isNotEmpty()) { "Cannot publish an empty list of messages" }
        require(messages.size <= 100) { "Maximum 100 messages per publish request" }

        publishCalls.incrementAndGet()
        messagesSent.addAndGet(messages.size)

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

        if (statusCode != 204) {
            handleErrorResponse(statusCode, body)
        }
    }

    /**
     * Publish messages in batches.
     *
     * @param messages All messages to publish.
     * @param batchSize Maximum messages per batch (default: 100).
     */
    fun publishBatch(messages: List<PublishMessage>, batchSize: Int = 100) {
        messages.chunked(batchSize).forEach { batch ->
            publish(batch)
        }
    }

    /**
     * Purge all messages from the topic.
     */
    fun purgeTopic() {
        purgeCalls.incrementAndGet()

        val request = Request.Builder()
            .url("$baseDataplaneUrl/v1alpha/purge")
            .post("{}".toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .build()

        val (statusCode, _) = executeWithRetry(request)

        if (statusCode != 204) {
            handleErrorResponse(statusCode, "")
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
                        throw PubSubError.HttpError.ServerError("Server error $statusCode after $attempt retries: $body")
                    }
                    val delay = getRetryDelay(retryConfig, attempt)
                    Thread.sleep(delay)
                    continue
                }

                return statusCode to body
            } catch (e: IOException) {
                lastException = e
                if (!isRetryableException(e) || attempt >= retryConfig.maxRetries) {
                    throw PubSubError.RequestFailed("Request failed: ${e.message}", e)
                }
                val delay = getRetryDelay(retryConfig, attempt)
                Thread.sleep(delay)
            }
        }

        @Suppress("UNCHECKED_CAST")
        throw (lastException as? Exception) ?: throw lastException!!
    }

    private fun handleErrorResponse(statusCode: Int, body: String): Nothing {
        val error = when (statusCode) {
            400 -> PubSubError.HttpError.BadRequest("Bad request: $body")
            401 -> PubSubError.HttpError.Unauthorized("Unauthorized: $body")
            403 -> PubSubError.HttpError.Forbidden("Forbidden: $body")
            404 -> PubSubError.HttpError.NotFound("Not found: $body")
            413 -> PubSubError.HttpError.PayloadTooLarge("Request body too large: $body")
            in 500..599 -> PubSubError.HttpError.ServerError("Server error $statusCode: $body")
            else -> PubSubError.HttpError.Unexpected("Unexpected error $statusCode: $body")
        }
        throw error
    }
}
