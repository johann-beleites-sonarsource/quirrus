package com.sonarsource.dev.quirrus.wallboard

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonarsource.dev.quirrus.wallboard.WallboardConfig.branches
import com.sonarsource.dev.quirrus.wallboard.WallboardConfig.repo
import com.sonarsource.dev.quirrus.wallboard.data.BuildWithTasks
import com.sonarsource.dev.quirrus.wallboard.data.CirrusData
import com.sonarsource.dev.quirrus.wallboard.data.DataProcessing
import com.sonarsource.dev.quirrus.wallboard.data.StatusCategory
import com.sonarsource.dev.quirrus.wallboard.data.TaskDiffData
import com.sonarsource.dev.quirrus.wallboard.guicomponents.ErrorScreen
import com.sonarsource.dev.quirrus.wallboard.guicomponents.Histogram
import com.sonarsource.dev.quirrus.wallboard.guicomponents.Label
import com.sonarsource.dev.quirrus.wallboard.guicomponents.ListTitle
import com.sonarsource.dev.quirrus.wallboard.guicomponents.LoadingScreen
import com.sonarsource.dev.quirrus.wallboard.guicomponents.SideTab
import com.sonarsource.dev.quirrus.wallboard.guicomponents.TaskList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Locale

var cirrusData = CirrusData(API_CONF)

internal enum class AppState {
    LOADING, ERROR, NONE, INIT
}

@Composable
@Preview
fun WallboardApp() {
    var state by remember { mutableStateOf(AppState.INIT) }
    var error by remember { mutableStateOf("Unknown") }
    var lastTasks by remember { mutableStateOf(mutableStateMapOf<String, List<BuildWithTasks>?>()) }
    var dataByBranch = lastTasks.mapNotNull { (branch, tasks) ->
        if (tasks == null) null
        else branch to DataProcessing.processData(tasks)
    }.toMap()
    var selectedTab: String? by remember { mutableStateOf(null) }
    var branches by remember { mutableStateOf(branches) }
    var branchesTextFieldVal by remember { mutableStateOf(WallboardConfig.branches.joinToString(",")) }
    var repoTextFieldVal by remember { mutableStateOf(repo) }
    var clickPosition by remember { mutableStateOf(-1f) }
    val taskListScrollState = rememberScrollState(0)
    //var autoRefresh by remember { mutableStateOf(autoRefreshEnabled) }
    var autoRefresh = false
    var backgroundRefreshCounter by remember { mutableStateOf(0L) }
    var lastSelectedTab: String? by remember { mutableStateOf(null) }
    var backgroundLoadingInProgress by remember { mutableStateOf(false) }
    val tasksWithDiffs by remember { mutableStateOf(mutableStateMapOf<String, TaskDiffData?>()) }
    val branchState by remember { mutableStateOf(mutableStateMapOf<String, AppState>()) }
    val errors by remember { mutableStateOf(mutableStateMapOf<String, String>()) }

    fun saveConfig() {
        with(WallboardConfig) {
            WallboardConfig.branches = branches
            repo = repoTextFieldVal
            autoRefreshEnabled = autoRefresh
            WallboardConfig.saveConfig()
        }
    }

    fun triggerReload() {
        lastTasks.clear()
        lastTasks.putAll(branches.map { it to null })
        branchState.clear()
        branchState.putAll(branches.map { it to AppState.LOADING })
        if (selectedTab !in branches) {
            selectedTab = branches.firstOrNull()
        }
        reloadData(
            state,
            repoTextFieldVal,
            branches,
            selectedTab,
            lastTasks,
            { state = it },
            { error = it },
            { repoTextFieldVal = it },
            { selectedTab = it },
            { saveConfig() },
            { branch, state -> branchState[branch] = state },
            { branch, error -> errors[branch] = error }
        )
    }

    DataProcessing.extractTasksThatRequireLazyLoadingOfDiffRules(dataByBranch, tasksWithDiffs).let {
        if (it.isNotEmpty()) {
            updateRulesWithDiff(it, tasksWithDiffs::put, tasksWithDiffs::remove)
        }
    }

    fun startBackgroundRefreshPoll() {
        val branch = selectedTab ?: return
        launchBackgroundRefreshPoll(
            backgroundRefreshCounter,
            { backgroundRefreshCounter },
            branch,
            repoTextFieldVal,
            { backgroundLoadingInProgress = it },
        ) {
            it.entries.forEach { (branch, data) ->
                if (data != null) lastTasks[branch] = data
            }
        }
    }

    fun changeAutoReloadSetting() {
        autoRefresh = !autoRefresh
        backgroundRefreshCounter++
        if (autoRefresh) {
            startBackgroundRefreshPoll()
        }
        saveConfig()
    }

    if (selectedTab != null && lastSelectedTab != selectedTab) {
        if (autoRefresh) {
            backgroundRefreshCounter++
            startBackgroundRefreshPoll()
        }
        lastSelectedTab = selectedTab
    }

    MaterialTheme {
        Box {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Column(modifier = Modifier.weight(0.1f)) {
                    Column(modifier = Modifier.background(Color.LightGray).fillMaxWidth().padding(top = 5.dp, bottom = 5.dp)) {
                        Text(
                            TASK_STATUS_LEGEND,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 4.dp),
                            color = MaterialTheme.colors.onBackground
                        )
                        StatusCategory.values().forEach { status ->
                            Text(
                                status.name.lowercase()
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                                    .replace("_", " "),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .align(Alignment.CenterHorizontally),
                                color = status.color,
                            )
                        }
                    }

                    TextField(
                        value = repoTextFieldVal,
                        label = { Label(CIRRUS_REPO_TEXT_FIELD_LABEL) },
                        modifier = Modifier.padding(top = 5.dp),
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
                            bgColor = (dataByBranch[branch]?.firstOrNull()?.second?.keys?.maxByOrNull { it }?.color ?: Color.Gray),
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

                    Row(modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(start = 5.dp, end = 5.dp, top = 5.dp)
                        .fillMaxWidth()
                        .clickable(enabled = state != AppState.LOADING) { triggerReload() }
                        .background(color = if (state != AppState.LOADING) MaterialTheme.colors.secondary else Color.LightGray),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (state != AppState.LOADING) {
                            Icon(
                                Icons.Outlined.Refresh,
                                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colors.onSecondary
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.align(alignment = Alignment.CenterVertically)
                                    .scale(.6f)
                            )
                        }
                    }

                    Row(modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(start = 5.dp, end = 5.dp, top = 5.dp)
                        .fillMaxWidth()
                        .clickable { authenticate { triggerReload() } }
                        .background(color = MaterialTheme.colors.secondary),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Authenticate",
                            color = MaterialTheme.colors.onSecondary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                        )
                    }

                    /*Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, top = 5.dp)
                            .clickable { changeAutoReloadSetting() }
                            .fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = autoRefresh,
                            enabled = lastTasks.isNotEmpty(),
                            onCheckedChange = { changeAutoReloadSetting() },
                        )
                        Text("Auto-refresh")
                    }*/
                }

                Column(modifier = Modifier.weight(0.9f)) {
                    Row {
                        if (state == AppState.INIT) {
                            triggerReload()
                            return@Row
                        }

                        when (branchState[selectedTab]) {
                            AppState.LOADING -> LoadingScreen()
                            AppState.ERROR -> ErrorScreen(errors[selectedTab] ?: "NULL")
                            else -> dataByBranch[selectedTab]?.let { taskHistory ->
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
                                        it.key.status == StatusCategory.COMPLETED
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
                                        selectedTasks.first.buildCreatedTimestamp.toLong(),
                                        backgroundLoadingInProgress,
                                    )

                                    Row(modifier = Modifier.weight(0.4f).padding(vertical = 5.dp)) {
                                        Histogram(taskHistory, clickedIndex) {
                                            clickPosition = it
                                        }
                                    }

                                    Row(modifier = Modifier.weight(0.6f)) {
                                        TaskList(selectedTasks, taskListScrollState, tasksWithDiffs)
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




