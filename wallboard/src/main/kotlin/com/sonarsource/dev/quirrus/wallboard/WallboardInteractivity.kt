package com.sonarsource.dev.quirrus.wallboard

import com.sonarsource.dev.quirrus.wallboard.data.BuildWithTasks
import com.sonarsource.dev.quirrus.wallboard.data.DataProcessing
import com.sonarsource.dev.quirrus.wallboard.data.TaskDiffData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.Task
import org.sonarsource.dev.quirrus.api.Common
import org.sonarsource.dev.quirrus.api.LogDownloader
import org.sonarsource.dev.quirrus.gui.GuiAuthenticationHelper
import javax.net.ssl.SSLHandshakeException

internal fun reloadData(
    currentState: AppState,
    repoTextFieldVal: String,
    branches: List<String>,
    selectedTab: String?,
    setState: (AppState) -> Unit,
    setError: (String) -> Unit,
    setRepoTextFieldVal: (String) -> Unit,
    setLastTasks: (Map<String, List<BuildWithTasks>?>) -> Unit,
    setSelectedTab: (String) -> Unit,
    saveConfig: () -> Unit,
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

                DataProcessing.processData(cirrusData.getLastPeachBuilds(repoId, branches, 8)).also {
                    setLastTasks(it)
                }
            }.onFailure { e ->
                val errorMsg = when (e) {
                    is SSLHandshakeException -> "Could not verify TLS certificate!\n\n"
                    is NoSuchFileException, is java.nio.file.NoSuchFileException -> "Have you tried authenticating?\n\n"
                    else -> ""
                }
                setError(errorMsg + e.stackTraceToString())
                setState(AppState.ERROR)
                e.printStackTrace(System.err)
            }.onSuccess { lastTasks ->
                if (selectedTab == null) {
                    setSelectedTab(lastTasks.keys.minOf { it })
                }
                setState(AppState.NONE)
            }
        }
    }
}

internal fun launchBackgroundRefreshPoll(
    runId: Long,
    getCurrentRunId: () -> Long,
    branch: String,
    repoTextFieldVal: String,
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
        setBackgroundLoadingInProgress(true)
        DataProcessing.processData(cirrusData.getLastPeachBuilds(repoId, listOf(branch), 8)).also {
            if (getCurrentRunId() == runId) {
                setResult(it)
                setBackgroundLoadingInProgress(false)
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
    for (task in taskList) {
        addTaskDiff(task.id, null)
        GlobalScope.async {
            val job = launch {
                logDownloader.downloadLogsForTasks(listOf(task), "snapshot_generation.log").firstOrNull()?.let { (_, log) ->
                    val rules = ruleRegex.findAll(log).mapNotNull {
                        val key = it.groups["ruleKey"]?.value ?: return@mapNotNull null
                        val number = it.groups["number"]?.value?.toIntOrNull() ?: return@mapNotNull null
                        key to number
                    }.toMap()

                    val (newCount, absentCount) = taskDiffGeneralInfoRegex.find(log)?.let {
                        it.groups["newCount"]?.value?.toIntOrNull() to it.groups["absentCount"]?.value?.toIntOrNull()
                    } ?: null to null

                    addTaskDiff(task.id, TaskDiffData(rules, newCount, absentCount))
                }
            }
            delay(30_000)
            if (!job.isCompleted) {
                job.cancel()
                removeTaskDiff(task.id)
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
