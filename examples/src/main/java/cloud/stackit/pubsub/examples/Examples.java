package cloud.stackit.pubsub.examples;

import cloud.stackit.pubsub.sdk.auth.AuthConfig;
import cloud.stackit.pubsub.sdk.client.PubSubClient;
import cloud.stackit.pubsub.sdk.errors.PubSubAuthError;
import cloud.stackit.pubsub.sdk.models.Message;
import cloud.stackit.pubsub.sdk.models.PublishMessage;
import cloud.stackit.pubsub.sdk.retry.RetryConfig;

import java.util.Base64;
import java.util.UUID;
import java.util.List;

/**
 * Example usage of the STACKIT PubSub Java SDK.
 *
 * This class demonstrates:
 * - SDK client instantiation with secure authentication
 * - Publishing messages with error handling
 * - Subscribing to messages with ack/nack handling
 * - Batch publishing
 * - Topic purging
 * - Custom retry configuration
 */
public class Examples {

    private static final UUID TOPIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static void main(String[] args) {
        System.out.println("=== STACKIT PubSub SDK Examples ===\n");

        // Example 1: Basic publish and subscribe
        basicExample();

        // Example 2: Error handling
        errorHandlingExample();

        // Example 3: Batch publishing
        batchExample();

        // Example 4: Topic purging
        purgeExample();

        // Example 5: Custom retry configuration
        retryExample();
    }

    /**
     * Example 1: Basic publish and subscribe operations.
     * Demonstrates the core workflow of sending and receiving messages.
     */
    public static void basicExample() {
        System.out.println("--- Example 1: Basic Publish & Subscribe ---");

        try {
            // Initialize auth config with service account key file path
            AuthConfig authConfig = new AuthConfig(
                "sa-key.json",                    // service account key file path
                null,                             // privateKeyPath (optional)
                "https://authorization.stackit.cloud/auth/token", // token endpoint
                "dataplane"                       // scope
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
            System.out.println();

        } catch (PubSubAuthError e) {
            System.err.println("PubSub error: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  Caused by: " + e.getCause().getMessage());
            }
        }
    }

    /**
     * Example 2: Error handling for various error conditions.
     * Demonstrates how to catch and handle different types of errors.
     */
    public static void errorHandlingExample() {
        System.out.println("--- Example 2: Error Handling ---");

        try {
            AuthConfig authConfig = new AuthConfig(
                "sa-key.json",
                null,
                null,
                "dataplane"
            );

            PubSubClient client = new PubSubClient(TOPIC_ID, authConfig);
            var publisher = client.getPublisher();

            // Try to publish (will fail if auth is invalid or topic doesn't exist)
            PublishMessage message = new PublishMessage(Base64.getEncoder().encodeToString("test".getBytes()));
            publisher.publish(List.of(message));

        } catch (PubSubAuthError.MissingCredentials e) {
            System.err.println("Missing credentials - please configure a service account key");
        } catch (PubSubAuthError.TokenAcquisitionFailed e) {
            System.err.println("Token acquisition failed: " + e.getMessage());
            // This catches HTTP errors (4xx, 5xx) and network errors
            if (e.getCause() != null) {
                System.err.println("  Caused by: " + e.getCause().getClass().getSimpleName());
            }
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Example 3: Batch publishing for higher throughput.
     * Demonstrates sending multiple messages in batches.
     */
    public static void batchExample() {
        System.out.println("--- Example 3: Batch Publishing ---");

        try {
            AuthConfig authConfig = new AuthConfig(
                "sa-key.json",
                null,
                null,
                "dataplane"
            );

            PubSubClient client = new PubSubClient(TOPIC_ID, authConfig);
            var publisher = client.getPublisher();

            // Create 250 messages to publish in batches of 100
            List<PublishMessage> messages = java.util.stream.IntStream.range(0, 250)
                .mapToObj(i -> new PublishMessage(
                    Base64.getEncoder().encodeToString(("Message " + i).getBytes())))
                .toList();

            // Publish in batches
            var responses = publisher.publishBatch(messages, 100);
            System.out.println("Published " + responses.size() + " batches");
            for (int i = 0; i < responses.size(); i++) {
                System.out.println("  Batch " + (i + 1) + ": " + responses.get(i).getMessageIds().size() + " messages");
            }
            System.out.println("Total messages sent: " + publisher.getMessagesSent());
            System.out.println("Total publish calls: " + publisher.getPublishCalls());
            System.out.println();

        } catch (PubSubAuthError e) {
            System.err.println("PubSub error: " + e.getMessage());
        }
    }

    /**
     * Example 4: Topic purging.
     * Demonstrates clearing all messages from a topic.
     */
    public static void purgeExample() {
        System.out.println("--- Example 4: Topic Purging ---");

        try {
            AuthConfig authConfig = new AuthConfig(
                "sa-key.json",
                null,
                null,
                "dataplane"
            );

            PubSubClient client = new PubSubClient(TOPIC_ID, authConfig);
            var publisher = client.getPublisher();

            // Purge all messages from the topic
            publisher.purgeTopic();
            System.out.println("Topic purged successfully");
            System.out.println("Purge calls: " + publisher.getPurgeCalls());
            System.out.println();

        } catch (PubSubAuthError e) {
            System.err.println("PubSub error: " + e.getMessage());
        }
    }

    /**
     * Example 5: Custom retry configuration.
     * Demonstrates configuring retry behavior for transient errors.
     */
    public static void retryExample() {
        System.out.println("--- Example 5: Custom Retry Configuration ---");

        try {
            AuthConfig authConfig = new AuthConfig(
                "sa-key.json",
                null,
                null,
                "dataplane"
            );

            PubSubClient client = new PubSubClient(TOPIC_ID, authConfig);

            // Configure custom retry settings:
            // - maxRetries: Maximum number of retry attempts (3 = 1 initial + 3 retries)
            // - baseDelay: Initial delay in milliseconds (500ms)
            // - maxDelay: Maximum delay cap in milliseconds (10000ms = 10s)
            // - multiplier: Exponential backoff multiplier (2.0x per attempt)
            RetryConfig retryConfig = new RetryConfig(
                3,      // maxRetries
                500L,   // baseDelay
                10000L, // maxDelay
                2.0     // multiplier
            );

            // Create publisher with custom retry config
            var publisher = client.getPublisher(retryConfig);

            PublishMessage message = new PublishMessage(
                Base64.getEncoder().encodeToString("retry-test".getBytes())
            );
            publisher.publish(List.of(message));

            System.out.println("Published with custom retry config");
            System.out.println("Messages sent: " + publisher.getMessagesSent());

        } catch (PubSubAuthError e) {
            System.err.println("PubSub error after retries: " + e.getMessage());
            // Note: After all retries are exhausted, the error includes retry information
            if (e.getMessage().contains("retries")) {
                System.err.println("  All retry attempts were exhausted");
            }
        }
    }
}
