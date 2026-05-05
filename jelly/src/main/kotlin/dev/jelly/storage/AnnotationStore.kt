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

    suspend fun save(screenKey: String, annotations: List<Annotation>) {
        val key = stringPreferencesKey(prefix(screenKey))
        context.dataStore.edit { prefs ->
            if (annotations.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = json.encodeToString(annotations)
            }
        }
    }

    private fun decode(raw: String): List<Annotation> = runCatching {
        json.decodeFromString<List<Annotation>>(raw)
    }.getOrDefault(emptyList())

    private fun isFresh(annotation: Annotation): Boolean {
        val cutoff = System.currentTimeMillis() - SEVEN_DAYS_MS
        return annotation.timestamp >= cutoff
    }

    private fun prefix(screenKey: String) = "annotations.$screenKey"

    companion object {
        private const val SEVEN_DAYS_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
