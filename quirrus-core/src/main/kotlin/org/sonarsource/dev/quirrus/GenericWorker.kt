package org.sonarsource.dev.quirrus

import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.exec
import org.sonarsource.dev.quirrus.generated.graphql.GetTasks
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Task
import kotlin.system.exitProcess

class GenericWorker(private val apiConfig: ApiConfiguration) {
    private val logger by lazy { apiConfig.logger }

    suspend fun getTasksForBuild(build: Build, repositoryId: String): Pair<BuildWithMetadata, List<Task>> {
        for (i in 0..apiConfig.connectionRetries) {
            sendRequestToGetTasksForBuilds(build, repositoryId)
                .let { response ->
                    if (response.errors?.isEmpty() == true) {
                        response.data?.let { data ->
                            return BuildWithMetadata(
                                getBuildId(data),
                                getBuildDate(data),
                                build,
                                getBuildNode(data)
                            ).let { buildWithMetadata ->
                                buildWithMetadata to extractAllTasks(data).also {
                                    logger?.info {
                                        "Successfully extracted ${it.size} tasks for build '$buildWithMetadata'"
                                    }
                                }
                            }
                        } ?: throw IllegalStateException("No data in response body - this seems wrong, we should have failed earlier.")
                    } else {
                        logger?.error("Errors encountered while trying to get tasks for build $build in repo $repositoryId:")
                        for (error in response.errors ?: emptyList()) {
                            logger?.error("  ${error.message}")
                        }
                    }
                }
        }

        logger?.error("Retries failed - exiting")
        exitProcess(1)
    }

    private suspend fun sendRequestToGetTasksForBuilds(build: Build, repositoryId: String) =
        GetTasks(variables = GetTasks.Variables(repositoryId, build.branchName, build.buildOffset))
            .exec(apiConfig)


    private fun extractAllTasks(result: GetTasks.Result) = getBuildNode(result).tasks

    private fun getBuildId(result: GetTasks.Result): String = getBuildNode(result).id

    private fun getBuildDate(result: GetTasks.Result): Long = getBuildNode(result).buildCreatedTimestamp.toLong()

    private fun getBuildNode(result: GetTasks.Result) =
        result.repository?.builds?.edges?.let { it[it.size - 1].node }
            ?: throw IllegalStateException("We got an empty response body - this seems wrong, we should have failed earlier.")
}
