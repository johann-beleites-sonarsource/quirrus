package org.sonarsource.dev.quirrus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import kotlin.system.exitProcess

class Worker(
    private val apiUrl: String,
    private val authenticator: (Request) -> Request,
    private val repositoryId: String,
    private val builds: List<Build>,
    private val dataExtractorRegex: Regex,
    private val logName: String,
    private val notFoundPlaceHolder: String,
    private val logger: CliLogger?
) {
    fun run() {
        val collector = mutableMapOf<String, MutableMap<Build, String>>()
        builds
            .asSequence()
            .map { build -> build to getTasksForLatestBuild(build) }
            .map { (build, tasks) ->
                logger?.print { "Fetching logs for build '$build'" }
                build to fetchRawDataForTasks(tasks).sortedBy { (task, _) -> task.name }
            }
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
            .let { Printer.csvPrint(it, collector, notFoundPlaceHolder) }
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
                            "ERROR: Could not get fetch data for '${task.name}' from ${request.url}: " +
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

    private fun getTasksForLatestBuild(build: Build): List<Task> {
        val objectMapper = ObjectMapper().registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE

        return apiUrl.httpPost()
            .authenticate()
            .jsonBody(RequestBuilder.tasksQuery(repositoryId, build.branchName, build.buildOffset).toRequestString())
            .responseObject<Response>()
            .let { (_, _, result) ->
                when (result) {
                    is Result.Failure -> {
                        logger?.error("Failure: ${result.error.localizedMessage}")
                        exitProcess(1)
                    }
                    is Result.Success -> {
                        extractAllTasks(result).also {
                            logger?.print {
                                "Successfully extracted ${it.size} tasks for build '$build' (${getBuildId(result)})"
                            }
                        }
                    }
                }
            }
    }

    private fun extractAllTasks(result: Result<Response, FuelError>): List<Task> =
        result.get().data.repository.builds.edges.let { it[it.size - 1].node.tasks }

    private fun getBuildId(result: Result<Response, FuelError>): String =
        result.get().data.repository.builds.edges.let { it[it.size - 1].node.id }

    private fun Request.authenticate(): Request = authenticator(this)
}
