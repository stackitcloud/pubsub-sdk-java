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
            AuthConfig authConfig = new AuthConfig(null, "sa-key.json");

            PubSubClient client = new PubSubClient(TOPIC_ID, authConfig);
            var publisher = client.getPublisher();

            // Try to publish (will fail if auth is invalid or topic doesn't exist)
            PublishMessage message = new PublishMessage(Base64.getEncoder().encodeToString("test".getBytes()));
            publisher.publish(List.of(message));

        } catch (PubSubError.AuthError.MissingCredentials e) {
            System.err.println("Missing credentials - please configure a service account key");
        } catch (PubSubError.AuthError.TokenAcquisitionFailed e) {
            System.err.println("Token acquisition failed: " + e.getMessage());
            // This catches HTTP errors (4xx, 5xx) and network errors
            if (e.getCause() != null) {
                System.err.println("  Caused by: " + e.getCause().getClass().getSimpleName());
            }
        } catch (PubSubError.HttpError e) {
            // Thrown for non-retryable API responses (400, 401, 403, 404, 413) and exhausted retries on 5xx
            System.err.println("API error (" + e.getClass().getSimpleName() + "): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }
}
