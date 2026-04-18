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
     * Gemma 3 1B IT INT4 (.task) — the on-device model officially shipped for
     * MediaPipe tasks-genai. Google hosts the canonical .task file on their own
     * CDN. Gemma 4 E2B's web-format .task doesn't load with tasks-genai 0.10.21
     * on Android (MediaPipeTasksStatus=104, "Unable to open zip archive") so we
     * stay on Gemma 3 1B INT4 here; it's 555 MB instead of 2 GB, which also
     * downloads much faster.
     */
    Gemma(
        displayName = "Gemma 3 1B INT4 (on-device)",
        filename = "gemma3-1b-it-int4.task",
        downloadUrl = "https://storage.googleapis.com/mediapipe-models/llm/gemma3-1b-it-int4.task",
        approxSizeMb = 555,
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

    init {
        // Clean up any stale model files from older releases (e.g. the old
        // Gemma-4 .task that doesn't load with current MediaPipe).
        val expected = ModelKind.entries.map { it.filename }.toSet()
        modelsDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name !in expected) {
                Timber.i("Removing stale model file: ${f.name}")
                f.delete()
            }
        }
    }

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
