package com.sonarsource.dev.quirrus.wallboard.data

import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Build
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.RepositoryBuildsConnection
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Task

object DataProcessing {

    internal fun processData(buildsByBranch: Map<String, RepositoryBuildsConnection?>) =
        buildsByBranch.map { (branch, builds) ->
            if (builds == null || builds.edges.isEmpty()) return@map branch to null
            branch to processBuildData(builds)
        }.toMap()

    internal fun processBuildData(builds: RepositoryBuildsConnection): List<BuildWithTasks> {

        val enrichedBuilds = builds.edges.map {
            it.node
        }.sortedByDescending {
            it.buildCreatedTimestamp
        }.mapIndexed { buildIndex, buildNode ->
            val tasks = buildNode.tasks.groupBy { task ->
                task.name
            }.map { (name, reruns) ->
                // We set the last build with different status later.
                name to EnrichedTask(reruns.sortedByDescending { it.creationTimestamp }, buildNode, null)
            }.toMap()
            BuildWithTasks(buildNode, tasks)
        }

        enrichedBuilds.forEachIndexed { i, build ->
            build.node to build.tasks.forEach { (taskName, task) ->
                val currentStatus = task.latestRerun.status
                task.lastBuildWithDifferentStatus = enrichedBuilds.drop(i + 1).firstOrNull { (_, previousTasks) ->
                    (previousTasks[taskName]?.latestRerun?.status ?: currentStatus) != currentStatus
                }?.let { (_, previousTasks) ->
                    previousTasks[taskName]?.build
                }
            }
        }

        return enrichedBuilds
    }



    /**
     * TODO
     */
    internal fun processData(history: List<BuildWithTasks>): List<Pair<Build, Map<Status, List<EnrichedTask>>>> {

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

internal fun extractTasksThatRequireLazyLoadingOfDiffRules(
    dataByBranch: Map<String, List<Pair<Build, Map<Status, List<EnrichedTask>>>>>,
    tasksWithDiffs: Map<String, *>,
): List<Task> =
    dataByBranch.flatMap { (_, tasks) ->
        tasks.flatMap { (_, taskMap) ->
            taskMap.filterKeys {
                it.status == StatusCategory.DIFF_SNAPSHOT
            }.flatMap {
                it.value.flatMap { it.taskReruns }
            }.filter { task ->
                !tasksWithDiffs.containsKey(task.id) && task.artifacts.any { it.name == "diff_report" && it.files.isNotEmpty() }
            }
        }
    }
}
