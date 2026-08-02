package app.otakureader.core.js.store

import android.content.Context
import android.content.SharedPreferences
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.preferences.EncryptedPrefsFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable per-source preferences for JavaScript sources.
 *
 * **Keystore-encrypted**, via the same [EncryptedPrefsFactory] the tracker tokens use. These
 * values routinely hold the user's login for a site — a source resolving a session cookie or an
 * API key stores it here — so writing them through ordinary preferences would put site
 * credentials in cleartext on disk while every other credential in the app is encrypted. The
 * per-source scoping below is described as a security boundary; it is only worth that name if
 * what sits behind it is protected too.
 *
 * Scoped strictly per source id, so one installed source cannot read another's stored login.
 * Stored as one JSON document per source rather than a key per preference, because the key set
 * is defined by the extension and is therefore unknown here — there is nothing to enumerate
 * against, and a flat namespace would make one source's keys indistinguishable from another's.
 */
@Singleton
class JsSourcePreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    internal companion object {
        const val FILE_NAME = "js_source_preferences"

        /**
         * Ceiling on one source's stored preferences.
         *
         * Not tidiness — a bound is required for correctness. The whole preference map travels
         * to the sidecar inside `JsSourceConfig` on every `loadSource`, which is a Binder
         * transaction with a hard limit near 1 MB. A source that writes an enormous value would
         * therefore persist it, and then every subsequent call would fail with
         * `TransactionTooLargeException` — permanently, because the oversized config is replayed
         * on each attempt. The source would be bricked with no way back short of reinstalling.
         *
         * Refusing the write keeps the failure at the moment of the write, where it is a
         * misbehaving source rather than a dead one.
         */
        const val MAX_TOTAL_BYTES = 256 * 1024

        /** Bounds any single value, so one runaway key cannot consume the whole budget. */
        const val MAX_VALUE_BYTES = 32 * 1024
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedPrefsFactory.create(context, FILE_NAME)
    }

    private fun keyFor(sourceId: String) = sourceId.toFileName()

    /**
     * Read a source's stored preferences.
     *
     * A corrupt value yields an empty map rather than throwing: preferences are supporting data,
     * and losing them costs the user a re-entered setting, while throwing here would drop the
     * source out of Browse entirely for the same fault.
     */
    suspend fun get(sourceId: String): Map<String, String> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(keyFor(sourceId), null) ?: return@withContext emptyMap()
        runCatching {
            JsProtocol.json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())
    }

    /**
     * Store a source's preferences, refusing an oversized payload.
     *
     * Returns false when the write was refused, so the caller can decline to hold the value in
     * memory either — accepting it in the registered config while rejecting it on disk would
     * reintroduce the oversized-transaction failure the bound exists to prevent.
     */
    suspend fun put(sourceId: String, preferences: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            val encoded = JsProtocol.json.encodeToString(preferences)
            val oversizedValue = preferences.values.any { it.toByteArray().size > MAX_VALUE_BYTES }
            if (oversizedValue || encoded.toByteArray().size > MAX_TOTAL_BYTES) {
                return@withContext false
            }
            prefs.edit().putString(keyFor(sourceId), encoded).commit()
        }

    /**
     * Called on uninstall so a source's stored credentials do not outlive it.
     *
     * `commit` rather than `apply`, and **the result is checked**: a caller removing credentials
     * needs to know the removal actually reached disk before it reports the source gone. The
     * previous version said exactly that and then discarded the boolean, so a failed write left
     * the user's login for the site on disk while the uninstall reported success — and a later
     * reinstall of the same id would silently inherit it.
     *
     * Throwing rather than returning false because the caller orders this first: a failure has
     * to abort the uninstall while the source is still wholly intact, so retrying is clean.
     */
    suspend fun clear(sourceId: String) = withContext(Dispatchers.IO) {
        check(prefs.edit().remove(keyFor(sourceId)).commit()) {
            "Could not clear stored preferences for $sourceId"
        }
    }
}
