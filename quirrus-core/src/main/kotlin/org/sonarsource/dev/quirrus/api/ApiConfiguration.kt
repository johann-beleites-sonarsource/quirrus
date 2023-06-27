package org.sonarsource.dev.quirrus.api

import com.github.kittinunf.fuel.core.Request
import io.ktor.client.request.*
import kotlinx.serialization.json.Json
import org.sonarsource.dev.quirrus.common.Logger

val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

data class ApiConfiguration(
    val authenticator: (Request) -> Request,
    val authenticator2: ((HttpRequestBuilder) -> Unit)? = null,
    val apiUrl: String = "https://api.cirrus-ci.com/graphql",
    val requestTimeout: Int? = null,
    val connectionRetries: Int = 5,
    val logger: Logger? = null
)
