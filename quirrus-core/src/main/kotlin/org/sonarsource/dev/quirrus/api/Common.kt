package org.sonarsource.dev.quirrus.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import me.lazmaid.kraph.Kraph
import org.sonarsource.dev.quirrus.OwnerRepositoryApiResponse
import org.sonarsource.dev.quirrus.RequestBuilder
import org.sonarsource.dev.quirrus.ViewerApiResponse

class Common(val apiConfiguration: ApiConfiguration) {
    suspend fun resolveRepositoryId(repoName: String) =
        apiConfiguration.post(RequestBuilder.repoIdQuery(repoName)).let { result ->
            if (result.status != HttpStatusCode.OK) {
                throw ApiException(result, "Could not fetch repository ID for '$repoName'")
            }
            result.body<OwnerRepositoryApiResponse>().data?.ownerRepository?.id
                ?: throw ApiException(result, "Could not fetch repository ID for '$repoName' (got null).")
        }

    suspend fun fetchAuthenticatedUserId() =
        apiConfiguration.post(RequestBuilder.viewerId()).let { response ->
            if (response.status != HttpStatusCode.OK) {
                apiConfiguration.logger?.error("Error fetching authenticated user ID: ${response.status} ${response.bodyAsText()}")
                null
            } else {
                response.body<ViewerApiResponse>().data.viewer.id
            }
        }
}
