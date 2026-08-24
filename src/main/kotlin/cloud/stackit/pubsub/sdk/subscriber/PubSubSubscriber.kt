package cloud.stackit.pubsub.sdk.subscriber

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonDeserializationContext
import java.lang.reflect.Type
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import cloud.stackit.pubsub.sdk.auth.AuthProvider
import cloud.stackit.pubsub.sdk.errors.PubSubAuthError
import cloud.stackit.pubsub.sdk.models.AckMessagesRequest
import cloud.stackit.pubsub.sdk.models.Message
import cloud.stackit.pubsub.sdk.models.MessagesResponse
import cloud.stackit.pubsub.sdk.models.NackMessagesRequest
import cloud.stackit.pubsub.sdk.retry.RetryConfig
import cloud.stackit.pubsub.sdk.retry.getRetryDelay
import cloud.stackit.pubsub.sdk.retry.isRetryableException
import cloud.stackit.pubsub.sdk.retry.isRetryableStatusCode
import java.io.IOException
import java.time.Duration
import java.util.UUID

/**
 * Subscriber interface for receiving messages from a STACKIT PubSub subscription.
 *
 * @param subscriptionId The subscription UUID.
 * @param baseDataplaneUrl The dataplane base URL.
 * @param authProvider Provider for STACKIT authorization tokens.
 * @param retryConfig Configuration for retry behavior.
 * @param maxMessages Maximum messages to pull per request.
 * @param longPullDurationMs Long pull timeout in milliseconds.
 */
class PubSubSubscriber(
    private val subscriptionId: UUID,
    private val baseDataplaneUrl: String,
    private val authProvider: AuthProvider,
    private val retryConfig: RetryConfig = RetryConfig.Default,
    private val maxMessages: Int = 16,
    private val longPullDurationMs: Int = 5000
) {
    private val gson = GsonBuilder()
        .registerTypeAdapter(java.time.Instant::class.java, object : JsonDeserializer<java.time.Instant> {
            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): java.time.Instant {
                return java.time.Instant.parse(json.asString)
            }
        })
        .registerTypeAdapter(java.time.Instant::class.java, object : JsonSerializer<java.time.Instant> {
            override fun serialize(src: java.time.Instant, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
                return JsonPrimitive(src.toString())
            }
        })
        .create()
    private val jsonMediaType = "application/json".toMediaType()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(10))
        .build()

    init {
        require(maxMessages in 1..32) { "maxMessages must be between 1 and 32" }
        require(longPullDurationMs in 100..5000) { "longPullDurationMs must be between 100 and 5000" }
    }

    /**
     * Pull messages from the subscription.
     *
     * @param maxMessages Override max messages for this request (default: 16, max: 32).
     * @param longPullDurationMs Override long pull duration (default: 5000ms).
     * @return List of messages.
     */
    fun pull(
        maxMessages: Int = this.maxMessages,
        longPullDurationMs: Int = this.longPullDurationMs
    ): List<Message> {
        require(maxMessages in 1..32) { "maxMessages must be between 1 and 32" }

        val request = Request.Builder()
            .url("$baseDataplaneUrl/v1alpha/subscriptions/$subscriptionId/pull")
            .post("{}".toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .addHeader("PubSub-Max-Messages", maxMessages.toString())
            .addHeader("PubSub-Long-Pull-Duration", longPullDurationMs.toString())
            .build()

        val (statusCode, body) = executeWithRetry(request)

        if (statusCode == 200) {
            val response = gson.fromJson(body, MessagesResponse::class.java)
            return response.messages
        }

        handleErrorResponse(statusCode, body)
        return emptyList()
    }

    /**
     * Acknowledge messages.
     *
     * @param ackIds IDs of messages to acknowledge.
     */
    fun ack(ackIds: List<String>) {
        require(ackIds.isNotEmpty()) { "ackIds must not be empty" }

        val request = Request.Builder()
            .url("$baseDataplaneUrl/v1alpha/subscriptions/$subscriptionId/ack")
            .post(gson.toJson(AckMessagesRequest(ackIds)).toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .build()

        val (statusCode, body) = executeWithRetry(request)

        if (statusCode == 204) return

        handleErrorResponse(statusCode, body)
    }

    /**
     * Negatively acknowledge messages (requeue).
     *
     * @param nackIds IDs of messages to nack.
     */
    fun nack(nackIds: List<String>) {
        require(nackIds.isNotEmpty()) { "nackIds must not be empty" }

        val request = Request.Builder()
            .url("$baseDataplaneUrl/v1alpha/subscriptions/$subscriptionId/nack")
            .post(gson.toJson(NackMessagesRequest(nackIds)).toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${authProvider.getToken()}")
            .build()

        val (statusCode, body) = executeWithRetry(request)

        if (statusCode == 204) return

        handleErrorResponse(statusCode, body)
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
                    val delay = getRetryDelay(retryConfig, attempt)
                    Thread.sleep(delay)
                    continue
                }

                return statusCode to body
            } catch (e: IOException) {
                lastException = e
                if (!isRetryableException(e) || attempt >= retryConfig.maxRetries) {
                    throw PubSubAuthError.TokenAcquisitionFailed("Request failed: ${e.message}", e)
                }
                val delay = getRetryDelay(retryConfig, attempt)
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
