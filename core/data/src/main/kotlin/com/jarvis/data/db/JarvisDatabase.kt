package com.jarvis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jarvis.data.dao.GraphDao
import com.jarvis.data.entities.CaptureEntity
import com.jarvis.data.entities.ChunkEntity
import com.jarvis.data.entities.EdgeEntity
import com.jarvis.data.entities.EmbeddingEntity
import com.jarvis.data.entities.MessageEntity
import com.jarvis.data.entities.NodeEntity

@Database(
    entities = [
        NodeEntity::class,
        EdgeEntity::class,
        ChunkEntity::class,
        EmbeddingEntity::class,
        CaptureEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun graphDao(): GraphDao
}
