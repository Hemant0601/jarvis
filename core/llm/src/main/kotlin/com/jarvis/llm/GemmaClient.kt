package com.jarvis.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Wraps MediaPipe's on-device LLM inference engine. The .task model file is downloaded
 * by ModelCatalog and loaded lazily on first use.
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

    override fun stream(prompt: String, options: GenOptions): Flow<String> = callbackFlow {
        val e = engine ?: error("Gemma model not loaded")
        e.generateResponseAsync(prompt) { partial, done ->
            trySend(partial)
            if (done) close()
        }
        awaitClose { /* no-op: MediaPipe manages its own lifecycle */ }
    }
}
