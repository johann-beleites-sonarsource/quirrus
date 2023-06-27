package com.sonarsource.dev.quirrus.cmd

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.Authentication.authenticateWithConfigFile
import org.sonarsource.dev.quirrus.common.GenericCirrusCommand
import org.sonarsource.dev.quirrus.gui.GuiAuthenticationHelper

class AuthenticationHelper: GenericCirrusCommand() {
    val verifyOnly by option("--verify-only").flag(default = false)

    override fun run() {
        val apiConfig = ApiConfiguration(
            authenticator = { requestBuilder -> requestBuilder.authenticateWithConfigFile(credentialConfigFilePath) },
        )

        val helper = GuiAuthenticationHelper(apiConfig, credentialConfigFilePath)
        if (!verifyOnly) {
            helper.AuthWebView(credentialConfigFilePath)
        }
        helper.verify()
    }
}
