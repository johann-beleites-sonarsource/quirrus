package com.sonarsource.dev.quirrus.wallboard

import com.sonarsource.dev.quirrus.wallboard.data.TaskDiffData
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sonarsource.dev.quirrus.RequestBuilder
import org.sonarsource.dev.quirrus.api.ApiException
import org.sonarsource.dev.quirrus.api.LogDownloader
import org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Task

internal class BuildDataManager(
    private val getBuildsPerBranch: (String) -> List<String>?,
    private val getBuildData: (String) -> BuildDataItem?,
    private val updateState: (AppState) -> Unit,
    private val updateBuildDataDirectory: (BuildDataItem) -> Unit,
    private val updateTaskDiffs: (String, TaskDiffData) -> Unit
) {

    private var buildFetchingAssistant: BuildFetchingAssistant? = null
    private var backgroundAutoRefreshJob: Job? = null
    private var netWorker = AsyncNetWorker().apply { start() }

    private val fetchingAssistant
        get() = buildFetchingAssistant ?: BuildFetchingAssistant(getBuildData, ::updateBuildData) { updateState(AppState.NONE) }.also {
            buildFetchingAssistant = it
        }

    private fun updateBuildData(buildDataItem: BuildDataItem) {
        updateBuildDataDirectory(buildDataItem)
        recomputeStatusFreshness(buildDataItem)
        if (buildDataItem is LoadedBuildData) loadDiffsIfNecessary(buildDataItem)
    }

    private fun loadDiffsIfNecessary(buildData: LoadedBuildData) {
        buildData.build.tasks.forEach { task ->
            if (task.artifacts.any { it.name == "diff_report" && it.files.isNotEmpty() }) {
                val request = RequestToSend(
                    requestFun = { TaskDiffDataLoader.loadDiffData(task) },
                    callback = { result ->
                        result?.let { TaskDiffDataLoader.processResult(it) }?.let { updateTaskDiffs(task.id, it) }
                    },
                    errorHandler = { e -> println("Failed to load diff data for ${task.id}: ${e.message}") }
                )
                runBlocking {
                    netWorker.send(request)
                }
            }
        }
    }

    private fun recomputeStatusFreshness(updatedBuild: BuildDataItem) {
        getBuildsPerBranch(updatedBuild.baseInfo.branch).let { builds ->
            val index = builds?.indexOf(updatedBuild.baseInfo.id) ?: return

            // update subsequent builds
            for (i in index - 1 downTo 0) {
                val build = getBuildData(builds[i]) ?: continue
                if (build is LoadedBuildData) {
                    val lastBuild = getBuildData(builds[i + 1]) as? LoadedBuildData ?: break
                    updateBuildData(build.update(lastBuild))
                } else break
            }
        }
    }

    fun cancel() {
        buildFetchingAssistant?.cancel()
        buildFetchingAssistant = null
        netWorker.start()
    }

    suspend fun load(buildDataItems: List<PendingBuildData>) {
        fetchingAssistant.start(buildDataItems)
    }

    fun startBackgroundRefreshPoll(branches: List<String>, setLoadingStatus: (String, Boolean) -> Unit) {
        stopBackgroundRefreshPoll()
        backgroundAutoRefreshJob = GlobalScope.launch {
            while (true) {
                branches.forEach { branch ->
                    getBuildsPerBranch(branch)?.firstOrNull()?.let { buildId ->
                        getBuildData(buildId) as? LoadedBuildData
                    }?.let {
                        setLoadingStatus(branch, true)
                        reload(it) {
                            setLoadingStatus(branch, false)
                        }
                    }
                }
                delay(20_000)
            }
        }
    }

    fun stopBackgroundRefreshPoll() {
        backgroundAutoRefreshJob?.cancel()
        backgroundAutoRefreshJob = null
    }

    private fun reload(buildData: LoadedBuildData, doneHandler: () -> Unit) {
        fetchingAssistant.reload(buildData, doneHandler)
    }

    fun isBackgroundRefreshPollRunning() = backgroundAutoRefreshJob != null
}

private object TaskDiffDataLoader {
    private val ruleRegex = """:(?<ruleKey>S[0-9]{3,4})[":\s]+(?<number>[0-9]+)""".toRegex()
    private val taskDiffGeneralInfoRegex = """NEW: (?<newCount>[0-9]+),.+ABSENT: (?<absentCount>[0-9]+)""".toRegex()

    suspend fun loadDiffData(task: Task): Pair<Task, String>? {
        val downloadLink = RequestBuilder.logDownloadLink(task.id, "snapshot_generation.log")
        API_CONF.logger?.debug { "Downloading log for '${task.name}' (${task.id}) from $downloadLink..." }
        return API_CONF.get(downloadLink).let { response ->
            if (response.status != HttpStatusCode.OK) {
                val errorMsg =
                    "ERROR: Could not fetch data for '${task.name}' from ${response.request.url}: ${response.status}."
                API_CONF.logger?.error(errorMsg) ?: throw ApiException(response, errorMsg)
                null
            } else {
                API_CONF.logger?.debug { "SUCCESS: Download of log for '${task.name}' from ${response.request.url} done." }
                task to response.bodyAsText()
            }
        }
    }

    fun processResult(data: Pair<Task, String>?): TaskDiffData? {
        return data?.let { (_, log) ->
            val rules = ruleRegex.findAll(log).mapNotNull {
                val key = it.groups["ruleKey"]?.value ?: return@mapNotNull null
                val number = it.groups["number"]?.value?.toIntOrNull() ?: return@mapNotNull null
                key to number
            }.toMap()

            val (newCount, absentCount) = taskDiffGeneralInfoRegex.find(log)?.let {
                it.groups["newCount"]?.value?.toIntOrNull() to it.groups["absentCount"]?.value?.toIntOrNull()
            } ?: null to null

            TaskDiffData(rules, newCount, absentCount)
        }
    }
}