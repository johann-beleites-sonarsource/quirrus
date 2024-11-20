package com.sonarsource.dev.quirrus.wallboard.data

import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Task
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Build as BuildNode

enum class DataItemState {
    PENDING, LOADING, DONE, ERROR
}

data class BuildWithTasks(val node: BuildNode, val tasks: Map<String, EnrichedTask>)

data class EnrichedTask(val taskReruns: List<Task>, val build: BuildNode, var lastBuildWithDifferentStatus: BuildNode?) {
    val latestRerun
        get() = taskReruns.first()
}

data class TaskDiffData(val diffsByRule: Map<String, Int>, val newCount: Int?, val absentCount: Int?)
