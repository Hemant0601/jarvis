package com.jarvis.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.audio.SpeechRecognitionClient
import com.jarvis.graph.GraphRagRetriever
import com.jarvis.graph.IngestPipeline
import com.jarvis.graph.LlmRouter
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
    val listening: Boolean = false,
    val voiceAmplitude: Float = 0f,
    val voicePartial: String = "",
    val errorMessage: String? = null,
    val focusNodeId: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val retriever: GraphRagRetriever,
    private val llm: LlmRouter,
    private val ingest: IngestPipeline,
    private val speech: SpeechRecognitionClient,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun onInputChanged(v: String) = _state.update { it.copy(input = v, errorMessage = null) }
    fun setUseCloud(enabled: Boolean) = _state.update { it.copy(useCloud = enabled) }

    fun seedFromNode(nodeId: String) = viewModelScope.launch {
        val node = retriever.describeNode(nodeId) ?: return@launch
        _state.update { it.copy(title = node.label, focusNodeId = nodeId) }
    }

    fun send() = viewModelScope.launch {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return@launch

        // Stop any live-voice session before committing.
        if (_state.value.listening) stopLiveVoice()

        _state.update {
            it.copy(
                input = "",
                voicePartial = "",
                thinking = true,
                errorMessage = null,
                messages = it.messages + ChatMessageUi(fromUser = true, text = text),
            )
        }

        // Every user message is a memory — ingest first so retrieval can see it.
        runCatching { ingest.ingestText(text) }
            .onFailure { Timber.w(it, "ingest failed for user message") }

        val context = runCatching { retriever.retrieve(text) }.getOrNull()
        val answer = runCatching {
            if (context != null) {
                llm.answer(
                    question = text,
                    context = context,
                    preferCloud = _state.value.useCloud,
                )
            } else {
                "Saved that as a memory."
            }
        }.getOrElse { err ->
            "Saved that as a memory. (Couldn't generate a reply: ${err.message})"
        }

        val cites = context?.chunks?.take(3)?.map { it.nodeLabel }.orEmpty()
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
    }

    fun toggleLiveVoice() {
        if (_state.value.listening) viewModelScope.launch { stopLiveVoice() }
        else startLiveVoice()
    }

    private fun startLiveVoice() {
        if (!speech.hasMicPermission()) {
            _state.update { it.copy(errorMessage = "Microphone permission not granted.") }
            return
        }
        if (!speech.isAvailable()) {
            _state.update { it.copy(errorMessage = "Voice recognition unavailable on this device. Install Google Speech Services.") }
            return
        }
        _state.update { it.copy(listening = true, voicePartial = "", errorMessage = null) }
        viewModelScope.launch {
            speech.start(
                onAmplitude = { amp -> _state.update { it.copy(voiceAmplitude = amp) } },
                onPartial = { partial ->
                    _state.update { it.copy(voicePartial = partial) }
                },
                onFinal = { finalText ->
                    _state.update {
                        it.copy(
                            listening = false,
                            voicePartial = "",
                            voiceAmplitude = 0f,
                            input = if (it.input.isBlank()) finalText else "${it.input} $finalText".trim(),
                        )
                    }
                },
                onError = { msg ->
                    _state.update {
                        it.copy(
                            listening = false,
                            voiceAmplitude = 0f,
                            voicePartial = "",
                            errorMessage = msg,
                        )
                    }
                },
            )
        }
    }

    private suspend fun stopLiveVoice() {
        speech.stop()
        _state.update { it.copy(listening = false, voiceAmplitude = 0f) }
    }
}
