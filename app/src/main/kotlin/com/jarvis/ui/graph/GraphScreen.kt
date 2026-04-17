package com.jarvis.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max
import kotlin.math.min

/**
 * Primary "brain" view — a neural-network-style visualization of the graph RAG store.
 * Supports pinch/zoom, pan, tap-on-node to drill into a chat/detail screen.
 */
@Composable
fun GraphScreen(
    onOpenNode: (String) -> Unit,
    viewModel: GraphViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.3f, 4f)
                            offset += pan
                        }
                    }
                    .pointerInput(state.nodes) {
                        detectTapGestures { tap ->
                            val world = (tap - offset) / scale
                            val hit = state.nodes.firstOrNull {
                                val dx = it.x - world.x
                                val dy = it.y - world.y
                                dx * dx + dy * dy <= (it.radius * it.radius)
                            }
                            if (hit != null) onOpenNode(hit.id)
                        }
                    }
            ) {
                // Edges — opacity scales with strength, like synapse weights.
                state.edges.forEach { edge ->
                    val from = state.nodes.firstOrNull { it.id == edge.fromId } ?: return@forEach
                    val to = state.nodes.firstOrNull { it.id == edge.toId } ?: return@forEach
                    drawLine(
                        color = Color(0xFF6EA8FE).copy(alpha = 0.15f + 0.65f * edge.weight),
                        start = Offset(from.x, from.y) * scale + offset,
                        end = Offset(to.x, to.y) * scale + offset,
                        strokeWidth = (1f + edge.weight * 3f) * scale
                    )
                }
                // Nodes — size/color by category, pulse by recency.
                state.nodes.forEach { node ->
                    val center = Offset(node.x, node.y) * scale + offset
                    drawCircle(
                        color = node.color.copy(alpha = 0.18f),
                        radius = (node.radius + 8f) * scale,
                        center = center
                    )
                    drawCircle(
                        color = node.color,
                        radius = node.radius * scale,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = node.radius * scale,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                    // Label (only when zoomed in enough).
                    if (scale > 0.8f) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 28f * min(max(scale, 0.8f), 2f)
                                isAntiAlias = true
                            }
                            drawText(
                                node.label,
                                center.x + node.radius * scale + 6f,
                                center.y + 8f,
                                paint
                            )
                        }
                    }
                }
            }

            // Filter chips (node categories: Person, Topic, Note, Event, ...)
            LazyRow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.availableCategories) { cat ->
                    val selected = cat in state.activeCategories
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleCategory(cat) },
                        label = { Text(cat, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }

            // Stats pill (nodes/edges count)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    "${state.nodes.size} nodes · ${state.edges.size} edges",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
