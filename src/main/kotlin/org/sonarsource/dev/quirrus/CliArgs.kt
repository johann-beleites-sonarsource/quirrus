package org.sonarsource.dev.quirrus

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*

class CliArgs : CliktCommand() {
    val repositoryId by option(
            "-r", "--repository",
            help = "The ID of the repository in question."
    ).required()

    val apiToken: String by option(
            "-t", "--token",
            help = "The API token to access Cirrus CI"
    ).default(System.getenv("CIRRUS_TOKEN") ?: "")

    val cookie: String by option(
            "--cookie",
            help = "Alternatively to a token, you can use cookie authentication. In that case, use this flag or the " +
                    "environment variable"
    ).default(System.getenv("CIRRUS_COOKIE") ?: "")

    val dataExtractionRegex by option(
            "-x", "--regex",
            help = "The regex used for data extraction. E.g. to extract the time in ms the C# scanner ran: "
    ).convert { it.toRegex(RegexOption.MULTILINE) }.required()

    val verbose by option("-v", "--verbose").flag(default = false)

    val quiet by option("--quiet").flag(default = false)

    val branches by argument(help = "The names of the builds (e.g. peachee branches) to compare")
        .multiple(required = true)

    val apiUrl by option(
        "--api-url",
        help = "Override the default API endpoint"
    ).default("https://api.cirrus-ci.com/graphql")

    val notFoundPlaceholder by option(
        "--not-found",
        help = "Will be used whenever a value could not be found/extracted in place of that value."
    ).default("-")

    val logName by option(
        "-l", "--log-name",
        help = "The name of the log file to download"
    ).required()

    override fun run() = Unit
}
