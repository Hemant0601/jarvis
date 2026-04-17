package com.jarvis.embed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ONNX Runtime-backed embedding service. Expects a SentencePiece/WordPiece tokenizer
 * bundled with the model file. The model should produce a pooled sentence embedding;
 * all-MiniLM-L6-v2 (384d) is a solid default; EmbeddingGemma (768d) is higher quality
 * but larger.
 *
 * The tokenizer implementation is intentionally pluggable via [Tokenizer]: concrete
 * impls live alongside the downloaded model file and are selected by modelId.
 */
@Singleton
class OnnxEmbeddingService @Inject constructor(
    @ApplicationContext private val context: Context,
) : EmbeddingService {

    @Volatile private var session: OrtSession? = null
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var tokenizer: Tokenizer? = null
    @Volatile private var modelName: String = "uninitialised"
    @Volatile private var dim: Int = 384

    override fun modelId(): String = modelName
    override fun dim(): Int = dim

    fun load(modelFile: File, tokenizer: Tokenizer, name: String, dim: Int) {
        this.env = OrtEnvironment.getEnvironment()
        this.session = this.env!!.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        this.tokenizer = tokenizer
        this.modelName = name
        this.dim = dim
    }

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val s = session ?: error("Embedding model not loaded")
        val env = env ?: error("ONNX env missing")
        val tok = tokenizer ?: error("Tokenizer missing")
        val encoded = tok.encode(text, maxLen = 256)

        val ids = LongBuffer.wrap(encoded.ids.map { it.toLong() }.toLongArray())
        val mask = LongBuffer.wrap(encoded.attentionMask.map { it.toLong() }.toLongArray())
        val shape = longArrayOf(1, encoded.ids.size.toLong())

        val inputs = mutableMapOf<String, OnnxTensor>(
            "input_ids" to OnnxTensor.createTensor(env, ids, shape),
            "attention_mask" to OnnxTensor.createTensor(env, mask, shape),
        )
        encoded.typeIds?.let {
            val typeBuf = LongBuffer.wrap(it.map(Int::toLong).toLongArray())
            inputs["token_type_ids"] = OnnxTensor.createTensor(env, typeBuf, shape)
        }

        val out = s.run(inputs)
        @Suppress("UNCHECKED_CAST")
        val arr = out[0].value as Array<FloatArray>
        out.close()
        inputs.values.forEach { it.close() }
        Vectors.normalise(arr[0])
    }
}
