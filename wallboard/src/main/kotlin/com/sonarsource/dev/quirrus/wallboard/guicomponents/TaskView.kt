package com.sonarsource.dev.quirrus.wallboard.guicomponents

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
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
import com.sonarsource.dev.quirrus.wallboard.EnrichedTask
import com.sonarsource.dev.quirrus.wallboard.Status
import com.sonarsource.dev.quirrus.wallboard.StatusCategory
import org.sonarsource.dev.quirrus.BuildNode
import java.awt.Desktop
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale


private val dateTimeFormat = SimpleDateFormat("dd.MM.yyy HH:mm", Locale.getDefault())

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TaskList(buildNodeTasks: Pair<BuildNode, Map<Status, List<EnrichedTask>>>, verticalScrollState: ScrollState) {
    val (_, tasks) = buildNodeTasks

    val (completed, failed) = tasks.entries
        .flatMap { (status, tasks) ->
            tasks.map { status to it }
                .sortedBy { (_, task) ->
                    task.latestRerun.name
                }
        }.partition { (status, _) ->
            status.status == StatusCategory.COMPLETED
        }.let { (first, second) ->
            first.sortedByDescending { it.first } to second.sortedByDescending { it.first }
        }

    Box {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(verticalScrollState)) {
            //items(failed) { task ->
            for ((status, enrichedTask) in failed) {
                enrichedTask.taskReruns.forEachIndexed { index, task ->
                    val backgroundColor = if (index == 0) {
                        status.color
                    } else {
                        Color.LightGray.copy(alpha = 0.5f)
                    }

                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp)) {
                        Column {
                            if (index == 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(backgroundColor)
                                        .padding(top = 5.dp, start = 5.dp, end = 5.dp, bottom = 1.dp)
                                ) {
                                    Text(
                                        task.name,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Black//MaterialTheme.colors.onError
                                    )

                                    SinceText(enrichedTask, Color.Black/*MaterialTheme.colors.onError*/)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(color = backgroundColor.copy(alpha = 0.2f))
                                    .padding(top = 1.dp, bottom = 2.dp)
                            ) {
                                val text = AnnotatedString.Builder().apply {
                                    pushStyle(
                                        MaterialTheme.typography.body2.toSpanStyle()
                                            .copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground)
                                    )
                                    if (task.firstFailedCommand != null) {
                                        append(task.firstFailedCommand?.name ?: "")
                                    } else if (task.artifacts.any { it.name == "diff_report" && it.files.isNotEmpty() }) {
                                        append("snapshot_generation")
                                    } else {
                                        append("")
                                    }
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
                                    val logsArg = (task.firstFailedCommand?.name
                                        ?: if (task.artifacts.any { it.name == "diff_report" && it.files.isNotEmpty() }) {
                                            "snapshot_generation"
                                        } else {
                                            null
                                        })?.let { "?logs=$it#1" } ?: ""

                                    openWebpage(URI("https://cirrus-ci.com/task/${task.id}$logsArg"))
                                }
                            }
                            if (task.firstFailedCommand == null && task.artifacts.any { it.name == "diff_report" && it.files.isNotEmpty() }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(color = backgroundColor.copy(alpha = 0.2f))
                                        .padding(top = 1.dp, bottom = 2.dp)
                                ) {
                                    ClickableText(
                                        AnnotatedString("download diff_report.zip"),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp)
                                            .pointerHoverIcon(PointerIconDefaults.Hand),
                                    ) {
                                        openWebpage(URI("https://api.cirrus-ci.com/v1/artifact/task/${task.id}/diff_report.zip"))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            for ((status, task) in completed) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp)) {
                    Text(
                        task.latestRerun.name,
                        modifier = Modifier
                            .background(color = status.color)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.SinceText(task: EnrichedTask, color: Color) {
    task.lastBuildWithDifferentStatus?.let { lastDifferentBuild ->
        val text = AnnotatedString.Builder().apply {
            pushStyle(MaterialTheme.typography.body2.toSpanStyle().copy(fontWeight = FontWeight.Light, color = color))
            append("since build ${lastDifferentBuild.id} (${dateTimeFormat.format(lastDifferentBuild.buildCreatedTimestamp)})")
        }.toAnnotatedString()

        ClickableText(
            text,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .pointerHoverIcon(PointerIconDefaults.Hand),
        ) {
            lastDifferentBuild.tasks.firstOrNull { it.name == task.taskReruns.first().name }?.let { task ->
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
