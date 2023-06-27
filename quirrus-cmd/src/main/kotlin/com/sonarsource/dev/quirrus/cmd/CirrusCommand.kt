package com.sonarsource.dev.quirrus.cmd

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import kotlinx.coroutines.runBlocking
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.Authentication.authenticateWithConfigFile
import org.sonarsource.dev.quirrus.api.Common
import org.sonarsource.dev.quirrus.common.GenericCirrusCommand
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess


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
            runBlocking {
                Common(apiConfiguration).resolveRepositoryId(repositoryIdOrName).also {
                    logger.print { "Found ID '$it' for repository '$repositoryIdOrName'" }
                }
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

    val authenticator: (HttpRequestBuilder) -> Unit by lazy {
        when {
            apiToken.isNotEmpty() -> {
                logger.print { "Using token authentication" };
                { request: HttpRequestBuilder -> request.bearerAuth(apiToken) }
            }

            cookie.isNotEmpty() -> {
                logger.print { "Using cookie authentication" };
                { request: HttpRequestBuilder -> request.header("Cookie", cookie) }
            }

            credentialConfigFilePath.isRegularFile() -> {
                logger.print { "Using credentials configured in '$credentialConfigFilePath'" };
                { request: HttpRequestBuilder -> request.authenticateWithConfigFile(credentialConfigFilePath) }
            }

            else -> {
                logger.error(
                    "No authentication details provided. Expecting environment variable CIRRUS_TOKEN or " +
                            "CIRRUS_COOKIE to be set or a credentials file to be present."
                )
                exitProcess(2)
            }
        }
    }

    val apiConfiguration by lazy {
        ApiConfiguration(
            authenticator = authenticator,
            apiUrl = apiUrl,
            requestTimeoutOverride = requestTimeout,
            connectionRetries = connectionRetries,
            logger = logger,
        )
    }
}
