package org.sonarsource.dev.quirrus

object RequestBuilder {
    fun logDownloadLink(taskId: String, logName: String) =
        "https://api.cirrus-ci.com/v1/task/$taskId/logs/$logName"
}
