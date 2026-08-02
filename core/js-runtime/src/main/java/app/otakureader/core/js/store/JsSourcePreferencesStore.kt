package app.otakureader.core.js.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otakureader.core.js.protocol.JsProtocol
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable per-source preferences for JavaScript sources.
 *
 * Scoped strictly per source id. That is a security boundary, not tidiness: these values
 * frequently hold the user's credentials for a site, and a shared namespace would let any
 * installed source read any other's stored login.
 *
 * Stored as one JSON object per source rather than one DataStore key per preference, because the
 * key set is defined by the extension and is therefore unknown to this code — there is nothing to
 * enumerate against, and a flat namespace would make one source's keys indistinguishable from
 * another's.
 */
@Singleton
class JsSourcePreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private fun keyFor(sourceId: String) = stringPreferencesKey("$KEY_PREFIX${sourceId.toFileName()}")

    /**
     * Read a source's stored preferences.
     *
     * A corrupt value yields an empty map rather than throwing: preferences are supporting data,
     * and losing them costs the user a re-entered setting, while throwing here would drop the
     * source out of Browse entirely for the same fault.
     */
    suspend fun get(sourceId: String): Map<String, String> {
        val raw = dataStore.data.first()[keyFor(sourceId)] ?: return emptyMap()
        return runCatching {
            JsProtocol.json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())
    }

    suspend fun put(sourceId: String, preferences: Map<String, String>) {
        dataStore.edit { it[keyFor(sourceId)] = JsProtocol.json.encodeToString(preferences) }
    }

    /** Called on uninstall so a source's stored credentials do not outlive it. */
    suspend fun clear(sourceId: String) {
        dataStore.edit { it.remove(keyFor(sourceId)) }
    }

    private companion object {
        const val KEY_PREFIX = "js_source_prefs_"
    }
}
