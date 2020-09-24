package org.sonarsource.dev.quirrus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import kotlin.system.exitProcess

private const val API = "https://api.cirrus-ci.com/graphql"
private var COOKIE: String? = System.getenv("CIRRUS_COOKIE")
private var TOKEN: String? = System.getenv("CIRRUS_TOKEN")
private const val REPOSITORY_ID = "5933424424517632"

private val dataExtractorRegex = "[^0-9]*(?<time>[0-9]+)ms".toRegex()

fun main(args: Array<String>) {
    if (TOKEN == null) {
        if (COOKIE == null) {
            System.err.println("No authentication details provided. Expecting environment variable CIRRUS_TOKEN or " +
                    "CIRRUS_COOKIE to be set.")
            exitProcess(2)
        } else {
            println("Using cookie authentication")
        }
    } else {
        println("Using token authentication")
    }

    val branches = args.asList()
    println("Starting for branches: ${branches.joinToString(", ")}")

    val collector = mutableMapOf<String, MutableMap<String, Int>>()
    branches
        .asSequence()
        .map { branch -> branch to getTasksForLatestBuild(branch) }
        .map { (branch, tasks) ->
            println("Fetching logs for branch '$branch'")
            branch to fetchRawDataForTasks(tasks).sortedBy { (task, _) -> task.name }
        }
        .map { (branch, tasksWithData) ->
            tasksWithData.map { (task, rawData) ->
                val time = dataExtractorRegex.find(rawData)!!.run {
                    groups["time"]!!.value.toInt()
                }
                collector.computeIfAbsent(task.name) { mutableMapOf() }
                collector[task.name]!!.put(branch, time)
            }
            branch
        }
        .toSet()
        .sorted()
        .let { Printer.csvPrint(it, collector) }
}

private fun extractAllTasks(result: Result<Response, FuelError>): List<Task> =
    result.get().data.repository.builds.edges[0].node.tasks

private fun fetchRawDataForTasks(tasks: List<Task>): List<Pair<Task, String>> {
    val rawData = mutableListOf<Pair<Task, String>>()
    tasks.map { task ->
        RequestBuilder.logDownloadLink(task.id)
            .httpGet()
            .authenticate()
            .responseString { request, _, result ->
                when (result) {
                    is Result.Failure -> System.err.println(
                        "ERROR: Could not get fetch data for '${task.name}' from ${request.url}: " +
                                "${result.error.localizedMessage}."
                    )
                    is Result.Success -> {
                        //println("Download of log for '${task.name}' from ${request.url} successful.")
                        result.get().run {
                            val startIndex = indexOf("Sensor CSharpSecuritySensor [security] (done) | time=")
                            val endIndex = indexOf("ms", startIndex)
                            rawData.add(task to substring(startIndex, endIndex + 2))
                        }
                    }
                }
            }
    }.forEach { async -> async.join() }
    return rawData
}

private fun getTasksForLatestBuild(branchName: String): List<Task> {
    val objectMapper = ObjectMapper().registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE

    return API.httpPost()
        .authenticate()
        .jsonBody(RequestBuilder.tasksQuery(REPOSITORY_ID, branchName).toRequestString())
        .responseObject<Response>()
        .let { (_, _, result) ->
            when (result) {
                is Result.Failure -> {
                    System.err.println("Failure: ${result.error.localizedMessage}")
                    exitProcess(1)
                }
                is Result.Success -> {
                    extractAllTasks(result)
                }
            }
        }
}

fun Request.authenticate(): Request =
    TOKEN?.let { this.header("Authorization", "Bearer $TOKEN") }
        ?: this.header("Cookie", COOKIE!!)
