package com.sonarsource.dev.quirrus.wallboard.data

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.ApiException
import org.sonarsource.dev.quirrus.api.LogDownloader

class CirrusData(val apiConfig: ApiConfiguration) {
    suspend fun getLastPeachBuilds(repo: String, branch: String, numberOfBuilds: Int = 1, beforeTimestamp: Long? = null) =
        LogDownloader(apiConfig).getLastNBuilds(repo, branch, numberOfBuilds, beforeTimestamp).let { (response, repositoryApiResponse) ->
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
        }.map { (branch, deferred) ->
            branch to deferred.await()
        }.toMap()
}
