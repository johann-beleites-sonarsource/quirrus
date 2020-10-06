package org.sonarsource.dev.quirrus

import kotlin.system.exitProcess

val buildWithLastNumberParserRegex = "(?<branch>[^~]+)(~(?<number>[0-9]*))?".toRegex()

class Build private constructor(
    val buildString: String,
    val branchName: String,
    val buildOffset: Int
) : Comparable<Build> {
    companion object {
        fun ofBuild(buildString: String): Build {
            buildWithLastNumberParserRegex.matchEntire(buildString)?.let { matchResult ->
                return Build(
                    buildString,
                    (matchResult.groups["branch"]?.value ?: run {
                        System.err.println("Could not extract branch name from $buildString")
                        exitProcess(6)
                    }), (matchResult.groups["number"]?.value?.toInt() ?: 1)
                )
            } ?: run {
                System.err.println("Could not parse build '$buildString'")
                exitProcess(5)
            }
        }
    }

    override fun toString(): String = buildString;
    override fun compareTo(other: Build): Int = buildString.compareTo(other.buildString)
}
