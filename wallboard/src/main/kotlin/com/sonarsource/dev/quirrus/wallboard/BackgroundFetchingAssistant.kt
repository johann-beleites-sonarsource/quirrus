package com.sonarsource.dev.quirrus.wallboard

import com.sonarsource.dev.quirrus.wallboard.data.DataItemState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Build
import java.util.concurrent.atomic.AtomicInteger

class BackgroundFetchingAssistant(
    private val updateBuildData: (BuildDataItem) -> Unit,
    private val doneHandler: () -> Unit
) {

    private lateinit var worker: Worker
    private val toProcess = AtomicInteger(0)

    private class Worker(updateBuildData: (BuildDataItem) -> Unit, doneHandler: () -> Unit) {
        val buildsToFetch = Channel<PendingBuildData>()
        private val dataToDisplay = Channel<Pair<LoadingBuildData, Build>>()

        private val buildFetcherBackgroundJob = GlobalScope.launch {
            buildsToFetch.consumeEach { displayItem ->
                launch {
                    LoadingBuildData.from(displayItem).let {
                        updateBuildData(it)
                        dataToDisplay.send(it to cirrusData.getTasksOfSingleBuild(it.id))
                    }
                }
            }
        }

        private val dataDisplayJob = GlobalScope.launch {
            dataToDisplay.consumeEach { (displayItem, build) ->
                LoadedBuildData(
                    id = displayItem.id,
                    buildCreatedTimestamp = displayItem.buildCreatedTimestamp,
                    build = build,
                ).let(updateBuildData)

                doneHandler()
            }
        }

        fun cancel() {
            buildFetcherBackgroundJob.cancel()
            dataDisplayJob.cancel()
            buildsToFetch.close()
            dataToDisplay.close()
        }
    }

    suspend fun start(dataItems: List<PendingBuildData>) {
        check (toProcess.get() <= 0) { "Already processing" }

        toProcess.set(dataItems.size)
        worker = Worker(updateBuildData) {
            if (toProcess.decrementAndGet() <= 0) {
                doneHandler()
            }
        }
        dataItems.forEach {
            worker.buildsToFetch.send(it)
        }
    }

    fun cancel() {
        worker.cancel()
    }
}
