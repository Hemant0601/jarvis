package com.jarvis.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.graph.GraphRagRetriever
import com.jarvis.graph.LlmRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val fromUser: Boolean,
    val text: String,
)

data class ChatUiState(
    val title: String = "Chat",
    val messages: List<ChatMessageUi> = emptyList(),
    val input: String = "",
    val useCloud: Boolean = false,
    val thinking: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val retriever: GraphRagRetriever,
    private val llm: LlmRouter,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun onInputChanged(v: String) = _state.update { it.copy(input = v) }
    fun setUseCloud(enabled: Boolean) = _state.update { it.copy(useCloud = enabled) }

    fun seedFromNode(nodeId: String) = viewModelScope.launch {
        val node = retriever.describeNode(nodeId) ?: return@launch
        _state.update { it.copy(title = node.label) }
    }

    fun send() = viewModelScope.launch {
        val q = _state.value.input.trim()
        if (q.isEmpty()) return@launch
        _state.update {
            it.copy(
                input = "",
                thinking = true,
                messages = it.messages + ChatMessageUi(fromUser = true, text = q),
            )
        }
        val context = retriever.retrieve(q)
        val answer = llm.answer(
            question = q,
            context = context,
            preferCloud = _state.value.useCloud,
        )
        _state.update {
            it.copy(
                thinking = false,
                messages = it.messages + ChatMessageUi(fromUser = false, text = answer),
            )
        }
    }
}
