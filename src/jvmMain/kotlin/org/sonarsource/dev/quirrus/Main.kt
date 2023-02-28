package org.sonarsource.dev.quirrus

import org.sonarsource.dev.quirrus.gui.AuthenticationHelper
import kotlin.system.exitProcess

private val COMMANDS = mapOf<String, (List<String>) -> Unit>(
    "EXTRACT" to { ExtractorWorker().main(it) },
    "LOGS" to { LogDownloader().main(it) },
    "AUTH" to { AuthenticationHelper().main(it) },
)

fun main(rawArgs: Array<String>) {
    rawArgs.firstOrNull()?.uppercase()?.let { COMMANDS[it] }?.let { command ->
        command(rawArgs.drop(1))
        exitProcess(0)
    } ?: run {
        System.err.println("Available commands: ${COMMANDS.keys.joinToString()}")
        exitProcess(1)
    }
}

