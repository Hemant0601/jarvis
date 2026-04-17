package com.jarvis.graph

/**
 * Shared data classes for the graph domain. Kept separate from Room entities so
 * the UI and retrieval layers don't depend on the database module directly.
 */

data class GraphNode(
    val id: String,
    val label: String,
    val category: String,
    val summary: String,
    val degree: Int,
    val updatedAt: Long,
)

data class GraphEdge(
    val fromId: String,
    val toId: String,
    val kind: String,
    val weight: Float,
)

data class GraphSnapshot(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val seed: Int,
)

data class RetrievedChunk(
    val nodeId: String,
    val nodeLabel: String,
    val text: String,
    val score: Float,
    val hops: Int,
)

data class RetrievalContext(
    val query: String,
    val chunks: List<RetrievedChunk>,
    val paths: List<List<String>>,
)
