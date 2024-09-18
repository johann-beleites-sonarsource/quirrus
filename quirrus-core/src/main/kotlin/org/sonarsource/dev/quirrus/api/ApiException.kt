package org.sonarsource.dev.quirrus.api

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking

class ApiException(override val message: String?) : Exception(message) {
    constructor(response: HttpResponse, errorMsg: String) :
        this("$errorMsg\n${response.status} [${response.call.request.url} with data `${response.call.request.content.prettyPrint()}`]: ${runBlocking { response.bodyAsText() }}")

    //override val message: String?
    //    get() = "$errorMsg\n${response.status} [${response.call.request.url} with data `${response.call.request.content.prettyPrint()}`]: ${runBlocking { response.bodyAsText() }}"
}

private fun OutgoingContent.prettyPrint() = if (this is TextContent) {
    this.text
} else {
    "UNPRINTABLE"
}
