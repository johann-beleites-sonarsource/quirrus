package com.sonarsource.dev.quirrus.wallboard

import com.sonarsource.dev.quirrus.wallboard.data.Status
import com.sonarsource.dev.quirrus.wallboard.data.StatusCategory
import org.sonarsource.dev.quirrus.generated.graphql.ID
import org.sonarsource.dev.quirrus.generated.graphql.enums.TaskStatus
import org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Build

typealias TaskReruns = List<org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Task>

interface BuildDataItem {
    val id: ID
    val buildCreatedTimestamp: Long
}

data class PendingBuildData(
    override val id: ID,
    override val buildCreatedTimestamp: Long,
) : BuildDataItem

data class LoadingBuildData(
    override val id: ID,
    override val buildCreatedTimestamp: Long,
) : BuildDataItem {
    companion object {
        fun from(pending: PendingBuildData) = LoadingBuildData(pending.id, pending.buildCreatedTimestamp)
    }
}

data class FailedBuildData(
    override val id: ID,
    override val buildCreatedTimestamp: Long,
) : BuildDataItem

data class LoadedBuildData(
    override val id: ID,
    override val buildCreatedTimestamp: Long,
    val build: Build,
    val rerunsByStatus: Map<Status, List<TaskReruns>>,
    val metadataByName: Map<String, TaskMetadata>,
) : BuildDataItem {

    companion object {
        fun from(pending: PendingBuildData, build: Build, reference: LoadedBuildData?) : LoadedBuildData {
            val reruns: Map<String, TaskReruns> = build.tasks.groupBy {
                it.name
            }?.mapValues {
                it.value.sortedByDescending { run -> run.creationTimestamp }
            } ?: return

            val metadataByName = mutableMapOf<String, TaskMetadata>()
            val rerunsByStatus = mutableMapOf<Status, MutableList<TaskReruns>>()

            if (reference == null) {
                reruns.forEach { (name, reruns) ->
                    val status = Status(StatusCategory.ofCirrusTask(reruns.first()), false)
                    metadataByName[name] = TaskMetadata(status, null, reruns)
                    rerunsByStatus.computeIfAbsent(status) { mutableListOf() }.add(reruns)
                }
            } else {
                val shouldBeUsedAsReference = reference.shouldBeUsedAsReference()
                reruns.forEach { (name, reruns) ->
                    val referenceMetadata = reference.metadataByName[name]
                    val statusCategory = StatusCategory.ofCirrusTask(reruns.first())
                    val isStatusNew = shouldBeUsedAsReference && referenceMetadata?.status?.status?.let { it != statusCategory } ?: false
                    val status = Status(statusCategory, isStatusNew)

                    val lastDifferentBuild = if (isStatusNew) reference else referenceMetadata?.lastBuildWithDifferentStatus
                    metadataByName[name] = TaskMetadata(status, lastDifferentBuild, reruns)
                    rerunsByStatus.computeIfAbsent(status) { mutableListOf() }.add(reruns)
                }
            }

            return LoadedBuildData(pending.id, pending.buildCreatedTimestamp, build, rerunsByStatus, metadataByName)
        }

        private fun LoadedBuildData?.shouldBeUsedAsReference() =
            this?.build?.tasks?.any { it.status !in listOf(TaskStatus.SKIPPED, TaskStatus.ABORTED) } ?: true
    }

}

data class TaskMetadata(var status: Status, var lastBuildWithDifferentStatus: BuildDataItem?, var reruns: TaskReruns)
