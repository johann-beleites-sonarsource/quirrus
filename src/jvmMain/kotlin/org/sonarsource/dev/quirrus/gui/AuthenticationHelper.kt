package org.sonarsource.dev.quirrus.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.onError
import com.sun.javafx.application.PlatformImpl
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.sonarsource.dev.quirrus.RequestBuilder
import org.sonarsource.dev.quirrus.ViewerApiResponse
import org.sonarsource.dev.quirrus.common.GenericCirrusCommand
import org.sonarsource.dev.quirrus.json
import java.awt.BorderLayout
import java.net.CookieHandler
import java.net.CookieManager
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val minWidth = 1000
private const val minHeight = 800

class AuthenticationHelper : GenericCirrusCommand() {

    val verifyOnly by option("--verify-only").flag(default = false)

    override fun run() {
        if (!verifyOnly) {
            AuthWebView(credentialConfigFilePath)
        }
        verify()
    }


    private fun verify() {
        logger.verbose {
            """
            #######################################################
            WARNING WARNING WARNING WARNING WARNING WARNING WARNING
                                    
            [SENSITIVE DATA] Stored credentials: ${loadCookies(credentialConfigFilePath)}
                                    
            WARNING WARNING WARNING WARNING WARNING WARNING WARNING
            #######################################################
            """.trimIndent()
        }

        apiUrl.httpPost()
            .authenticate(credentialConfigFilePath)
            .let { request ->
                requestTimeout?.let { request.timeout(it) } ?: request
            }
            .jsonBody(RequestBuilder.viewerId().toRequestString())
            .responseObject<ViewerApiResponse>(json)
            .let { (_, _, result) ->
                result.onError { error ->
                    System.err.println("Error verifying authentication: ${error.message}")
                }

                result.get().data.viewer.id
            }.let { userId ->
                println("Successfully authenticated as user $userId")
            }

    }
}

fun Request.authenticate(authConfigFile: Path) = header("Cookie", loadCookies(authConfigFile))

fun AuthWebView(authConfigFile: Path) = application(exitProcessOnExit = false) {

    // Required to make sure the JavaFx event loop doesn't finish (can happen when java fx panels in app are shown/hidden)
    val finishListener = object : PlatformImpl.FinishListener {
        override fun idle(implicitExit: Boolean) {}
        override fun exitCalled() {}
    }
    PlatformImpl.addListener(finishListener)

    Window(
        title = "Cirrus Authentication",
        //resizable = false,
        state = WindowState(
            placement = WindowPlacement.Floating,
            size = DpSize(minWidth.dp, minHeight.dp)
        ),
        onCloseRequest = {
            PlatformImpl.removeListener(finishListener)
            exitApplication()
        }
    ) {
        val jfxPanel = remember { JFXPanel() }
        var jsObject = remember<JSObject?> { null }
        var text by remember { mutableStateOf("Cookie will be displayed here") }

        Column {
            Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                ComposeJFXPanel(
                    composeWindow = window,
                    jfxPanel = jfxPanel,
                    onCreate = {
                        Platform.runLater {
                            val root = WebView()

                            val cookieManager = CookieManager()
                            CookieHandler.setDefault(cookieManager)

                            val engine = root.engine
                            val scene = Scene(root)
                            engine.loadWorker.stateProperty().addListener { _, _, newState ->
                                if (newState === Worker.State.SUCCEEDED) {
                                    jsObject = root.engine.executeScript("window") as JSObject
                                    // execute other javascript / setup js callbacks fields etc..

                                    if (engine.location.startsWith("https://cirrus-ci.com/")) {
                                        val userIdCookie =
                                            cookieManager.cookieStore.cookies.firstOrNull { it.name == "cirrusUserId" }?.value
                                                ?: return@addListener

                                        val tokenCookie =
                                            cookieManager.cookieStore.cookies.firstOrNull { it.name == "cirrusAuthToken" }?.value
                                                ?: return@addListener


                                        storeCookies(authConfigFile, "cirrusUserId=$userIdCookie; cirrusAuthToken=$tokenCookie")

                                        this@application.exitApplication()
                                    }
                                }
                            }
                            engine.loadWorker.exceptionProperty().addListener { _, _, newError ->
                                println("page load error : $newError")
                            }
                            jfxPanel.scene = scene
                            engine.load("https://api.cirrus-ci.com/redirect/auth/github") // can be a html document from resources ..
                            engine.setOnError { error -> println("onError : $error") }
                        }
                    }, onDestroy = {
                        Platform.runLater {
                            jsObject?.let { jsObj ->
                                // clean up code for more complex implementations i.e. removing javascript callbacks etc..
                            }
                        }
                    })
            }
        }
    }
}

@Composable
fun ComposeJFXPanel(
    composeWindow: ComposeWindow,
    jfxPanel: JFXPanel,
    onCreate: () -> Unit,
    onDestroy: () -> Unit = {}
) {
    val jPanel = remember { JPanel() }
    val density = LocalDensity.current.density

    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { childCoordinates ->
            val coordinates = childCoordinates.parentCoordinates!!
            val location = coordinates.localToWindow(Offset.Zero).round()
            val size = coordinates.size
            jPanel.setBounds(
                (location.x / density).toInt(),
                (location.y / density).toInt(),
                (size.width / density).toInt(),
                (size.height / density).toInt()
            )
            jPanel.validate()
            jPanel.repaint()
        },
        measurePolicy = { _, _ -> layout(0, 0) {} })

    DisposableEffect(jPanel) {
        composeWindow.add(jPanel)
        jPanel.layout = BorderLayout(0, 0)
        jPanel.add(jfxPanel)
        onCreate()
        onDispose {
            onDestroy()
            composeWindow.remove(jPanel)
        }
    }
}

internal fun loadCookies(configFile: Path) = configFile.readText()

internal fun storeCookies(configFile: Path, cookies: String) = configFile
    .also {
        it.parent.createDirectories()
    }.writeText(cookies)
