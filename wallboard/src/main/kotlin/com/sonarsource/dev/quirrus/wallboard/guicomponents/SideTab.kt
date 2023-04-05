package com.sonarsource.dev.quirrus.wallboard.guicomponents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIconDefaults
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SideTab(onClick: () -> Unit, text: String, bgColor: Color) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIconDefaults.Hand)
            .height(50.dp)
            .padding(bottom = 1.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = bgColor,
            contentColor = MaterialTheme.colors.onPrimary
        ),
    ) {
        Text(text)
    }
}
