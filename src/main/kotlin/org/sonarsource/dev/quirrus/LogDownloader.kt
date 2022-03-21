package org.sonarsource.dev.quirrus

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.kittinunf.fuel.core.awaitUnit
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.core.requests.download
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.getOrElse
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
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

    override fun run() {
        val discoveredTasks = getLastNBuilds(numberOfTasks).let { (req, _, result) ->
            result.getOrElse { e ->
                logger.error("Could not fetch last $numberOfTasks builds: $e")
                exitProcess(1)
            }.let { apiResponse ->
                tasks
                apiResponse.data?.repository?.builds?.edges?.flatMap { edge ->
                    edge.node.let { buildNode ->
                        buildNode.tasks.filter { task ->
                            tasks?.contains(task.name) ?: true
                        }.map { it to buildNode }
                    }
                } ?: run {
                    logger.error("Got invalid response while trying to get last $numberOfTasks builds: $apiResponse")
                    exitProcess(1)
                }
            }
        }

        runBlocking {
            downloadLogs((taskIds?.map { Task(it, "UNKNOWN", 0) to null } ?: emptyList()) + discoveredTasks)
        }
    }

    fun getLastNBuilds(numberOfBuilds: Int) =
        apiUrl.httpPost()
            .authenticate()
            .let { request ->
                requestTimeout?.let {
                    request.timeout(it * 1000)
                    request.timeoutRead(it * 1000)
                } ?: request
            }
            .jsonBody(RequestBuilder.tasksQuery(repositoryId, branch, numberOfBuilds).toRequestString())
            .responseObject<RepositoryApiResponse>(json)

    val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm")
    private suspend fun downloadLogs(taskIds: List<Pair<Task, BuildNode?>>) {
        var successful = AtomicInteger()
        var failed = AtomicInteger()
        taskIds.map { (task, buildNode) ->
            GlobalScope.launch {
                val destinationPath = if (buildNode != null) {
                    val branchEscaped = buildNode.branch.replace(File.separatorChar, '_').replace(File.pathSeparatorChar, '_')
                    val buildTime = dateFormat.format(Date(task.creationTimestamp))

                    targetDirectory.resolve("${logFileName}_${buildTime}_${branchEscaped}_${task.id}.log")
                } else {
                    targetDirectory.resolve("${logFileName}_${task.id}.log")
                }
                val downloadLink = RequestBuilder.logDownloadLink(task.id, logFileName)
                logger.verbose { "Downloading log for task ${task.id} from $downloadLink..." }
                runCatching {
                    downloadLink
                        .httpGet()
                        .authenticate()
                        .download()
                        .fileDestination { _, _ -> destinationPath.toFile() }
                        .awaitUnit()
                }.onFailure { e ->
                    failed.incrementAndGet()
                    logger.error("Error downloading $destinationPath from '$downloadLink': ${e.localizedMessage}")
                }.onSuccess {
                    successful.incrementAndGet()
                    logger.print { "Downloaded $destinationPath." }
                }
            }
        }.forEach {
            it.join()
        }

        logger.print { "Downloaded ${successful.get()} successfully, ${failed.get()} failed." }
    }
}
