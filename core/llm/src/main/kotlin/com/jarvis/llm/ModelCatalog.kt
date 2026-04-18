package com.jarvis.llm

import android.content.Context
import android.net.Uri
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

/**
 * @property displayName  Human-facing name in Settings.
 * @property filename     On-disk filename we store the model under.
 * @property approxSizeMb Used for progress fallback when Content-Length is unknown.
 * @property downloadUrl  Direct URL we can `GET` without authentication. null when
 *                        the model is gated (Gemma) — in that case the app opens
 *                        [landingPageUrl] in the browser instead.
 * @property landingPageUrl  Browser-friendly page for the user to land on, accept
 *                        the licence, and download the model manually. Always set.
 */
enum class ModelKind(
    val displayName: String,
    val filename: String,
    val approxSizeMb: Int,
    val downloadUrl: String?,
    val landingPageUrl: String,
) {
    /**
     * Gemma 3 1B IT INT4 (.task). Weights are gated behind Google's terms on
     * HuggingFace (and on Kaggle), so in-app auto-download returns 401/404.
     * The app opens the HuggingFace page, user accepts terms and downloads the
     * .task manually, then imports it via the file picker.
     */
    Gemma(
        displayName = "Gemma 3 1B INT4 (on-device)",
        filename = "gemma3-1b-it-int4.task",
        approxSizeMb = 555,
        downloadUrl = null,
        landingPageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
    ),
    /**
     * Sentence-Transformers all-MiniLM-L6-v2 ONNX (384-dim embeddings).
     * Public, not gated — direct download works.
     */
    Embedding(
        displayName = "all-MiniLM-L6-v2 (384d)",
        filename = "all-minilm-l6-v2.onnx",
        approxSizeMb = 90,
        downloadUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
        landingPageUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2",
    ),
}

@Singleton
class ModelCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val settings: LlmSettings,
) {
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    init {
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

    /** Kick off an auto-download if the model has a public URL; otherwise reports
     * via [onError] that the caller should open the landing page and import. */
    fun download(
        kind: ModelKind,
        scope: CoroutineScope,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit = {},
    ): Job? {
        val url = kind.downloadUrl ?: run {
            onError("${kind.displayName} is gated. Tap \"Get model\" to open the download page, then \"Import file\" once you've downloaded it.")
            return null
        }
        return scope.launch(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).build()
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
    }

    /**
     * Copy a user-picked file into the app's models dir under [kind]'s canonical
     * filename. Reports 0-1 progress and returns total bytes copied. Errors are
     * surfaced via [onError].
     */
    fun importFrom(
        kind: ModelKind,
        uri: Uri,
        scope: CoroutineScope,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit = {},
    ): Job = scope.launch(Dispatchers.IO) {
        runCatching {
            val total = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull()?.takeIf { it > 0 }
                ?: (kind.approxSizeMb * 1024L * 1024L)

            val tmp = File(modelsDir, "${kind.filename}.part")
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Could not open picked file.")
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
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
            withContext(Dispatchers.Main) { onProgress(1f) }
        }.onFailure { err ->
            Timber.e(err, "Import failed: ${kind.filename}")
            withContext(Dispatchers.Main) { onError(err.message ?: err.javaClass.simpleName) }
        }
    }

    fun remove(kind: ModelKind) { fileOf(kind).delete() }

    fun openRouterKey(): String = settings.openRouterKey()
    fun setOpenRouterKey(v: String) = settings.setOpenRouterKey(v)
    fun biometricEnabled(): Boolean = settings.biometricEnabled()
    fun setBiometricEnabled(v: Boolean) = settings.setBiometricEnabled(v)
}
