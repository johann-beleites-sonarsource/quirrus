package com.sonarsource.dev.quirrus.wallboard

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.ApiException
import org.sonarsource.dev.quirrus.api.LogDownloader

class CirrusData(val apiConfig: ApiConfiguration) {
    suspend fun getLastPeachBuilds(repo: String, branch: String, numberOfBuilds: Int = 1) =
        LogDownloader(apiConfig).getLastNBuilds(repo, branch, numberOfBuilds).let { (response, repositoryApiResponse) ->
            if (response.status == HttpStatusCode.OK) {
                repositoryApiResponse.data?.repository?.builds.let {
                    it ?: repositoryApiResponse.errors?.firstOrNull()?.let { e -> throw ApiException(response, e.message) }
                }
            } else {
                throw ApiException(response, "Could not get last $numberOfBuilds peach jobs from branch $branch (repo $repo).")
            }
        }

    suspend fun getLastPeachBuilds(repo: String, branches: List<String>, numberOfBuilds: Int = 1) =
        coroutineScope {
            branches.associateWith { branch ->
                async {
                    getLastPeachBuilds(repo, branch, numberOfBuilds)
                }
            }
        }.map { (k, deferred) ->
            k to deferred.await()
        }.toMap()
}
