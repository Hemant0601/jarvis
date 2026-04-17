package com.jarvis

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jarvis.llm.GemmaClient
import com.jarvis.llm.ModelCatalog
import com.jarvis.llm.ModelKind
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class JarvisApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var gemma: GemmaClient
    @Inject lateinit var modelCatalog: ModelCatalog

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        val file = modelCatalog.fileOf(ModelKind.Gemma)
        if (file.exists()) runCatching { gemma.load(file) }
    }
}
