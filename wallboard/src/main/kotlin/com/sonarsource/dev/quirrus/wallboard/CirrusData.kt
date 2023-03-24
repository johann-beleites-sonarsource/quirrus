package com.sonarsource.dev.quirrus.wallboard

import com.github.kittinunf.result.getOrElse
import org.sonarsource.dev.quirrus.api.ApiConfiguration
import org.sonarsource.dev.quirrus.api.LogDownloader

//private const val PEACHEE_CIRRUS_REPO = "5933424424517632"
private const val PEACHEE_CIRRUS_REPO = "5216277258567680"

class CirrusData(val apiConfig: ApiConfiguration) {
    fun getLastPeachBuilds(branch: String, numberOfBuilds: Int = 1) =
        LogDownloader(apiConfig).getLastNBuilds(PEACHEE_CIRRUS_REPO, branch, numberOfBuilds).third
            .getOrElse {
                throw it
            }.data?.repository?.builds

    fun getLastPeachBuilds(branches: List<String>, numberOfBuilds: Int = 1) =
        branches.associateWith { branch ->
            getLastPeachBuilds(branch, numberOfBuilds)
        }
}
