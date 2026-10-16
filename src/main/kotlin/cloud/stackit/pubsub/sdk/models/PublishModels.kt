package cloud.stackit.pubsub.sdk.models

/**
 * A single message to publish to a topic.
 *
 * @param data Base64-encoded message payload.
 */
data class PublishMessage(
    val data: String
)

/**
 * Request to publish messages to a topic.
 *
 * @param messages The messages to publish.
 */
data class PublishMessagesRequest(
    val messages: List<PublishMessage>
)

