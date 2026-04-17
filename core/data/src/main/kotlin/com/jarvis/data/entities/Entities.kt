package com.jarvis.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema for the graph RAG store.
 *
 *   nodes      — everything the brain knows about: notes, people, topics, events, places.
 *   edges      — typed, weighted relationships between nodes.
 *   chunks     — fixed-size text chunks owned by a node, used for retrieval.
 *   embeddings — dense vectors for nodes and chunks (float arrays serialised as blob).
 *   captures   — raw originals: a note, a transcript, an imported doc.
 *   messages   — chat history with the assistant.
 */

@Entity(
    tableName = "nodes",
    indices = [Index("category"), Index("updatedAt")],
)
data class NodeEntity(
    @PrimaryKey val id: String,
    val label: String,
    val category: String,
    val summary: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sensitivity: Int = 0,
)

@Entity(
    tableName = "edges",
    primaryKeys = ["fromId", "toId", "kind"],
    indices = [Index("fromId"), Index("toId"), Index("kind")],
)
data class EdgeEntity(
    val fromId: String,
    val toId: String,
    val kind: String,
    val weight: Float,
    val createdAt: Long,
)

@Entity(
    tableName = "chunks",
    indices = [Index("nodeId")],
)
data class ChunkEntity(
    @PrimaryKey val id: String,
    val nodeId: String,
    val text: String,
    val tokenCount: Int,
    val position: Int,
)

@Entity(tableName = "embeddings")
data class EmbeddingEntity(
    @PrimaryKey val ownerId: String,
    val ownerKind: String,
    val dim: Int,
    val vector: ByteArray,
    val model: String,
) {
    override fun equals(other: Any?): Boolean = other is EmbeddingEntity && other.ownerId == ownerId
    override fun hashCode(): Int = ownerId.hashCode()
}

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val id: String,
    val nodeId: String?,
    val kind: String,
    val text: String,
    val audioPath: String?,
    val capturedAt: Long,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val createdAt: Long,
    val citedNodeIds: String,
)
