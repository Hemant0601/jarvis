package com.jarvis.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Reserved for app-wide Hilt bindings the individual core modules don't own.
 * Most DI lives in the core modules themselves so that Hilt can resolve them
 * without the :app module having to know each impl.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
