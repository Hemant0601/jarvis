package com.jarvis.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

data class RecordedClip(val pcmFile: File, val sampleRate: Int = 16_000)

/**
 * 16 kHz / mono / PCM16 recorder. Writes raw PCM to the app cache while
 * publishing amplitude and any error text to the UI. All construction errors
 * (permission missing, unsupported source, bogus buffer size) are caught and
 * reported through [onError] — the app never crashes from here.
 */
@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var record: AudioRecord? = null
    private var job: Job? = null
    private var output: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun start(
        onAmplitude: (Float) -> Unit,
        onPartial: (String) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        if (!hasMicPermission()) {
            onError("Microphone permission not granted.")
            return
        }
        runCatching {
            val sampleRate = 16_000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            require(minBuf > 0) { "Invalid min buffer size: $minBuf" }

            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4,
            )
            check(rec.state == AudioRecord.STATE_INITIALIZED) {
                "AudioRecord not initialised (state=${rec.state})"
            }
            val file = File(context.cacheDir, "capture-${System.currentTimeMillis()}.pcm")
            output = file
            rec.startRecording()
            record = rec

            job = scope.launch {
                runCatching {
                    val buf = ShortArray(minBuf)
                    file.outputStream().use { out ->
                        while (isActive) {
                            val n = rec.read(buf, 0, buf.size)
                            if (n < 0) break
                            if (n == 0) continue
                            var peak = 0
                            for (i in 0 until n) peak = max(peak, abs(buf[i].toInt()))
                            onAmplitude(min(1f, peak / 8000f))
                            val bytes = ByteArray(n * 2)
                            for (i in 0 until n) {
                                bytes[i * 2] = (buf[i].toInt() and 0xff).toByte()
                                bytes[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xff).toByte()
                            }
                            out.write(bytes)
                        }
                    }
                }.onFailure { err ->
                    Timber.e(err, "Recording loop failed")
                    onError(err.message ?: err.javaClass.simpleName)
                }
            }
        }.onFailure { err ->
            Timber.e(err, "Recorder start failed")
            onError(err.message ?: err.javaClass.simpleName)
        }
    }

    suspend fun stop(): RecordedClip {
        job?.cancel()
        job = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        return RecordedClip(output ?: File(context.cacheDir, "empty.pcm").apply { writeBytes(ByteArray(0)) })
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
