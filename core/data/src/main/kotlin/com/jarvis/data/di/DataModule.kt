package com.jarvis.data.di

import android.content.Context
import androidx.room.Room
import com.jarvis.data.dao.GraphDao
import com.jarvis.data.db.DatabaseKey
import com.jarvis.data.db.JarvisDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
        key: DatabaseKey,
    ): JarvisDatabase {
        val passphrase = key.obtain()
        val bytes = passphrase.joinToString("").toByteArray(Charsets.UTF_8)
        return Room.databaseBuilder(ctx, JarvisDatabase::class.java, "jarvis.db")
            .openHelperFactory(SupportOpenHelperFactory(bytes))
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideGraphDao(db: JarvisDatabase): GraphDao = db.graphDao()
}
