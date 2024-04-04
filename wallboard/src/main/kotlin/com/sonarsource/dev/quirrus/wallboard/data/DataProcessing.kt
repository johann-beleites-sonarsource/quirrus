package com.sonarsource.dev.quirrus.wallboard.data

import com.sonarsource.dev.quirrus.wallboard.EnrichedTask
import org.sonarsource.dev.quirrus.BuildNode

object DataProcessing {
    /**
     * TODO
     */
    fun processData(history: List<BuildWithTasks>): List<Pair<BuildNode, Map<Status, List<EnrichedTask>>>> {

        var maxFailed = 0
        var maxOther = 0

        // Filters old builds that are stale
        val filteredHistory = history.filterIndexed { i, (_, tasks) ->
            i == 0 || tasks.values.any { StatusCategory.ofCirrusTask(it.latestRerun) != StatusCategory.STALE }
        }

        //
        return filteredHistory.mapIndexed { i, (build, taskMap) ->
            var failed = 0
            var other = 0

            val grouped = taskMap.values.groupBy { task ->
                val status = StatusCategory.ofCirrusTask(task.latestRerun)

                when {
                    status.isFailingState() -> failed++
                    else -> other++
                }

                val isNewStatus = filteredHistory.getOrNull(i + 1)?.tasks?.get(task.latestRerun.name)?.latestRerun?.let { lastRun ->
                    lastRun.status != task.latestRerun.status || StatusCategory.ofCirrusTask(lastRun) != status
                } ?: (i < filteredHistory.size - 1)

                Status(status, isNewStatus)
            }

            if (failed > maxFailed) maxFailed = failed
            if (other > maxOther) maxOther = other

            build to grouped
        }
    }
}
