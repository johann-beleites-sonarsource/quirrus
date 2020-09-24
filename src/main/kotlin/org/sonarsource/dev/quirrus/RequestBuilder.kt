package org.sonarsource.dev.quirrus

import com.github.kittinunf.fuel.core.Request
import me.lazmaid.kraph.Kraph

object RequestBuilder {
    fun logDownloadLink(taskId: String) = "https://api.cirrus-ci.com/v1/task/$taskId/logs/scanner_end.log"

    fun tasksQuery(repositoryId: String, branchName: String) =
        Kraph {
            query {
                fieldObject("repository", args = mapOf("id" to repositoryId)) {
                    fieldObject("builds", args = mapOf("branch" to branchName, "last" to 1)) {
                        fieldObject("edges") {
                            fieldObject("node") {
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
