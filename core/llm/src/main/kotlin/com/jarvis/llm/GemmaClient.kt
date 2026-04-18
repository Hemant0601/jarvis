package com.jarvis.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Wraps MediaPipe's on-device LLM inference engine. Loads lazily on first use
 * if the .task file is present in [ModelCatalog] — so restarting the app after
 * a Gemma download doesn't leave the user stuck on the setup card.
 */
@Singleton
class GemmaClient @Inject constructor(
    @ApplicationContext private val context: Context,
    // Indirect via Provider to break the construction cycle with ModelCatalog
    // (ModelCatalog doesn't need Gemma, but we don't want Hilt to eager-build
    // either during graph validation of LlmRouter).
    private val catalogProvider: Provider<ModelCatalog>,
) : LlmClient {

    override val id: String = "gemma-4-on-device"

    @Volatile private var engine: LlmInference? = null
    @Volatile private var lastError: String? = null

    fun load(modelFile: File, maxTokens: Int = 1024) {
        engine?.close()
        engine = null
        lastError = null
        try {
            engine = LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(maxTokens)
                    .build(),
            )
        } catch (t: Throwable) {
            Timber.e(t, "Gemma load failed")
            lastError = t.message ?: t.javaClass.simpleName
            throw t
        }
    }

    fun isLoaded(): Boolean = engine != null
    fun isInstalled(): Boolean = catalogProvider.get().isInstalled(ModelKind.Gemma)
    fun isReady(): Boolean = isLoaded() || isInstalled()

    fun lastLoadError(): String? = lastError

    override suspend fun generate(prompt: String, options: GenOptions): String =
        withContext(Dispatchers.Default) {
            ensureLoaded()
            val e = engine ?: error(lastError ?: "Gemma model not loaded")
            e.generateResponse(prompt)
        }

    /**
     * Force-loads Gemma if the .task file is installed but not yet loaded in
     * memory. Throws the load exception on failure so callers (e.g. a "Verify
     * model" button) can surface it.
     */
    fun ensureLoaded() {
        if (engine != null) return
        val file = catalogProvider.get().fileOf(ModelKind.Gemma)
        check(file.exists()) { "Gemma .task file not installed." }
        load(file)
    }

    /** Round-trip a small prompt to confirm the engine is alive. Used by Settings. */
    suspend fun verify(): String = withContext(Dispatchers.Default) {
        ensureLoaded()
        val e = engine ?: error(lastError ?: "Gemma model not loaded")
        e.generateResponse("Reply with exactly one short sentence: what are you?")
    }
}
