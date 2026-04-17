package com.jarvis.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Thin wrapper around Android's built-in [SpeechRecognizer]. No native libs to
 * ship — uses whatever speech service is on the device (Google Speech Services
 * is on every Android phone and also works offline once the user downloads the
 * language pack).
 *
 * Lifecycle:
 *   UI → start(onAmplitude, onPartial, onFinal, onError)
 *   User speaks → onAmplitude / onPartial called repeatedly
 *   Silence detected → onFinal called with the complete transcript
 *   UI → stop() to cancel early
 */
@Singleton
class SpeechRecognitionClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun start(
        onAmplitude: (Float) -> Unit,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) = withContext(Dispatchers.Main) {
        if (!hasMicPermission()) {
            onError("Microphone permission not granted.")
            return@withContext
        }
        if (!isAvailable()) {
            onError("Speech recognition service unavailable on this device.")
            return@withContext
        }

        recognizer?.destroy()
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { /* UI pulse handles this */ }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB ranges roughly from -2 (silent) to +10 (loud). Map to 0..1.
                val normalised = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                onAmplitude(normalised)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val msg = describeError(error)
                // ERROR_NO_MATCH / ERROR_SPEECH_TIMEOUT aren't really errors —
                // the user just didn't say anything. Silently ignore.
                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    Timber.w("SpeechRecognizer error: $msg")
                    onError(msg)
                }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                onFinal(text)
            }
            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { rec.startListening(intent) }
            .onFailure { onError(it.message ?: it.javaClass.simpleName) }
    }

    suspend fun stop() = withContext(Dispatchers.Main) {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording failed."
        SpeechRecognizer.ERROR_CLIENT -> "Recognition client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted."
        SpeechRecognizer.ERROR_NETWORK -> "Network error — connect and try again, or install an offline voice pack."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser is busy."
        SpeechRecognizer.ERROR_SERVER -> "Speech server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timed out waiting for speech."
        else -> "Voice error ($code)."
    }
}
