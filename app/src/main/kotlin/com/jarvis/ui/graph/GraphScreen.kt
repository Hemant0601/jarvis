package com.jarvis.ui.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Chat
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
    var centered by remember { mutableStateOf(false) }

    LaunchedEffect(state.nodes.size, canvasSize) {
        if (state.nodes.isNotEmpty() && canvasSize.x > 0f && !centered) {
            offset = Offset(canvasSize.x / 2f, canvasSize.y / 2f)
            centered = true
        }
    }

    val pulsePhase by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "pulse",
    )
    val ringPulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(2200),
            RepeatMode.Reverse,
        ),
        label = "ringPulse",
    )

    val visibleNodes by remember(state.nodes, state.activeCategories) {
        derivedStateOf {
            if (state.activeCategories.isEmpty()) state.nodes
            else state.nodes.filter { it.category in state.activeCategories || it.category == "Root" }
        }
    }
    val visibleIdSet by remember(visibleNodes) { derivedStateOf { visibleNodes.map { it.id }.toSet() } }
    val visibleEdges by remember(state.edges, visibleIdSet) {
        derivedStateOf { state.edges.filter { it.fromId in visibleIdSet && it.toId in visibleIdSet } }
    }
    val nodeById by remember(visibleNodes) {
        derivedStateOf { visibleNodes.associateBy { it.id } }
    }
    val selectedNode by remember(selectedId, visibleNodes) {
        derivedStateOf { nodeById[selectedId] }
    }
    val connectedIds by remember(selectedId, visibleEdges) {
        derivedStateOf {
            if (selectedId == null) emptySet()
            else visibleEdges.mapNotNull {
                when (selectedId) {
                    it.fromId -> it.toId
                    it.toId -> it.fromId
                    else -> null
                }
            }.toSet()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF13213D), Color(0xFF070B16), Color(0xFF020308)),
                    radius = 1400f,
                ),
            ),
    ) {
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

            // Star-field backdrop: deterministic, stable across frames.
            drawStarfield(size.width, size.height)

            // Edges first (underneath nodes).
            visibleEdges.forEach { edge ->
                val from = nodeById[edge.fromId] ?: return@forEach
                val to = nodeById[edge.toId] ?: return@forEach
                val selected = selectedId != null &&
                    (edge.fromId == selectedId || edge.toId == selectedId)
                val dimmed = selectedId != null && !selected

                val start = Offset(from.x, from.y) * scale + offset
                val end = Offset(to.x, to.y) * scale + offset

                // Curved edge via quadratic Bézier.
                val dx = end.x - start.x
                val dy = end.y - start.y
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val nx = -dy / len
                val ny = dx / len
                val curve = 0.14f * len
                val control = Offset(
                    (start.x + end.x) / 2f + nx * curve,
                    (start.y + end.y) / 2f + ny * curve,
                )
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    quadraticBezierTo(control.x, control.y, end.x, end.y)
                }

                val baseAlpha = 0.12f + 0.55f * edge.weight
                val alpha = when {
                    selected -> 0.9f
                    dimmed -> baseAlpha * 0.2f
                    else -> baseAlpha
                }
                drawPath(
                    path = path,
                    color = Color(0xFF6EA8FE).copy(alpha = alpha),
                    style = Stroke(width = (0.8f + edge.weight * 2.2f) * scale),
                )

                // Travelling energy pulse — a small bright dot moving along the edge.
                if (!dimmed) {
                    val t = ((pulsePhase + (from.x + to.x) * 0.00013f) % 1f).coerceIn(0f, 1f)
                    val p = quadraticBezierPoint(start, control, end, t)
                    drawCircle(
                        color = Color(0xFF9DB8FF).copy(alpha = 0.9f * if (selected) 1f else 0.6f),
                        radius = 2.4f + scale,
                        center = p,
                    )
                }
            }

            // Nodes.
            visibleNodes.forEach { node ->
                val center = Offset(node.x, node.y) * scale + offset
                val isSelected = node.id == selectedId
                val isConnected = node.id in connectedIds
                val isRoot = node.category == "Root"
                val dimmed = selectedId != null && !isSelected && !isConnected && !isRoot

                val baseR = node.radius * scale
                val alpha = if (dimmed) 0.3f else 1f
                val ringR = baseR * if (isSelected || isRoot) ringPulse * 1.6f else 2.0f

                // Outer halo
                drawCircle(
                    color = node.color.copy(alpha = 0.06f * alpha),
                    radius = ringR * 2f,
                    center = center,
                )
                drawCircle(
                    color = node.color.copy(alpha = 0.18f * alpha),
                    radius = ringR,
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
                    color = Color.White.copy(alpha = if (isRoot) 0.9f else 0.55f * alpha),
                    radius = baseR,
                    center = center,
                    style = Stroke(width = if (isRoot) 2.5f else 1.5f),
                )
                // Selected ring
                if (isSelected) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = baseR * ringPulse * 1.4f,
                        center = center,
                        style = Stroke(width = 2.5f),
                    )
                }
                // Root always gets a label (anchor).
                val showLabel = isRoot || isSelected || isConnected || scale > 0.9f
                if (showLabel) {
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (alpha * if (isRoot) 255 else 235).toInt(),
                                235, 238, 246,
                            )
                            textSize = (if (isRoot) 34f else 26f) * min(max(scale, 0.9f), 1.6f)
                            isAntiAlias = true
                            isFakeBoldText = isRoot
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
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            items(state.availableCategories.filter { it != "Root" }) { cat ->
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

        // Stats pill
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.06f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                "${visibleNodes.size} nodes · ${visibleEdges.size} edges",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
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

        // Selected node detail card
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
                    connections = connectedIds.size,
                    onOpenChat = { onOpenNode(node.id) },
                    onDismiss = { selectedId = null },
                )
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

// ---- Low-level drawing helpers ----

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStarfield(
    w: Float,
    h: Float,
) {
    val seed = 1337L
    val rng = kotlin.random.Random(seed)
    repeat(80) {
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h
        val a = 0.08f + rng.nextFloat() * 0.25f
        val r = 0.6f + rng.nextFloat() * 1.4f
        drawCircle(color = Color.White.copy(alpha = a), radius = r, center = Offset(x, y))
    }
}

private fun quadraticBezierPoint(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    val x = u * u * a.x + 2f * u * t * c.x + t * t * b.x
    val y = u * u * a.y + 2f * u * t * c.y + t * t * b.y
    return Offset(x, y)
}
