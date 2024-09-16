package org.sonarsource.dev.quirrus.api

import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.sonarsource.dev.quirrus.OwnerRepositoryApiResponse
import org.sonarsource.dev.quirrus.RequestBuilder
import org.sonarsource.dev.quirrus.ViewerApiResponse
import org.sonarsource.dev.quirrus.generated.graphql.GetRepo

class Common(val apiConfiguration: ApiConfiguration) {
    suspend fun resolveRepositoryId(repoName: String) =
        apiConfiguration.sendGraphQlRequest(GetRepo(variables = GetRepo.Variables(repoName))).let { response ->
            if (response.errors?.isNotEmpty() == true) {
                throw ApiException("Could not fetch repository ID for '$repoName'\n: ${response.errors?.joinToString("\n  ")}")
            }

            response.data?.repository?.id
                ?: throw ApiException("Could not fetch repository ID for '$repoName' (got null).")
        }
        /*apiConfiguration.post(RequestBuilder.repoIdQuery(repoName)).let { result ->
            if (result.status != HttpStatusCode.OK) {
                throw ApiException(result, "Could not fetch repository ID for '$repoName'")
            }
            result.body<OwnerRepositoryApiResponse>().data?.ownerRepository?.id
                ?: throw ApiException(result, "Could not fetch repository ID for '$repoName' (got null).")
        }*/

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
