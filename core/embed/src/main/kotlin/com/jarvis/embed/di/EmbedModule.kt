package com.jarvis.embed.di

import com.jarvis.embed.EmbeddingService
import com.jarvis.embed.OnnxEmbeddingService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EmbedModule {
    @Binds @Singleton
    abstract fun bindEmbeddingService(impl: OnnxEmbeddingService): EmbeddingService
}
