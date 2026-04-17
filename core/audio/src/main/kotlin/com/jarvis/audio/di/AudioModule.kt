package com.jarvis.audio.di

import com.jarvis.audio.Transcriber
import com.jarvis.audio.WhisperTranscriber
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds @Singleton
    abstract fun bindTranscriber(impl: WhisperTranscriber): Transcriber
}
