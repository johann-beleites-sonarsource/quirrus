package org.sonarsource.dev.quirrus.common

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.sonarsource.dev.quirrus.CliLogger
import java.nio.file.Path

abstract class GenericCirrusCommand : CliktCommand() {
    val apiUrl by option(
        "--api-url",
        help = "Override the default API endpoint"
    ).default("https://api.cirrus-ci.com/graphql")

    val requestTimeout: Int? by option(
        "--request-timeout",
        help = "Set the timeout for API requests in seconds."
    ).int()

    val connectionRetries: Int by option(
        "--connection-retries"
    ).int().default(5)

    val credentialConfigFilePath: Path by option(
        "--auth-file",
        help = "You can store cirrus credentials (cirrusUserId and cirrusAuthToken) in a file and use that. This option will be used if " +
                "no api token or cookies are provided explicitly."
    ).path(mustExist = false, canBeDir = false, mustBeReadable = true)
        .default(Path.of(System.getenv("HOME"), ".quirrus", "auth.conf"))

    val verbose by option("-v", "--verbose").flag(default = false)

    val quiet by option("--quiet").flag(default = false)

    val logger by lazy { CliLogger(verbose = verbose, quiet = quiet) }

}
