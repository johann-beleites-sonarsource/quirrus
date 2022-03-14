package org.sonarsource.dev.quirrus

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.serialization.responseObject
import com.github.kittinunf.result.getOrElse
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

abstract class CirrusCommand : CliktCommand() {
    val apiUrl by option(
        "--api-url",
        help = "Override the default API endpoint"
    ).default("https://api.cirrus-ci.com/graphql")

    val requestTimeout: Int? by option(
        "--request-timeout",
        help = "Set the timeout for API requests in seconds."
    ).int()

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
        help = "The API token to access Cirrus CI. If it doesn't work, try and use the cookie instead."
    ).default(System.getenv("CIRRUS_TOKEN") ?: "")

    val cookie: String by option(
        "--cookie",
        help = "Alternatively to a token, you can use cookie authentication. In that case, use this flag or the " +
            "environment variable"
    ).default(System.getenv("CIRRUS_COOKIE") ?: "")

    val connectionRetries: Int by option(
        "--connection-retries"
    ).int().default(5)

    val verbose by option("-v", "--verbose").flag(default = false)

    val quiet by option("--quiet").flag(default = false)

    val logger by lazy { CliLogger(verbose = verbose, quiet = quiet) }

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
