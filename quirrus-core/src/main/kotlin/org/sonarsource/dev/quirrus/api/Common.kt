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
            .let { (_, _, result) ->
                result.getOrElse { e ->
                    apiConfiguration.logger?.error("Could not fetch repository ID for '$repoName': ${e.localizedMessage}")
                    exitProcess(1)
                }.data?.ownerRepository?.id ?: run {
                    apiConfiguration.logger?.error("Could not fetch repository ID for '$repoName' (got null).")
                    exitProcess(1)
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
