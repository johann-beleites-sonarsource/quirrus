package org.sonarsource.dev.quirrus

import io.ktor.client.call.body
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import kotlin.system.exitProcess

class GenericWorker(private val apiConfig: ApiConfiguration) {
    private val logger by lazy { apiConfig.logger }

    suspend fun getTasksForBuild(build: Build, repositoryId: String): Pair<BuildWithMetadata, List<Task>> {
        for (i in 0..apiConfig.connectionRetries) {
            sendRequestToGetTasksForBuilds(build, repositoryId)
                .let { response ->
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            val result = response.body<RepositoryApiResponse>()

                            if (result.errors == null) {
                                return BuildWithMetadata(
                                    getBuildId(result),
                                    getBuildDate(result),
                                    build,
                                    getBuildNode(result)
                                ).let { buildWithMetadata ->
                                    buildWithMetadata to extractAllTasks(result).also {
                                        logger?.info {
                                            "Successfully extracted ${it.size} tasks for build '$buildWithMetadata'"
                                        }
                                    }
                                }
                            } else {
                                logger?.error("Errors encountered while sending request to ${response.request.url}:")
                                for (error in result.errors) {
                                    logger?.error("  ${error.message}")
                                }
                            }
                        }
                        else -> {
                            logger?.error("Failure getting build ${build.buildString} (Attempt ${i + 1}): ${response.status}")
                        }
                    }
                }
        }

        logger?.error("Retries failed - exiting")
        exitProcess(1)
    }

    private suspend fun sendRequestToGetTasksForBuilds(build: Build, repositoryId: String) =
        apiConfig.post(RequestBuilder.tasksQuery(repositoryId, build.branchName, build.buildOffset))


    private fun extractAllTasks(result: RepositoryApiResponse) = getBuildNode(result).tasks

    private fun getBuildId(result: RepositoryApiResponse): String = getBuildNode(result).id

    private fun getBuildDate(result: RepositoryApiResponse): Long = getBuildNode(result).buildCreatedTimestamp

    private fun getBuildNode(result: RepositoryApiResponse): BuildNode =
        result.data?.repository?.builds?.edges?.let { it[it.size - 1].node }
            ?: throw IllegalStateException("We got an empty response body - this seems wrong, we should have failed earlier.")
}
