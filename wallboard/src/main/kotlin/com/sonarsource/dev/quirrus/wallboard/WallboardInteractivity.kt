package com.sonarsource.dev.quirrus.wallboard

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.gui.GuiAuthenticationHelper

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

fun authenticate(triggerReload: () -> Unit) {
    GlobalScope.launch {
        GuiAuthenticationHelper(API_CONF, AUTH_CONF_FILE).AuthWebView(AUTH_CONF_FILE)
        triggerReload()
    }
}

