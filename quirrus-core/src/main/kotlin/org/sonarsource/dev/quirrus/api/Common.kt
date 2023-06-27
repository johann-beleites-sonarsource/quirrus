package org.sonarsource.dev.quirrus.api

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.getOrElse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import org.sonarsource.dev.quirrus.OwnerRepositoryApiResponse
import org.sonarsource.dev.quirrus.RequestBuilder
import org.sonarsource.dev.quirrus.ViewerApiResponse

class Common(val apiConfiguration: ApiConfiguration) {

    private val httpClient = HttpClient(CIO) {
        expectSuccess = false

        install(ContentNegotiation) {
            register(ContentType.Application.Json, KotlinxSerializationConverter(json))
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
        }
    }

    suspend fun resolveRepositoryId(repoName: String) =
        httpClient.post(apiConfiguration.apiUrl) {
            apiConfiguration.authenticator2?.invoke(this)
            contentType(ContentType.Application.Json)
            setBody(RequestBuilder.repoIdQuery(repoName).toRequestString())
        }.let { result ->
            if (result.status != HttpStatusCode.OK) {
                throw ApiException(result, "Could not fetch repository ID for '$repoName'")
            }
            result.body<OwnerRepositoryApiResponse>().data?.ownerRepository?.id
        }

    /*fun resolveRepositoryId(repoName: String) =
        apiConfiguration.apiUrl.httpPost()
            .let { apiConfiguration.authenticator(it) }
            .let { request ->
                apiConfiguration.requestTimeout?.let { request.timeout(it) } ?: request
            }
            .jsonBody(RequestBuilder.repoIdQuery(repoName).toRequestString())
            .responseObject<OwnerRepositoryApiResponse>(json)
            .let { (_, response, result) ->
                result.getOrElse { e ->
                    val msg = "Could not fetch repository ID for '$repoName': ${e.localizedMessage}"
                    apiConfiguration.logger?.error(msg)
                    throw ApiException(response, msg)
                }.data?.ownerRepository?.id ?: run {
                    val msg = "Could not fetch repository ID for '$repoName' (got null)."
                    apiConfiguration.logger?.error(msg)
                    throw ApiException(response, msg)
                }
            }*/

    fun fetchAuthenticatedUserId() =
        apiConfiguration.apiUrl.httpPost()
            .let { apiConfiguration.authenticator(it) }
            .let { request ->
                apiConfiguration.requestTimeout?.let { request.timeout(it) } ?: request
            }
            .jsonBody(RequestBuilder.viewerId().toRequestString())
            .responseObject<ViewerApiResponse>(json)
            .third.getOrElse { error ->
                apiConfiguration.logger?.error("Error fetching authenticated user ID: ${error.message}")
                null
            }?.data?.viewer?.id
}
