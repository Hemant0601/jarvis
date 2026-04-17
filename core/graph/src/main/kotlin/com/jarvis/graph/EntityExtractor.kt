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
 * from a newly captured note. Falls back to an empty list if the model isn't loaded
 * or the response can't be parsed — ingest should still persist the raw text.
 */
@Singleton
class EntityExtractor @Inject constructor(
    private val gemma: GemmaClient,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ExtractionResult::class.java)

    suspend fun extract(text: String): ExtractionResult {
        if (!gemma.isLoaded()) return ExtractionResult()
        val prompt = """
            Extract structured entities from the note below. Return ONLY JSON matching this shape:
            {"entities":[{"name":"...","category":"Person|Topic|Event|Place|Document","description":"...","relationToSource":"...","confidence":0.0-1.0}]}

            Note:
            ""${'"'}$text""${'"'}
        """.trimIndent()
        val raw = runCatching { gemma.generate(prompt, GenOptions(maxTokens = 512, temperature = 0.2f)) }
            .getOrNull() ?: return ExtractionResult()
        val json = raw.substringAfter('{', "").let { "{$it" }.substringBeforeLast('}', "") + "}"
        return runCatching { adapter.fromJson(json) ?: ExtractionResult() }.getOrElse { ExtractionResult() }
    }
}
