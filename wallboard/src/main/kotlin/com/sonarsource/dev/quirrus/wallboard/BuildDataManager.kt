package com.sonarsource.dev.quirrus.wallboard

internal class BuildDataManager(
    private val getBuildData: (String) -> BuildDataItem?,
    private val updateState: (AppState) -> Unit,
    private val updateBuildData: (BuildDataItem) -> Unit
) {

    private var backgroundFetchingAssistant: BackgroundFetchingAssistant? = null

    private val fetchingAssistant
        get() = backgroundFetchingAssistant ?: BackgroundFetchingAssistant(getBuildData, updateBuildData) { updateState(AppState.NONE) }.also {
            backgroundFetchingAssistant = it
        }

    fun cancel() {
        backgroundFetchingAssistant?.cancel()
        backgroundFetchingAssistant = null
    }

    suspend fun load(buildDataItems: List<PendingBuildData>) {
        fetchingAssistant.start(buildDataItems)
    }
}
