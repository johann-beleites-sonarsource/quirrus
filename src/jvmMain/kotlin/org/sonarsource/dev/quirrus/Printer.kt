package org.sonarsource.dev.quirrus

import java.util.*

object Printer {
    fun csvPrint(
        prefix: String,
        builds: List<BuildWithMetadata>,
        timesByTask: Map<String, Map<Build, String>>,
        notFoundPlaceholder: String
    ) {
        println(prefix)
        println("project;${builds.joinToString(";", transform = { build -> "$build [${Date(build.buildDate)}]" })}")
        timesByTask.entries.sortedBy { (key, _) -> key }.forEach { (task, timesByBranch) ->
            println("$task;${builds.joinToString(";") { timesByBranch[it] ?: notFoundPlaceholder }}")
        }
    }
}
