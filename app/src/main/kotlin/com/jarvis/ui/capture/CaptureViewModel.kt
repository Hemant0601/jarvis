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

data class CaptureUiState(
    val recording: Boolean = false,
    val amplitude: Float = 0f,
    val partialTranscript: String = "",
    val draft: String = "",
    val committed: Boolean = false,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val recorder: AudioRecorder,
    private val transcriber: Transcriber,
    private val ingest: IngestPipeline,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    fun onDraftChanged(v: String) = _state.update { it.copy(draft = v) }

    fun toggleRecording() = viewModelScope.launch {
        if (_state.value.recording) {
            val audio = recorder.stop()
            _state.update { it.copy(recording = false) }
            val text = transcriber.transcribe(audio)
            _state.update { it.copy(partialTranscript = "", draft = (it.draft + "\n" + text).trim()) }
        } else {
            _state.update { it.copy(recording = true, partialTranscript = "") }
            recorder.start(
                onAmplitude = { amp -> _state.update { s -> s.copy(amplitude = amp) } },
                onPartial = { p -> _state.update { s -> s.copy(partialTranscript = p) } },
            )
        }
    }

    fun commitDraft() = viewModelScope.launch {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return@launch
        ingest.ingestText(text)
        _state.update { it.copy(draft = "", committed = true) }
    }
}
