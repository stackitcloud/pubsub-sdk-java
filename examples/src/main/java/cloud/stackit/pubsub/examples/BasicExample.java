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

package cloud.stackit.pubsub.examples;

import cloud.stackit.pubsub.sdk.auth.AuthConfig;
import cloud.stackit.pubsub.sdk.client.PubSubClient;
import cloud.stackit.pubsub.sdk.errors.PubSubError;
import cloud.stackit.pubsub.sdk.models.Message;
import cloud.stackit.pubsub.sdk.models.PublishMessage;

import java.util.Base64;
import java.util.UUID;
import java.util.List;

/**
 * Basic publish and subscribe operations.
 * Demonstrates the core workflow of sending and receiving messages.
 */
public class BasicExample {

    private static final UUID TOPIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static void main(String[] args) {
        System.out.println("--- Example 1: Basic Publish & Subscribe ---");

        try {
            // Initialize auth config with service account key file path
            AuthConfig authConfig = new AuthConfig(null, "sa-key.json");

            // Create the client with topic ID and auth config
            PubSubClient client = new PubSubClient(TOPIC_ID, authConfig);

            // Publish a message
            String payload = "Hello, PubSub!";
            String base64Payload = Base64.getEncoder().encodeToString(payload.getBytes());
            PublishMessage message = new PublishMessage(base64Payload);

            var publisher = client.getPublisher();
            publisher.publish(List.of(message));
            System.out.println("Published 1 message successfully.");
            System.out.println("Messages sent: " + publisher.getMessagesSent());
            System.out.println("Publish calls: " + publisher.getPublishCalls());

            // Subscribe to messages
            var subscriber = client.getSubscriber(SUBSCRIPTION_ID);
            List<Message> messages = subscriber.pull();
            System.out.println("Received " + messages.size() + " messages");

            for (Message msg : messages) {
                System.out.println("  - Ack ID: " + msg.getAckId() + ", Text: " + msg.getText() +
                    ", Attempts: " + msg.getDeliveryAttempts());
            }

            // Acknowledge all received messages
            subscriber.ack(messages.stream().map(Message::getAckId).toList());
            System.out.println("Acknowledged all messages");

            System.out.println("Subscriber ack calls: " + subscriber.getAckCalls());

        } catch (PubSubError e) {
            System.err.println("PubSub error: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  Caused by: " + e.getCause().getMessage());
            }
        }
    }
}
