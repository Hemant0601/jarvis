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
 *   2. Top-k cosine search against chunk embeddings (vector stage).
 *   3. Expand the owning nodes by 1–2 graph hops, collecting neighbours.
 *   4. Pull chunks for the expanded set, rank, return top N with provenance.
 */
@Singleton
class GraphRagRetriever @Inject constructor(
    private val dao: GraphDao,
    private val embeddings: EmbeddingService,
) {
    suspend fun describeNode(id: String): NodeCard? =
        dao.node(id)?.let { NodeCard(it.id, it.label, it.category) }

    suspend fun retrieve(query: String, topK: Int = 8, hops: Int = 2): RetrievalContext {
        val q = embeddings.embed(query)

        // Stage 1: vector search over chunk embeddings.
        val chunkEmbeds = dao.embeddings("chunk")
        val ranked = chunkEmbeds
            .asSequence()
            .map { it to Vectors.cosine(q, Vectors.unpack(it.vector, it.dim)) }
            .sortedByDescending { it.second }
            .take(topK * 2)
            .toList()

        val seedChunkIds = ranked.map { it.first.ownerId }
        val seedChunks = dao.chunksFor(emptyList()) // fetched below by node
        val rankedByChunk = ranked.associate { it.first.ownerId to it.second }

        // Stage 2: resolve owner nodes + 1–2 hop expansion.
        val chunks = dao.chunksFor(emptyList()) // placeholder; real impl: chunk-by-id DAO
        val seedNodeIds = chunks.filter { it.id in seedChunkIds }.map { it.nodeId }.distinct()
        val expanded = expand(seedNodeIds, hops)

        // Stage 3: pull chunks for all nodes in the expanded set, re-rank.
        val allChunks = dao.chunksFor(expanded.keys.toList())
        val results = allChunks
            .map { c ->
                val baseScore = rankedByChunk[c.id] ?: 0f
                val hopPenalty = 1f / (1f + (expanded[c.nodeId] ?: 0))
                RetrievedChunk(
                    nodeId = c.nodeId,
                    nodeLabel = dao.node(c.nodeId)?.label ?: c.nodeId,
                    text = c.text,
                    score = baseScore * hopPenalty,
                    hops = expanded[c.nodeId] ?: 0,
                )
            }
            .sortedByDescending { it.score }
            .take(topK)

        return RetrievalContext(query = query, chunks = results, paths = emptyList())
    }

    private suspend fun expand(seedNodeIds: List<String>, hops: Int): Map<String, Int> {
        val distances = mutableMapOf<String, Int>()
        seedNodeIds.forEach { distances[it] = 0 }
        var frontier: Set<String> = seedNodeIds.toSet()
        repeat(hops) { depth ->
            if (frontier.isEmpty()) return@repeat
            val nextEdges: List<EdgeEntity> = dao.edgesForNodes(frontier.toList())
            val next = mutableSetOf<String>()
            nextEdges.forEach { e ->
                listOf(e.fromId, e.toId).forEach { id ->
                    if (id !in distances) {
                        distances[id] = depth + 1
                        next += id
                    }
                }
            }
            frontier = next
        }
        return distances
    }
}
