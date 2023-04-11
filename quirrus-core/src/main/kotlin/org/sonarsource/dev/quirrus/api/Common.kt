package org.sonarsource.dev.quirrus.api

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.getOrElse
import org.sonarsource.dev.quirrus.OwnerRepositoryApiResponse
import org.sonarsource.dev.quirrus.RequestBuilder
import org.sonarsource.dev.quirrus.ViewerApiResponse
import kotlin.system.exitProcess

class Common(val apiConfiguration: ApiConfiguration) {
    fun resolveRepositoryId(repoName: String) =
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
            }

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
