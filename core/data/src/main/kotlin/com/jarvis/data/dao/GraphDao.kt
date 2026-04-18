package com.jarvis.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.data.entities.ChunkEntity
import com.jarvis.data.entities.EdgeEntity
import com.jarvis.data.entities.EmbeddingEntity
import com.jarvis.data.entities.NodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdges(edges: List<EdgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunks(chunks: List<ChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: EmbeddingEntity)

    @Query("SELECT * FROM nodes ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentNodes(limit: Int = 500): List<NodeEntity>

    @Query("SELECT * FROM edges WHERE fromId IN (:ids) OR toId IN (:ids)")
    suspend fun edgesForNodes(ids: List<String>): List<EdgeEntity>

    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun node(id: String): NodeEntity?

    /** Case-insensitive lookup by label. Used by the ingest pipeline to reuse
     * existing Topic / Person nodes instead of creating duplicates. */
    @Query("SELECT * FROM nodes WHERE LOWER(label) = LOWER(:label) AND category = :category LIMIT 1")
    suspend fun findNodeByLabel(label: String, category: String): NodeEntity?

    @Query("SELECT * FROM chunks WHERE nodeId IN (:nodeIds)")
    suspend fun chunksFor(nodeIds: List<String>): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE id IN (:ids)")
    suspend fun chunksByIds(ids: List<String>): List<ChunkEntity>

    @Query("SELECT * FROM embeddings WHERE ownerKind = :kind")
    suspend fun embeddings(kind: String): List<EmbeddingEntity>

    @Query("SELECT COUNT(*) FROM nodes")
    fun nodeCount(): Flow<Int>

    @Query("DELETE FROM nodes")
    suspend fun deleteAllNodes()

    @Query("DELETE FROM edges")
    suspend fun deleteAllEdges()

    @Query("DELETE FROM chunks")
    suspend fun deleteAllChunks()

    @Query("DELETE FROM embeddings")
    suspend fun deleteAllEmbeddings()

    @Query("DELETE FROM captures")
    suspend fun deleteAllCaptures()

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
