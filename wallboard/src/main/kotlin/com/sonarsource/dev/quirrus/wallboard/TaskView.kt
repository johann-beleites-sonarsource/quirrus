package com.sonarsource.dev.quirrus.wallboard

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIconDefaults
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI
import java.text.SimpleDateFormat


private val timeDateFormat = SimpleDateFormat("dd.MM.yyy hh:mm")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TaskList(title: String, completed: List<EnrichedTask>, failed: List<EnrichedTask>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp)
    ) {
        AnnotatedString.Builder().apply {
            pushStyle(MaterialTheme.typography.h4.toSpanStyle())
            append("$title ")

            pushStyle(MaterialTheme.typography.h5.toSpanStyle())

            append("(${failed.size} failed | ${completed.size} completed | ${completed.size + failed.size} total)")
        }.toAnnotatedString().let {
            Text(it)
            //Text("(${failed.size} failed | ${completed.size} completed | ${tasks.size} total)")
        }
        Divider(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(color = MaterialTheme.colors.primary)
                .padding(bottom = 5.dp)
        )

        val verticalScrollState = rememberScrollState(0)

        Box {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(verticalScrollState)) {
                //items(failed) { task ->
                for (enrichedTask in failed) {
                    enrichedTask.tasks.forEachIndexed { index, task ->
                        val backgroundColor = with(
                            if (completed.any { it.tasks.first().name == task.name }) MaterialTheme.colors.secondary
                            else MaterialTheme.colors.error
                        ) {
                            if (index > 0) copy(alpha = 0.4f)
                            else this
                        }

                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp)) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(backgroundColor)
                                        .padding(top = 5.dp, start = 5.dp, end = 5.dp, bottom = 1.dp)
                                ) {
                                    Text(
                                        task.name,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colors.onError
                                    )

                                    SinceText(enrichedTask, MaterialTheme.colors.onError)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(color = MaterialTheme.colors.error.copy(alpha = 0.2f))
                                        .padding(top = 1.dp, bottom = 2.dp)
                                ) {
                                    val text = AnnotatedString.Builder().apply {
                                        pushStyle(
                                            MaterialTheme.typography.body2.toSpanStyle()
                                                .copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground)
                                        )
                                        append(task.firstFailedCommand?.name ?: "???")

                                        pushStyle(
                                            MaterialTheme.typography.body2.toSpanStyle()
                                                .copy(fontWeight = FontWeight.Light, color = MaterialTheme.colors.onBackground)
                                        )
                                        append(" ${task.status}")

                                        task.firstFailedCommand?.durationInSeconds?.let { duration ->
                                            pushStyle(
                                                MaterialTheme.typography.body2.toSpanStyle()
                                                    .copy(color = MaterialTheme.colors.onBackground)
                                            )
                                            append(" after $duration s")
                                        }
                                    }.toAnnotatedString()

                                    ClickableText(
                                        text,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp)
                                            .pointerHoverIcon(PointerIconDefaults.Hand),
                                    ) {
                                        openWebpage(URI("https://cirrus-ci.com/task/${task.id}"))
                                    }
                                }
                            }
                        }
                    }
                }

                //items(completed) { task ->
                for (task in completed) {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp)) {
                        Text(
                            task.tasks.last().name,
                            modifier = Modifier
                                .background(color = MaterialTheme.colors.secondary)
                                .padding(all = 5.dp)
                                .fillMaxWidth(),
                            color = MaterialTheme.colors.onSecondary
                        )

                        SinceText(task, MaterialTheme.colors.onSecondary)
                    }
                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = verticalScrollState)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.SinceText(task: EnrichedTask, color: Color) {
    task.lastBuildWithDifferentStatus?.let { lastDifferentBuild ->
        val text = AnnotatedString.Builder().apply {
            pushStyle(MaterialTheme.typography.body2.toSpanStyle().copy(fontWeight = FontWeight.Light, color = color))
            append("since build ${lastDifferentBuild.id} (${timeDateFormat.format(lastDifferentBuild.buildCreatedTimestamp)})")
        }.toAnnotatedString()

        ClickableText(
            text,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .pointerHoverIcon(PointerIconDefaults.Hand),
        ) {
            task.lastBuildWithDifferentStatus.tasks.firstOrNull { it.name == task.tasks.first().name }?.let { task ->
                openWebpage(URI("https://cirrus-ci.com/task/${task.id}"))
            }
        }
    }
}

fun openWebpage(uri: URI?): Boolean {
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
        try {
            desktop.browse(uri)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return false
}
