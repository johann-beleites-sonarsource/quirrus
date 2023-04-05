package org.sonarsource.dev.quirrus.api

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.awaitUnit
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.core.requests.download
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.getOrElse
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.BuildNode
import org.sonarsource.dev.quirrus.RepositoryApiResponse
import org.sonarsource.dev.quirrus.RequestBuilder
import org.sonarsource.dev.quirrus.Task
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class LogDownloader(private val apiConfiguration: ApiConfiguration) {
    /**
     * Returns a list of pairs of task to the corresponding log
     */
    fun downloadLogsForTasks(tasks: List<Task>, logName: String): List<Pair<Task, String>> {
        val rawData = mutableListOf<Pair<Task, String>>()
        tasks.map { task ->
            RequestBuilder.logDownloadLink(task.id, logName)
                .also { apiConfiguration.logger?.debug { "Downloading log for '${task.name}' (${task.id}) from $it..." } }
                .httpGet()
                .authenticate()
                .responseString { request, _, result ->
                    result.getOrElse { error ->
                        val errorMsg = "ERROR: Could not fetch data for '${task.name}' from ${request.url}: ${error.localizedMessage}."
                        apiConfiguration.logger?.error(errorMsg) ?: throw ApiException(error.response, errorMsg)

                        null
                    }?.let { log ->
                        apiConfiguration.logger?.debug { "SUCCESS: Download of log for '${task.name}' from ${request.url} done." }
                        /*result.get().run {
                            val startIndex = indexOf("Sensor CSharpSecuritySensor [security] (done) | time=")
                            val endIndex = indexOf("ms", startIndex)
                            rawData.add(task to substring(startIndex, endIndex + 2))
                        }*/
                        rawData.add(task to log)
                    }
                }
        }.forEach { async -> async.join() }
        return rawData
    }

    fun getLastNBuilds(repositoryId: String, branch: String?, numberOfBuilds: Int) =
        apiConfiguration.apiUrl.httpPost()
            .authenticate()
            .let { request ->
                apiConfiguration.requestTimeout?.let {
                    request.timeout(it * 1000)
                    request.timeoutRead(it * 1000)
                } ?: request
            }
            .jsonBody(RequestBuilder.tasksQuery(repositoryId, branch, numberOfBuilds).toRequestString())
            .responseObject<RepositoryApiResponse>(json)


    suspend fun downloadLogs(taskIds: List<Pair<Task, BuildNode?>>, dateFormat: SimpleDateFormat, logFileName: String, targetDirectory: Path) {
        val successful = AtomicInteger()
        val failed = AtomicInteger()
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
                apiConfiguration.logger?.debug { "Downloading log for task ${task.id} from $downloadLink..." }
                runCatching {
                    downloadLink
                        .httpGet()
                        .authenticate()
                        .download()
                        .fileDestination { _, _ -> destinationPath.toFile() }
                        .awaitUnit()
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

        apiConfiguration.logger?.info { "Downloaded ${successful.get()} successfully, ${failed.get()} failed." }
    }

    private fun Request.authenticate() = apiConfiguration.authenticator(request)
}
