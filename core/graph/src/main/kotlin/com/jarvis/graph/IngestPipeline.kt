package com.jarvis.graph

import com.jarvis.common.Ids
import com.jarvis.data.dao.GraphDao
import com.jarvis.data.entities.ChunkEntity
import com.jarvis.data.entities.EdgeEntity
import com.jarvis.data.entities.EmbeddingEntity
import com.jarvis.data.entities.NodeEntity
import com.jarvis.embed.EmbeddingService
import com.jarvis.embed.Vectors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.Clock

/**
 * Ingest pipeline for every capture (text or transcribed voice):
 *   • Chunk the text.
 *   • Generate embeddings per chunk + per parent node.
 *   • Run on-device Gemma entity/relation extraction to produce extra nodes + edges.
 *   • Link the new Note node back to the Jarvis root so the brain stays connected.
 */
@Singleton
class IngestPipeline @Inject constructor(
    private val dao: GraphDao,
    private val embeddings: EmbeddingService,
    private val extractor: EntityExtractor,
    private val seed: SeedData,
) {
    suspend fun ingestText(text: String, label: String? = null): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val rootId = seed.ensureRoot()
        val nodeId = Ids.new()

        val parent = NodeEntity(
            id = nodeId,
            label = label ?: text.take(48).substringBefore('\n').ifBlank { "Note" },
            category = "Note",
            summary = text.take(240),
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertNode(parent)

        val chunks = chunkify(text).mapIndexed { i, c ->
            ChunkEntity(
                id = Ids.new(),
                nodeId = nodeId,
                text = c,
                tokenCount = c.split(" ").size,
                position = i,
            )
        }
        dao.upsertChunks(chunks)

        // Node-level embedding.
        val nodeVec = embeddings.embed(parent.summary)
        dao.upsertEmbedding(
            EmbeddingEntity(
                ownerId = nodeId,
                ownerKind = "node",
                dim = nodeVec.size,
                vector = Vectors.pack(nodeVec),
                model = embeddings.modelId(),
            )
        )
        // Chunk-level embeddings.
        chunks.forEach { c ->
            val v = embeddings.embed(c.text)
            dao.upsertEmbedding(
                EmbeddingEntity(
                    ownerId = c.id,
                    ownerKind = "chunk",
                    dim = v.size,
                    vector = Vectors.pack(v),
                    model = embeddings.modelId(),
                )
            )
        }

        // Every Note is linked back to the Jarvis root.
        val edgeEntities = mutableListOf(
            EdgeEntity(
                fromId = rootId,
                toId = nodeId,
                kind = "remembers",
                weight = 0.6f,
                createdAt = now,
            )
        )

        // Extracted entities become child nodes of the Note.
        val extracted = extractor.extract(text)
        extracted.entities.forEach { e ->
            val entId = Ids.new()
            dao.upsertNode(
                NodeEntity(
                    id = entId,
                    label = e.name.take(48),
                    category = e.category,
                    summary = e.description.ifBlank { e.name },
                    createdAt = now,
                    updatedAt = now,
                )
            )
            edgeEntities += EdgeEntity(
                fromId = nodeId,
                toId = entId,
                kind = e.relationToSource.ifBlank { "mentions" },
                weight = e.confidence.coerceIn(0.1f, 1.0f),
                createdAt = now,
            )
        }
        dao.upsertEdges(edgeEntities)
        return nodeId
    }

    private fun chunkify(text: String, maxChars: Int = 800, overlap: Int = 120): List<String> {
        if (text.length <= maxChars) return listOf(text.trim())
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val end = (i + maxChars).coerceAtMost(text.length)
            out += text.substring(i, end).trim()
            if (end == text.length) break
            i = end - overlap
        }
        return out
    }
}
