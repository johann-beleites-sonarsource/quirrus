package org.sonarsource.dev.quirrus

import me.lazmaid.kraph.Kraph
import java.util.Objects

object RequestBuilder {
    fun logDownloadLink(taskId: String, logName: String) =
        "https://api.cirrus-ci.com/v1/task/$taskId/logs/$logName"

    fun tasksQuery(repositoryId: String, branchName: String?, numberOfLatestBuilds: Int = 1): Kraph {
        val buildsArgs: MutableMap<String, Any> = mutableMapOf("last" to numberOfLatestBuilds)
        branchName?.let { buildsArgs["branch"] = it }

        return Kraph {
            query {
                fieldObject("repository", args = mapOf("id" to repositoryId)) {
                    fieldObject("builds", args = buildsArgs) {
                        fieldObject("edges") {
                            fieldObject("node") {
                                field("id")
                                field("buildCreatedTimestamp")
                                fieldObject("tasks") {
                                    field("name")
                                    field("id")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun repoIdQuery(repoName: String) =
        Kraph {
            query {
                fieldObject("ownerRepository", args = mapOf("platform" to "github", "owner" to "SonarSource", "name" to repoName)) {
                    field("id")
                }
            }
        }
}
