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

plugins {
    `java-library`
    kotlin("jvm") version "2.4.10"
    id("com.vanniktech.maven.publish") version "0.37.0"
    id("jacoco")
}

group = "cloud.stackit.pubsub.sdk"
version = (findProperty("releaseVersion") as String?)?.removePrefix("v")
    ?: System.getenv("RELEASE_VERSION")?.removePrefix("v")
    ?: "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("cloud.stackit.sdk:core:0.4.1")
    implementation("com.auth0:java-jwt:4.5.0")
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

mavenPublishing {
    coordinates(group.toString(), "pubsub-sdk-java", version.toString())

    // Uploads via the Central Portal API and releases once validation passes.
    publishToMavenCentral(automaticRelease = true)

    // Signing is only required for Maven Central; allow unsigned local publishing.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set("pubsub-sdk-java")
        description.set("Kotlin/Java SDK for the STACKIT PubSub Data Plane API")
        url.set("https://github.com/stackitcloud/pubsub-sdk-java")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("stackit")
                name.set("STACKIT")
                email.set("opensource@stackit.cloud")
                organization.set("Schwarz Digits Cloud GmbH & Co. KG")
                organizationUrl.set("https://stackit.de")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/stackitcloud/pubsub-sdk-java.git")
            developerConnection.set("scm:git:ssh://github.com:stackitcloud/pubsub-sdk-java.git")
            url.set("https://github.com/stackitcloud/pubsub-sdk-java")
        }
    }
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
            includes = listOf(
                "cloud.stackit.pubsub.sdk.publisher.*",
                "cloud.stackit.pubsub.sdk.subscriber.*"
            )
            excludes = listOf(
                "cloud.stackit.pubsub.sdk.subscriber.PubSubSubscriber*gson*"
            )
            limit {
                minimum = BigDecimal("0.85")
            }
        }
    }
}