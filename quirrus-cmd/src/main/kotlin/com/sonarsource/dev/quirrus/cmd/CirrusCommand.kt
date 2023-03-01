package com.sonarsource.dev.quirrus.cmd

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.kittinunf.fuel.core.Request
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
            Common(apiConfiguration).resolveRepositoryId(repositoryIdOrName).also {
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
                { request: Request -> request.authenticateWithConfigFile(credentialConfigFilePath) }
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
            requestTimeout = requestTimeout,
            connectionRetries = connectionRetries,
            logger = logger,
        )
    }
}
