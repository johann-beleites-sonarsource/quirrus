package com.sonarsource.dev.quirrus.wallboard

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Build
import java.util.concurrent.atomic.AtomicInteger

class BackgroundFetchingAssistant(
    private val getBuildData: (String) -> BuildDataItem?,
    private val updateBuildData: (BuildDataItem) -> Unit,
    private val doneHandler: () -> Unit
) {

    private lateinit var worker: Worker
    private val toProcess = AtomicInteger(0)

    private class Worker(
        private val getBuildData: (String) -> BuildDataItem?,
        private val updateBuildData: (BuildDataItem) -> Unit,
        private val doneHandler: () -> Unit,
    ) {
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
                val reference = displayItem.getReference() // TODO: thread safety

                LoadedBuildData.from(
                    pending = displayItem,
                    build = build,
                    reference = reference,
                ).let(updateBuildData)

                doneHandler()
            }
        }

        private fun BuildDataItem.getReference(): LoadedBuildData? {
            val previousBuildNode = previousBuild?.let { getBuildData(it) }
            return when (previousBuildNode) {
                is LoadedBuildData ->
                    if (previousBuildNode.shouldBeUsedAsReference()) previousBuildNode
                    else previousBuildNode.getReference()
                else -> null
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
        check(toProcess.get() <= 0) { "Already processing" }

        toProcess.set(dataItems.size)
        worker = Worker(getBuildData, updateBuildData) {
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
