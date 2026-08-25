package cloud.stackit.pubsub.examples;

import cloud.stackit.pubsub.sdk.auth.AuthConfig;
import cloud.stackit.pubsub.sdk.client.PubSubClient;
import cloud.stackit.pubsub.sdk.errors.PubSubAuthError;
import cloud.stackit.pubsub.sdk.models.PublishMessage;

import java.util.Base64;
import java.util.UUID;
import java.util.List;

/**
 * Batch publishing for higher throughput.
 * Demonstrates sending multiple messages in batches.
 */
public class BatchExample {

    private static final UUID TOPIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static void main(String[] args) {
        System.out.println("--- Example 3: Batch Publishing ---");

        try {
            AuthConfig authConfig = new AuthConfig(
                null,
                "sa-key.json",
                null,
                "https://authorization.stackit.cloud/auth/token"
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

        } catch (PubSubAuthError e) {
            System.err.println("PubSub error: " + e.getMessage());
        }
    }
}
