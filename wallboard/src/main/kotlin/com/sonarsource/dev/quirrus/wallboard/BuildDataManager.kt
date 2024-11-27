package com.sonarsource.dev.quirrus.wallboard

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class BuildDataManager(
    private val getBuildsPerBranch: (String) -> List<String>?,
    private val getBuildData: (String) -> BuildDataItem?,
    private val updateState: (AppState) -> Unit,
    private val updateBuildDataDirectory: (BuildDataItem) -> Unit
) {

    private var asyncFetchingAssistant: AsyncFetchingAssistant? = null
    private var backgroundAutoRefreshJob: Job? = null

    private val fetchingAssistant
        get() = asyncFetchingAssistant ?: AsyncFetchingAssistant(getBuildData, ::updateBuildData) { updateState(AppState.NONE) }.also {
            asyncFetchingAssistant = it
        }

    private fun updateBuildData(buildDataItem: BuildDataItem) {
        updateBuildDataDirectory(buildDataItem)
        recomputeStatusFreshness(buildDataItem)
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
        asyncFetchingAssistant?.cancel()
        asyncFetchingAssistant = null
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
