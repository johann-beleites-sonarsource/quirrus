package org.sonarsource.dev.quirrus.api

import org.sonarsource.dev.quirrus.generated.graphql.GetRepoId
import org.sonarsource.dev.quirrus.generated.graphql.GetViewerId

class Common(private val apiConfiguration: ApiConfiguration) {
    suspend fun resolveRepositoryId(repoName: String) =
        GetRepoId(variables = GetRepoId.Variables(repoName)).exec(apiConfiguration).let { response ->
            if (response.errors?.isNotEmpty() == true) {
                throw ApiException("Could not fetch repository ID for '$repoName'\n: ${response.errors?.joinToString("\n  ")}")
            }

            response.data?.ownerRepository?.id
                ?: throw ApiException("Could not fetch repository ID for '$repoName' (got null).")
        }

    suspend fun fetchAuthenticatedUserId() =
        GetViewerId().exec(apiConfiguration).let { response ->
            if (response.errors?.isNotEmpty() == true) {
                throw ApiException("Could not fetch authenticated user ID\n: ${response.errors?.joinToString("\n  ")}")
            }

            response.data?.viewer?.id
                ?: throw ApiException("Could not fetch authenticated user ID (got null).")
        }
}
