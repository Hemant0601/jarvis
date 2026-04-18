package com.jarvis.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.audio.SpeechRecognitionClient
import com.jarvis.audio.TtsClient
import com.jarvis.graph.GraphRagRetriever
import com.jarvis.graph.IngestPipeline
import com.jarvis.graph.LlmRouter
import com.jarvis.llm.LlmSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val fromUser: Boolean,
    val text: String,
    val memoryCites: List<String> = emptyList(),
)

data class ChatUiState(
    val title: String = "Jarvis",
    val messages: List<ChatMessageUi> = emptyList(),
    val input: String = "",
    val useCloud: Boolean = false,
    val thinking: Boolean = false,
    /** In live-voice mode: auto-send user utterances, TTS each reply, auto-relisten. */
    val liveMode: Boolean = false,
    /** Microphone currently open and capturing. */
    val listening: Boolean = false,
    /** Jarvis currently speaking the reply aloud. */
    val speaking: Boolean = false,
    val voiceAmplitude: Float = 0f,
    val voicePartial: String = "",
    val errorMessage: String? = null,
    val focusNodeId: String? = null,
    /** True if no on-device Gemma AND no OpenRouter key — triggers the setup card. */
    val needsSetup: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val retriever: GraphRagRetriever,
    private val llm: LlmRouter,
    private val ingest: IngestPipeline,
    private val speech: SpeechRecognitionClient,
    private val tts: TtsClient,
    private val llmSettings: LlmSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init { refreshSetupState() }

    fun refreshSetupState() {
        _state.update { it.copy(needsSetup = !llm.hasModel()) }
    }

    fun onInputChanged(v: String) = _state.update { it.copy(input = v, errorMessage = null) }
    fun setUseCloud(enabled: Boolean) = _state.update { it.copy(useCloud = enabled) }

    fun seedFromNode(nodeId: String) = viewModelScope.launch {
        val node = retriever.describeNode(nodeId) ?: return@launch
        _state.update { it.copy(title = node.label, focusNodeId = nodeId) }
    }

    // ---- Text / tap-send path -----------------------------------------------

    fun send() = viewModelScope.launch {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return@launch
        if (handleSlashCommand(text)) return@launch
        respondTo(text)
    }

    /**
     * Intercepts /-prefixed commands without ingesting them as memories.
     * Returns true if handled so send() short-circuits.
     */
    private fun handleSlashCommand(text: String): Boolean {
        val lower = text.lowercase()
        when {
            lower == "/help" || lower == "/commands" -> {
                appendSystem(
                    text,
                    "Commands:\n" +
                        "• /persona <instructions> — tell me how to behave from now on\n" +
                        "• /persona clear — reset to default persona\n" +
                        "• /persona show — print the current persona",
                )
                return true
            }
            lower.startsWith("/persona ") -> {
                val arg = text.substringAfter("/persona ").trim()
                when (arg.lowercase()) {
                    "clear", "reset", "default" -> {
                        llmSettings.setPersona("")
                        appendSystem(text, "Persona cleared. Back to the default Jarvis behaviour.")
                    }
                    "show", "what", "?" -> {
                        val current = llmSettings.persona()
                        val body = if (current.isBlank()) "No custom persona set." else "Current persona:\n$current"
                        appendSystem(text, body)
                    }
                    else -> {
                        llmSettings.setPersona(arg)
                        appendSystem(text, "Got it — from now on I'll follow this: \"${arg.take(160)}\".")
                    }
                }
                return true
            }
            lower == "/persona" -> {
                appendSystem(text, "Usage: /persona <your instructions>  (or: /persona clear, /persona show)")
                return true
            }
        }
        return false
    }

    private fun appendSystem(userText: String, reply: String) {
        _state.update {
            it.copy(
                input = "",
                messages = it.messages +
                    ChatMessageUi(fromUser = true, text = userText) +
                    ChatMessageUi(fromUser = false, text = reply),
            )
        }
    }

    private suspend fun respondTo(text: String) {
        _state.update {
            it.copy(
                input = "",
                voicePartial = "",
                thinking = true,
                errorMessage = null,
                messages = it.messages + ChatMessageUi(fromUser = true, text = text),
            )
        }

        val newNodeId = runCatching { ingest.ingestText(text) }
            .onFailure { Timber.w(it, "ingest failed") }
            .getOrNull()

        val context = runCatching { retriever.retrieve(text, excludeNodeId = newNodeId) }.getOrNull()
        val answer = runCatching {
            if (context != null) {
                llm.answer(
                    question = text,
                    context = context,
                    preferCloud = _state.value.useCloud,
                )
            } else "Got it."
        }.getOrElse { err ->
            "Saved that. (Couldn't generate a reply: ${err.message})"
        }

        val cites = context?.chunks?.take(3)?.map { it.nodeLabel.take(40) }.orEmpty()
        _state.update {
            it.copy(
                thinking = false,
                messages = it.messages + ChatMessageUi(
                    fromUser = false,
                    text = answer,
                    memoryCites = cites,
                ),
            )
        }

        // In live mode, speak the reply and then relisten.
        if (_state.value.liveMode) speakAndRelisten(answer)
    }

    // ---- Live-voice conversation loop ---------------------------------------

    fun toggleLiveVoice() {
        if (_state.value.liveMode) exitLiveMode() else enterLiveMode()
    }

    private fun enterLiveMode() {
        if (!speech.hasMicPermission()) {
            _state.update { it.copy(errorMessage = "Microphone permission not granted.") }
            return
        }
        if (!speech.isAvailable()) {
            _state.update { it.copy(errorMessage = "Voice recognition unavailable. Install Google Speech Services.") }
            return
        }
        _state.update { it.copy(liveMode = true, errorMessage = null) }
        startListening()
    }

    private fun exitLiveMode() {
        _state.update {
            it.copy(
                liveMode = false,
                listening = false,
                speaking = false,
                voiceAmplitude = 0f,
                voicePartial = "",
            )
        }
        viewModelScope.launch {
            runCatching { speech.stop() }
            runCatching { tts.stop() }
        }
    }

    private fun startListening() {
        if (!_state.value.liveMode) return
        _state.update { it.copy(listening = true, voicePartial = "") }
        viewModelScope.launch {
            speech.start(
                onAmplitude = { amp -> _state.update { it.copy(voiceAmplitude = amp) } },
                onPartial = { p -> _state.update { it.copy(voicePartial = p) } },
                onFinal = { finalText -> onVoiceFinal(finalText) },
                onError = { msg -> onVoiceError(msg) },
            )
        }
    }

    private fun onVoiceFinal(text: String) {
        val clean = text.trim()
        _state.update { it.copy(listening = false, voicePartial = "", voiceAmplitude = 0f) }
        if (clean.isEmpty()) {
            // User didn't say anything — keep the loop alive.
            if (_state.value.liveMode) startListening()
            return
        }
        viewModelScope.launch { respondTo(clean) }
    }

    private fun onVoiceError(msg: String) {
        _state.update {
            it.copy(
                listening = false,
                voiceAmplitude = 0f,
                voicePartial = "",
                errorMessage = if (it.liveMode) null else msg,
            )
        }
        // On transient errors keep the loop alive so the user doesn't have to re-tap.
        if (_state.value.liveMode) startListening()
    }

    private fun speakAndRelisten(text: String) {
        if (!_state.value.liveMode) return
        _state.update { it.copy(speaking = true) }
        viewModelScope.launch {
            runCatching { tts.speak(text) }
            _state.update { it.copy(speaking = false) }
            if (_state.value.liveMode) startListening()
        }
    }

    // ---- Setup -------------------------------------------------------------

    /**
     * Called when the user returns from Settings so the setup card can
     * auto-dismiss if they installed Gemma or typed an OpenRouter key.
     */
    fun dismissSetup() { refreshSetupState() }
}
