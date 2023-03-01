package org.sonarsource.dev.quirrus

import org.sonarsource.dev.quirrus.common.Logger

class CliLogger(val verbose: Boolean = false, val quiet: Boolean = false) : Logger {

    @Deprecated(message = "Superseded by debug()", replaceWith = ReplaceWith("debug"))
    fun verbose(message: () -> String) {
        if (verbose) println(message())
    }

    @Deprecated(message = "Superseded by info()", replaceWith = ReplaceWith("info"))
    fun print(message: () -> String) {
        if (!quiet) println(message())
    }

    override fun error(msg: String) = System.err.println(msg)

    override fun info(msgSupplier: () -> String) {
        if (!quiet) println(msgSupplier())
    }

    override fun debug(msgSupplier: () -> String) {
        if (verbose) println(msgSupplier())
    }
}
