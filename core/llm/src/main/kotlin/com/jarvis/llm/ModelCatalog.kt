package com.jarvis.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

enum class ModelKind(
    val displayName: String,
    val filename: String,
    val downloadUrl: String,
    val approxSizeMb: Int,
) {
    /**
     * Gemma 4 E2B (Apr 2026, Apache 2.0). Effective-2B model, ~2 GB .task for
     * MediaPipe LlmInference. The upstream repo keeps both a `.litertlm` and a
     * `-web.task` — the .task is the MediaPipe-unified format that tasks-genai
     * can load on Android.
     */
    Gemma(
        displayName = "Gemma 4 E2B (on-device)",
        filename = "gemma-4-e2b-it.task",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task",
        approxSizeMb = 2_000,
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
 * app's private files dir (not visible to other apps).
 */
@Singleton
class ModelCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val settings: LlmSettings,
) {
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    fun fileOf(kind: ModelKind): File = File(modelsDir, kind.filename)
    fun isInstalled(kind: ModelKind): Boolean = fileOf(kind).exists()

    fun statusOf(kind: ModelKind): String {
        val f = fileOf(kind)
        if (!f.exists()) return "Not installed · ~${kind.approxSizeMb} MB"
        val mb = f.length() / (1024 * 1024)
        return "Installed · $mb MB"
    }

    fun download(
        kind: ModelKind,
        scope: CoroutineScope,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit = {},
    ): Job = scope.launch(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(kind.downloadUrl).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val total = resp.body?.contentLength()?.takeIf { it > 0 }
                    ?: (kind.approxSizeMb * 1024L * 1024L)
                val tmp = File(modelsDir, "${kind.filename}.part")
                tmp.outputStream().use { out ->
                    resp.body!!.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            // Throttle UI callbacks — a 2 GB download produces ~32k ticks otherwise.
                            if (read - lastReport >= 1_048_576) {
                                lastReport = read
                                withContext(Dispatchers.Main) {
                                    onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                tmp.renameTo(fileOf(kind))
            }
            withContext(Dispatchers.Main) { onProgress(1f) }
        }.onFailure { err ->
            Timber.e(err, "Download failed: ${kind.filename}")
            withContext(Dispatchers.Main) { onError(err.message ?: err.javaClass.simpleName) }
        }
    }

    fun remove(kind: ModelKind) { fileOf(kind).delete() }

    fun openRouterKey(): String = settings.openRouterKey()
    fun setOpenRouterKey(v: String) = settings.setOpenRouterKey(v)
    fun biometricEnabled(): Boolean = settings.biometricEnabled()
    fun setBiometricEnabled(v: Boolean) = settings.setBiometricEnabled(v)
}
