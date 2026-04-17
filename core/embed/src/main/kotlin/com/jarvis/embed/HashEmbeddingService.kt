package com.jarvis.embed

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

/**
 * Zero-dependency fallback embedder. Uses a deterministic random projection over
 * lowercased word hashes to produce a 384-dim unit vector.
 *
 * This is NOT semantically great, but:
 *   • It needs no model file → the app works the instant you open it.
 *   • Cosine similarity between two texts with shared terms is still meaningful.
 *   • It's stable across runs, so stored embeddings don't rot.
 *
 * Swap in [OnnxEmbeddingService] once the user downloads a proper embedding model.
 */
@Singleton
class HashEmbeddingService @Inject constructor() : EmbeddingService {

    private val dim: Int = 384

    override fun modelId(): String = "hash-fallback-v1-$dim"
    override fun dim(): Int = dim

    override suspend fun embed(text: String): FloatArray {
        val out = FloatArray(dim)
        val tokens = text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return out
        for (tok in tokens) {
            val seed = stableHash(tok)
            // Two projections per token: reduces collisions, keeps it cheap.
            for (k in 0 until 2) {
                val idx = ((seed + k * 31L).mod(dim.toLong())).toInt()
                val angle = ((seed xor (k * 2654435761L)).toInt()).toFloat() * 1e-8f
                out[idx] += cos(angle)
                out[(idx + 17) % dim] += sin(angle) * 0.5f
            }
        }
        return Vectors.normalise(out)
    }

    private fun stableHash(s: String): Long {
        var h = 1125899906842597L // a large prime
        for (c in s) h = 31L * h + c.code
        return h
    }
}
