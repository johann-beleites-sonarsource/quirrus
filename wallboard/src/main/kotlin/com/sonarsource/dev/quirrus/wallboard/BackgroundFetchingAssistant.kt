package com.sonarsource.dev.quirrus.wallboard

import com.sonarsource.dev.quirrus.wallboard.data.DataItemState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Build
import java.util.concurrent.atomic.AtomicInteger

class BackgroundFetchingAssistant(val doneHandler: () -> Unit) {

    private lateinit var worker: Worker
    private val toProcess = AtomicInteger(0)

    private class Worker(doneHandler: () -> Unit) {
        val buildsToFetch = Channel<DataItemToDisplay>()
        private val dataToDisplay = Channel<Pair<DataItemToDisplay, Build>>()

        private val buildFetcherBackgroundJob = GlobalScope.launch {
            buildsToFetch.consumeEach { displayItem ->
                launch {
                    displayItem.state = DataItemState.LOADING
                    dataToDisplay.send(displayItem to cirrusData.getTasksOfSingleBuild(displayItem.buildId))
                }
            }
        }

        private val dataDisplayJob = GlobalScope.launch {
            dataToDisplay.consumeEach { (displayItem, build) ->
                displayItem.build = build
                displayItem.state = DataItemState.DONE
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

    suspend fun start(dataItems: List<DataItemToDisplay>) {
        toProcess.set(dataItems.size)
        worker = Worker {
            if(toProcess.decrementAndGet() <= 0) {
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