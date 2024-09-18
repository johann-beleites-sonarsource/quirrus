package com.sonarsource.dev.quirrus.wallboard.data

import androidx.compose.ui.graphics.Color
import org.sonarsource.dev.quirrus.generated.graphql.enums.TaskStatus
import org.sonarsource.dev.quirrus.generated.graphql.enums.TaskStatus.*
import org.sonarsource.dev.quirrus.generated.graphql.gettasks.Task

enum class StatusCategory(val color: Color) {
    IN_PROGRESS(Color.Gray),
    COMPLETED(Color.Green),
    FAIL_ANALYZE(Color.Red),
    DIFF_SNAPSHOT(Color(0, 220, 240)),
    FAIL_OTHER(Color.Yellow),
    STALE(Color.DarkGray);

    companion object {
        fun ofCirrusTask(task: Task) = when (task.status) {
            CREATED, TRIGGERED, SCHEDULED, EXECUTING -> IN_PROGRESS
            TaskStatus.COMPLETED -> if (task.artifacts.any { it.name == "diff_report" && it.files.isNotEmpty() }) DIFF_SNAPSHOT else COMPLETED
            ABORTED, FAILED -> if (task.firstFailedCommand?.name?.contains("analyze") == true) FAIL_ANALYZE else FAIL_OTHER
            SKIPPED, PAUSED -> STALE
            else -> throw Exception("Unknown task status ${task.status}")
        }
    }

    fun isFailingState() = this in listOf(FAIL_ANALYZE, FAIL_OTHER, DIFF_SNAPSHOT)
}
