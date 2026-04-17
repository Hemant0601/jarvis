package com.jarvis.graph

import com.jarvis.llm.GemmaClient
import com.jarvis.llm.LlmClient
import com.jarvis.llm.LlmSettings
import com.jarvis.llm.OpenRouterClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a question to either the on-device Gemma client or the OpenRouter cloud
 * client based on user preference + availability, builds the Graph RAG prompt,
 * and returns the model's answer.
 *
 * Per session decisions live in the UI (per-chat toggle). The router only honours
 * the request when the selected client is actually available.
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
        val prompt = buildPrompt(question, context)
        val client: LlmClient = when {
            preferCloud && settings.openRouterKey().isNotBlank() -> cloud
            gemma.isLoaded() -> gemma
            settings.openRouterKey().isNotBlank() -> cloud
            else -> return "No LLM is available yet. Download Gemma or add an OpenRouter key in Settings."
        }
        return client.generate(prompt)
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
}
