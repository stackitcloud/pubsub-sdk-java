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

package cloud.stackit.pubsub.sdk.retry

/**
 * Configuration for retry behavior with exponential backoff.
 *
 * @param maxRetries Maximum number of retry attempts (0 = no retries). Default: 3
 * @param baseDelay Base delay in milliseconds between retries. Default: 500
 * @param maxDelay Maximum delay in milliseconds between retries. Default: 30000
 * @param multiplier Multiplier applied to delay after each retry. Default: 2.0
 * @param jitterFactor Randomization factor (0.0-1.0) to avoid thundering herd. Default: 0.25
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val baseDelay: Long = 500,
    val maxDelay: Long = 30_000,
    val multiplier: Double = 2.0,
    val jitterFactor: Double = 0.25
) {
    companion object {
        val Default = RetryConfig()
    }
}
