package org.sonarsource.dev.quirrus.api

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import org.sonarsource.dev.quirrus.common.Logger
import java.net.URL
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

    val graphQlClient = GraphQLKtorClient(url = URL("https://api.cirrus-ci.com/graphql"), httpClient = httpClient)

    suspend fun <T : Any> sendGraphQlRequest(request: GraphQLClientRequest<T>): GraphQLClientResponse<T> {
        return runCatching {
            withTimeout(requestTimeoutOverride ?: 120_000) {
                async {
                    graphQlClient.execute(request) {
                        authenticator?.invoke(this)
                        contentType(ContentType.Application.Json)
                    }
                }
            }.await()
        }.onFailure { e ->
            logger?.error("Failed to send GraphQL request: ${e.message}. Request:\n$request")
            throw ApiException("Failed to send GraphQL request: ${e.message}")
        }.getOrNull() ?: throw ApiException("Failed to send GraphQL request")
    }

    suspend fun get(url: String) = httpClient.get(url) {
        authenticator?.invoke(this)
    }

    suspend fun downloadToFile(url: String, file: Path) = get(url).bodyAsChannel().copyAndClose(file.toFile().writeChannel())
}

suspend fun <T : Any> GraphQLClientRequest<T>.exec(apiConfiguration: ApiConfiguration) = apiConfiguration.sendGraphQlRequest(this)
