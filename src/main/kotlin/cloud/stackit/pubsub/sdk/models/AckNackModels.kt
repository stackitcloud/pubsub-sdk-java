package cloud.stackit.pubsub.sdk.models

/**
 * Request to acknowledge messages.
 *
 * @param ackIds IDs of messages to acknowledge.
 */
data class AckMessagesRequest(
    val ackIds: List<String>
)

/**
 * Request to negatively acknowledge messages (requeue).
 *
 * @param nackIds IDs of messages to nack.
 */
data class NackMessagesRequest(
    val nackIds: List<String>
)
