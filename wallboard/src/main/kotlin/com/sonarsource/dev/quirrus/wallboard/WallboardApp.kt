package com.sonarsource.dev.quirrus.wallboard

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
            }.map { (name, tasks) ->
                // We set the last build with different status later.
                name to EnrichedTask(tasks, buildNode, null)
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
    LOADING, ERROR, NONE
}

@Composable
@Preview
fun WallboardApp() {
    var state by remember { mutableStateOf(AppState.NONE) }
    var error by remember { mutableStateOf("Unknown") }
    //var lastTasks by remember { mutableStateOf(emptyMap<String, SortedTasks?>()) }
    var lastTasks by remember { mutableStateOf(emptyMap<String, List<Pair<BuildNode, Map<String, EnrichedTask>>>?>()) }
    var selectedTab: String? by remember { mutableStateOf(null) }
    var branches by remember { mutableStateOf(initialBranches) }
    var branchesTextFieldVal by remember { mutableStateOf(initialBranches.joinToString(",")) }
    var repoTextFieldVal by remember { mutableStateOf(initialRepo) }
    var clickedIndex by remember { mutableStateOf(0) }


    fun reload() {
        if (state != AppState.LOADING) {
            if (repoTextFieldVal.isBlank()) {
                error = "The $CIRRUS_REPO_TEXT_FIELD_LABEL is required."
                state = AppState.ERROR
                return
            } else if (branches.isEmpty()) {
                error = "You need to provide at least 1 branch to fetch data for."
                state = AppState.ERROR
                return
            }

            state = AppState.LOADING

            configFile.writeText("${branches.joinToString(",")};$repoTextFieldVal")

            GlobalScope.launch {
                runCatching {
                    val trimmedRepo = repoTextFieldVal.trim()
                    val repoId = if (trimmedRepo.toLongOrNull() != null) {
                        trimmedRepo
                    } else {
                        Common(API_CONF).resolveRepositoryId(trimmedRepo).also {
                            repoTextFieldVal = it
                        }
                    }

                    lastTasks = processData(cirrusData.getLastPeachBuilds(repoId, branches, 15))
                }.onFailure { e ->
                    error = e.stackTraceToString()

                    if (e is NoSuchFileException || e is java.nio.file.NoSuchFileException) {
                        error = "Have you tried authenticating?\n\n$error"
                    }

                    state = AppState.ERROR
                    e.printStackTrace(System.err)
                }.onSuccess {
                    if (selectedTab == null) {
                        selectedTab = lastTasks.keys.minOf { it }
                    }
                    state = AppState.NONE
                }
            }
        }
    }

    fun authenticate() {
        GlobalScope.launch {
            GuiAuthenticationHelper(API_CONF, AUTH_CONF_FILE).AuthWebView(AUTH_CONF_FILE)
            reload()
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
                                clickedIndex = 0
                                selectedTab = branch
                            },
                            //text = "$branch (${lastTasks.get(branch)?.failed?.size})",
                            text = "$branch",
                            bgColor = if (branch == selectedTab) MaterialTheme.colors.primary else MaterialTheme.colors.primaryVariant
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
                        onClick = ::reload,
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
                            else -> lastTasks.get(selectedTab)?.let { tasks ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(5.dp)
                                ) {
                                    val processedData = processData(tasks)
                                    val (taskHistory, metadata) = processedData

                                    val selectedTasks = taskHistory[clickedIndex]
                                    val amountFailed = selectedTasks.second.filter {
                                        it.key.status == StatusCategory.FAIL
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
                                            clickedIndex = it
                                        }
                                    }

                                    Row(modifier = Modifier.weight(0.6f)) {
                                        TaskList(selectedTasks)
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

    val buildAndTasksToReturn = history.mapIndexed { i, (build, taskMap) ->
        var failed = 0
        var other = 0

        val grouped = taskMap.values.groupBy { task ->
            val status = StatusCategory.ofCirrusString(task.latestRerun.status)

            when (status) {
                StatusCategory.FAIL -> failed++
                else -> other++
            }

            val isNewStatus =
                i < (history.size - 1) && history[i + 1].second[task.latestRerun.name]?.latestRerun?.status != task.latestRerun.status
            Status(status, isNewStatus)
        }

        if (failed > maxFailed) maxFailed = failed
        if (other > maxOther) maxOther = other

        build to grouped
    }

    return buildAndTasksToReturn to MetaData(maxFailed, maxOther)
}

data class Status(val status: StatusCategory, val new: Boolean) : Comparable<Status> {
    companion object {
        private fun StatusCategory.toInt() = when (this) {
            StatusCategory.SUCCESS -> 2
            StatusCategory.UNDECIDED -> 4
            StatusCategory.FAIL -> 6
        }
    }

    val color: Color = when (status) {
        StatusCategory.FAIL -> Color.Red
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
    SUCCESS, FAIL, UNDECIDED;

    companion object {
        fun ofCirrusString(statusString: String) = when (statusString) {
            "COMPLETED" -> SUCCESS
            "ABORTED", "FAILED" -> FAIL
            "CREATED", "TRIGGERED", "SCHEDULED", "EXECUTING", "SKIPPED", "PAUSED" -> UNDECIDED
            else -> throw Exception("Unknown task status ${statusString}")
        }
    }
}
