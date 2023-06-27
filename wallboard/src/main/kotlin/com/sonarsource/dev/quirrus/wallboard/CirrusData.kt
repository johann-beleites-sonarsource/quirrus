package com.sonarsource.dev.quirrus.wallboard

import com.github.kittinunf.result.getOrElse
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.ApiException
import org.sonarsource.dev.quirrus.api.ApiExceptionOld
import org.sonarsource.dev.quirrus.api.LogDownloader

class CirrusData(val apiConfig: ApiConfiguration) {
    fun getLastPeachBuilds(repo: String, branch: String, numberOfBuilds: Int = 1) =
        LogDownloader(apiConfig).getLastNBuilds(repo, branch, numberOfBuilds).let { (_, response, result) ->
            result.getOrElse {
                throw it
            }.let { repositoryApiResponse ->
                repositoryApiResponse.data?.repository?.builds.let {
                    if (it == null) {
                        repositoryApiResponse.errors?.firstOrNull()?.let { e -> throw ApiExceptionOld(response, e.message) }
                    } else it
                }
            }
        }

    fun getLastPeachBuilds(repo: String, branches: List<String>, numberOfBuilds: Int = 1) =
        branches.associateWith { branch ->
            getLastPeachBuilds(repo, branch, numberOfBuilds)
        }
}
