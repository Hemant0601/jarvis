package com.jarvis.graph

import com.jarvis.llm.GemmaClient
import com.jarvis.llm.GenOptions
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class ExtractedEntity(
    val name: String,
    val category: String,
    val description: String = "",
    val relationToSource: String = "mentions",
    val confidence: Float = 0.7f,
)

@JsonClass(generateAdapter = true)
data class ExtractionResult(
    val entities: List<ExtractedEntity> = emptyList(),
)

/**
 * Two-layer entity extraction:
 *   1. If Gemma is loaded, ask it for structured JSON. Small models don't always
 *      comply, so parsing is forgiving — we also accept bare "name | category"
 *      lines and fall back to heuristics if nothing parses.
 *   2. Heuristic extractor: @mentions, CamelCase product names, capitalised
 *      noun phrases, and a small dictionary of high-signal domain nouns
 *      (meeting, project, groceries, birthday, etc.). Deduplicated + capped.
 */
@Singleton
class EntityExtractor @Inject constructor(
    private val gemma: GemmaClient,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ExtractionResult::class.java)

    suspend fun extract(text: String): ExtractionResult {
        val llm = if (gemma.isLoaded()) extractWithGemma(text) else ExtractionResult()
        val heuristic = extractHeuristically(text)
        val merged = (llm.entities + heuristic.entities)
            .distinctBy { it.name.lowercase().trim() }
            .take(8)
        return ExtractionResult(merged)
    }

    private suspend fun extractWithGemma(text: String): ExtractionResult {
        val prompt = buildString {
            appendLine("Extract entities from the note. Only output a JSON object of this exact shape:")
            appendLine("""{"entities":[{"name":"...","category":"Person|Topic|Event|Place|Project","description":"...","confidence":0.0-1.0}]}""")
            appendLine("- Include people, projects, topics, places, events.")
            appendLine("- Max 6 entities.")
            appendLine("- Do not output anything outside the JSON.")
            appendLine()
            appendLine("Note:")
            appendLine(text)
        }
        val raw = runCatching { gemma.generate(prompt, GenOptions(maxTokens = 400, temperature = 0.2f)) }
            .getOrNull() ?: return ExtractionResult()
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return ExtractionResult()
        val json = raw.substring(start, end + 1)
        return runCatching { adapter.fromJson(json) ?: ExtractionResult() }
            .getOrElse { ExtractionResult() }
    }

    /**
     * Regex + keyword extraction with no ML. Works as a safety net when Gemma
     * can't produce clean JSON, and populates the graph with something useful
     * even before Gemma is installed.
     */
    private fun extractHeuristically(text: String): ExtractionResult {
        val out = mutableListOf<ExtractedEntity>()

        // People: @mentions.
        Regex("""@([A-Za-z][A-Za-z0-9_]{2,})""").findAll(text).forEach {
            out += ExtractedEntity(
                name = it.groupValues[1],
                category = "Person",
                confidence = 0.6f,
                relationToSource = "mentions",
            )
        }

        // CamelCase single-token product / project names (OpenProxy, FooBar, iOS).
        Regex("""\b([A-Z][a-z]+[A-Z][A-Za-z0-9]+)\b""").findAll(text).forEach {
            out += ExtractedEntity(name = it.value, category = "Project", confidence = 0.55f)
        }

        // Multi-word Proper Noun phrases (2–4 capitalised words).
        Regex("""\b([A-Z][a-z]{2,}(?:\s+[A-Z][a-z]{2,}){1,3})\b""").findAll(text).forEach {
            out += ExtractedEntity(name = it.value, category = "Topic", confidence = 0.5f)
        }

        // Single capitalised word that's a likely proper noun (>=4 chars, not
        // common sentence-starters).
        val sentenceStarters = setOf(
            "The", "This", "That", "These", "Those", "My", "Our", "Your", "Their",
            "An", "And", "But", "For", "You", "Tomorrow", "Today", "Yesterday",
            "Next", "Last", "First", "Second", "Final", "Just", "Now", "Hey", "Hi",
        )
        Regex("""\b([A-Z][a-z]{3,})\b""").findAll(text).forEach {
            val word = it.value
            if (word !in sentenceStarters) {
                out += ExtractedEntity(name = word, category = "Topic", confidence = 0.35f)
            }
        }

        // High-signal domain words — show up in the graph as Topic nodes so
        // "when am I going shopping?" can find "shopping" via simple retrieval.
        val keywords = mapOf(
            "shopping" to "Topic", "groceries" to "Topic",
            "meeting" to "Event", "call" to "Event", "interview" to "Event",
            "appointment" to "Event", "lunch" to "Event", "dinner" to "Event",
            "breakfast" to "Event", "flight" to "Event", "trip" to "Event",
            "birthday" to "Event", "anniversary" to "Event", "deadline" to "Event",
            "project" to "Project", "workout" to "Topic", "gym" to "Place",
            "office" to "Place", "home" to "Place", "airport" to "Place",
            "email" to "Topic", "invoice" to "Topic", "payment" to "Topic",
        )
        val lowered = text.lowercase()
        for ((kw, cat) in keywords) {
            if (Regex("""\b$kw\b""").containsMatchIn(lowered)) {
                out += ExtractedEntity(
                    name = kw.replaceFirstChar { it.uppercase() },
                    category = cat,
                    confidence = 0.4f,
                )
            }
        }

        return ExtractionResult(
            out.distinctBy { it.name.lowercase() }.take(8),
        )
    }
}
