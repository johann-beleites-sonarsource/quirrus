package com.sonarsource.dev.quirrus.wallboard

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.BuildNode
import org.sonarsource.dev.quirrus.Builds
import org.sonarsource.dev.quirrus.Task
import java.lang.IllegalStateException
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

private val initialBranches = configFile.readText().split(',')

var cirrusData = CirrusData(API_CONF)

private fun processData(builds: Map<String, Builds?>) =
    builds.map { (branch, builds) ->
        if (builds?.edges?.isNotEmpty() != true) return@map branch to null

        val (latestBuild, previousBuilds) = builds.edges.map {
            it.node
        }.sortedByDescending {
            it.buildCreatedTimestamp
        }.let {
            it.first() to it.drop(1)
        }

        // This could be more efficient e.g. by transforming the data into maps to lookup in instead

        val (completed, failed) = latestBuild.tasks
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

        branch to SortedTasks(completed, failed)
    }.toMap()

data class EnrichedTask(val tasks: List<Task>, val build: BuildNode, val lastBuildWithDifferentStatus: BuildNode?)
data class SortedTasks(val completed: List<EnrichedTask>, val failed: List<EnrichedTask>)

@Composable
@Preview
fun WallboardApp() {
    var loading by remember { mutableStateOf(false) }
    var lastTasks by remember { mutableStateOf(emptyMap<String, SortedTasks?>()) }
    var selectedTab: String? by remember { mutableStateOf(null) }
    var branches by remember { mutableStateOf(initialBranches) }
    var branchesTextFieldVal by remember { mutableStateOf(initialBranches.joinToString(",")) }

    fun reload() {
        if (!loading) {
            loading = true

            configFile.writeText(branches.joinToString(","))

            GlobalScope.launch {
                runCatching {
                    lastTasks = processData(cirrusData.getLastPeachBuilds(branches, 15))
                }.onFailure { e ->
                    e.printStackTrace(System.err)
                }.onSuccess {
                    if (selectedTab == null) {
                        selectedTab = lastTasks.keys.minOf { it }
                    }
                }
                loading = false
            }
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
                    lastTasks.keys.sorted().forEach { branch ->
                        Button(
                            onClick = { selectedTab = branch },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (branch == selectedTab) MaterialTheme.colors.primary else MaterialTheme.colors.primaryVariant,
                                contentColor = MaterialTheme.colors.onPrimary
                            )
                        ) {
                            Text("$branch (${lastTasks.get(branch)?.failed?.size})")
                        }
                    }

                    TextField(value = branchesTextFieldVal, onValueChange = { newValue ->
                        branchesTextFieldVal = newValue
                        branches = newValue.split(',')
                    })

                    Button(
                        onClick = ::reload,
                        enabled = !loading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary,
                            contentColor = MaterialTheme.colors.onSecondary
                        ),
                    ) {
                        Icon(Icons.Outlined.Refresh, "Refresh")
                    }
                }

                Column(modifier = Modifier.weight(0.9f)) {
                    when (loading) {
                        true -> LoadingScreen()
                        else -> lastTasks.get(selectedTab)?.let { (completed, failed) ->
                            TaskList("$selectedTab Peach Jobs", completed, failed)
                        }
                    }
                }
            }
        }
    }
}
