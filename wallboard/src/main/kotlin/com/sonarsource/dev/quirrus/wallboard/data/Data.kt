package com.sonarsource.dev.quirrus.wallboard.data

import com.sonarsource.dev.quirrus.wallboard.EnrichedTask
import org.sonarsource.dev.quirrus.BuildNode

data class BuildWithTasks(val node: BuildNode, val tasks: Map<String, EnrichedTask>)
