package org.sonarsource.dev.quirrus.api

import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.RequestBuilder
import org.sonarsource.dev.quirrus.generated.graphql.GetTasks
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Build
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Task
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class LogDownloader(private val apiConfiguration: ApiConfiguration) {
    /**
     * Returns a list of pairs of task to the corresponding log
     */
    suspend fun downloadLogsForTasks(tasks: List<Task>, logName: String): List<Pair<Task, String>> {
        return coroutineScope {
            tasks.map { task ->
                val downloadLink = RequestBuilder.logDownloadLink(task.id, logName)
                apiConfiguration.logger?.debug { "Downloading log for '${task.name}' (${task.id}) from $downloadLink..." }
                async {
                    apiConfiguration.get(downloadLink).let { response ->
                        if (response.status != HttpStatusCode.OK) {
                            val errorMsg =
                                "ERROR: Could not fetch data for '${task.name}' from ${response.request.url}: ${response.status}."
                            apiConfiguration.logger?.error(errorMsg) ?: throw ApiException(response, errorMsg)
                            null
                        } else {
                            apiConfiguration.logger?.debug { "SUCCESS: Download of log for '${task.name}' from ${response.request.url} done." }
                            task to response.bodyAsText()
                        }
                    }
                }
            }
        }.mapNotNull { async ->
            async.await()
        }.toList()
    }

    suspend fun getLastNBuilds(repositoryId: String, branch: String?, numberOfBuilds: Int, beforeTimestamp: Long? = null) =
        GetTasks(variables = GetTasks.Variables(repositoryId, branch, numberOfBuilds, beforeTimestamp?.toString())).exec(apiConfiguration)


    suspend fun downloadLogs(
        taskIds: List<Pair<Task, Build?>>,
        dateFormat: SimpleDateFormat,
        logFileName: String,
        targetDirectory: Path
    ) {
        val successful = AtomicInteger()
        val failed = AtomicInteger()
        coroutineScope {
            taskIds.map { (task, buildNode) ->
                launch {
                    val destinationPath = if (buildNode != null) {
                        val branchEscaped = buildNode.branch.replace(File.separatorChar, '_').replace(File.pathSeparatorChar, '_')
                        val buildTime = dateFormat.format(Date(task.creationTimestamp))

                        targetDirectory.resolve("${logFileName}_${buildTime}_${branchEscaped}_${task.id}.log")
                    } else {
                        targetDirectory.resolve("${logFileName}_${task.id}.log")
                    }
                    val downloadLink = RequestBuilder.logDownloadLink(task.id, logFileName)
                    apiConfiguration.logger?.debug { "Downloading log for task ${task.id} from $downloadLink..." }
                    runCatching {
                        apiConfiguration.downloadToFile(downloadLink, destinationPath)
                    }.onFailure { e ->
                        failed.incrementAndGet()
                        apiConfiguration.logger?.error("Error downloading $destinationPath from '$downloadLink': ${e.localizedMessage}")
                    }.onSuccess {
                        successful.incrementAndGet()
                        apiConfiguration.logger?.info { "Downloaded $destinationPath." }
                    }
                }
            }.forEach {
                it.join()
            }
        }

        apiConfiguration.logger?.info { "Downloaded ${successful.get()} successfully, ${failed.get()} failed." }
    }
}
