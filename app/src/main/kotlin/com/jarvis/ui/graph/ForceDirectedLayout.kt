package com.jarvis.ui.graph

import androidx.compose.ui.graphics.Color
import com.jarvis.graph.GraphSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlin.random.Random

data class LaidOutGraph(val nodes: List<GraphNodeUi>, val edges: List<GraphEdgeUi>)

/**
 * Lightweight Fruchterman-Reingold style force-directed layout. Good enough for a few
 * hundred nodes in a mobile canvas. Deterministic seed so the brain looks consistent
 * across launches until data changes.
 */
@Singleton
class ForceDirectedLayout @Inject constructor() {

    private val categoryPalette = mapOf(
        "Person" to Color(0xFFFF7AB6),
        "Topic" to Color(0xFF6EA8FE),
        "Note" to Color(0xFFA5F3B6),
        "Event" to Color(0xFFFFD36E),
        "Document" to Color(0xFFC78AFF),
        "Place" to Color(0xFFFFB86E),
    )
    private val defaultColor = Color(0xFF8FA3B8)

    fun layout(snapshot: GraphSnapshot, iterations: Int = 180): LaidOutGraph {
        if (snapshot.nodes.isEmpty()) return LaidOutGraph(emptyList(), emptyList())

        val rng = Random(snapshot.seed)
        val area = 1_400f * 1_400f
        val k = sqrt(area / snapshot.nodes.size.toFloat())
        var temperature = 400f

        // Init positions in a disc.
        val positions = snapshot.nodes.map {
            floatArrayOf(rng.nextFloat() * 1200f - 600f, rng.nextFloat() * 1200f - 600f)
        }
        val displacements = Array(snapshot.nodes.size) { floatArrayOf(0f, 0f) }
        val indexById = snapshot.nodes.withIndex().associate { (i, n) -> n.id to i }

        repeat(iterations) {
            // Repulsion
            for (i in snapshot.nodes.indices) {
                displacements[i][0] = 0f
                displacements[i][1] = 0f
                for (j in snapshot.nodes.indices) {
                    if (i == j) continue
                    val dx = positions[i][0] - positions[j][0]
                    val dy = positions[i][1] - positions[j][1]
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                    val force = k * k / dist
                    displacements[i][0] += dx / dist * force
                    displacements[i][1] += dy / dist * force
                }
            }
            // Attraction along edges
            snapshot.edges.forEach { e ->
                val a = indexById[e.fromId] ?: return@forEach
                val b = indexById[e.toId] ?: return@forEach
                val dx = positions[a][0] - positions[b][0]
                val dy = positions[a][1] - positions[b][1]
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                val force = dist * dist / k * (0.3f + 0.7f * e.weight)
                val ux = dx / dist * force
                val uy = dy / dist * force
                displacements[a][0] -= ux
                displacements[a][1] -= uy
                displacements[b][0] += ux
                displacements[b][1] += uy
            }
            // Apply & cool
            for (i in snapshot.nodes.indices) {
                val dx = displacements[i][0]
                val dy = displacements[i][1]
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                positions[i][0] += dx / d * minOf(d, temperature)
                positions[i][1] += dy / d * minOf(d, temperature)
            }
            temperature *= 0.96f
        }

        val uiNodes = snapshot.nodes.mapIndexed { i, n ->
            GraphNodeUi(
                id = n.id,
                label = n.label,
                x = positions[i][0],
                y = positions[i][1],
                radius = 10f + (n.degree.coerceAtMost(20) * 1.2f),
                color = categoryPalette[n.category] ?: defaultColor,
                category = n.category,
            )
        }
        val uiEdges = snapshot.edges.map { GraphEdgeUi(it.fromId, it.toId, it.weight) }
        return LaidOutGraph(uiNodes, uiEdges)
    }
}
