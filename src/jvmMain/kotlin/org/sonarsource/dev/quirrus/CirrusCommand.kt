package org.sonarsource.dev.quirrus

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.getOrElse
import kotlinx.serialization.json.Json
import org.sonarsource.dev.quirrus.common.GenericCirrusCommand
import org.sonarsource.dev.quirrus.gui.authenticate
import org.sonarsource.dev.quirrus.gui.loadCookies
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess

val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

abstract class CirrusCommand : GenericCirrusCommand() {

    private val repositoryIdOrName: String by option(
        "-r", "--repository",
        help = "The numeric ID or name of the repository in question."
    ).required()

    val repositoryId by lazy {
        runCatching {
            repositoryIdOrName.toLong()
            repositoryIdOrName
        }.getOrElse {
            resolveRepositoryId(repositoryIdOrName).also {
                logger.print { "Found ID '$it' for repository '$repositoryIdOrName'" }
            }
        }
    }

    val apiToken: String by option(
        "-t", "--token",
        help = "[Note: Cirrus CI currently does not support user-level API tokens] The API token to access Cirrus CI. " +
                "If it doesn't work, try and use the cookie instead."
    ).default(System.getenv("CIRRUS_TOKEN") ?: "")

    val cookie: String by option(
        "--cookie",
        help = "Alternatively to a token, you can use cookie authentication. In that case, use this flag or the " +
                "environment variable"
    ).default(System.getenv("CIRRUS_COOKIE") ?: "")

    val authenticator: (Request) -> Request by lazy {
        when {
            apiToken.isNotEmpty() -> {
                logger.print { "Using token authentication" };
                { request: Request -> request.header("Authorization", "Bearer $apiToken") }
            }

            cookie.isNotEmpty() -> {
                logger.print { "Using cookie authentication" };
                { request: Request -> request.header("Cookie", cookie) }
            }

            credentialConfigFilePath.isRegularFile() -> {
                logger.print { "Using credentials configured in '$credentialConfigFilePath'" };
                { request: Request -> request.authenticate(credentialConfigFilePath) }
            }

            else -> {
                logger.error(
                    "No authentication details provided. Expecting environment variable CIRRUS_TOKEN or " +
                            "CIRRUS_COOKIE to be set."
                )
                exitProcess(2)
            }
        }
    }

    fun Request.authenticate(): Request = authenticator(this)

    private fun resolveRepositoryId(repoName: String) =
        apiUrl.httpPost()
            .authenticate()
            .let { request ->
                requestTimeout?.let { request.timeout(it) } ?: request
            }
            .jsonBody(RequestBuilder.repoIdQuery(repoName).toRequestString())
            .responseObject<OwnerRepositoryApiResponse>(json)
            .let { (_, _, result) ->
                result.getOrElse { e ->
                    logger.error("Could not fetch repository ID for '$repoName': ${e.localizedMessage}")
                    exitProcess(1)
                }.data?.ownerRepository?.id ?: run {
                    logger.error("Could not fetch repository ID for '$repoName' (got null).")
                    exitProcess(1)
                }
            }
}
