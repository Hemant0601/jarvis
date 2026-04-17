package com.jarvis

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jarvis.graph.SeedData
import com.jarvis.llm.GemmaClient
import com.jarvis.llm.ModelCatalog
import com.jarvis.llm.ModelKind
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class JarvisApp : Application(), Configuration.Provider {

    // Eager: WorkManager asks for this synchronously via Configuration.Provider.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Lazy: these can trigger heavy init (SQLCipher, EncryptedSharedPreferences,
    // MediaPipe). Keeping them behind dagger.Lazy means any failure happens inside
    // our try/catch instead of inside Hilt's generated super.onCreate().
    @Inject lateinit var gemma: Lazy<GemmaClient>
    @Inject lateinit var modelCatalog: Lazy<ModelCatalog>
    @Inject lateinit var seed: Lazy<SeedData>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Install before Hilt runs so we capture even its init failures.
        CrashReporter.install(base)
    }

    override fun onCreate() {
        runCatching { super.onCreate() }
            .onFailure { CrashReporter.appendNonFatal(this, "super.onCreate", it) }

        if (BuildConfig.DEBUG) runCatching { Timber.plant(Timber.DebugTree()) }

        // Gemma load — only if the file exists, never blocks startup.
        scope.launch {
            runCatching {
                val file = modelCatalog.get().fileOf(ModelKind.Gemma)
                if (file.exists()) gemma.get().load(file)
            }.onFailure { CrashReporter.appendNonFatal(this@JarvisApp, "gemma.load", it) }
        }

        // Seed the graph off the main thread so a bad DB init can't crash the UI.
        scope.launch {
            runCatching { seed.get().seedIfNeeded() }
                .onFailure { CrashReporter.appendNonFatal(this@JarvisApp, "seed.seedIfNeeded", it) }
        }
    }
}
