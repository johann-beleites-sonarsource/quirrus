package com.sonarsource.dev.quirrus.cmd

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sonarsource.dev.quirrus.Build
import org.sonarsource.dev.quirrus.GenericWorker
import org.sonarsource.dev.quirrus.Printer
import org.sonarsource.dev.quirrus.api.LogDownloader


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


    override fun run() {
        logger.debug { "Using data extraction regexes: [ ${dataExtractionRegexes.joinToString(separator = ", ") { r -> "\"$r\"" }} ]" }

        val collector = mutableMapOf<String, MutableMap<Build, String>>()

        runBlocking {
            branches
                .map { Build.ofBuild(it) }
                .also { builds -> logger.print { "Starting for builds: ${builds.joinToString(", ")}" } }
                .map { build -> GenericWorker(apiConfiguration).getTasksForBuild(build, repositoryId) }
                .map { (build, tasks) ->
                    logger.print { "Fetching logs for build '$build'" }
                    build to LogDownloader(apiConfiguration).downloadLogsForTasks(tasks, logName).sortedBy { (task, _) -> task.name }
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
    }

    /*private fun getBuildNode(result: Result<RepositoryApiResponse, FuelError>): BuildNode =
        result.get().data?.repository?.builds?.edges?.let { it[it.size - 1].node }
            ?: throw IllegalStateException("We got an empty response body - this seems wrong, we should have failed earlier.")*/
}
