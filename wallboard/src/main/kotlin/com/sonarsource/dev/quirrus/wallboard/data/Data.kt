package com.sonarsource.dev.quirrus.wallboard.data

data class TaskDiffData(val diffsByRule: Map<String, Int>, val newCount: Int?, val absentCount: Int?)
