package com.jarvis.ui.graph

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.graph.GraphRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GraphNodeUi(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val category: String,
)

data class GraphEdgeUi(
    val fromId: String,
    val toId: String,
    val weight: Float,
)

data class GraphUiState(
    val nodes: List<GraphNodeUi> = emptyList(),
    val edges: List<GraphEdgeUi> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val activeCategories: Set<String> = emptySet(),
    val loading: Boolean = true,
)

@HiltViewModel
class GraphViewModel @Inject constructor(
    private val repo: GraphRepository,
    private val layout: ForceDirectedLayout,
) : ViewModel() {

    private val _state = MutableStateFlow(GraphUiState())
    val state: StateFlow<GraphUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        val snapshot = repo.snapshot()
        val laidOut = layout.layout(snapshot)
        val categories = laidOut.nodes.map { it.category }.distinct()
        _state.update {
            it.copy(
                nodes = laidOut.nodes,
                edges = laidOut.edges,
                availableCategories = categories,
                activeCategories = categories.toSet(),
                loading = false,
            )
        }
    }

    fun toggleCategory(cat: String) {
        _state.update {
            val next = if (cat in it.activeCategories) it.activeCategories - cat else it.activeCategories + cat
            it.copy(
                activeCategories = next,
                nodes = it.nodes.filter { n -> n.category in next }.ifEmpty { it.nodes },
            )
        }
    }
}
