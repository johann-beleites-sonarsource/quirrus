package org.sonarsource.dev.quirrus.api

import com.github.kittinunf.fuel.core.Response
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking

class ApiException(val response: HttpResponse, val errorMsg: String) : Exception(errorMsg) {
    override val message: String?
        get() = "$errorMsg\n${response.status} [${response.call.request.url} with data `${response.call.request.content.prettyPrint()}`]: ${runBlocking { response.bodyAsText() }}"
}

private fun OutgoingContent.prettyPrint() = if (this is TextContent) {
    this.text
} else {
    "UNPRINTABLE"
}

@Deprecated(message = "Use ApiException instead")
class ApiExceptionOld(val response: Response, val errorMsg: String): Exception(errorMsg)
