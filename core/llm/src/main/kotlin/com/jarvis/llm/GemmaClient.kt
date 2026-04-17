package com.jarvis.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps MediaPipe's on-device LLM inference engine. The .task model file is downloaded
 * by ModelCatalog and loaded lazily on first use.
 *
 * Kept deliberately sync-only for v1; streaming can be added later once the MediaPipe
 * ProgressListener API stabilises across versions.
 */
@Singleton
class GemmaClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlmClient {

    override val id: String = "gemma-3-on-device"

    @Volatile private var engine: LlmInference? = null

    fun load(modelFile: File, maxTokens: Int = 1024) {
        engine?.close()
        engine = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(maxTokens)
                .build(),
        )
    }

    fun isLoaded(): Boolean = engine != null

    override suspend fun generate(prompt: String, options: GenOptions): String =
        withContext(Dispatchers.Default) {
            val e = engine ?: error("Gemma model not loaded")
            e.generateResponse(prompt)
        }
}
