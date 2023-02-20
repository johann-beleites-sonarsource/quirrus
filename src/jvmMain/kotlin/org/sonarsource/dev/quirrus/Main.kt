package org.sonarsource.dev.quirrus

import com.github.kittinunf.fuel.core.Request
import kotlin.system.exitProcess

private val COMMANDS = mapOf<String, (List<String>) -> Unit>(
    "EXTRACT" to { ExtractorWorker().main(it) },
    "LOGS" to { LogDownloader().main(it) },
)

fun main(rawArgs: Array<String>) {
    rawArgs.firstOrNull()?.uppercase()?.let { COMMANDS[it] }?.let { it(rawArgs.drop(1)) }
        ?: run {
            System.err.println("Available commands: ${COMMANDS.keys.joinToString()}")
            exitProcess(1)
        }
}

