package com.jarvis.ui.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Primary "brain" view — a glowing, neural-network-style visualization of the
 * graph RAG store.
 *
 *   Pinch to zoom, drag to pan.
 *   Tap a node to select it (shows a detail card at the bottom + highlights its
 *     edges). Tap again on the detail card to open a chat scoped to the node.
 *   Tap empty space to deselect.
 *   Filter chips at the top toggle categories.
 */
@Composable
fun GraphScreen(
    onOpenNode: (String) -> Unit,
    viewModel: GraphViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(Offset(0f, 0f)) }

    // Auto-center when data first loads.
    LaunchedEffect(state.nodes.size, canvasSize) {
        if (state.nodes.isNotEmpty() && canvasSize.x > 0f && offset == Offset.Zero) {
            offset = Offset(canvasSize.x / 2f, canvasSize.y / 2f)
        }
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "nodePulse",
    )

    val visibleNodes by remember(state.nodes, state.activeCategories) {
        derivedStateOf {
            if (state.activeCategories.isEmpty()) state.nodes
            else state.nodes.filter { it.category in state.activeCategories }
        }
    }
    val visibleIds by remember(visibleNodes) { derivedStateOf { visibleNodes.map { it.id }.toSet() } }
    val visibleEdges by remember(state.edges, visibleIds) {
        derivedStateOf { state.edges.filter { it.fromId in visibleIds && it.toId in visibleIds } }
    }
    val selectedNode by remember(selectedId, visibleNodes) {
        derivedStateOf { visibleNodes.firstOrNull { it.id == selectedId } }
    }
    val selectedEdgeIds by remember(selectedId, visibleEdges) {
        derivedStateOf {
            if (selectedId == null) emptySet<String>()
            else visibleEdges.mapNotNull {
                if (it.fromId == selectedId) it.toId
                else if (it.toId == selectedId) it.fromId
                else null
            }.toSet()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Radial gradient backdrop — makes the "brain" float against space.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1A2540), Color(0xFF060912)),
                            radius = 1200f,
                        ),
                    ),
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.3f, 4f)
                            offset += pan
                        }
                    }
                    .pointerInput(visibleNodes) {
                        detectTapGestures { tap ->
                            val world = (tap - offset) / scale
                            val hit = visibleNodes.firstOrNull {
                                val dx = it.x - world.x
                                val dy = it.y - world.y
                                sqrt(dx * dx + dy * dy) <= (it.radius + 14f)
                            }
                            selectedId = hit?.id
                        }
                    }
            ) {
                canvasSize = Offset(size.width, size.height)

                // Edges
                visibleEdges.forEach { edge ->
                    val from = visibleNodes.firstOrNull { it.id == edge.fromId } ?: return@forEach
                    val to = visibleNodes.firstOrNull { it.id == edge.toId } ?: return@forEach
                    val selected = selectedId != null &&
                        (edge.fromId == selectedId || edge.toId == selectedId)
                    val dimmed = selectedId != null && !selected

                    val start = Offset(from.x, from.y) * scale + offset
                    val end = Offset(to.x, to.y) * scale + offset

                    // Curved edge: cubic bezier with a perpendicular control offset.
                    val mid = (start + end) / 2f
                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val nx = -dy / len
                    val ny = dx / len
                    val curve = 0.16f * len
                    val control = Offset(mid.x + nx * curve, mid.y + ny * curve)

                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        quadraticBezierTo(control.x, control.y, end.x, end.y)
                    }
                    val baseAlpha = 0.10f + 0.55f * edge.weight
                    val alpha = when {
                        selected -> 0.85f
                        dimmed -> baseAlpha * 0.25f
                        else -> baseAlpha
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF6EA8FE).copy(alpha = alpha),
                        style = Stroke(width = (0.8f + edge.weight * 2.5f) * scale),
                    )
                }

                // Nodes
                visibleNodes.forEach { node ->
                    val center = Offset(node.x, node.y) * scale + offset
                    val isSelected = node.id == selectedId
                    val isConnected = node.id in selectedEdgeIds
                    val dimmed = selectedId != null && !isSelected && !isConnected

                    val baseR = node.radius * scale
                    val glowR = baseR * if (isSelected) pulse * 1.8f else 2.1f
                    val alpha = if (dimmed) 0.35f else 1f

                    // Halo / glow
                    drawCircle(
                        color = node.color.copy(alpha = 0.08f * alpha),
                        radius = glowR * 1.6f,
                        center = center,
                    )
                    drawCircle(
                        color = node.color.copy(alpha = 0.18f * alpha),
                        radius = glowR,
                        center = center,
                    )
                    // Core
                    drawCircle(
                        color = node.color.copy(alpha = alpha),
                        radius = baseR,
                        center = center,
                    )
                    // Rim highlight
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * alpha),
                        radius = baseR,
                        center = center,
                        style = Stroke(width = 1.5f),
                    )
                    // Selected ring
                    if (isSelected) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = baseR * pulse * 1.45f,
                            center = center,
                            style = Stroke(width = 2.5f),
                        )
                    }

                    // Label — always show selected/connected; otherwise only when zoomed in.
                    val showLabel = isSelected || isConnected || scale > 0.9f
                    if (showLabel) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(
                                    (alpha * 235).toInt(), 235, 238, 246,
                                )
                                textSize = 28f * min(max(scale, 0.9f), 1.6f)
                                isAntiAlias = true
                                setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
                            }
                            drawText(
                                node.label,
                                center.x + baseR + 8f,
                                center.y + 9f,
                                paint,
                            )
                        }
                    }
                }
            }

            // Filter chips (top)
            LazyRow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                items(state.availableCategories) { cat ->
                    val selected = cat in state.activeCategories
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleCategory(cat) },
                        label = { Text(cat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // Stats pill + re-center FAB
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Text(
                        "${visibleNodes.size} nodes · ${visibleEdges.size} edges",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    scale = 1f
                    offset = Offset(canvasSize.x / 2f, canvasSize.y / 2f)
                    selectedId = null
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Re-center")
            }

            // Selected node detail card (bottom sheet-ish)
            AnimatedVisibility(
                visible = selectedNode != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
            ) {
                selectedNode?.let { node ->
                    NodeDetailCard(
                        node = node,
                        connections = selectedEdgeIds.size,
                        onOpenChat = { onOpenNode(node.id) },
                        onDismiss = { selectedId = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeDetailCard(
    node: GraphNodeUi,
    connections: Int,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A2233).copy(alpha = 0.95f),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(node.color),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    node.category,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "$connections ${if (connections == 1) "link" else "links"}",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                node.label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenChat,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chat about this")
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Close")
                }
            }
        }
    }
}
