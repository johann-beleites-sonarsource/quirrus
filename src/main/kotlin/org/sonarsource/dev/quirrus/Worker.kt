package org.sonarsource.dev.quirrus

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.Result
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

class Worker(
    private val apiUrl: String,
    private val authenticator: (Request) -> Request,
    private val repositoryId: String,
    private val builds: List<Build>,
    private val dataExtractorRegexes: List<Regex>,
    private val logName: String,
    private val notFoundPlaceHolder: String,
    private val logger: CliLogger?
) {
    fun run() {
        val collector = mutableMapOf<String, MutableMap<Build, String>>()
        builds
            .map { build -> getTasksForLatestBuild(build) }
            .map { (build, tasks) ->
                logger?.print { "Fetching logs for build '$build'" }
                build to fetchRawDataForTasks(tasks).sortedBy { (task, _) -> task.name }
            }
            .let { dataList ->
                dataExtractorRegexes.forEach { dataExtractorRegex ->
                    dataList
                        .map { (build, tasksWithData) ->
                            tasksWithData.map { (task, rawData) ->
                                dataExtractorRegex.find(rawData)?.run {
                                    groups["data"]?.value
                                }?.let { data ->
                                    collector.computeIfAbsent(task.name) { mutableMapOf() }
                                    collector[task.name]!!.put(build, data)
                                }
                            }
                            build
                        }
                        .toList()
                        .let { Printer.csvPrint("# regex: $dataExtractorRegex", it, collector, notFoundPlaceHolder) }
                }
            }
    }

    private fun fetchRawDataForTasks(tasks: List<Task>): List<Pair<Task, String>> {
        val rawData = mutableListOf<Pair<Task, String>>()
        tasks.map { task ->
            RequestBuilder.logDownloadLink(task.id, logName)
                .also { logger?.verbose { "Downloading log for '${task.name}' (${task.id}) from $it..." } }
                .httpGet()
                .authenticate()
                .responseString { request, _, result ->
                    when (result) {
                        is Result.Failure -> logger?.error(
                            "ERROR: Could not fetch data for '${task.name}' from ${request.url}: " +
                                "${result.error.localizedMessage}."
                        )
                        is Result.Success -> {
                            logger?.verbose { "SUCCESS: Download of log for '${task.name}' from ${request.url} done." }
                            /*result.get().run {
                                val startIndex = indexOf("Sensor CSharpSecuritySensor [security] (done) | time=")
                                val endIndex = indexOf("ms", startIndex)
                                rawData.add(task to substring(startIndex, endIndex + 2))
                            }*/
                            rawData.add(task to result.get())
                        }
                    }
                }
        }.forEach { async -> async.join() }
        return rawData
    }

    private fun getTasksForLatestBuild(build: Build): Pair<BuildWithMetadata, List<Task>> {
        return apiUrl.httpPost()
            .authenticate()
            .jsonBody(RequestBuilder.tasksQuery(repositoryId, build.branchName, build.buildOffset).toRequestString())
            .responseObject<Response>(json)
            .let { (request, _, result) ->
                when (result) {
                    is Result.Failure -> {
                        logger?.error("Failure: ${result.error.localizedMessage}")
                        exitProcess(1)
                    }
                    is Result.Success -> {
                        if (result.get().errors == null) {
                            BuildWithMetadata(
                                getBuildId(result),
                                getBuildDate(result),
                                build
                            ).let { buildWithMetadata ->
                                buildWithMetadata to extractAllTasks(result).also {
                                    logger?.print {
                                        "Successfully extracted ${it.size} tasks for build '$buildWithMetadata'"
                                    }
                                }
                            }
                        } else {
                            logger?.error("Errors encountered while sending request to ${request.url}:")
                            for (error in result.get().errors!!) {
                                logger?.error("  ${error.message}")
                            }
                            exitProcess(2);
                        }
                    }
                }
            }
    }

    private fun extractAllTasks(result: Result<Response, FuelError>): List<Task> = getBuildNode(result).tasks

    private fun getBuildId(result: Result<Response, FuelError>): String = getBuildNode(result).id

    private fun getBuildDate(result: Result<Response, FuelError>): Long = getBuildNode(result).buildCreatedTimestamp

    private fun getBuildNode(result: Result<Response, FuelError>): Node =
        result.get().data?.repository?.builds?.edges?.let { it[it.size - 1].node }
            ?: throw IllegalStateException("We got an empty response body - this seems wrong, we should have failed earlier.")

    private fun Request.authenticate(): Request = authenticator(this)
}
