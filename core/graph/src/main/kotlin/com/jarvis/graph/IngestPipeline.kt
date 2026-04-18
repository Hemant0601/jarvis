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
 *   • Skip conversational filler so "hi" and "hey" don't become nodes.
 *   • Chunk the text, embed each chunk and the note.
 *   • Run Gemma / regex entity extraction, reusing existing Topic/Person
 *     nodes by label so the brain doesn't fill up with duplicates.
 *   • Link every note back to the Jarvis root so the graph stays connected.
 *
 * Returns the id of the ingested Note node, or null if the message was
 * deemed too trivial to store.
 */
@Singleton
class IngestPipeline @Inject constructor(
    private val dao: GraphDao,
    private val embeddings: EmbeddingService,
    private val extractor: EntityExtractor,
    private val seed: SeedData,
) {
    suspend fun ingestText(text: String, label: String? = null): String? {
        val clean = text.trim()
        if (clean.length < 12) return null
        if (trivialChat.matches(clean)) return null

        val now = Clock.System.now().toEpochMilliseconds()
        val rootId = seed.ensureRoot()
        val nodeId = Ids.new()

        val parent = NodeEntity(
            id = nodeId,
            label = label ?: clean.take(48).substringBefore('\n').ifBlank { "Note" },
            category = "Note",
            summary = clean.take(240),
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertNode(parent)

        val chunks = chunkify(clean).mapIndexed { i, c ->
            ChunkEntity(
                id = Ids.new(),
                nodeId = nodeId,
                text = c,
                tokenCount = c.split(" ").size,
                position = i,
            )
        }
        dao.upsertChunks(chunks)

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

        val edges = mutableListOf(
            EdgeEntity(
                fromId = rootId,
                toId = nodeId,
                kind = "remembers",
                weight = 0.6f,
                createdAt = now,
            )
        )

        val extracted = extractor.extract(clean)
        extracted.entities.forEach { e ->
            val name = e.name.trim().take(48)
            if (name.length < 2) return@forEach
            // Re-use existing Topic/Person etc. by label instead of creating a
            // new node every time the user mentions the same thing.
            val existing = dao.findNodeByLabel(name, e.category)
            val entId = existing?.id ?: Ids.new()
            if (existing == null) {
                dao.upsertNode(
                    NodeEntity(
                        id = entId,
                        label = name,
                        category = e.category,
                        summary = e.description.ifBlank { name },
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            edges += EdgeEntity(
                fromId = nodeId,
                toId = entId,
                kind = e.relationToSource.ifBlank { "mentions" },
                weight = e.confidence.coerceIn(0.1f, 1.0f),
                createdAt = now,
            )
        }
        dao.upsertEdges(edges)
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

    private companion object {
        /** Whole-message greetings / acks we don't want to store as memories. */
        val trivialChat = Regex(
            """^(hi|hey|hello|yo+|sup|howdy|ok|okay|cool|nice|thanks?|thx|ty|bye|cya|gn|gm|wow|lol|hmm+|nah|yes|no|yep|nope|k)\s*[!.?]*\s*${'$'}""",
            RegexOption.IGNORE_CASE,
        )
    }
}
