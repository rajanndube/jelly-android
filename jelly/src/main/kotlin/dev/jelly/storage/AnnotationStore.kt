package dev.jelly.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.jelly.model.Annotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists annotations per screen with 7-day expiry, mirroring
 * package/src/utils/storage.ts (loadAnnotations / saveAnnotations).
 *
 * Keyed by screen identifier so each screen has an independent annotation list,
 * matching how the web version keys by `pathname`.
 */
class AnnotationStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun observe(screenKey: String): Flow<List<Annotation>> {
        val key = stringPreferencesKey(prefix(screenKey))
        return context.dataStore.data.map { prefs ->
            val raw = prefs[key] ?: return@map emptyList()
            decode(raw).filter { isFresh(it) }
        }
    }

    suspend fun load(screenKey: String): List<Annotation> = observe(screenKey).first()

    /**
     * Returns all stored annotations across every screen key. Used by the
     * "catch-up sync" flow when the user annotates before the endpoint /
     * cable is set up and later wants to push the backlog. Expired entries
     * are filtered out to match [observe].
     */
    suspend fun enumerateAll(): Map<String, List<Annotation>> {
        val prefs = context.dataStore.data.first()
        val out = mutableMapOf<String, List<Annotation>>()
        for ((key, value) in prefs.asMap()) {
            val keyName = key.name
            if (!keyName.startsWith(KEY_PREFIX)) continue
            if (value !is String) continue
            val screenKey = keyName.removePrefix(KEY_PREFIX)
            val anns = decode(value).filter { isFresh(it) }
            if (anns.isNotEmpty()) out[screenKey] = anns
        }
        return out
    }

    suspend fun save(screenKey: String, annotations: List<Annotation>) {
        val key = stringPreferencesKey(prefix(screenKey))
        // Belt and suspenders: dedupe by id before writing. The popup flow
        // already guards against double-submit, but anything reaching this
        // function with two entries sharing an id will crash AnnotationsScreen
        // (LazyColumn requires unique keys). `distinctBy` keeps the first
        // occurrence, so any later resurrected duplicate is silently dropped.
        val deduped = if (annotations.size > 1) annotations.distinctBy { it.id } else annotations
        context.dataStore.edit { prefs ->
            if (deduped.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = json.encodeToString(deduped)
            }
        }
    }

    private fun decode(raw: String): List<Annotation> = runCatching {
        // Dedupe on read too: a pre-existing DataStore from before the
        // double-submit fix may already contain duplicates that would crash
        // AnnotationsScreen on the next open. distinctBy keeps the user's
        // app usable without forcing a data wipe.
        json.decodeFromString<List<Annotation>>(raw).distinctBy { it.id }
    }.onFailure {
        // Silent fall-back to emptyList would mask real corruption (schema
        // bump, DataStore truncation, accidentally-saved garbage). Log so the
        // root cause is recoverable from logcat instead of a mystery wipe.
        android.util.Log.w("JellyStore", "Failed to decode annotations from DataStore", it)
    }.getOrDefault(emptyList())

    private fun isFresh(annotation: Annotation): Boolean {
        val cutoff = System.currentTimeMillis() - SEVEN_DAYS_MS
        return annotation.timestamp >= cutoff
    }

    private fun prefix(screenKey: String) = "$KEY_PREFIX$screenKey"

    companion object {
        private const val KEY_PREFIX = "annotations."
        private const val SEVEN_DAYS_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
