package com.jarvis.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Thin wrapper around Android's built-in [TextToSpeech]. Used so Jarvis can
 * speak responses back in live voice mode. Uses the device's installed TTS
 * engine (Google TTS on Play-Services-capable phones, Samsung TTS on Samsung,
 * etc.); no extra downloads required.
 */
@Singleton
class TtsClient @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val ready = AtomicBoolean(false)
    @Volatile private var pending: CancellableContinuation<Unit>? = null

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready.set(true)
            runCatching { tts.language = Locale.getDefault() }
            runCatching { tts.setSpeechRate(1.0f) }
        } else {
            Timber.w("TTS init failed: status=$status")
        }
    }.also { engine ->
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { resume() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { resume() }
            override fun onError(utteranceId: String?, errorCode: Int) { resume() }
        })
    }

    /** Suspends until the utterance finishes (or fails). Safe to call if TTS isn't ready. */
    suspend fun speak(text: String) = withContext(Dispatchers.Main) {
        if (!ready.get() || text.isBlank()) return@withContext
        pending?.let { runCatching { it.resumeWith(Result.success(Unit)) } }
        suspendCancellableCoroutine<Unit> { cont ->
            pending = cont
            val id = UUID.randomUUID().toString()
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id) }
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
            if (result != TextToSpeech.SUCCESS) resume()
            cont.invokeOnCancellation { runCatching { tts.stop() } }
        }
    }

    fun stop() {
        runCatching { tts.stop() }
        resume()
    }

    private fun resume() {
        val cont = pending ?: return
        pending = null
        runCatching { cont.resumeWith(Result.success(Unit)) }
    }
}
