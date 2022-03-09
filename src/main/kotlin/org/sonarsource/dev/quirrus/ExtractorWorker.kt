package org.sonarsource.dev.quirrus

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result


class ExtractorWorker : CirrusCommand() {
    val dataExtractionRegexes by option(
        "-x", "--regex",
        help = "The regex used for data extraction. Each regex must contain a \"data\" class, which will be used to " +
            "extract the data. Multiple regexes are allowed."
    ).convert { it.toRegex(RegexOption.MULTILINE) }
        .multiple(required = true)
        .check { regexList ->
            regexList
                .map { regex -> regex.pattern.indexOf("(?<data>") >= 0 }
                .fold(true, { acc, v -> acc && v })
        }

    val branches by argument(help = "The names of the builds (e.g. peachee branches) to compare")
        .multiple(required = true)

    val notFoundPlaceholder by option(
        "--not-found",
        help = "Will be used whenever a value could not be found/extracted in place of that value."
    ).default("-")

    val logName by option(
        "-l", "--log-name",
        help = "The name of the Cirrus task log file to download"
    ).required()

    private val genericWorker = GenericWorker(this)

    override fun run() {
        "Using data extraction regexes: [ ${dataExtractionRegexes.joinToString(separator = ", ") { r -> "\"$r\"" }} ]"

        val collector = mutableMapOf<String, MutableMap<Build, String>>()
        branches
            .map { Build.ofBuild(it) }
            .also { builds -> logger.print { "Starting for builds: ${builds.joinToString(", ")}" } }
            .map { build -> genericWorker.getTasksForBuild(build) }
            .map { (build, tasks) ->
                logger.print { "Fetching logs for build '$build'" }
                build to fetchRawDataForTasks(tasks).sortedBy { (task, _) -> task.name }
            }
            .let { dataList ->
                dataExtractionRegexes.forEach { dataExtractorRegex ->
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
                        .let { Printer.csvPrint("# regex: $dataExtractorRegex", it, collector, notFoundPlaceholder) }
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

    private fun getBuildNode(result: Result<ApiResponse, FuelError>): Node =
        result.get().data?.repository?.builds?.edges?.let { it[it.size - 1].node }
            ?: throw IllegalStateException("We got an empty response body - this seems wrong, we should have failed earlier.")

    private fun Request.authenticate(): Request = authenticator(this)
}
