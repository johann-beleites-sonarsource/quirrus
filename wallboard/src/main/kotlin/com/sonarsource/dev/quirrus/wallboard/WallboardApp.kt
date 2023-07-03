package com.sonarsource.dev.quirrus.wallboard

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sonarsource.dev.quirrus.wallboard.guicomponents.ErrorScreen
import com.sonarsource.dev.quirrus.wallboard.guicomponents.Histogram
import com.sonarsource.dev.quirrus.wallboard.guicomponents.Label
import com.sonarsource.dev.quirrus.wallboard.guicomponents.ListTitle
import com.sonarsource.dev.quirrus.wallboard.guicomponents.LoadingScreen
import com.sonarsource.dev.quirrus.wallboard.guicomponents.SideTab
import com.sonarsource.dev.quirrus.wallboard.guicomponents.TaskList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sonarsource.dev.quirrus.BuildNode
import org.sonarsource.dev.quirrus.Builds
import org.sonarsource.dev.quirrus.Task
import org.sonarsource.dev.quirrus.api.Common
import org.sonarsource.dev.quirrus.gui.GuiAuthenticationHelper
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val configFile = Path.of(System.getenv("HOME"), ".quirrus", "branches.conf").also { file ->
    if (!file.parent.exists()) {
        file.parent.createDirectories()
    }

    if (!file.exists()) {
        file.createFile()
    } else if (!file.isRegularFile()) {
        throw IllegalStateException("config file '$file' exists but is not a regular file")
    }
}

private val configStrings = configFile.readText().split(";")
private val initialBranches = configStrings.elementAtOrNull(0)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
private val initialRepo = configStrings.elementAtOrNull(1) ?: ""

var cirrusData = CirrusData(API_CONF)

private fun processData(builds: Map<String, Builds?>) =
    builds.map { (branch, builds) ->
        if (builds == null || builds.edges.isEmpty()) return@map branch to null

        /*val (latestBuild, previousBuilds) = builds.edges.map {
            it.node
        }.sortedByDescending {
            it.buildCreatedTimestamp
        }.let {
            it.first() to it.drop(1)
        }*/

        val enrichedBuilds = builds.edges.map {
            it.node
        }.sortedByDescending {
            it.buildCreatedTimestamp
        }.mapIndexed { buildIndex, buildNode ->
            buildNode to buildNode.tasks.groupBy { task ->
                task.name
            }.map { (name, reruns) ->
                // We set the last build with different status later.
                name to EnrichedTask(reruns.sortedByDescending { it.creationTimestamp }, buildNode, null)
            }.toMap()
        }

        enrichedBuilds.mapIndexed { i, tasks ->
            tasks.first to tasks.second.forEach { (taskName, task) ->
                val currentStatus = task.latestRerun.status
                task.lastBuildWithDifferentStatus = enrichedBuilds.drop(i + 1).firstOrNull { (_, previousTasks) ->
                    (previousTasks[taskName]?.latestRerun?.status ?: currentStatus) != currentStatus
                }?.let { (_, previousTasks) ->
                    previousTasks[taskName]?.build
                }
            }
        }


        branch to enrichedBuilds

        // This could be more efficient e.g. by transforming the data into maps to lookup in instead

        /*val (completed, failed) = sortedBuilds.tasks
            .groupBy { it.name }
            .map { (name, tasks) ->
                val tasksSorted = tasks.sortedBy { it.id }
                val currentTask = tasksSorted.last()

                val lastDifferingBuild = previousBuilds.firstOrNull { previousBuild ->
                    previousBuild.tasks.firstOrNull { previousTask ->
                        previousTask.name == currentTask.name
                    }?.status !in listOf(currentTask.status, "SKIPPED", "ABORTED")
                }

                EnrichedTask(tasks, latestBuild, lastDifferingBuild)
            }.partition {
                it.tasks.last().status == "COMPLETED"
            }

        branch to SortedTasks(completed, failed)*/
    }.toMap()

data class EnrichedTask(val taskReruns: List<Task>, val build: BuildNode, var lastBuildWithDifferentStatus: BuildNode?) {
    val latestRerun
        get() = taskReruns.first()
}

data class SortedTasks(val completed: List<EnrichedTask>, val failed: List<EnrichedTask>)

private enum class AppState {
    LOADING, ERROR, NONE, INIT
}

private fun reload(
    currentState: AppState,
    repoTextFieldVal: String,
    branches: List<String>,
    selectedTab: String?,
    setState: (AppState) -> Unit,
    setError: (String) -> Unit,
    setRepoTextFieldVal: (String) -> Unit,
    setLastTasks: (Map<String, List<Pair<BuildNode, Map<String, EnrichedTask>>>?>) -> Unit,
    setSelectedTab: (String) -> Unit,
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

        configFile.writeText("${branches.joinToString(",")};$repoTextFieldVal")

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

                processData(cirrusData.getLastPeachBuilds(repoId, branches, 15)).also {
                    setLastTasks(it)
                }
            }.onFailure { e ->
                val error = e.stackTraceToString()
                setError(error)

                if (e is NoSuchFileException || e is java.nio.file.NoSuchFileException) {
                    setError("Have you tried authenticating?\n\n$error")
                }

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

@Composable
@Preview
fun WallboardApp() {
    var state by remember { mutableStateOf(AppState.INIT) }
    var error by remember { mutableStateOf("Unknown") }
    //var lastTasks by remember { mutableStateOf(emptyMap<String, SortedTasks?>()) }
    var lastTasks by remember { mutableStateOf(emptyMap<String, List<Pair<BuildNode, Map<String, EnrichedTask>>>?>()) }
    var dataByBranch = lastTasks.mapNotNull { (branch, tasks) ->
        if (tasks == null) null
        else branch to processData(tasks)
    }.toMap()
    var selectedTab: String? by remember { mutableStateOf(null) }
    var branches by remember { mutableStateOf(initialBranches) }
    var branchesTextFieldVal by remember { mutableStateOf(initialBranches.joinToString(",")) }
    var repoTextFieldVal by remember { mutableStateOf(initialRepo) }
    var clickPosition by remember { mutableStateOf(-1f) }
    val taskListScrollState = rememberScrollState(0)

    fun triggerReload() = reload(
        state,
        repoTextFieldVal,
        branches,
        selectedTab,
        { state = it },
        { error = it },
        { repoTextFieldVal = it },
        { lastTasks = it },
        { selectedTab = it }
    )

    fun authenticate() {
        GlobalScope.launch {
            GuiAuthenticationHelper(API_CONF, AUTH_CONF_FILE).AuthWebView(AUTH_CONF_FILE)
            triggerReload()
        }
    }

    MaterialTheme {
        Box {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Column(modifier = Modifier.weight(0.1f)) {
                    TextField(
                        value = repoTextFieldVal,
                        label = { Label(CIRRUS_REPO_TEXT_FIELD_LABEL) },
                        onValueChange = { newValue ->
                            repoTextFieldVal = newValue
                        }
                    )

                    lastTasks.keys.sorted().forEach { branch ->
                        SideTab(
                            onClick = {
                                clickPosition = -1f
                                GlobalScope.launch { taskListScrollState.scrollTo(0) }
                                selectedTab = branch
                            },
                            //text = "$branch (${lastTasks.get(branch)?.failed?.size})",
                            text = branch,
                            //bgColor = if (branch == selectedTab) MaterialTheme.colors.primary else MaterialTheme.colors.primaryVariant
                            bgColor = (dataByBranch[branch]?.first?.firstOrNull()?.second?.keys?.maxByOrNull { it }?.color ?: Color.Gray),
                            selected = branch == selectedTab
                        )
                    }

                    TextField(
                        value = branchesTextFieldVal,
                        label = { Label(BRANCHES_FIELD_LABEL) },
                        onValueChange = { newValue ->
                            branchesTextFieldVal = newValue
                            branches = newValue.split(',').filter { it.isNotBlank() }
                        },
                    )

                    Button(
                        onClick = ::triggerReload,
                        enabled = state != AppState.LOADING,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary,
                            contentColor = MaterialTheme.colors.onSecondary
                        ),
                    ) {
                        Icon(Icons.Outlined.Refresh, "Refresh")
                    }

                    Button(
                        onClick = ::authenticate,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary,
                            contentColor = MaterialTheme.colors.onSecondary
                        ),
                    ) {
                        Text("Authenticate")
                    }
                }

                Column(modifier = Modifier.weight(0.9f)) {
                    Row {
                        when (state) {
                            AppState.LOADING -> LoadingScreen()
                            AppState.ERROR -> ErrorScreen(error)
                            AppState.INIT -> triggerReload()
                            else -> dataByBranch[selectedTab]?.let { (taskHistory, metadata) ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(5.dp)
                                ) {
                                    val clickedIndex = if (clickPosition >= 0) {
                                        taskHistory.size - (taskHistory.size.toFloat() * clickPosition).toInt() - 1
                                    } else {
                                        0
                                    }

                                    val selectedTasks = taskHistory[clickedIndex]
                                    val amountFailed = selectedTasks.second.filter {
                                        it.key.status.isFailingState()
                                    }.map {
                                        it.value.size
                                    }.sum()
                                    val amountSucceeded = selectedTasks.second.filter {
                                        it.key.status == StatusCategory.SUCCESS
                                    }.map {
                                        it.value.size
                                    }.sum()
                                    val totalAmount = selectedTasks.second.map {
                                        it.value.size
                                    }.sum()


                                    ListTitle(
                                        "$selectedTab Peach Jobs",
                                        amountSucceeded,
                                        amountFailed,
                                        totalAmount,
                                        selectedTasks.first.buildCreatedTimestamp
                                    )

                                    Row(modifier = Modifier.weight(0.4f).padding(vertical = 5.dp)) {
                                        Histogram(taskHistory, metadata, clickedIndex) {
                                            clickPosition = it
                                        }
                                    }

                                    Row(modifier = Modifier.weight(0.6f)) {
                                        TaskList(selectedTasks, taskListScrollState)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun processData(history: List<Pair<BuildNode, Map<String, EnrichedTask>>>): Pair<List<Pair<BuildNode, Map<Status, List<EnrichedTask>>>>, MetaData> {

    var maxFailed = 0
    var maxOther = 0

    val filteredHistory = history.filterIndexed { i, (_, tasks) ->
        i == 0 || tasks.values.any { StatusCategory.ofCirrusTask(it.latestRerun) != StatusCategory.UNDECIDED }
    }

    val res = filteredHistory.mapIndexed { i, (build, taskMap) ->
        var failed = 0
        var other = 0

        val grouped = taskMap.values.groupBy { task ->
            val status = StatusCategory.ofCirrusTask(task.latestRerun)

            when {
                status.isFailingState() -> failed++
                else -> other++
            }

            val isNewStatus = filteredHistory.getOrNull(i + 1)?.second?.get(task.latestRerun.name)?.latestRerun?.let { lastRun ->
                lastRun.status != task.latestRerun.status || StatusCategory.ofCirrusTask(lastRun) != status
            } ?: (i < filteredHistory.size - 1)

            Status(status, isNewStatus)
        }

        if (failed > maxFailed) maxFailed = failed
        if (other > maxOther) maxOther = other

        build to grouped
    }

    return res to MetaData(maxFailed, maxOther)
}

data class Status(val status: StatusCategory, val new: Boolean) : Comparable<Status> {
    companion object {
        private fun StatusCategory.toInt() = when (this) {
            StatusCategory.SUCCESS -> 2
            StatusCategory.UNDECIDED -> 4
            StatusCategory.FAIL_SOFT -> 6
            StatusCategory.FAIL_HARD -> 8
        }
    }

    val color: Color = when (status) {
        StatusCategory.FAIL_HARD -> Color.Red
        StatusCategory.FAIL_SOFT -> Color.Yellow
        StatusCategory.SUCCESS -> Color.Green
        StatusCategory.UNDECIDED -> Color.Gray
    }.let {
        if (!new) {
            it.copy(alpha = 0.4f)
        } else it
    }

    private fun toInt() = status.toInt().let { if (!new) it - 1 else it }
    override fun compareTo(other: Status) = toInt() - other.toInt()
}

data class MetaData(val maxFailed: Int, val maxOther: Int) {
    val total = maxFailed + maxOther
}

enum class StatusCategory {
    SUCCESS, FAIL_HARD, FAIL_SOFT, UNDECIDED;

    companion object {
        fun ofCirrusTask(task: Task) = when (task.status) {
            "COMPLETED" -> SUCCESS
            "ABORTED", "FAILED" -> {
                if (task.firstFailedCommand?.name?.contains("analyze") == true) {
                    FAIL_HARD
                } else {
                    FAIL_SOFT
                }
            }

            "CREATED", "TRIGGERED", "SCHEDULED", "EXECUTING", "SKIPPED", "PAUSED" -> UNDECIDED
            else -> throw Exception("Unknown task status ${task.status}")
        }
    }

    fun isFailingState() = this == FAIL_HARD || this == FAIL_SOFT
}
