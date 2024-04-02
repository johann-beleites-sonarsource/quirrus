package org.sonarsource.dev.quirrus.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.serialization.json.Json
import me.lazmaid.kraph.Kraph
import okhttp3.ConnectionPool
import org.sonarsource.dev.quirrus.common.Logger
import java.nio.file.Path
import java.util.concurrent.TimeUnit

val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

class ApiConfiguration(
    val authenticator: ((HttpRequestBuilder) -> Unit)? = null,
    val apiUrl: String = "https://api.cirrus-ci.com/graphql",
    val requestTimeoutOverride: Long? = 60_000,
    val connectionRetries: Int = 5,
    val logger: Logger? = null
) {
    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = false

        engine {
            config {
                connectionPool(ConnectionPool(300, 10, TimeUnit.SECONDS))
            }
        }

        install(ContentNegotiation) {
            register(ContentType.Application.Json, KotlinxSerializationConverter(json))
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = connectionRetries)
            exponentialDelay()
        }

        requestTimeoutOverride?.let { timeoutOverride ->
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutOverride
                socketTimeoutMillis = timeoutOverride
            }
        }
    }

    suspend fun post(body: Kraph) = httpClient.post(apiUrl) {
        authenticator?.invoke(this)
        contentType(ContentType.Application.Json)
        setBody(body.toRequestString())
    }

    suspend fun get(url: String) = httpClient.get(url) {
        authenticator?.invoke(this)
    }

    suspend fun downloadToFile(url: String, file: Path) = get(url).bodyAsChannel().copyAndClose(file.toFile().writeChannel())
}
