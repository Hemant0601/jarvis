package com.jarvis.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.audio.AudioRecorder
import com.jarvis.audio.Transcriber
import com.jarvis.graph.IngestPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class CaptureUiState(
    val recording: Boolean = false,
    val amplitude: Float = 0f,
    val partialTranscript: String = "",
    val draft: String = "",
    val committed: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val recorder: AudioRecorder,
    private val transcriber: Transcriber,
    private val ingest: IngestPipeline,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    fun onDraftChanged(v: String) = _state.update { it.copy(draft = v, errorMessage = null) }

    fun toggleRecording() = viewModelScope.launch {
        runCatching {
            if (_state.value.recording) {
                val audio = recorder.stop()
                _state.update { it.copy(recording = false) }
                val text = transcriber.transcribe(audio)
                _state.update {
                    it.copy(
                        partialTranscript = "",
                        draft = (it.draft + "\n" + text).trim(),
                    )
                }
            } else {
                _state.update { it.copy(recording = true, partialTranscript = "", errorMessage = null) }
                recorder.start(
                    onAmplitude = { amp -> _state.update { s -> s.copy(amplitude = amp) } },
                    onPartial = { p -> _state.update { s -> s.copy(partialTranscript = p) } },
                    onError = { msg ->
                        _state.update { s ->
                            s.copy(
                                recording = false,
                                amplitude = 0f,
                                errorMessage = msg,
                            )
                        }
                    },
                )
            }
        }.onFailure { err ->
            Timber.e(err, "toggleRecording")
            _state.update {
                it.copy(
                    recording = false,
                    amplitude = 0f,
                    errorMessage = err.message ?: err.javaClass.simpleName,
                )
            }
        }
    }

    fun commitDraft() = viewModelScope.launch {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return@launch
        runCatching { ingest.ingestText(text) }
            .onFailure { err ->
                _state.update { it.copy(errorMessage = "Save failed: ${err.message}") }
                return@launch
            }
        _state.update { it.copy(draft = "", committed = true) }
    }
}
