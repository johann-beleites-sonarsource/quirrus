package com.sonarsource.dev.quirrus.wallboard.data

import androidx.compose.ui.graphics.Color

data class Status(val status: StatusCategory, val new: Boolean) : Comparable<Status> {
    companion object {
        private fun StatusCategory.toInt() = when (this) {
            StatusCategory.IN_PROGRESS -> 0
            StatusCategory.COMPLETED -> 2
            StatusCategory.STALE -> 4
            StatusCategory.FAIL_OTHER -> 6
            StatusCategory.DIFF_SNAPSHOT -> 8
            StatusCategory.FAIL_ANALYZE -> 10
        }
    }

    val color: Color = status.color.let {
        if (!new) {
            it.copy(alpha = 0.4f)
        } else it
    }

    private fun toInt() = status.toInt().let { if (!new) it - 1 else it }
    override fun compareTo(other: Status) = toInt() - other.toInt()
}
