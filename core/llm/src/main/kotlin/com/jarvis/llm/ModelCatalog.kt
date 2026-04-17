package com.jarvis.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

enum class ModelKind(
    val displayName: String,
    val filename: String,
    val downloadUrl: String,
    val approxSizeMb: Int,
) {
    Gemma(
        displayName = "Gemma 3 1B (INT4)",
        filename = "gemma3-1b-it-int4.task",
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/llm/gemma3-1b-it-int4.task",
        approxSizeMb = 555,
    ),
    Whisper(
        displayName = "Whisper small (multilingual)",
        filename = "ggml-small.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
        approxSizeMb = 466,
    ),
    Embedding(
        displayName = "all-MiniLM-L6-v2 (384d)",
        filename = "all-minilm-l6-v2.onnx",
        downloadUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
        approxSizeMb = 90,
    ),
}

/**
 * Manages download + lifecycle of all on-device model files. Files land in the
 * app's private files dir (not visible to other apps). The UI reads progress via
 * callbacks wired through the ViewModel.
 */
@Singleton
class ModelCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val settings: LlmSettings,
) {
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    fun fileOf(kind: ModelKind): File = File(modelsDir, kind.filename)

    fun statusOf(kind: ModelKind): String {
        val f = fileOf(kind)
        if (!f.exists()) return "Not installed · ${kind.approxSizeMb} MB"
        val mb = f.length() / (1024 * 1024)
        return "Installed · $mb MB"
    }

    fun download(kind: ModelKind, scope: CoroutineScope, onProgress: (Float) -> Unit): Job =
        scope.launch {
            val req = Request.Builder().url(kind.downloadUrl).build()
            http.newCall(req).execute().use { resp ->
                val total = resp.body?.contentLength()?.takeIf { it > 0 } ?: kind.approxSizeMb * 1024L * 1024L
                val tmp = File(modelsDir, "${kind.filename}.part")
                tmp.outputStream().use { out ->
                    resp.body!!.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                tmp.renameTo(fileOf(kind))
                onProgress(1f)
            }
        }

    fun remove(kind: ModelKind) { fileOf(kind).delete() }

    fun openRouterKey(): String = settings.openRouterKey()
    fun setOpenRouterKey(v: String) = settings.setOpenRouterKey(v)
    fun biometricEnabled(): Boolean = settings.biometricEnabled()
    fun setBiometricEnabled(v: Boolean) = settings.setBiometricEnabled(v)
}
