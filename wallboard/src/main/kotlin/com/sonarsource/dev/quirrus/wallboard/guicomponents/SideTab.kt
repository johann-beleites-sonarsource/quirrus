package com.sonarsource.dev.quirrus.wallboard.guicomponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp

@Composable
fun SideTab(onClick: () -> Unit, text: String, bgColor: Color, selected: Boolean) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .height(50.dp)
            .padding(bottom = 1.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = bgColor,
            contentColor = Color.Black
        ),
        border = BorderStroke(width = if (selected) 5.dp else 0.dp, color = Color.Black)
    ) {
        Text(text)
    }
}
