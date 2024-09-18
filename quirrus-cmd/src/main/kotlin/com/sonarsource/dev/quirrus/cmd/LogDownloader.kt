package com.sonarsource.dev.quirrus.cmd

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.runBlocking
import org.sonarsource.dev.quirrus.api.LogDownloader
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Build
import java.nio.file.Path
import java.text.SimpleDateFormat
import kotlin.system.exitProcess

class LogDownloader : CirrusCommand() {

    val branch: String? by option(
        "-b", "--branch",
        help = "The branch from which to get the logs from. If not provided, quirrus will not filter for branches."
    )

    val numberOfTasks: Int by option(
        "-n", "--number-of-tasks",
        help = "The number of tasks for which to fetch the logs, starting and including the newest task, going backwards in time."
    ).int().default(0)

    val tasks: List<String>? by option(
        "-k", "--tasks",
        help = "A comma-separated list of the name(s) of the task(s) to download logs for. Not providing a value here will attempt to " +
            "download the logs for all tasks."
    ).convert { it.split(",") }

    val taskIds: List<String>? by option(
        "--taskIds",
        help = "A comma-separated list of task IDs of the concrete tasks of which to get the logs."
    ).convert { it.split(",") }

    val logFileName: String by option(
        "-l", "--logfile",
        help = "The name of the log file to download."
    ).required()

    val targetDirectory: Path by argument().path(mustExist = true, canBeFile = false)

    val logDownloader by lazy {
        LogDownloader(apiConfiguration)
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm")

    override fun run() {
        runBlocking {
            val discoveredTasks = logDownloader.getLastNBuilds(repositoryId, branch, numberOfTasks).let { response ->
                // TODO: handle network issues
                if (response.errors?.isNotEmpty() == true) {
                    logger.error("Errors encountered while trying to get last $numberOfTasks tasks on branch $branch in repo $repositoryId:")
                    for (error in response.errors!!) {
                        logger.error("  ${error.message}")
                    }
                    exitProcess(1)
                } else {
                    response.data?.repository?.builds?.edges?.flatMap { edge ->
                        edge.node.let { buildNode ->
                            buildNode.tasks.filter { task ->
                                tasks?.contains(task.name) ?: true
                            }.map { it to buildNode }
                        }
                    }
                }
            } ?: emptyList()

            val predef = taskIds?.map {
                org.sonarsource.dev.quirrus.generated.graphql.gettasks.Task(
                    id = it,
                    name = "UNKNOWN",
                    creationTimestamp = 0,
                    automaticReRun = false,
                    artifacts = emptyList(),
                ) to (null as Build?)
            } ?: emptyList()

            logDownloader.downloadLogs(predef + discoveredTasks,
                dateFormat,
                logFileName,
                targetDirectory)
        }
    }
}
