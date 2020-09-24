package org.sonarsource.dev.quirrus

object Printer {
    fun csvPrint(branches: List<String>, timesByTask: Map<String, Map<String, Int>>) {
        println("project;${branches.joinToString(";")}")
        timesByTask.entries.sortedBy { (key, _) -> key }.forEach { (task, timesByBranch) ->
            println("$task;${branches.joinToString(";") { timesByBranch[it]?.toString() ?: "-" }}")
        }
    }
}
