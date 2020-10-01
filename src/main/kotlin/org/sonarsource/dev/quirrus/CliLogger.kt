package org.sonarsource.dev.quirrus

class CliLogger(val verbose: Boolean = false, val quiet: Boolean = false) {
    fun verbose(message: () -> String) {
        if (verbose) println(message())
    }

    fun print(message: () -> String) {
        if (!quiet) println(message())
    }

    fun error(message: String) = System.err.println(message)
}
