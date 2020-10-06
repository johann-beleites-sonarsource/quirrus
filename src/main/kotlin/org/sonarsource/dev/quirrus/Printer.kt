package org.sonarsource.dev.quirrus

object Printer {
    fun csvPrint(
        prefix: String,
        branches: List<Build>,
        timesByTask: Map<String, Map<Build, String>>,
        notFoundPlaceholder: String
    ) {
        println(prefix)
        println("project;${branches.joinToString(";")}")
        timesByTask.entries.sortedBy { (key, _) -> key }.forEach { (task, timesByBranch) ->
            println("$task;${branches.joinToString(";") { timesByBranch[it] ?: notFoundPlaceholder }}")
        }
    }
}
