package org.sonarsource.dev.quirrus

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.Result
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

class GenericWorker(val cirrusCommand: CirrusCommand) {
    private val logger by lazy { cirrusCommand.logger }

    fun getTasksForBuild(build: Build): Pair<BuildWithMetadata, List<Task>> {
        for (i in 0..cirrusCommand.connectionRetries) {
            sendRequestToGetTasksForBuilds(build)
                .let { (request, _, result) ->
                    when (result) {
                        is Result.Failure -> {
                            logger.error("Failure getting build ${build.buildString} (Attempt ${i + 1}): ${result.error.localizedMessage}")
                        }
                        is Result.Success -> {
                            if (result.get().errors == null) {
                                return BuildWithMetadata(
                                    getBuildId(result),
                                    getBuildDate(result),
                                    build,
                                    getBuildNode(result)
                                ).let { buildWithMetadata ->
                                    buildWithMetadata to extractAllTasks(result).also {
                                        logger.print {
                                            "Successfully extracted ${it.size} tasks for build '$buildWithMetadata'"
                                        }
                                    }
                                }
                            } else {
                                logger.error("Errors encountered while sending request to ${request.url}:")
                                for (error in result.get().errors!!) {
                                    logger.error("  ${error.message}")
                                }
                            }
                        }
                    }
                }
        }

        logger.error("Retries failed - exiting")
        exitProcess(1)
    }

    private fun sendRequestToGetTasksForBuilds(build: Build) =
        cirrusCommand.apiUrl.httpPost()
            .authenticate()
            .let { request ->
                cirrusCommand.requestTimeout?.let { request.timeout(it) } ?: request
            }
            .jsonBody(RequestBuilder.tasksQuery(cirrusCommand.repositoryId, build.branchName, build.buildOffset).toRequestString())
            .responseObject<RepositoryApiResponse>(json)


    private fun extractAllTasks(result: Result<RepositoryApiResponse, FuelError>) = getBuildNode(result).tasks

    private fun getBuildId(result: Result<RepositoryApiResponse, FuelError>): String = getBuildNode(result).id

    private fun getBuildDate(result: Result<RepositoryApiResponse, FuelError>): Long = getBuildNode(result).buildCreatedTimestamp

    private fun getBuildNode(result: Result<RepositoryApiResponse, FuelError>): BuildNode =
        result.get().data?.repository?.builds?.edges?.let { it[it.size - 1].node }
            ?: throw IllegalStateException("We got an empty response body - this seems wrong, we should have failed earlier.")

    fun Request.authenticate(): Request = cirrusCommand.authenticator(this)
}
