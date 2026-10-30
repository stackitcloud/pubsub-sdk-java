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

package cloud.stackit.pubsub.sdk.auth

import cloud.stackit.sdk.core.config.CoreConfiguration

data class AuthConfig @JvmOverloads constructor(
    val serviceAccountKey: String? = null,
    val serviceAccountKeyPath: String? = null,
    val privateKeyPath: String? = null,
    val tokenEndpoint: String? = "https://authorization.stackit.cloud/auth/token"
) {
    init {
        tokenEndpoint?.let { url ->
            require(url.startsWith("https://")) { "tokenEndpoint must use HTTPS: $url" }
        }
    }

    fun toCoreConfiguration(): CoreConfiguration {
        val config = CoreConfiguration()
        serviceAccountKey?.let { config.serviceAccountKey(it) }
        serviceAccountKeyPath?.let { config.serviceAccountKeyPath(it) }
        privateKeyPath?.let { config.privateKeyPath(it) }
        tokenEndpoint?.let { config.tokenCustomUrl(it) }
        return config
    }
}
