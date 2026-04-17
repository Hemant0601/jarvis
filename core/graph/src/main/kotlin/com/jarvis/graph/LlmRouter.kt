package com.jarvis.graph

import com.jarvis.llm.GemmaClient
import com.jarvis.llm.LlmClient
import com.jarvis.llm.LlmSettings
import com.jarvis.llm.OpenRouterClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a question to either the on-device Gemma client or the OpenRouter cloud
 * client, builds the Graph RAG prompt, and returns the answer. If neither is
 * available, returns a "grounded summary" straight from the retrieved memories —
 * not as fluent as an LLM answer, but still useful and completely offline.
 */
@Singleton
class LlmRouter @Inject constructor(
    private val gemma: GemmaClient,
    private val cloud: OpenRouterClient,
    private val settings: LlmSettings,
) {
    suspend fun answer(
        question: String,
        context: RetrievalContext,
        preferCloud: Boolean,
    ): String {
        val client: LlmClient? = when {
            preferCloud && settings.openRouterKey().isNotBlank() -> cloud
            gemma.isLoaded() -> gemma
            settings.openRouterKey().isNotBlank() -> cloud
            else -> null
        }
        if (client == null) return fallbackAnswer(question, context)
        val prompt = buildPrompt(question, context)
        return runCatching { client.generate(prompt) }
            .getOrElse { error ->
                "Couldn't reach ${client.id} (${error.message ?: "unknown"}).\n\n" + fallbackAnswer(question, context)
            }
    }

    private fun buildPrompt(question: String, ctx: RetrievalContext): String = buildString {
        appendLine("You are Jarvis, the user's personal second brain.")
        appendLine("Answer using ONLY the memories below. Cite memories as [#N] inline.")
        appendLine("If the memories don't contain the answer, say so plainly.")
        appendLine()
        appendLine("Memories:")
        ctx.chunks.forEachIndexed { i, c ->
            appendLine("[#${i + 1}] (${c.nodeLabel}, hop=${c.hops}) ${c.text}")
        }
        appendLine()
        appendLine("Question: $question")
        appendLine("Answer:")
    }

    /**
     * Offline, no-LLM fallback. Returns the top retrieved memory excerpts framed as
     * a short, honest answer — enough for the app to be useful before any model is
     * downloaded.
     */
    private fun fallbackAnswer(question: String, ctx: RetrievalContext): String {
        if (ctx.chunks.isEmpty()) {
            return "I don't have any memories about that yet. Capture a note and ask again."
        }
        return buildString {
            appendLine("From your memory:")
            ctx.chunks.take(5).forEachIndexed { i, c ->
                appendLine("${i + 1}. ${c.nodeLabel} — ${c.text.take(220)}")
            }
            appendLine()
            append("(Download Gemma or add an OpenRouter key in Settings for a proper answer.)")
        }
    }
}
