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

fun main(rawArgs: Array<String>) {
    val args = CliArgs().also { it.main(rawArgs) }
    val token = args.apiToken
    val cookie = args.cookie

    val cliLogger = CliLogger(verbose = args.verbose, quiet = args.quiet)

    val authenticator: (Request) -> Request = when {
        token.isNotEmpty() -> {
            cliLogger.print { "Using token authentication" };
            { request: Request -> request.header("Authorization", "Bearer $token") }
        }
        cookie.isNotEmpty() -> {
            cliLogger.print { "Using cookie authentication" };
            { request: Request -> request.header("Cookie", cookie) }
        }
        else -> {
            cliLogger.error(
                "No authentication details provided. Expecting environment variable CIRRUS_TOKEN or " +
                        "CIRRUS_COOKIE to be set."
            )
            exitProcess(2)
        }
    }

    cliLogger.print { "Using data extraction regex \"${args.dataExtractionRegex}\"" }

    args.branches.let { branches ->
        cliLogger.print { "Starting for branches: ${branches.joinToString(", ")}" }
        Main(
            apiUrl = args.apiUrl,
            authenticator = authenticator,
            repositoryId = args.repositoryId,
            branches = branches,
            dataExtractorRegex = args.dataExtractionRegex,
            logName = args.logName,
            notFoundPlaceHolder = args.notFoundPlaceholder,
            logger = cliLogger
        ).run()
    }
}

class Main(
    private val apiUrl: String,
    private val authenticator: (Request) -> Request,
    private val repositoryId: String,
    private val branches: List<String>,
    private val dataExtractorRegex: Regex,
    private val logName: String,
    private val notFoundPlaceHolder: String,
    private val logger: CliLogger?
) {
    fun run() {
        val collector = mutableMapOf<String, MutableMap<String, String>>()
        branches
            .asSequence()
            .map { branch -> branch to getTasksForLatestBuild(branch) }
            .map { (branch, tasks) ->
                logger?.print { "Fetching logs for branch '$branch'" }
                branch to fetchRawDataForTasks(tasks).sortedBy { (task, _) -> task.name }
            }
            .map { (branch, tasksWithData) ->
                tasksWithData.map { (task, rawData) ->
                    dataExtractorRegex.find(rawData)?.run {
                        groups["data"]?.value
                    }?.let { data ->
                        collector.computeIfAbsent(task.name) { mutableMapOf() }
                        collector[task.name]!!.put(branch, data)
                    }
                }
                branch
            }
            .toSet()
            .sorted()
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

    private fun getTasksForLatestBuild(branchName: String): List<Task> {
        val objectMapper = ObjectMapper().registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE

        return apiUrl.httpPost()
            .authenticate()
            .jsonBody(RequestBuilder.tasksQuery(repositoryId, branchName).toRequestString())
            .responseObject<Response>()
            .let { (request, response, result) ->
                when (result) {
                    is Result.Failure -> {
                        logger?.error("Failure: ${result.error.localizedMessage}")
                        exitProcess(1)
                    }
                    is Result.Success -> {
                        extractAllTasks(result)
                    }
                }
            }
    }

    private fun extractAllTasks(result: Result<Response, FuelError>): List<Task> =
        result.get().data.repository.builds.edges[0].node.tasks

    private fun Request.authenticate(): Request = authenticator(this)
}

