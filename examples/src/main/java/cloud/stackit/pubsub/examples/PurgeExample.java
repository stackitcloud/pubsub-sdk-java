package cloud.stackit.pubsub.examples;

import cloud.stackit.pubsub.sdk.auth.AuthConfig;
import cloud.stackit.pubsub.sdk.client.PubSubClient;
import cloud.stackit.pubsub.sdk.errors.PubSubAuthError;
import cloud.stackit.pubsub.sdk.models.PublishMessage;

import java.util.Base64;
import java.util.UUID;
import java.util.List;

/**
 * Topic purging.
 * Demonstrates clearing all messages from a topic.
 */
public class PurgeExample {

    private static final UUID TOPIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static void main(String[] args) {
        System.out.println("--- Example 4: Topic Purging ---");

        try {
            AuthConfig authConfig = new AuthConfig(
                null,
                "sa-key.json",
                null,
                null
            );

            PubSubClient client = new PubSubClient(TOPIC_ID, authConfig);
            var publisher = client.getPublisher();

            // Purge all messages from the topic
            publisher.purgeTopic();
            System.out.println("Topic purged successfully");
            System.out.println("Purge calls: " + publisher.getPurgeCalls());

        } catch (PubSubAuthError e) {
            System.err.println("PubSub error: " + e.getMessage());
        }
    }
}
