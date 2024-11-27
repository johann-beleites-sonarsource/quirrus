package com.sonarsource.dev.quirrus.wallboard.guicomponents

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import com.sonarsource.dev.quirrus.wallboard.BuildDataItem
import com.sonarsource.dev.quirrus.wallboard.LoadedBuildData
import com.sonarsource.dev.quirrus.wallboard.data.StatusCategory
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import java.text.SimpleDateFormat
import java.util.Date

val barPadding = 5f

val dateOnly = SimpleDateFormat("dd.MM.yyyy")
val timeOnly = SimpleDateFormat("HH:mm")

@Composable
fun Histogram(
    displayItems: List<BuildDataItem>,
    selectItem: Int,
    updateClickIndexFraction: (Float) -> Unit
) {

    val interactionSource = remember { MutableInteractionSource() }

    val hoverable = Modifier.hoverable(interactionSource = interactionSource)
    val isHoveredOver by interactionSource.collectIsHoveredAsState()
    val bgColor = if (isHoveredOver) Color.Gray else Color.LightGray
    var slotPerBar by remember { mutableStateOf(0f) }
    var maxX by remember { mutableStateOf(0f) }

    val pointerInput = Modifier.pointerInput(Unit) {
        detectTapGestures { offset: Offset ->
            updateClickIndexFraction(offset.x / maxX)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.LightGray)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize().then(hoverable).then(pointerInput).pointerHoverIcon(PointerIcon.Hand)
        ) {
            maxX = size.width
            val maxY = size.height - 20f

            val maxFailed = displayItems.filterIsInstance<LoadedBuildData>().maxOfOrNull { displayItem ->
                displayItem.rerunsByStatus.filter { (status, _) ->
                    status.status.isFailingState()
                }.values.sumOf { it.size }
            } ?: 0

            val maxNotFailed = displayItems.filterIsInstance<LoadedBuildData>().maxOfOrNull { displayItem ->
                displayItem.rerunsByStatus.filter { (status, _) ->
                    !status.status.isFailingState() && !(status.status == StatusCategory.COMPLETED && !status.new)
                }.values.sumOf { it.size }
            } ?: 0

            val total = maxFailed + maxNotFailed

            val stepHeight = (maxY) / total.toFloat()
            val zeroYOffset = stepHeight * maxFailed.toFloat() + 5f

            slotPerBar = maxX / displayItems.size.toFloat()
            val barWidth = slotPerBar - (2 * barPadding)

            drawRect(
                color = Color.Gray.copy(alpha = 0.3f),
                topLeft = Offset(x = maxX - ((barWidth + (2 * barPadding)) * (selectItem + 1)), y = 0f),
                size = Size(width = slotPerBar, height = maxY),
            )

            drawLine(
                color = Color.Black,
                start = Offset(x = 0f, y = zeroYOffset),
                end = Offset(x = maxX, y = zeroYOffset),
            )

            displayItems.filterIsInstance<LoadedBuildData>().forEachIndexed { i, displayItem ->
                displayItem.rerunsByStatus.filter { (status, _) ->
                    // Don't display successful tasks that have been successful for a while
                    status.status != StatusCategory.COMPLETED || status.new
                }.map { (status, tasks) ->
                    status to tasks.count()
                }.partition { (status, _) ->
                    status.status.isFailingState()
                }.let { (failed, other) ->
                    failed.sortedBy { (status, _) -> status } to
                        other.sortedBy { (status, _) -> status }
                }.let { (failed, other) ->
                    val left = maxX - ((barWidth + (2 * barPadding)) * (i + 1)) + barPadding
                    failed.fold(zeroYOffset) { yOffset, (status, count) ->
                        val height = stepHeight * count
                        val top = yOffset - height
                        drawRect(
                            color = status.color,
                            topLeft = Offset(x = left, y = top),
                            size = Size(width = barWidth, height = height),
                        )
                        top
                    }

                    other.fold(zeroYOffset) { top, (status, count) ->
                        val height = stepHeight * count
                        drawRect(
                            color = status.color,
                            topLeft = Offset(x = left, y = top),
                            size = Size(width = barWidth, height = height),
                        )

                        top + height
                    }

                    val creationDate = Date(displayItem.baseInfo.buildCreatedTimestamp)
                    drawText(dateOnly.format(creationDate), left, maxY + 2f)
                    drawText(timeOnly.format(creationDate), left, maxY + 17f)
                }
            }
        }
    }
}

fun DrawScope.drawText(text: String, x: Float, y: Float) {
    drawContext.canvas.nativeCanvas.apply {
        drawTextLine(TextLine.make(text, Font()), x, y, Paint())
    }
}
