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

