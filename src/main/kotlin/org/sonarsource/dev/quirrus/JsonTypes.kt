package org.sonarsource.dev.quirrus

data class Response(val data: Data?, val errors: List<ResponseError>?)
data class Data(val repository: Repository?)
data class Repository(val builds: Builds)
data class Builds(val edges: List<Edge>)
data class Edge(val node: Node)
data class Node(val id: String, val buildCreatedTimestamp: Long, val tasks: List<Task>)
data class Task(val id: String, val name: String)
data class ResponseError(val message: String)

