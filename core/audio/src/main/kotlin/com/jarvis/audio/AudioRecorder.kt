package com.jarvis.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

data class RecordedClip(val pcmFile: File, val sampleRate: Int = 16_000)

/**
 * 16 kHz / mono / PCM16 recorder. Writes a raw .pcm file to the app cache while
 * publishing amplitude (0..1) to the UI. The "partial transcript" hook is stubbed
 * — we keep the design open so we can later plug Whisper's streaming mode once the
 * JNI wrapper supports it.
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
    fun start(onAmplitude: (Float) -> Unit, onPartial: (String) -> Unit) {
        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 4,
        )
        val file = File(context.cacheDir, "capture-${System.currentTimeMillis()}.pcm")
        output = file
        rec.startRecording()
        record = rec

        job = scope.launch {
            val buf = ShortArray(minBuf)
            file.outputStream().use { out ->
                while (isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    // Compute amplitude for UI pulse.
                    var peak = 0
                    for (i in 0 until n) peak = max(peak, abs(buf[i].toInt()))
                    onAmplitude(min(1f, peak / 8000f))
                    // Persist PCM16.
                    val bytes = ByteArray(n * 2)
                    for (i in 0 until n) {
                        bytes[i * 2] = (buf[i].toInt() and 0xff).toByte()
                        bytes[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xff).toByte()
                    }
                    out.write(bytes)
                }
            }
        }
    }

    suspend fun stop(): RecordedClip {
        job?.cancel()
        job = null
        record?.apply { stop(); release() }
        record = null
        return RecordedClip(output ?: error("No clip recorded"))
    }

}
