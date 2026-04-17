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
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
        key: DatabaseKey,
    ): JarvisDatabase {
        // sqlcipher-android 4.6 no longer auto-loads its native lib via a
        // ContentProvider; callers must do it themselves or the first call
        // into the JNI binding throws UnsatisfiedLinkError. If the load fails
        // (e.g. native lib not built for this kernel's page size) we fall
        // back to an un-encrypted Room database so the app still boots.
        val factory = runCatching {
            System.loadLibrary("sqlcipher")
            SupportOpenHelperFactory(
                String(key.obtain()).toByteArray(Charsets.UTF_8)
            )
        }.onFailure { Timber.e(it, "SQLCipher unavailable; falling back to plain SQLite") }
            .getOrNull()

        val builder = Room.databaseBuilder(ctx, JarvisDatabase::class.java, "jarvis.db")
            .fallbackToDestructiveMigration()
        if (factory != null) builder.openHelperFactory(factory)
        return builder.build()
    }

    @Provides
    fun provideGraphDao(db: JarvisDatabase): GraphDao = db.graphDao()
}
