package com.sonarsource.dev.quirrus.wallboard

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

class AsyncNetWorker {
    private var requestsToSend: Channel<RequestToSend<*>>? = null
    private var resultsToProcess: Channel<ResultToProcess<*>>? = null

    private var networkWorker: Job? = null
    private var processingWorker: Job? = null

    /**
     * Starts or restarts the worker. If the worker is already started, all current jobs are cancelled to facilitate the restart.
     */
    fun start() {
        requestsToSend?.cancel()
        resultsToProcess?.cancel()

        requestsToSend = Channel()
        resultsToProcess = Channel()


        processingWorker = GlobalScope.launch {
            resultsToProcess!!.consumeEach { result ->
                launch {
                    runCatching {
                        result.process()
                    }.onFailure { e ->
                        result.errorHandler?.invoke(e)
                    }
                }
            }
        }

        networkWorker = GlobalScope.launch {
            requestsToSend!!.consumeEach { request: RequestToSend<*> ->
                launch {
                    for (i in 0 until request.maxRetries) {
                        try {
                            val toProcess = request.executeRequest()
                            // process the request
                            resultsToProcess!!.send(toProcess)
                        } catch (e: Throwable) {
                            if (i == request.maxRetries - 1) {
                                request.errorHandler?.invoke(e)
                            }
                            continue
                        }
                        break
                    }
                }
            }
        }
    }

    /**
     * Stops the worker. All current jobs are cancelled.
     */
    fun stop() {
        requestsToSend?.cancel()
        resultsToProcess?.cancel()
        networkWorker?.cancel()
        processingWorker?.cancel()

        requestsToSend = null
        resultsToProcess = null
        networkWorker = null
        processingWorker = null
    }

    /**
     * Sends a request to the worker.
     */
    suspend fun send(request: RequestToSend<*>) = requestsToSend?.send(request)

    suspend fun send(requests: Collection<RequestToSend<*>>) = requests.forEach { send(it) }
}

data class RequestToSend<T>(
    val requestFun: suspend () -> T,
    val callback: (T) -> Unit,
    val errorHandler: ((Throwable) -> Unit)? = null,
    val maxRetries: Int = 3
)

private data class ResultToProcess<T>(val result: T, val callback: (T) -> Unit, val errorHandler: ((Throwable) -> Unit)?) {
    fun process() = callback(result)
}

private suspend fun <T> RequestToSend<T>.executeRequest() = ResultToProcess(requestFun(), callback, errorHandler)
