/*
 * Copyright 2026 Schwarz Digits Cloud GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cloud.stackit.pubsub.sdk.models

import java.time.Instant
import java.util.Base64

/**
 * A message received from a topic subscription.
 *
 * @param ackId ID for acknowledging the message.
 * @param data Base64-encoded message payload.
 * @param createTime When the message was created.
 * @param deliveryAttempts Number of delivery attempts.
 */
data class Message(
    val ackId: String,
    val data: String,
    val createTime: Instant,
    val deliveryAttempts: Long
) {
    /**
     * Decodes the base64-encoded data to bytes.
     */
    val payload: ByteArray
        get() = Base64.getDecoder().decode(data)

    /**
     * Decodes the base64-encoded data to a UTF-8 string.
     */
    val text: String
        get() = String(Base64.getDecoder().decode(data), java.nio.charset.StandardCharsets.UTF_8)
}

/**
 * Response containing messages pulled from a subscription.
 *
 * @param messages The pulled messages.
 */
data class MessagesResponse(
    val messages: List<Message>
)
