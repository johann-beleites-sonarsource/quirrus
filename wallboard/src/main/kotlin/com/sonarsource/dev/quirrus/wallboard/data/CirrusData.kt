package com.sonarsource.dev.quirrus.wallboard.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.ApiException
import org.sonarsource.dev.quirrus.api.LogDownloader
import org.sonarsource.dev.quirrus.api.exec
import org.sonarsource.dev.quirrus.generated.graphql.GetLastNBuilds
import org.sonarsource.dev.quirrus.generated.graphql.GetTasksOfSingleBuild
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.RepositoryBuildsConnection

class CirrusData(val apiConfig: ApiConfiguration) {
    suspend fun getLastPeachBuilds(repo: String, branch: String, numberOfBuilds: Int = 1, beforeTimestamp: Long? = null) =
        LogDownloader(apiConfig).getLastNBuilds(repo, branch, numberOfBuilds, beforeTimestamp).let { response ->
            if (response.errors?.isNotEmpty() == true) {
                throw ApiException("Could not get last $numberOfBuilds peach jobs from branch $branch (repo $repo):\n  ${response.errors?.joinToString("\n  ") { it.message }}")
            } else {
                response.data?.repository?.builds.let {
                    it ?: throw ApiException("Could not get last $numberOfBuilds peach jobs from branch $branch (repo $repo).")
                }
            }
        }

    suspend fun getLastPeachBuilds(repo: String, branches: List<String>, numberOfBuilds: Int = 1): Map<String, RepositoryBuildsConnection?> =
        coroutineScope {
            branches.associateWith { branch ->
                async {
                    getLastPeachBuilds(repo, branch, numberOfBuilds)
                }
            }
        }.map { (branch, deferred) ->
            branch to deferred.await()
        }.toMap()

    suspend fun getLastPeachBuildsMetadata(repo: String, branch: String, numberOfBuilds: Int = 1, beforeTimestamp: Long? = null) =
        GetLastNBuilds(variables = GetLastNBuilds.Variables(repo, branch, numberOfBuilds, beforeTimestamp?.toString())).exec(apiConfig).let {
            if (it.errors?.isNotEmpty() == true) {
                throw ApiException("Could not get last $numberOfBuilds peach jobs from branch $branch (repo $repo):\n  ${it.errors?.joinToString("\n  ") { it.message }}")
            } else {
                it.data?.repository?.builds.let {
                    it ?: throw ApiException("Could not get last $numberOfBuilds peach jobs from branch $branch (repo $repo).")
                }
            }
        }

    suspend fun getTasksOfSingleBuild(buildId: String) =
        GetTasksOfSingleBuild(variables = GetTasksOfSingleBuild.Variables(buildId)).exec(apiConfig).let {
            if (it.errors?.isNotEmpty() == true) {
                throw ApiException("Could not get tasks of build $buildId:\n  ${it.errors?.joinToString("\n  ") { it.message }}")
            } else {
                it.data?.build.let {
                    it ?: throw ApiException("Could not get tasks of build $buildId.")
                }
            }
        }
}
