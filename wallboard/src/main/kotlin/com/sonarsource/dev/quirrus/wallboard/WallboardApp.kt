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
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonarsource.dev.quirrus.wallboard.WallboardConfig.branches
import com.sonarsource.dev.quirrus.wallboard.WallboardConfig.repo
import com.sonarsource.dev.quirrus.wallboard.data.CirrusData
import com.sonarsource.dev.quirrus.wallboard.data.StatusCategory
import com.sonarsource.dev.quirrus.wallboard.data.TaskDiffData
import com.sonarsource.dev.quirrus.wallboard.guicomponents.ErrorScreen
import com.sonarsource.dev.quirrus.wallboard.guicomponents.Histogram
import com.sonarsource.dev.quirrus.wallboard.guicomponents.Label
import com.sonarsource.dev.quirrus.wallboard.guicomponents.ListTitle
import com.sonarsource.dev.quirrus.wallboard.guicomponents.LoadingScreen
import com.sonarsource.dev.quirrus.wallboard.guicomponents.SideTab
import com.sonarsource.dev.quirrus.wallboard.guicomponents.TabTitle
import com.sonarsource.dev.quirrus.wallboard.guicomponents.TaskList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

var cirrusData = CirrusData(API_CONF)

internal enum class AppState {
    LOADING, ERROR, NONE, INIT
}

private const val numberOfBuildsToLoad = 10
private var refreshJob: Job? = null
private lateinit var buildDataManager: BuildDataManager

@Composable
@Preview
fun WallboardApp() {
    var state by remember { mutableStateOf(AppState.INIT) }

    val displayItems by remember {
        mutableStateOf(mutableStateMapOf<String, SnapshotStateList<String>>())
    }

    // Mapping of build ID -> data item. Used to store all data.
    val displayItemDirectory by remember {
        mutableStateOf(mutableStateMapOf<String, BuildDataItem>())
    }

    var selectedTab: String? by remember { mutableStateOf(null) }
    var branches by remember { mutableStateOf(branches) }
    var branchesTextFieldVal by remember { mutableStateOf(WallboardConfig.branches.joinToString(",")) }
    var repoTextFieldVal by remember { mutableStateOf(repo) }
    var clickPosition by remember { mutableStateOf(-1f) }
    val taskListScrollState = rememberScrollState(0)
    var autoRefresh by remember { mutableStateOf(WallboardConfig.autoRefreshEnabled) }
    val backgroundLoadingInProgress by remember { mutableStateOf(mutableStateMapOf<String, Boolean>()) }
    val tasksWithDiffs by remember { mutableStateOf(mutableStateMapOf<String, TaskDiffData?>()) }
    val branchState by remember { mutableStateOf(mutableStateMapOf<String, AppState>()) }
    val errors by remember { mutableStateOf(mutableStateMapOf<String, String>()) }
    var loadingCancelled by remember { mutableStateOf(false) }

    if (!::buildDataManager.isInitialized) {
        buildDataManager = BuildDataManager(
            { branch -> displayItems[branch] },
            { id -> displayItemDirectory[id] },
            { state = it },
        ) {
            displayItemDirectory[it.baseInfo.id] = it
        }
    }

    fun saveConfig() {
        with(WallboardConfig) {
            WallboardConfig.branches = branches
            repo = repoTextFieldVal
            autoRefreshEnabled = autoRefresh
            WallboardConfig.saveConfig()
        }
    }

    fun buildsByBranch(branch: String) = displayItems[branch]?.map {
        displayItemDirectory[it]!!
    }

    fun triggerReload() {
        if (state == AppState.LOADING) {
            // Cancel
            loadingCancelled = true
            refreshJob?.cancel()
            runBlocking {
                refreshJob?.join()
            }
            buildDataManager?.cancel()
            state = AppState.NONE
            loadingCancelled = false
        } else {
            displayItems.clear()
            displayItems.putAll(branches.map { branch ->
                branch to mutableStateListOf()
            })

            //lastTasks.clear()
            //lastTasks.putAll(branches.map { it to null })
            branchState.clear()
            branchState.putAll(branches.map { it to AppState.LOADING })
            if (selectedTab !in branches) {
                selectedTab = branches.firstOrNull()
            }
            /*reloadData(
                state,
                repoTextFieldVal,
                branches,
                selectedTab,
                lastTasks,
                { state = it; loadingCancelled = false },
                { error = it },
                { repoTextFieldVal = it },
                { selectedTab = it },
                { saveConfig() },
                { branch, state -> branchState[branch] = state },
                { branch, error -> errors[branch] = error },
                { loadingCancelled },
            )*/

            runCatching {
                reloadData(
                    repoTextFieldVal,
                    branches,
                    numberOfBuildsToLoad,
                    state,
                    buildDataManager,
                    { state = it; loadingCancelled = false },
                    { branch, builds ->
                        displayItems[branch] = mutableStateListOf(*builds.map { it.baseInfo.id }.toTypedArray())
                        displayItemDirectory.putAll(builds.associateBy { it.baseInfo.id })
                        branchState[branch] = AppState.NONE
                    },
                )?.let {
                    refreshJob = it
                }
            }.onFailure {
                state = AppState.ERROR
                System.err.println("Error: ${it.message}")
                it.printStackTrace()
                errors[selectedTab ?: ""] = it.message ?: "Unknown error"
            }
        }
    }

    /*DataProcessing.extractTasksThatRequireLazyLoadingOfDiffRules(dataByBranch, tasksWithDiffs).let {
        if (it.isNotEmpty()) {
            updateRulesWithDiff(it, tasksWithDiffs::put, tasksWithDiffs::remove)
        }
    }*/ // FIXME

    fun triggerAutoRefresh() {
        if (autoRefresh) {
            buildDataManager.startBackgroundRefreshPoll(branches) { branch: String, loading: Boolean ->
                backgroundLoadingInProgress[branch] = loading
            }
        } else {
            buildDataManager.stopBackgroundRefreshPoll()
            backgroundLoadingInProgress.clear()
        }
    }

    fun changeAutoReloadSetting() {
        autoRefresh = !autoRefresh
        triggerAutoRefresh()
        saveConfig()
    }

    if (autoRefresh && !buildDataManager.isBackgroundRefreshPollRunning() && state != AppState.INIT) {
        triggerAutoRefresh()
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
                        StatusCategory.entries.forEach { status ->
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

                    displayItems.keys.sorted().forEach { branch ->
                        SideTab(
                            onClick = {
                                clickPosition = -1f
                                GlobalScope.launch { taskListScrollState.scrollTo(0) }
                                selectedTab = branch
                            },
                            //text = "$branch (${lastTasks.get(branch)?.failed?.size})",
                            text = branch,
                            //bgColor = if (branch == selectedTab) MaterialTheme.colors.primary else MaterialTheme.colors.primaryVariant
                            bgColor = ((buildsByBranch(branch)?.firstOrNull() as? LoadedBuildData)?.rerunsByStatus?.keys?.maxByOrNull { it }?.color
                                ?: Color.Gray),
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
                        .clickable(enabled = state in listOf(AppState.LOADING, AppState.NONE) && !loadingCancelled) { triggerReload() }
                        .background(
                            color = if (state != AppState.LOADING) {
                                MaterialTheme.colors.secondary
                            } else if (!loadingCancelled) {
                                Color.Red.copy(alpha = .3f)
                            } else {
                                Color.LightGray
                            }
                        ),
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
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Row {
                                    CircularProgressIndicator(
                                        modifier = Modifier.scale(.6f)
                                    )
                                    val text = if (loadingCancelled) "Canceling" else "Cancel"
                                    Text(text, modifier = Modifier.align(Alignment.CenterVertically))
                                }
                            }
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 5.dp, top = 5.dp)
                            .clickable { changeAutoReloadSetting() }
                            .fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = autoRefresh,
                            enabled = displayItems[selectedTab]?.firstOrNull()
                                ?.let { buildId -> displayItemDirectory[buildId] is LoadedBuildData }
                                ?: false,
                            onCheckedChange = { changeAutoReloadSetting() },
                        )
                        Text("Auto-refresh")
                    }
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
                            else -> displayItems[selectedTab]?.let { tabDisplayItems ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(5.dp)
                                ) {
                                    val clickedIndex = if (clickPosition >= 0) {
                                        tabDisplayItems.size - (tabDisplayItems.size.toFloat() * clickPosition).toInt() - 1
                                    } else {
                                        0
                                    }

                                    if (tabDisplayItems.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No data available", color = MaterialTheme.colors.error, fontWeight = FontWeight.Bold)
                                        }
                                        // return@let -- this creates a compilation runtime error. so instead we have an else clause
                                    } else {

                                        val selectedTasks = displayItems[selectedTab]!![clickedIndex].let { buildId ->
                                            displayItemDirectory[buildId] ?: throw IllegalStateException("Build $buildId data not found")
                                        }

                                        if (selectedTasks is LoadedBuildData) {
                                            val amountFailed = selectedTasks.rerunsByStatus.filter {
                                                it.key.status.isFailingState()
                                            }.map {
                                                it.value.size
                                            }.sum()
                                            val amountSucceeded = selectedTasks.rerunsByStatus.filter {
                                                it.key.status == StatusCategory.COMPLETED
                                            }.map {
                                                it.value.size
                                            }.sum()
                                            val totalAmount = selectedTasks.rerunsByStatus.map {
                                                it.value.size
                                            }.sum()

                                            ListTitle(
                                                "$selectedTab Peach Jobs",
                                                amountSucceeded,
                                                amountFailed,
                                                totalAmount,
                                                selectedTasks.baseInfo.buildCreatedTimestamp,
                                                backgroundLoadingInProgress[selectedTab] ?: false,
                                            )
                                        } else {
                                            TabTitle("Loading jobs for $selectedTab...")
                                        }

                                        Row(modifier = Modifier.weight(0.4f).padding(vertical = 5.dp)) {
                                            Histogram(
                                                displayItems[selectedTab]!!.map { displayItemDirectory[it]!! }, /*taskHistory,*/
                                                clickedIndex
                                            ) {
                                                clickPosition = it
                                            }
                                        }

                                        Row(modifier = Modifier.weight(0.6f)) {
                                            if (selectedTasks is LoadedBuildData) {
                                                TaskList(selectedTasks, taskListScrollState, tasksWithDiffs)
                                            } else if (state != AppState.LOADING) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "No data available",
                                                        color = MaterialTheme.colors.error,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            } else {
                                                LoadingScreen()
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
    }
}





