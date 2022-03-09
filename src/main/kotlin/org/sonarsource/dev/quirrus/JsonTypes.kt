package org.sonarsource.dev.quirrus

import kotlinx.serialization.Serializable

@Serializable
data class Response(val data: Data? = null, val errors: List<ResponseError>? = null)

@Serializable
data class Data(val repository: Repository? = null)

@Serializable
data class Repository(val builds: Builds)

@Serializable
data class Builds(val edges: List<Edge>)

@Serializable
data class Edge(val node: Node)

@Serializable
data class Node(val id: String, val buildCreatedTimestamp: Long, val tasks: List<Task>)

@Serializable
data class Task(val id: String, val name: String)

@Serializable
data class ResponseError(val message: String)

