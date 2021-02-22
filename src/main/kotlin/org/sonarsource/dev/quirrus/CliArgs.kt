package org.sonarsource.dev.quirrus

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*

class CliArgs : CliktCommand() {
    val repositoryId by option(
        "-r", "--repository",
        help = "The numeric ID of the repository in question."
    ).required()

    val apiToken: String by option(
        "-t", "--token",
        help = "The API token to access Cirrus CI. If it doesn't work, try and use the cookie instead."
    ).default(System.getenv("CIRRUS_TOKEN") ?: "")

    val cookie: String by option(
        "--cookie",
        help = "Alternatively to a token, you can use cookie authentication. In that case, use this flag or the " +
                "environment variable"
    ).default(System.getenv("CIRRUS_COOKIE") ?: "")

    val dataExtractionRegexes by option(
        "-x", "--regex",
        help = "The regex used for data extraction. Each regex must contain a \"data\" class, which will be used to " +
                "extract the data. Multiple regexes are allowed."
    ).convert { it.toRegex(RegexOption.MULTILINE) }
        .multiple(required = true)
        .check { regexList ->
            regexList
                .map { regex -> regex.pattern.indexOf("(?<data>") >= 0 }
                .fold(true, { acc, v -> acc && v })
        }

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
        help = "The name of the Cirrus task log file to download"
    ).required()

    override fun run() = Unit
}
