package com.sonarsource.dev.quirrus.wallboard.guicomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorScreen(errorText: String) {
    Box(modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colors.onError)) {
        Text(
            modifier = Modifier.align(alignment = Alignment.Center),
            text = errorText,
            color = MaterialTheme.colors.error
        )
    }
}
