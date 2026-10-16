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

import org.junit.jupiter.api.Test
import java.time.Instant

class TestPublishMessage {
    @Test
    fun `fromPlain creates message with data`() {
        val msg = PublishMessage("SGVsbG8=")
        assert(msg.data == "SGVsbG8=")
    }

    @Test
    fun `fromBytes creates message with base64 encoded bytes`() {
        val data = "aGVsbG8gd29ybGQ=".toByteArray().toString()
        val msg = PublishMessage(data)
        assert(msg.data == data)
    }

    @Test
    fun `dataIsBase64Encoded validates data format`() {
        val msg = PublishMessage("dGVzdCBtZXNzYWdl")
        assert(msg.data == "dGVzdCBtZXNzYWdl")
    }
}

class TestPublishMessagesRequest {
    @Test
    fun `creates request with messages`() {
        val messages = listOf(PublishMessage("dGVzdDE="), PublishMessage("dGVzdDI="))
        val request = PublishMessagesRequest(messages)
        assert(request.messages == messages)
        assert(request.messages.size == 2)
    }
}

class TestMessage {
    @Test
    fun `create message`() {
        val msg = Message(
            ackId = "ack-1",
            data = "SGVsbG8=",
            createTime = Instant.parse("2024-01-01T00:00:00Z"),
            deliveryAttempts = 1L
        )
        assert(msg.ackId == "ack-1")
        assert(msg.deliveryAttempts == 1L)
    }

    @Test
    fun `payload decodes base64`() {
        val msg = Message(
            ackId = "ack-1",
            data = "dGVzdCBkYXRh",
            createTime = Instant.parse("2024-01-01T00:00:00Z"),
            deliveryAttempts = 0
        )
        val bytes = String(msg.payload)
        assert(bytes == "test data")
    }

    @Test
    fun `text decodes utf8`() {
        val msg = Message(
            ackId = "ack-1",
            data = "SGVsbG8sIFdvcmxkIQ==",
            createTime = Instant.parse("2024-01-01T00:00:00Z"),
            deliveryAttempts = 0
        )
        assert(msg.text == "Hello, World!")
    }
}

class TestMessagesResponse {
    @Test
    fun `container holds messages`() {
        val msg = Message(
            ackId = "ack-1",
            data = "dGVzdA==",
            createTime = Instant.parse("2024-01-01T00:00:00Z"),
            deliveryAttempts = 0
        )
        val response = MessagesResponse(listOf(msg))
        assert(response.messages.size == 1)
        assert(response.messages[0].ackId == "ack-1")
    }
}

class TestAckNackRequests {
    @Test
    fun `ack request`() {
        val request = AckMessagesRequest(listOf("ack-1", "ack-2"))
        assert(request.ackIds == listOf("ack-1", "ack-2"))
    }

    @Test
    fun `nack request`() {
        val request = NackMessagesRequest(listOf("nack-1", "nack-2"))
        assert(request.nackIds == listOf("nack-1", "nack-2"))
    }
}
