package org.sonarsource.dev.quirrus

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.kittinunf.fuel.core.awaitUnit
import com.github.kittinunf.fuel.core.requests.download
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.system.exitProcess

class LogDownloader : CirrusCommand() {

    val branch: String? by option(
        "-b", "--branch",
        help = "The branch from which to get the logs from. This must be supplied if tasks isn't."
    )

    val numberOfTasks: Int by option(
        "-n", "--number-of-tasks",
        help = "The number of tasks for which to fetch the logs, starting and including the newest task, going backwards in time."
    ).int().default(1)

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

    override fun run() {
        if (branch == null && tasks == null) {
            System.err.println("At least branch or tasks must be set.")
            exitProcess(1)
        }

        val genericWorker = GenericWorker(this)

        val discoveredTasks = branch?.let { branchName ->
            (0 until numberOfTasks).map {
                Build("$branch~$it", branchName, it)
            }.map { build ->
                GlobalScope.async {
                    genericWorker.getTasksForBuild(build).second
                }
            }.flatMap {
                runBlocking {
                    it.await()
                }
            }.filter { task ->
                tasks?.contains(task.name) ?: false
            }.map { it.id }
        } ?: emptyList()

        runBlocking {
            downloadLogs((taskIds ?: emptyList()) + discoveredTasks)
        }
    }

    private suspend fun downloadLogs(taskIds: List<String>) {
        taskIds.map { taskId ->
            GlobalScope.launch {
                val destinationPath = targetDirectory.resolve("${logFileName}_$taskId.log")
                val downloadLink = RequestBuilder.logDownloadLink(taskId, logFileName)
                logger.verbose { "Downloading log for task $taskId from $downloadLink..." }
                runCatching {
                    downloadLink
                        .httpGet()
                        .download()
                        .fileDestination { _, _ -> destinationPath.toFile() }
                        .awaitUnit()
                }.onFailure { e ->
                    logger.error("Error downloading $destinationPath from '$downloadLink': ${e.localizedMessage}")
                }.onSuccess {
                    logger.print { "Downloaded $destinationPath." }
                }
            }
        }.forEach {
            it.join()
        }
    }
}
