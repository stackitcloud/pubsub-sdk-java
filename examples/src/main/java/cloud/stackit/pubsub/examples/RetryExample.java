package cloud.stackit.pubsub.examples;

import cloud.stackit.pubsub.sdk.auth.AuthConfig;
import cloud.stackit.pubsub.sdk.client.PubSubClient;
import cloud.stackit.pubsub.sdk.errors.PubSubAuthError;
import cloud.stackit.pubsub.sdk.models.PublishMessage;
import cloud.stackit.pubsub.sdk.retry.RetryConfig;

import java.util.Base64;
import java.util.UUID;
import java.util.List;

/**
 * Custom retry configuration.
 * Demonstrates configuring retry behavior for transient errors.
 */
public class RetryExample {

    private static final UUID TOPIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static void main(String[] args) {
        System.out.println("--- Example 5: Custom Retry Configuration ---");

        try {
            AuthConfig authConfig = new AuthConfig(
                null,
                "sa-key.json",
                null,
                null
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
