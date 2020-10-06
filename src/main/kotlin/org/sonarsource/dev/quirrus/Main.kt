package org.sonarsource.dev.quirrus

import com.github.kittinunf.fuel.core.Request
import kotlin.system.exitProcess


fun main(rawArgs: Array<String>) {
    val args = CliArgs().also { it.main(rawArgs) }
    val token = args.apiToken
    val cookie = args.cookie

    val cliLogger = CliLogger(verbose = args.verbose, quiet = args.quiet)

    val authenticator: (Request) -> Request = when {
        token.isNotEmpty() -> {
            cliLogger.print { "Using token authentication" };
            { request: Request -> request.header("Authorization", "Bearer $token") }
        }
        cookie.isNotEmpty() -> {
            cliLogger.print { "Using cookie authentication" };
            { request: Request -> request.header("Cookie", cookie) }
        }
        else -> {
            cliLogger.error(
                "No authentication details provided. Expecting environment variable CIRRUS_TOKEN or " +
                        "CIRRUS_COOKIE to be set."
            )
            exitProcess(2)
        }
    }

    cliLogger.print { "Using data extraction regex \"${args.dataExtractionRegex}\"" }

    args.branches.let { builds ->
        cliLogger.print { "Starting for builds: ${builds.joinToString(", ")}" }
        Worker(
            apiUrl = args.apiUrl,
            authenticator = authenticator,
            repositoryId = args.repositoryId,
            builds = builds.map { Build.ofBuild(it) },
            dataExtractorRegex = args.dataExtractionRegex,
            logName = args.logName,
            notFoundPlaceHolder = args.notFoundPlaceholder,
            logger = cliLogger
        ).run()
    }
}

