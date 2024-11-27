package com.sonarsource.dev.quirrus.wallboard

internal class BuildDataManager(
    private val getBuildsPerBranch: (String) -> List<String>?,
    private val getBuildData: (String) -> BuildDataItem?,
    private val updateState: (AppState) -> Unit,
    private val updateBuildDataDirectory: (BuildDataItem) -> Unit
) {

    private var backgroundFetchingAssistant: BackgroundFetchingAssistant? = null

    private val fetchingAssistant
        get() = backgroundFetchingAssistant ?: BackgroundFetchingAssistant(getBuildData, ::updateBuildData) { updateState(AppState.NONE) }.also {
            backgroundFetchingAssistant = it
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
        backgroundFetchingAssistant?.cancel()
        backgroundFetchingAssistant = null
    }

    suspend fun load(buildDataItems: List<PendingBuildData>) {
        fetchingAssistant.start(buildDataItems)
    }
}
