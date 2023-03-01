package org.sonarsource.dev.quirrus.common

interface Logger {
    fun error(msg: String)
    fun info(msgSupplier: () -> String)
    fun debug(msgSupplier: () -> String)
}
