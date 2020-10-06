package org.sonarsource.dev.quirrus

import com.github.kittinunf.fuel.core.Request
import me.lazmaid.kraph.Kraph

object RequestBuilder {
    fun logDownloadLink(taskId: String, logName: String) =
        "https://api.cirrus-ci.com/v1/task/$taskId/logs/$logName"

    fun tasksQuery(repositoryId: String, branchName: String, numberOfLatestBuilds: Int = 1) =
        Kraph {
            query {
                fieldObject("repository", args = mapOf("id" to repositoryId)) {
                    fieldObject("builds", args = mapOf("branch" to branchName, "last" to numberOfLatestBuilds)) {
                        fieldObject("edges") {
                            fieldObject("node") {
                                field("id")
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
