plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.4.10"
    id("jacoco")
}

group = "cloud.stackit.pubsub.sdk"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("cloud.stackit.sdk:core:0.4.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.9.1")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            element = "CLASS"
            excludes = listOf(
                "cloud.stackit.pubsub.sdk.test.*",
                "cloud.stackit.pubsub.sdk.integration.*",
                "cloud.stackit.pubsub.sdk.auth.StackitAuthProvider*",
                "cloud.stackit.pubsub.sdk.auth.AuthConfig*",
                "cloud.stackit.pubsub.sdk.errors.PubSubAuthError*",
                "cloud.stackit.pubsub.sdk.subscriber.PubSubSubscriber*",
                "cloud.stackit.pubsub.sdk.retry.RetryUtilsKt",
                "cloud.stackit.pubsub.sdk.client.PubSubClient*"
            )
            limit {
                minimum = BigDecimal("0.85")
            }
        }
    }
}