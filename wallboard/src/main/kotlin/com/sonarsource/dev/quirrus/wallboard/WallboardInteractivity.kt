package com.sonarsource.dev.quirrus.wallboard

import com.sonarsource.dev.quirrus.wallboard.data.BuildWithTasks
import com.sonarsource.dev.quirrus.wallboard.data.DataItemState
import com.sonarsource.dev.quirrus.wallboard.data.DataProcessing
import com.sonarsource.dev.quirrus.wallboard.data.EnrichedTask
import com.sonarsource.dev.quirrus.wallboard.data.Status
import com.sonarsource.dev.quirrus.wallboard.data.StatusCategory
import com.sonarsource.dev.quirrus.wallboard.data.TaskDiffData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.api.Common
import org.sonarsource.dev.quirrus.api.LogDownloader
import org.sonarsource.dev.quirrus.generated.graphql.ID
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.RepositoryBuildsConnection
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Task
import org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Build
import org.sonarsource.dev.quirrus.gui.GuiAuthenticationHelper
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException
import kotlin.random.Random

internal fun reloadData(
    branches: List<String>,
    setTabDisplayItems: (String, List<DataItemToDisplay>) -> Unit,
) {
    branches.forEach { branch ->
        setTabDisplayItems(branch, (1..10).map { index -> DataItemToDisplay(branch, Random.nextLong().toString(), Random.nextLong(), index) })
    }
}

internal fun reloadData(
    currentState: AppState,
    repoTextFieldVal: String,
    branches: List<String>,
    selectedTab: String?,
    lastTasksAcc: MutableMap<String, List<BuildWithTasks>?>,
    setState: (AppState) -> Unit,
    setError: (String) -> Unit,
    setRepoTextFieldVal: (String) -> Unit,
    setSelectedTab: (String) -> Unit,
    saveConfig: () -> Unit,
    setBranchState: (String, AppState) -> Unit,
    setBranchError: (String, String) -> Unit,
    cancelled: () -> Boolean,
) {
    if (currentState != AppState.LOADING) {
        if (repoTextFieldVal.isBlank()) {
            setError("The $CIRRUS_REPO_TEXT_FIELD_LABEL is required.")
            setState(AppState.ERROR)
            return
        } else if (branches.isEmpty()) {
            setError("You need to provide at least 1 branch to fetch data for.")
            setState(AppState.ERROR)
            return
        }

        setState(AppState.LOADING)

        saveConfig()

        GlobalScope.launch {
            runCatching {
                val trimmedRepo = repoTextFieldVal.trim()
                val repoId = if (trimmedRepo.toLongOrNull() != null) {
                    trimmedRepo
                } else {
                    Common(API_CONF).resolveRepositoryId(trimmedRepo).also {
                        setRepoTextFieldVal(it)
                    }
                }

                fetchDataIncrementallyByBranch(repoId, branches, lastTasksAcc, setBranchState, setBranchError, cancelled)

            }.onFailure { e ->
                val errorMsg = when (e) {
                    is SSLHandshakeException -> "Could not verify TLS certificate!\n\n"
                    is NoSuchFileException, is java.nio.file.NoSuchFileException -> "Have you tried authenticating?\n\n"
                    else -> ""
                }
                setError(errorMsg + e.stackTraceToString())
                setState(AppState.ERROR)
                e.printStackTrace(System.err)
            }.onSuccess {
                if (selectedTab == null) {
                    setSelectedTab(branches.first())
                }
                setState(AppState.NONE)
            }
        }
    }
}

private suspend fun fetchDataIncrementallyByBranch(
    repoId: String,
    branches: List<String>,
    lastTasks: MutableMap<String, List<BuildWithTasks>?>,
    setBranchState: (String, AppState) -> Unit,
    setBranchError: (String, String) -> Unit,
    cancelled: () -> Boolean,
) {
    // First get only the last 2 builds for each branch
    coroutineScope {
        // TODO: error handling

        val numberOfBuildsToFetch = 10
        val fetchedBuildsPerBranch = branches.associateWith { AtomicInteger(0) }
        val dataInputChannel = Channel<Pair<String, RepositoryBuildsConnection?>>()
        val nextRequestChannel = Channel<Pair<String, Long>>()

        val updaterJob = launch {
            dataInputChannel.consumeEach { (branch, builds) ->
                if (builds != null) {
                    DataProcessing.processBuildData(builds).also {
                        lastTasks[branch] = (lastTasks[branch] ?: emptyList()) + it

                        // TODO: introduce background loading state
                        setBranchState(branch, AppState.NONE)
                    }
                }

                if (fetchedBuildsPerBranch[branch]!!.addAndGet(builds?.edges?.size ?: 0) < numberOfBuildsToFetch) {
                    builds?.edges?.minOfOrNull { it.node.changeTimestamp }?.let { timestamp ->
                        nextRequestChannel.send(branch to timestamp)
                    }
                }


            }
        }

        val fetcherJob = launch {
            nextRequestChannel.consumeEach { (branch, oldestTimestamp) ->
                async {
                    dataInputChannel.send(branch to cirrusData.getLastPeachBuilds(repoId, branch, 1, oldestTimestamp))
                }
            }
        }

        branches.map { branch ->
            async {
                runCatching {
                    dataInputChannel.send(branch to cirrusData.getLastPeachBuilds(repoId, branch, 2))
                }.onFailure { e ->
                    setBranchState(branch, AppState.ERROR)
                    setBranchError(branch, e.stackTraceToString())
                    e.printStackTrace(System.err)
                }
            }
        }.forEach {
            it.await()
        }



        while (fetchedBuildsPerBranch.values.any { it.get() < numberOfBuildsToFetch } && !cancelled()) {
            delay(500)
        }
        fetcherJob.cancel()
        updaterJob.cancel()
    }

}

internal fun launchBackgroundRefreshPoll(
    runId: Long,
    getCurrentRunId: () -> Long,
    branch: String,
    repoTextFieldVal: String,
    getAppState: () -> AppState,
    setBackgroundLoadingInProgress: (Boolean) -> Unit,
    setResult: (Map<String, List<BuildWithTasks>?>) -> Unit,
) = GlobalScope.launch {
    val trimmedRepo = repoTextFieldVal.trim()
    val repoId = if (trimmedRepo.toLongOrNull() != null) {
        trimmedRepo
    } else {
        return@launch
    }

    while (getCurrentRunId() <= runId) {
        if (getAppState() == AppState.NONE) {
            setBackgroundLoadingInProgress(true)
            DataProcessing.processData(cirrusData.getLastPeachBuilds(repoId, listOf(branch), 1)).also {
                if (getCurrentRunId() == runId) {
                    setResult(it)
                    setBackgroundLoadingInProgress(false)
                }
            }
        }
        delay(10_000)
    }
}

private val logDownloader = LogDownloader(API_CONF)
private val ruleRegex = """:(?<ruleKey>S[0-9]{3,4})[":\s]+(?<number>[0-9]+)""".toRegex()
private val taskDiffGeneralInfoRegex = """NEW: (?<newCount>[0-9]+),.+ABSENT: (?<absentCount>[0-9]+)""".toRegex()

internal fun updateRulesWithDiff(
    taskList: List<Task>,
    addTaskDiff: (String, TaskDiffData?) -> Unit,
    removeTaskDiff: (String) -> Unit,
) {
    GlobalScope.launch {
        coroutineScope {
            taskList.map { task ->
                addTaskDiff(task.id, null)
                launch {
                    runCatching {
                        logDownloader.downloadLogsForTasks(listOf(task), "snapshot_generation.log").firstOrNull()?.let { (_, log) ->
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
                    }.onFailure {
                        removeTaskDiff(task.id)
                    }.onSuccess {
                        if (it != null) {
                            addTaskDiff(task.id, it)
                        } else {
                            removeTaskDiff(task.id)
                        }
                    }
                }
            }.forEach {
                it.join()
            }
        }
    }
}

fun authenticate(triggerReload: () -> Unit) {
    GlobalScope.launch {
        GuiAuthenticationHelper(API_CONF, AUTH_CONF_FILE).AuthWebView(AUTH_CONF_FILE)
        triggerReload()
    }
}

typealias TaskReruns = List<Task>

class DataItemToDisplay(val branch: String, val buildId: ID, val buildCreatedTimestamp: Long, val index: Int, successorDataItem: DataItemToDisplay? = null) {
    /*companion object {
        private val nextId = AtomicLong(0)
    }

    val id = nextId.getAndIncrement()*/

    var successorDataItem: DataItemToDisplay? = successorDataItem
        set(value) {
            if (field != value) {
                field?.reprocessData(null)
                value?.reprocessData(this)
                field = value
            }
        }
    var state: DataItemState = DataItemState.PENDING
    var build: Build? = null
        set(value) {
            field = value
            successorDataItem?.reprocessData(this)
        }

    private fun reprocessData(reference: DataItemToDisplay?) {
        val reruns: Map<String, TaskReruns> = build?.tasks?.groupBy {
            it.name
        }?.mapValues {
            it.value.sortedBy { run -> run.creationTimestamp } as TaskReruns
        } ?: return

        if (reference == null) {

        } else {
            build?.tasks.first()

            /*return tasks.mapIndexed { i, task ->



                task.

                var failed = 0
                var other = 0

                val grouped = taskMap.values.groupBy { task ->
                    val status = StatusCategory.ofCirrusTask(task.latestRerun)

                    when {
                        status.isFailingState() -> failed++
                        else -> other++
                    }

                    val isNewStatus = filteredHistory.getOrNull(i + 1)?.tasks?.get(task.latestRerun.name)?.latestRerun?.let { lastRun ->
                        lastRun.status != task.latestRerun.status || StatusCategory.ofCirrusTask(lastRun) != status
                    } ?: (i < filteredHistory.size - 1)

                    Status(status, isNewStatus)
                }

                if (failed > maxFailed) maxFailed = failed
                if (other > maxOther) maxOther = other

                build to grouped
            }*/
        }
        successorDataItem?.reprocessData(this)
    }
}

data class TaskMetadata(var status: Status, var lastBuildWithDifferentStatus: ID)
