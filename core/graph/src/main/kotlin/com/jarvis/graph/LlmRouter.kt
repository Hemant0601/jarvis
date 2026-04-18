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
    /**
     * True when the assistant has a usable brain — either Gemma's .task file is
     * already on disk (loads lazily at first call), or an OpenRouter key is set.
     * Used to hide the setup card once the user has actually downloaded a model.
     */
    fun hasModel(): Boolean =
        gemma.isReady() || settings.openRouterKey().isNotBlank()

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
        appendLine("You are Jarvis, the user's private second brain and thinking partner.")
        appendLine("Two modes — detect which one the user is in from the latest input:")
        appendLine()
        appendLine("  NOTE MODE — they are telling you something to remember. You have already")
        appendLine("  stored it. Acknowledge in one short sentence, then add ONE sharp observation,")
        appendLine("  connection to an earlier memory, question, or next step. Example:")
        appendLine("    User: \"Coffee with Sarah Tuesday — she's interested in the Hadoop migration.\"")
        appendLine("    Jarvis: \"Got it. Sarah's also the third person this month asking about Hadoop — maybe worth a public update.\"")
        appendLine()
        appendLine("  QUESTION MODE — they are asking you something. Use the memories below as")
        appendLine("  context, synthesise an answer in your own words, and add a brief analytical")
        appendLine("  take (a pattern you notice, a caveat, or a suggestion). Never quote memories")
        appendLine("  verbatim, never write numbered lists of them, never prefix with \"From your memory\".")
        appendLine()
        appendLine("Always:")
        appendLine("- Address the user as \"you\".")
        appendLine("- Keep replies tight: 1–3 sentences unless the question explicitly asks for depth.")
        appendLine("- If you don't have enough memory, say so plainly and ask ONE clarifying question.")
        appendLine("- Never echo their exact words back at them.")
        appendLine()
        if (ctx.chunks.isEmpty()) {
            appendLine("Memories (context): none relevant retrieved.")
        } else {
            appendLine("Memories (context — for reference only, don't quote):")
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
