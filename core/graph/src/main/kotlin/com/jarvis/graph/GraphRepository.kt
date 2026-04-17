package com.jarvis.graph

import com.jarvis.data.dao.GraphDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphRepository @Inject constructor(
    private val dao: GraphDao,
) {
    suspend fun snapshot(limit: Int = 400): GraphSnapshot {
        val rawNodes = dao.recentNodes(limit)
        val nodeIds = rawNodes.map { it.id }
        val rawEdges = dao.edgesForNodes(nodeIds)
        val degree = mutableMapOf<String, Int>()
        rawEdges.forEach { e ->
            degree.merge(e.fromId, 1, Int::plus)
            degree.merge(e.toId, 1, Int::plus)
        }
        val nodes = rawNodes.map {
            GraphNode(
                id = it.id,
                label = it.label,
                category = it.category,
                summary = it.summary,
                degree = degree[it.id] ?: 0,
                updatedAt = it.updatedAt,
            )
        }
        val edges = rawEdges
            .filter { it.fromId in nodeIds.toSet() && it.toId in nodeIds.toSet() }
            .map { GraphEdge(it.fromId, it.toId, it.kind, it.weight) }
        return GraphSnapshot(nodes, edges, seed = nodes.size)
    }
}
