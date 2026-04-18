package com.jarvis.graph

import com.jarvis.llm.GemmaClient
import com.jarvis.llm.LlmClient
import com.jarvis.llm.LlmSettings
import com.jarvis.llm.OpenRouterClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a question to the on-device Gemma client or the OpenRouter cloud
 * client, builds a Graph-RAG-conditioned prompt, and returns a conversational
 * answer. When neither model is set up, returns a short honest acknowledgement
 * instead of dumping the retrieval context back at the user.
 */
@Singleton
class LlmRouter @Inject constructor(
    private val gemma: GemmaClient,
    private val cloud: OpenRouterClient,
    private val settings: LlmSettings,
) {
    /** True when the assistant actually has a brain — Gemma loaded or OpenRouter key set. */
    fun hasModel(): Boolean =
        gemma.isLoaded() || settings.openRouterKey().isNotBlank()

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
        if (client == null) return noModelAnswer(context)
        val prompt = buildPrompt(question, context)
        return runCatching { client.generate(prompt) }
            .map { sanitise(it) }
            .getOrElse { err ->
                "I couldn't reach ${client.id} (${err.message ?: "unknown error"}). " +
                    "Ask again when you're back online or try toggling Local/Cloud."
            }
    }

    private fun buildPrompt(question: String, ctx: RetrievalContext): String = buildString {
        appendLine("You are Jarvis, the user's personal memory assistant.")
        appendLine("You have access to the user's saved memories below. Use them as context, but DO NOT")
        appendLine("list them verbatim or quote them word-for-word. Synthesise a natural, conversational reply.")
        appendLine()
        appendLine("Rules:")
        appendLine("- Address the user directly as \"you\".")
        appendLine("- Keep replies concise: 1-3 sentences unless the question explicitly needs more.")
        appendLine("- If the memories don't contain what's needed, say so plainly and ask one clarifying question.")
        appendLine("- If the user is just telling you something (statement, not question), acknowledge briefly in one sentence.")
        appendLine("- Never prefix your answer with \"From your memory\" or bullet lists.")
        appendLine()
        if (ctx.chunks.isEmpty()) {
            appendLine("Memories: (none retrieved — the user has nothing relevant saved yet.)")
        } else {
            appendLine("Memories (context only, don't quote):")
            ctx.chunks.take(5).forEach { c -> appendLine("- ${c.nodeLabel}: ${c.text.take(220)}") }
        }
        appendLine()
        appendLine("User: $question")
        appendLine("Jarvis:")
    }

    /**
     * Strip any leading "Jarvis:" / "Assistant:" the model might echo back, plus
     * obvious artefacts from the stop token not being set on some runtimes.
     */
    private fun sanitise(raw: String): String {
        var s = raw.trim()
        val prefixes = listOf("Jarvis:", "Assistant:", "AI:")
        for (p in prefixes) if (s.startsWith(p, ignoreCase = true)) s = s.substring(p.length).trim()
        // Drop anything after a new "User:" turn the model might hallucinate.
        s = s.substringBefore("\nUser:").trim()
        s = s.substringBefore("\nJarvis:").trim()
        return s.ifEmpty { "Got it." }
    }

    /**
     * Shown when neither Gemma nor OpenRouter is set up. Keeps the experience
     * honest instead of dumping retrieval snippets that look like garbage.
     */
    private fun noModelAnswer(ctx: RetrievalContext): String {
        val tailHint = "Tap Settings → download Gemma 4 or add an OpenRouter key for a real answer."
        return if (ctx.chunks.isEmpty()) {
            "Saved. $tailHint"
        } else {
            val subject = ctx.chunks.first().nodeLabel.take(60)
            "Noted — this relates to \"$subject\" in your memory. $tailHint"
        }
    }
}
