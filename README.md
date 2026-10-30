# pubsub-sdk-java

[![Maven Central](https://img.shields.io/maven-central/v/cloud.stackit.pubsub.sdk/pubsub-sdk-java)](https://central.sonatype.com/artifact/cloud.stackit.pubsub.sdk/pubsub-sdk-java)

Kotlin/Java SDK for the **STACKIT PubSub Data Plane API**.

This SDK covers the **data plane only** (publish, pull, ack, nack). Creating/deleting topics and subscriptions, and granting publisher/subscriber access, is done via the separate STACKIT PubSub **control plane** REST API or the STACKIT Portal.

## Install

The SDK is published to [Maven Central](https://central.sonatype.com/artifact/cloud.stackit.pubsub.sdk/pubsub-sdk-java) as `cloud.stackit.pubsub.sdk:pubsub-sdk-java`. Every GitHub Release publishes a new version automatically (see `.github/workflows/release.yml`); check the badge above or the [releases page](https://github.com/stackitcloud/pubsub-sdk-java/releases) for the latest version.

**Maven** (`pom.xml`):
```xml
<dependency>
    <groupId>cloud.stackit.pubsub.sdk</groupId>
    <artifactId>pubsub-sdk-java</artifactId>
    <version>0.1.1</version>
</dependency>
```

**Gradle** (`build.gradle.kts`):
```kotlin
implementation("cloud.stackit.pubsub.sdk:pubsub-sdk-java:0.1.1")
```

Requires JDK 21+. Runtime dependencies: `cloud.stackit.sdk:core`, `java-jwt`, `okhttp`, `gson`.

## Auth

Authentication goes through `AuthConfig` + `StackitAuthProvider`, backed by a STACKIT service account key.

```kotlin
import cloud.stackit.pubsub.sdk.auth.AuthConfig

val authConfig = AuthConfig(
    serviceAccountKeyPath = "sa-key.json",  // path to service account key JSON file
    tokenEndpoint = "https://authorization.stackit.cloud/auth/token",  // default shown
)
```

`AuthConfig` fields: `serviceAccountKey` (inline JSON string), `serviceAccountKeyPath` (file path), `privateKeyPath`, `tokenEndpoint` (default `https://authorization.stackit.cloud/auth/token`).

## Environments

The SDK supports three environments via the `Environment` enum:

| Environment | Dataplane URL pattern |
|-------------|----------------------|
| `PROD` (default) | `https://{topic_id}.pubsub.{region}.onstackit.cloud` |
| `QA` | `https://{topic_id}.pubsub.{region}.qa.onstackit.cloud` |
| `DEV` | `https://{topic_id}.pubsub.{region}.dev.onstackit.cloud` |

```kotlin
import cloud.stackit.pubsub.sdk.config.Environment

val client = PubSubClient(
    topicId = UUID.fromString("your-topic-uuid"),
    authConfig = authConfig,
    environment = Environment.DEV  // use DEV environment
)
```

## Quickstart

```kotlin
import cloud.stackit.pubsub.sdk.client.PubSubClient
import cloud.stackit.pubsub.sdk.auth.AuthConfig
import cloud.stackit.pubsub.sdk.models.PublishMessage
import java.util.UUID

fun main() {
    val authConfig = AuthConfig(serviceAccountKeyPath = "sa-key.json")
    val client = PubSubClient(
        topicId = UUID.fromString("your-topic-uuid"),
        authConfig = authConfig
    )

    // Publish
    val publisher = client.getPublisher()
    publisher.publish(listOf(PublishMessage("aGVsbG8=")))  // base64-encoded "hello"

    // Subscribe
    val subscriber = client.getSubscriber(UUID.fromString("your-subscription-uuid"))
    val messages = subscriber.pull()
    for (msg in messages) {
        println(msg.text)  // base64-decoded payload
    }
    subscriber.ack(messages.map { it.ackId })
    // or: subscriber.nack(messages.map { it.ackId })
}
```

`client.baseDataplaneUrl` defaults to `https://{topic_id}.pubsub.eu01.onstackit.cloud`. Override with `region` or `environment` (e.g. `environment = Environment.DEV` for `https://{topic_id}.pubsub.eu01.dev.onstackit.cloud`).

`PublishMessage` takes a base64-encoded payload string. `Message.payload` (ByteArray) / `Message.text` (String) base64-decode it back.

Both `PubSubPublisher` and `PubSubSubscriber` expose call counters: `publisher.messagesSent` / `.publishCalls` / `.purgeCalls`, `subscriber.pullCalls` / `.ackCalls` / `.nackCalls`.

Other methods: `publisher.publishBatch(messages, batchSize = 100)`, `publisher.purgeTopic()`.

## Errors

All exceptions extend `PubSubAuthError` (sealed class):

| Exception                    | Raised when                                                                                                                                                   |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PubSubAuthError.MissingCredentials` | No service account key configured (local validation)                                                                             |
| `PubSubAuthError.TokenAcquisitionFailed` | HTTP 400/401/403/404/413, server errors (5xx), or token acquisition failure                                                                                               |
|                              |                                                                                                                                             |

## Retries

`PubSubPublisher`/`PubSubSubscriber` retry automatically on network/timeout errors (`IOException`, `ConnectException`, `SocketTimeoutException`, `TimeoutException`) and HTTP 500/502/503/504, with exponential backoff + jitter. Configure via `retryConfig = RetryConfig(...)` passed to the publisher/subscriber constructor (or `getPublisher()`/`getSubscriber()` kwargs):

```kotlin
import cloud.stackit.pubsub.sdk.retry.RetryConfig

val retryConfig = RetryConfig(maxRetries = 3, baseDelay = 500L, maxDelay = 30000L, multiplier = 2.0)
```

400/401/403/404/413 are never retried.

## Examples

Standalone example programs demonstrating common patterns are in [`examples/`](examples/):

- `BasicExample.java` — publish/subscribe workflow
- `ErrorHandlingExample.java` — error handling patterns
- `BatchExample.java` — batch publishing
- `PurgeExample.java` — topic purging
- `RetryExample.java` — custom retry configuration

The examples depend on the published SDK version set in `examples/pom.xml` (`pubsub-sdk.version`). Run one by passing its main class:

```sh
cd examples
mvn compile exec:java -Dexec.mainClass=cloud.stackit.pubsub.examples.BasicExample
```

To run them against your local checkout instead:

```sh
./gradlew publishToMavenLocal -PreleaseVersion=0.0.0-local
cd examples && mvn compile -Dpubsub-sdk.version=0.0.0-local
```
