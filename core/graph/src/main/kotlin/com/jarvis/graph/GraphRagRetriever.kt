package com.jarvis.graph

import com.jarvis.data.dao.GraphDao
import com.jarvis.data.entities.EdgeEntity
import com.jarvis.data.entities.EmbeddingEntity
import com.jarvis.embed.EmbeddingService
import com.jarvis.embed.Vectors
import javax.inject.Inject
import javax.inject.Singleton

data class NodeCard(val id: String, val label: String, val category: String)

/**
 * Hybrid retrieval:
 *   1. Embed the query.
 *   2. Cosine search against chunk embeddings → seed chunk IDs.
 *   3. Resolve owning nodes, expand by up to `hops` via edges (BFS).
 *   4. Pull chunks from the expanded node set, score each by
 *      (chunk-cosine · 1 / (1 + hops)), return top-K with provenance.
 */
@Singleton
class GraphRagRetriever @Inject constructor(
    private val dao: GraphDao,
    private val embeddings: EmbeddingService,
) {
    suspend fun describeNode(id: String): NodeCard? =
        dao.node(id)?.let { NodeCard(it.id, it.label, it.category) }

    suspend fun retrieve(
        query: String,
        topK: Int = 8,
        hops: Int = 2,
        excludeNodeId: String? = null,
    ): RetrievalContext {
        val q = embeddings.embed(query)
        val chunkEmbeds = dao.embeddings("chunk")
        if (chunkEmbeds.isEmpty()) return RetrievalContext(query, emptyList(), emptyList())

        // 1. Vector stage: rank chunk embeddings by cosine.
        val rankedByChunkId: Map<String, Float> = chunkEmbeds
            .asSequence()
            .map { it.ownerId to score(q, it) }
            .sortedByDescending { it.second }
            .take(topK * 3)
            .toMap()

        // 2. Resolve seed chunks → seed node IDs (drop the excluded node — typically
        // the one the caller just ingested, to avoid echoing it back).
        val seedChunks = dao.chunksByIds(rankedByChunkId.keys.toList())
            .filter { it.nodeId != excludeNodeId }
        val seedNodeIds = seedChunks.map { it.nodeId }.distinct()
        if (seedNodeIds.isEmpty()) return RetrievalContext(query, emptyList(), emptyList())

        // 3. BFS expansion.
        val distance: Map<String, Int> = bfs(seedNodeIds, hops)

        // 4. Pull every chunk for the expanded set, score, trim.
        val allChunks = dao.chunksFor(distance.keys.toList())
            .filter { it.nodeId != excludeNodeId }
        val results = allChunks
            .map { c ->
                val base = rankedByChunkId[c.id] ?: 0f
                val hop = distance[c.nodeId] ?: 0
                val score = base * (1f / (1f + hop))
                RetrievedChunk(
                    nodeId = c.nodeId,
                    nodeLabel = dao.node(c.nodeId)?.label ?: c.nodeId,
                    text = c.text,
                    score = score,
                    hops = hop,
                )
            }
            .sortedByDescending { it.score }
            .take(topK)

        return RetrievalContext(query = query, chunks = results, paths = emptyList())
    }

    private fun score(q: FloatArray, e: EmbeddingEntity): Float {
        val v = Vectors.unpack(e.vector, e.dim)
        return Vectors.cosine(q, v)
    }

    private suspend fun bfs(seeds: List<String>, hops: Int): Map<String, Int> {
        val distance = mutableMapOf<String, Int>()
        seeds.forEach { distance[it] = 0 }
        var frontier: Set<String> = seeds.toSet()
        for (depth in 1..hops) {
            if (frontier.isEmpty()) break
            val edges: List<EdgeEntity> = dao.edgesForNodes(frontier.toList())
            val next = mutableSetOf<String>()
            edges.forEach { e ->
                listOf(e.fromId, e.toId).forEach { id ->
                    if (distance.putIfAbsent(id, depth) == null) next += id
                }
            }
            frontier = next
        }
        return distance
    }
}
