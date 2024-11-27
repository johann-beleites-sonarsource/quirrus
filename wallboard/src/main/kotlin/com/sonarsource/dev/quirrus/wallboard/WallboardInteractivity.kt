package com.sonarsource.dev.quirrus.wallboard

import com.sonarsource.dev.quirrus.wallboard.data.BuildWithTasks
import com.sonarsource.dev.quirrus.wallboard.data.DataProcessing
import com.sonarsource.dev.quirrus.wallboard.data.TaskDiffData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.api.Common
import org.sonarsource.dev.quirrus.api.LogDownloader
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.RepositoryBuildsConnection
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Task
import org.sonarsource.dev.quirrus.gui.GuiAuthenticationHelper
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException

internal fun reloadData(
    repoId: String,
    branches: List<String>,
    numberOfBuildsToLoad: Int,
    appState: AppState,
    buildDataManager: BuildDataManager,
    setAppState: (AppState) -> Unit,
    setTabDisplayItems: (String, List<BuildDataItem>) -> Unit,
): Job? {
    if (appState == AppState.LOADING) return null
    setAppState(AppState.LOADING)


    return GlobalScope.launch {
        // 1. load only the build IDs of the last n builds for each branch. This is a relatively fast operation.
        val lastNBuildsMetadata = getMetadataOnLastNBuilds(repoId, branches, numberOfBuildsToLoad)

        // 2. Display the data
        branches.forEach { branch ->
            lastNBuildsMetadata[branch]?.let {
                setTabDisplayItems(branch, it)
            } ?: setTabDisplayItems(branch, emptyList())
        }

        // 3. launch the loading of the full build data for each build ID. Loading the data can take some time for builds with many tasks.
        lastNBuildsMetadata.values.flatten().sortedByDescending { it.baseInfo.buildCreatedTimestamp }.let {
            buildDataManager.load(it)
        }
    }
}

suspend fun getMetadataOnLastNBuilds(repoId: String, branches: List<String>, numberOfBuildsToLoad: Int) =
    coroutineScope {
        branches.associateWith { branch ->
            async {
                //log.debug("Loading metadata for branch $branch")
                cirrusData.getLastPeachBuildsMetadata(repoId, branch, numberOfBuildsToLoad).edges.sortedByDescending {
                    it.node.buildCreatedTimestamp
                }.fold(mutableListOf<PendingBuildData>()) { acc, buildEdge ->
                    acc.apply {
                        add(
                            PendingBuildData(
                                InitialBuildData(
                                    id = buildEdge.node.id,
                                    branch = branch,
                                    buildCreatedTimestamp = buildEdge.node.buildCreatedTimestamp,
                                    previousBuild = acc.lastOrNull()?.baseInfo?.id
                                )
                            )
                        )
                    }
                }
            }
        }.mapValues { (_, deferred) ->
            deferred.await()/*.also {
                log.debug("Finished loading metadata for branch ${it.first().branch}")
            }*/
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

