package com.jarvis.graph

import com.jarvis.data.dao.GraphDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

@Singleton
class GraphRepository @Inject constructor(
    private val dao: GraphDao,
) {
    /**
     * Hot flow of graph snapshots. Emits whenever the node count changes (new
     * capture saved, seed completed, etc.). Each emission re-reads the current
     * top-N recent nodes + their edges.
     */
    fun observeSnapshots(limit: Int = 400): Flow<GraphSnapshot> =
        dao.nodeCount()
            .distinctUntilChanged()
            .map { snapshot(limit) }
            .flowOn(Dispatchers.IO)

    suspend fun snapshot(limit: Int = 400): GraphSnapshot {
        val rawNodes = dao.recentNodes(limit)
        val nodeIds = rawNodes.map { it.id }
        val rawEdges = if (nodeIds.isEmpty()) emptyList() else dao.edgesForNodes(nodeIds)
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
        val idSet = nodeIds.toSet()
        val edges = rawEdges
            .filter { it.fromId in idSet && it.toId in idSet }
            .map { GraphEdge(it.fromId, it.toId, it.kind, it.weight) }
        return GraphSnapshot(nodes, edges, seed = nodes.size)
    }
}
