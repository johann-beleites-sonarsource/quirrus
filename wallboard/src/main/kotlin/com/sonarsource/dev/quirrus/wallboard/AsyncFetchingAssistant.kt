package com.sonarsource.dev.quirrus.wallboard

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Build
import java.util.concurrent.atomic.AtomicInteger

class AsyncFetchingAssistant(
    private val getBuildData: (String) -> BuildDataItem?,
    private val updateBuildData: (BuildDataItem) -> Unit,
    private val doneHandler: () -> Unit
) {

    private lateinit var worker: Worker
    private val toProcess = AtomicInteger(0)

    private class Worker(
        private val getBuildData: (String) -> BuildDataItem?,
        private val updateBuildData: (BuildDataItem) -> Unit,
    ) {
        private data class LoadTask(val displayItem: BuildDataItem, val doneHandler: () -> Unit)
        private data class LoadedData(val displayItem: LoadingBuildData, val build: Build, val doneHandler: () -> Unit)

        private val buildsToFetch = Channel<LoadTask>()
        private val dataToDisplay = Channel<LoadedData>()

        private val buildFetcherBackgroundJob = GlobalScope.launch {
            buildsToFetch.consumeEach { (displayItem, doneHandler) ->
                launch {
                    kotlin.runCatching {
                        LoadingBuildData.from(displayItem).let {
                            updateBuildData(it)
                            dataToDisplay.send(LoadedData(it, cirrusData.getTasksOfSingleBuild(it.baseInfo.id), doneHandler))
                        }
                    }.onFailure { e ->
                        FailedBuildData(displayItem.baseInfo, e.message).let(updateBuildData)
                        doneHandler()
                        println("Failed to fetch build data for ${displayItem.baseInfo.id}: ${e.message}")
                    }
                }

            }
        }

        private val dataDisplayJob = GlobalScope.launch {
            dataToDisplay.consumeEach { (displayItem, build, doneHandler) ->
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
            val previousBuildNode = baseInfo.previousBuild?.let { getBuildData(it) }
            return when (previousBuildNode) {
                is LoadedBuildData ->
                    if (previousBuildNode.shouldBeUsedAsReference()) previousBuildNode
                    else previousBuildNode.getReference()

                else -> null
            }
        }

        suspend fun load(toLoad: BuildDataItem, doneHandler: () -> Unit = {}) {
            buildsToFetch.send(LoadTask(toLoad, doneHandler))
        }

        fun cancel() {
            buildFetcherBackgroundJob.cancel()
            dataDisplayJob.cancel()
            buildsToFetch.close()
            dataToDisplay.close()
        }
    }

    fun reload(buildData: LoadedBuildData, doneHandler: () -> Unit) {
        runBlocking {
            worker.load(buildData, doneHandler)
        }
    }

    suspend fun start(dataItems: List<PendingBuildData>) {
        check(toProcess.get() <= 0) { "Already processing" }

        toProcess.set(dataItems.size)
        worker = Worker(getBuildData) {
            if (getBuildData(it.baseInfo.id) !is LoadedBuildData) {
                updateBuildData(it)
            }
        }
        dataItems.forEach {
            worker.load(it) {
                if (toProcess.decrementAndGet() <= 0) {
                    doneHandler()
                }
            }
        }
    }

    fun cancel() {
        worker.cancel()
    }
}
