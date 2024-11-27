package com.sonarsource.dev.quirrus.wallboard

import com.sonarsource.dev.quirrus.wallboard.data.Status
import com.sonarsource.dev.quirrus.wallboard.data.StatusCategory
import org.sonarsource.dev.quirrus.generated.graphql.ID
import org.sonarsource.dev.quirrus.generated.graphql.enums.TaskStatus
import org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Build

typealias TaskReruns = List<org.sonarsource.dev.quirrus.generated.graphql.gettasksofsinglebuild.Task>

data class InitialBuildData(
    val id: ID,
    val buildCreatedTimestamp: Long,
    val branch: String,
    val previousBuild: String?,
)

sealed interface BuildDataItem {
    val baseInfo: InitialBuildData
}

data class PendingBuildData(
    override val baseInfo: InitialBuildData,
) : BuildDataItem

data class LoadingBuildData(
    override val baseInfo: InitialBuildData
) : BuildDataItem {
    companion object {
        fun from(pending: PendingBuildData) = LoadingBuildData(pending.baseInfo)
    }
}

data class FailedBuildData(
    override val baseInfo: InitialBuildData
) : BuildDataItem

data class LoadedBuildData(
    override val baseInfo: InitialBuildData,
    val build: Build,
    val rerunsByStatus: Map<Status, List<TaskReruns>>,
    val metadataByName: Map<String, TaskMetadata>,
) : BuildDataItem {

    companion object {
        fun from(pending: LoadingBuildData, build: Build, reference: LoadedBuildData?): LoadedBuildData {
            val reruns: Map<String, TaskReruns> = build.tasks.groupBy {
                it.name
            }.mapValues {
                it.value.sortedByDescending { run -> run.creationTimestamp }
            }

            val metadataByName = mutableMapOf<String, TaskMetadata>()
            val rerunsByStatus = mutableMapOf<Status, MutableList<TaskReruns>>()

            return if (reference == null) {
                val metadataByName = mutableMapOf<String, TaskMetadata>()
                val rerunsByStatus = mutableMapOf<Status, MutableList<TaskReruns>>()

                reruns.forEach { (name, reruns) ->
                    val status = Status(StatusCategory.ofCirrusTask(reruns.first()), false)
                    metadataByName[name] = TaskMetadata(
                        status = status,
                        lastBuildWithDifferentStatus = null,
                        lastBuildWithDifferentStatusTimestamp = null,
                        reruns = reruns
                    )
                    rerunsByStatus.computeIfAbsent(status) { mutableListOf() }.add(reruns)
                }
                LoadedBuildData(pending.baseInfo, build, rerunsByStatus, metadataByName)
            } else {
                createWithReference(pending, build, reruns, reference)
            }
        }

        private fun createWithReference(
            base: BuildDataItem,
            build: Build,
            reruns: Map<String, TaskReruns>,
            reference: LoadedBuildData
        ): LoadedBuildData {
            val metadataByName = mutableMapOf<String, TaskMetadata>()
            val rerunsByStatus = mutableMapOf<Status, MutableList<TaskReruns>>()

            val shouldBeUsedAsReference = reference.shouldBeUsedAsReference()
            reruns.forEach { (name, reruns) ->
                val referenceMetadata = reference.metadataByName[name]
                val statusCategory = StatusCategory.ofCirrusTask(reruns.first())
                val isStatusNew = shouldBeUsedAsReference && referenceMetadata?.status?.status?.let { it != statusCategory } ?: false
                val status = Status(statusCategory, isStatusNew)

                val (lastDifferentBuild, lastDifferentTimestamp) = if (isStatusNew) {
                    reference.baseInfo.id to reference.baseInfo.buildCreatedTimestamp
                } else {
                    referenceMetadata?.lastBuildWithDifferentStatus to referenceMetadata?.lastBuildWithDifferentStatusTimestamp
                }

                metadataByName[name] = TaskMetadata(status, lastDifferentBuild, lastDifferentTimestamp, reruns)
                rerunsByStatus.computeIfAbsent(status) { mutableListOf() }.add(reruns)
            }

            return LoadedBuildData(base.baseInfo, build, rerunsByStatus, metadataByName)
        }
    }

    fun update(reference: LoadedBuildData): LoadedBuildData {
        return createWithReference(this, build, metadataByName.mapValues { it.value.reruns }, reference)
    }

}

internal fun BuildDataItem?.shouldBeUsedAsReference() =
    this is LoadedBuildData && this.build.tasks.any { it.status !in listOf(TaskStatus.SKIPPED, TaskStatus.ABORTED) }

data class TaskMetadata(
    var status: Status,
    var lastBuildWithDifferentStatus: String?,
    var lastBuildWithDifferentStatusTimestamp: Long?,
    var reruns: TaskReruns,
)
