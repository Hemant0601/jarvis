package com.jarvis.audio

import com.jarvis.llm.ModelCatalog
import com.jarvis.llm.ModelKind
import io.github.givimad.whisperjni.WhisperContext
import io.github.givimad.whisperjni.WhisperFullParams
import io.github.givimad.whisperjni.WhisperJNI
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device transcription via whisper.cpp (JNI binding). Loads lazily from the
 * model file managed by ModelCatalog.
 */
@Singleton
class WhisperTranscriber @Inject constructor(
    private val catalog: ModelCatalog,
) : Transcriber {

    private var whisper: WhisperJNI? = null
    private var context: WhisperContext? = null

    private fun ensureLoaded(modelFile: File) {
        if (context != null) return
        val w = WhisperJNI().also { WhisperJNI.loadLibrary() }
        context = w.init(modelFile.toPath())
        whisper = w
    }

    override suspend fun transcribe(clip: RecordedClip): String = withContext(Dispatchers.Default) {
        val model = catalog.fileOf(ModelKind.Whisper)
        if (!model.exists()) return@withContext "[Whisper model not installed]"
        ensureLoaded(model)
        val samples = pcmToFloats(clip.pcmFile)
        val params = WhisperFullParams()
        val w = whisper ?: return@withContext ""
        val ctx = context ?: return@withContext ""
        w.full(ctx, params, samples, samples.size)
        val segmentCount = w.fullNSegments(ctx)
        buildString {
            for (i in 0 until segmentCount) append(w.fullGetSegmentText(ctx, i))
        }.trim()
    }

    private fun pcmToFloats(file: File): FloatArray {
        val bytes = file.readBytes()
        val out = FloatArray(bytes.size / 2)
        for (i in out.indices) {
            val lo = bytes[i * 2].toInt() and 0xff
            val hi = bytes[i * 2 + 1].toInt()
            val s = (hi shl 8) or lo
            out[i] = s.toShort() / 32768f
        }
        return out
    }
}
