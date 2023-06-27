package com.sonarsource.dev.quirrus.wallboard

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.Authentication.authenticateWithConfigFile
import java.nio.file.Path

val API_CONF = ApiConfiguration(
    authenticator = { request -> request.authenticateWithConfigFile(AUTH_CONF_FILE) },
    authenticator2 = { requestBuilder -> requestBuilder.authenticateWithConfigFile(AUTH_CONF_FILE) },
    requestTimeout = 60
)

val AUTH_CONF_FILE = Path.of(System.getenv("HOME"), ".quirrus", "auth.conf")

private const val MIN_WINDOW_WIDTH = 1400
private const val MIN_WINDOW_HEIGHT = 900

fun main(args: Array<String>) {
    startWallboard()
}


fun startWallboard() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Peach Wallboard",
        state = WindowState(
            placement = WindowPlacement.Floating,
            size = DpSize(MIN_WINDOW_WIDTH.dp, MIN_WINDOW_HEIGHT.dp)
        ),
    ) {
        WallboardApp()
    }
}
