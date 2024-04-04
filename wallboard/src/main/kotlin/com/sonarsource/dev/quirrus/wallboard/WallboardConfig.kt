package com.sonarsource.dev.quirrus.wallboard

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

object WallboardConfig {
    private val configFile = Path.of(System.getenv("HOME"), ".quirrus", "branches.conf").also { file ->
        if (!file.parent.exists()) {
            file.parent.createDirectories()
        }

        if (!file.exists()) {
            file.createFile()
        } else check(file.isRegularFile()) { "config file '$file' exists but is not a regular file" }
    }

    private val initialConfigStrings = configFile.readText().split(";")

    var branches = initialConfigStrings.elementAtOrNull(0)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    var repo = initialConfigStrings.elementAtOrNull(1) ?: ""

    var autoRefreshEnabled = initialConfigStrings.elementAtOrNull(2)?.toBoolean() ?: true

    fun saveConfig() {
        configFile.writeText("${branches.joinToString(",")};$repo;$autoRefreshEnabled")
    }
}
