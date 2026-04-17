package com.jarvis.embed.di

import com.jarvis.embed.EmbeddingService
import com.jarvis.embed.HashEmbeddingService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EmbedModule {
    /**
     * Default binding is the hashing fallback so the app works with zero model
     * downloads. Swap to [com.jarvis.embed.OnnxEmbeddingService] once a proper
     * ONNX embedder + tokenizer is wired.
     */
    @Binds @Singleton
    abstract fun bindEmbeddingService(impl: HashEmbeddingService): EmbeddingService
}
