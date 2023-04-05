package org.sonarsource.dev.quirrus.api

import com.github.kittinunf.fuel.core.Request
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

object Authentication {
    fun loadCookies(configFile: Path) = configFile.readText().trim()

    fun storeCookies(configFile: Path, cookies: String) = configFile
        .also {
            it.parent.createDirectories()
        }.writeText(cookies)

    fun Request.authenticateWithConfigFile(credentialConfigFilePath: Path) = header("Cookie", loadCookies(credentialConfigFilePath))
}
