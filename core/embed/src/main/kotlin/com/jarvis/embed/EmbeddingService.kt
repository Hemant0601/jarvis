package com.jarvis.embed

interface EmbeddingService {
    /** Returns a unit-normalised embedding for the given text. */
    suspend fun embed(text: String): FloatArray

    /** Batched form; default impl dispatches one by one. */
    suspend fun embedBatch(texts: List<String>): List<FloatArray> = texts.map { embed(it) }

    /** Opaque string that uniquely identifies the current embedding model. Stored with
     * each embedding so we can detect and migrate when the model changes. */
    fun modelId(): String

    /** Dimensionality of the embeddings produced. */
    fun dim(): Int
}
