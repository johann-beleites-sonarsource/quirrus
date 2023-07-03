package com.sonarsource.dev.quirrus.wallboard.guicomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

private val dateTimeFormatWithTimezone = SimpleDateFormat("dd.MM.yyy HH:mm Z", Locale.getDefault())

@Composable
fun ListTitle(title: String, numberCompleted: Int, numberFailed: Int, totalNumber: Int, buildCreatedTimestamp: Long) {
    Box(modifier = Modifier.fillMaxWidth()) {

        AnnotatedString.Builder().apply {
            pushStyle(MaterialTheme.typography.h4.toSpanStyle())
            append("$title ")

            pushStyle(MaterialTheme.typography.h5.toSpanStyle())
            append("($numberFailed failed | $numberCompleted completed | $totalNumber total)")
        }.toAnnotatedString().let {
            Text(it)
        }

        Text(dateTimeFormatWithTimezone.format(buildCreatedTimestamp), modifier = Modifier.align(Alignment.BottomEnd))
    }

    Divider(
        modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(color = MaterialTheme.colors.primary)
            .padding(bottom = 5.dp)
    )
}
