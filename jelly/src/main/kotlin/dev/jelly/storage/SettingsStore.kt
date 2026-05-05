package dev.jelly.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.jelly.output.DetailLevel
import dev.jelly.theme.AccentColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists user-mutable settings (detail level, accent color, sync preferences)
 * to DataStore. Mirrors the localStorage settings keys in the web version
 * (`outputDetail`, `annotationColorId`, etc.).
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val DetailLevelKey = stringPreferencesKey("settings.detailLevel")
        val AccentColorKey = stringPreferencesKey("settings.accentColor")
        val SyncEnabledKey = booleanPreferencesKey("settings.syncEnabled")
        val EndpointKey = stringPreferencesKey("settings.endpoint")
        val WebhookKey = stringPreferencesKey("settings.webhookUrl")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            detailLevel = prefs[Keys.DetailLevelKey]?.let { runCatching { DetailLevel.valueOf(it) }.getOrNull() }
                ?: DetailLevel.Standard,
            accentColor = prefs[Keys.AccentColorKey]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
                ?: AccentColor.Indigo,
            syncEnabled = prefs[Keys.SyncEnabledKey] ?: false,
            endpoint = prefs[Keys.EndpointKey],
            webhookUrl = prefs[Keys.WebhookKey],
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val current = Settings(
                detailLevel = prefs[Keys.DetailLevelKey]?.let { runCatching { DetailLevel.valueOf(it) }.getOrNull() }
                    ?: DetailLevel.Standard,
                accentColor = prefs[Keys.AccentColorKey]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
                    ?: AccentColor.Indigo,
                syncEnabled = prefs[Keys.SyncEnabledKey] ?: false,
                endpoint = prefs[Keys.EndpointKey],
                webhookUrl = prefs[Keys.WebhookKey],
            )
            val next = transform(current)
            prefs[Keys.DetailLevelKey] = next.detailLevel.name
            prefs[Keys.AccentColorKey] = next.accentColor.name
            prefs[Keys.SyncEnabledKey] = next.syncEnabled
            next.endpoint?.let { prefs[Keys.EndpointKey] = it }
                ?: prefs.remove(Keys.EndpointKey)
            next.webhookUrl?.let { prefs[Keys.WebhookKey] = it }
                ?: prefs.remove(Keys.WebhookKey)
        }
    }
}

data class Settings(
    val detailLevel: DetailLevel = DetailLevel.Standard,
    val accentColor: AccentColor = AccentColor.Indigo,
    val syncEnabled: Boolean = false,
    val endpoint: String? = null,
    val webhookUrl: String? = null,
)
