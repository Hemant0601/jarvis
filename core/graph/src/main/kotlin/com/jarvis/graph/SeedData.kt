package com.jarvis.graph

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.data.dao.GraphDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

/**
 * Writes a tiny starter graph on first launch so the Brain view has something to
 * render. Idempotent — guarded by a SharedPref flag and skipped if the user has
 * already captured anything.
 */
@Singleton
class SeedData @Inject constructor(
    @ApplicationContext context: Context,
    private val ingest: IngestPipeline,
    private val dao: GraphDao,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jarvis-seed", Context.MODE_PRIVATE)

    suspend fun seedIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val existing = dao.nodeCount().firstOrNull() ?: 0
        if (existing > 0) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return
        }

        ingest.ingestText(
            label = "Welcome to Jarvis",
            text = """
                Welcome. This is your private second brain — everything you capture lives on this device,
                encrypted at rest. Speak or type a note from the Capture tab. Ask your brain anything in Chat.
                Your knowledge shows up as a neural network in the Brain tab: nodes are memories, edges are
                relationships the AI detects automatically.
            """.trimIndent(),
        )
        ingest.ingestText(
            label = "How Graph RAG works",
            text = """
                Every note gets chunked, embedded, and linked to entities Gemma extracts from the text.
                When you ask a question, Jarvis finds the most relevant chunks by vector similarity, walks
                one or two hops through the graph to pick up related context, and answers using those
                memories as citations.
            """.trimIndent(),
        )
        ingest.ingestText(
            label = "Getting the best out of Jarvis",
            text = """
                Open Settings to download Gemma 4 E2B for on-device reasoning, Whisper for voice notes,
                and a sentence embedding model for higher-quality semantic search. Connect Google Drive
                for encrypted nightly backups.
            """.trimIndent(),
        )

        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    private companion object { const val KEY_SEEDED = "seeded-v1" }
}
