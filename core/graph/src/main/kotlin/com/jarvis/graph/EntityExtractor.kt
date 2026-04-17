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
 * Runs Gemma with a tight structured-output prompt to extract entities/relations
 * from a newly captured note. Falls back to a simple regex-based entity guesser
 * when Gemma isn't loaded so ingest still populates the graph.
 */
@Singleton
class EntityExtractor @Inject constructor(
    private val gemma: GemmaClient,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ExtractionResult::class.java)

    suspend fun extract(text: String): ExtractionResult {
        return if (gemma.isLoaded()) extractWithGemma(text) else extractHeuristically(text)
    }

    private suspend fun extractWithGemma(text: String): ExtractionResult {
        val prompt = buildString {
            appendLine("Extract entities from the note. Return ONLY JSON of this shape:")
            appendLine("""{"entities":[{"name":"...","category":"Person|Topic|Event|Place|Document","description":"...","relationToSource":"...","confidence":0.0-1.0}]}""")
            appendLine()
            appendLine("Note:")
            appendLine(text)
        }
        val raw = runCatching { gemma.generate(prompt, GenOptions(maxTokens = 512, temperature = 0.2f)) }
            .getOrNull() ?: return extractHeuristically(text)
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return extractHeuristically(text)
        val json = raw.substring(start, end + 1)
        return runCatching { adapter.fromJson(json) ?: ExtractionResult() }
            .getOrElse { extractHeuristically(text) }
    }

    /**
     * Zero-dependency fallback. Picks up capitalised multi-word tokens as Topics and
     * simple @mentions as Persons. Crude, but it keeps the graph alive before any
     * LLM is downloaded.
     */
    private fun extractHeuristically(text: String): ExtractionResult {
        val persons = Regex("""@([A-Za-z][A-Za-z0-9_]{2,})""")
            .findAll(text)
            .map { ExtractedEntity(name = it.groupValues[1], category = "Person", confidence = 0.5f, relationToSource = "mentions") }

        val topics = Regex("""\b([A-Z][a-z]{2,}(?:\s+[A-Z][a-z]{2,}){0,3})\b""")
            .findAll(text)
            .map { ExtractedEntity(name = it.value, category = "Topic", confidence = 0.4f, relationToSource = "mentions") }

        val entities = (persons + topics).distinctBy { it.name.lowercase() }.take(8).toList()
        return ExtractionResult(entities)
    }
}
