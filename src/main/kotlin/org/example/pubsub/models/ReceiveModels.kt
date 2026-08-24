package org.example.pubsub.models

import java.time.Instant

/**
 * A message received from a topic subscription.
 *
 * @param id Unique sequence number identifying the message.
 * @param ackId ID for acknowledging the message.
 * @param data Base64-encoded message payload.
 * @param createTime When the message was created.
 * @param deliveryAttempts Number of delivery attempts.
 */
data class Message(
    val id: Long,
    val ackId: String,
    val data: String,
    val createTime: Instant,
    val deliveryAttempts: Long
)

/**
 * Response containing messages pulled from a subscription.
 *
 * @param messages The pulled messages.
 */
data class MessagesResponse(
    val messages: List<Message>
)
