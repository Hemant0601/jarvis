package com.jarvis.graph

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.common.Ids
import com.jarvis.data.dao.GraphDao
import com.jarvis.data.entities.NodeEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.Clock

/**
 * On first launch, write a single "Jarvis" root node that anchors the brain.
 * Every user memory links (directly or via entity edges) back to this root,
 * so the Brain view always has a visible centre even before any notes exist.
 *
 * The root node's id is stored in a SharedPref so subsequent ingests can
 * attach their Note nodes to it.
 */
@Singleton
class SeedData @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: GraphDao,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jarvis-seed", Context.MODE_PRIVATE)

    suspend fun ensureRoot(): String {
        val existing = prefs.getString(KEY_ROOT_ID, null)
        if (existing != null && dao.node(existing) != null) return existing

        val now = Clock.System.now().toEpochMilliseconds()
        val id = Ids.new()
        dao.upsertNode(
            NodeEntity(
                id = id,
                label = "Jarvis",
                category = "Root",
                summary = "Your personal brain.",
                createdAt = now,
                updatedAt = now,
            )
        )
        prefs.edit().putString(KEY_ROOT_ID, id).apply()
        return id
    }

    suspend fun seedIfNeeded() { ensureRoot() }

    fun rootId(): String? = prefs.getString(KEY_ROOT_ID, null)

    /** Forget the saved root id so the next [ensureRoot] call creates a fresh one. */
    fun reset() { prefs.edit().remove(KEY_ROOT_ID).apply() }

    private companion object { const val KEY_ROOT_ID = "root-id-v1" }
}
