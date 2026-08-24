package cloud.stackit.pubsub.sdk.config

/**
 * STACKIT PubSub regions.
 */
enum class Region {
    EU01;

    val value: String
        get() = this.name.lowercase()
}

/**
 * STACKIT environments.
 */
enum class Environment {
    PROD,
    QA,
    DEV;

    val hostnamePrefix: String
        get() = when (this) {
            PROD -> ""
            QA -> "qa."
            DEV -> "dev."
        }
}
