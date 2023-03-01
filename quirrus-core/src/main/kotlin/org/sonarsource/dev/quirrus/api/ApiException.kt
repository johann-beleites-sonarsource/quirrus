package org.sonarsource.dev.quirrus.api

import com.github.kittinunf.fuel.core.Response

class ApiException(val response: Response, errorMsg: String) : Exception(errorMsg)
