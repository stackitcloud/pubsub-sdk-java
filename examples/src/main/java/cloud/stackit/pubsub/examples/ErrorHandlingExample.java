package cloud.stackit.pubsub.examples;

import cloud.stackit.pubsub.sdk.auth.AuthConfig;
import cloud.stackit.pubsub.sdk.client.PubSubClient;
import cloud.stackit.pubsub.sdk.errors.PubSubAuthError;
import cloud.stackit.pubsub.sdk.models.PublishMessage;

import java.util.Base64;
import java.util.UUID;
import java.util.List;

/**
 * Error handling for various error conditions.
 * Demonstrates how to catch and handle different types of errors.
 */
public class ErrorHandlingExample {

    private static final UUID TOPIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static void main(String[] args) {
        System.out.println("--- Example 2: Error Handling ---");

        try {
            AuthConfig authConfig = new AuthConfig(
                null,
                "sa-key.json",
                null,
                null
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
    }
}
