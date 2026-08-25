package cloud.stackit.pubsub.examples;

import cloud.stackit.pubsub.sdk.auth.AuthConfig;
import cloud.stackit.pubsub.sdk.client.PubSubClient;
import cloud.stackit.pubsub.sdk.errors.PubSubAuthError;
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
            AuthConfig authConfig = new AuthConfig(
                null,
                "sa-key.json",
                null,
                "https://authorization.stackit.cloud/auth/token"
            );

            // Create the client with topic ID and auth config
            PubSubClient client = new PubSubClient(
                TOPIC_ID,
                authConfig
            );

            // Publish a message
            String payload = "Hello, PubSub!";
            String base64Payload = Base64.getEncoder().encodeToString(payload.getBytes());
            PublishMessage message = new PublishMessage(base64Payload);

            var publisher = client.getPublisher();
            var response = publisher.publish(List.of(message));
            System.out.println("Published messages with IDs: " + response.getMessageIds());
            System.out.println("Messages sent: " + publisher.getMessagesSent());
            System.out.println("Publish calls: " + publisher.getPublishCalls());

            // Subscribe to messages
            var subscriber = client.getSubscriber(SUBSCRIPTION_ID);
            List<Message> messages = subscriber.pull();
            System.out.println("Received " + messages.size() + " messages");

            for (Message msg : messages) {
                System.out.println("  - ID: " + msg.getId() + ", Text: " + msg.getText() +
                    ", Attempts: " + msg.getDeliveryAttempts());
            }

            // Acknowledge all received messages
            subscriber.ack(messages.stream().map(Message::getAckId).toList());
            System.out.println("Acknowledged all messages");

            System.out.println("Subscriber ack calls: " + subscriber.getAckCalls());

        } catch (PubSubAuthError e) {
            System.err.println("PubSub error: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  Caused by: " + e.getCause().getMessage());
            }
        }
    }
}
