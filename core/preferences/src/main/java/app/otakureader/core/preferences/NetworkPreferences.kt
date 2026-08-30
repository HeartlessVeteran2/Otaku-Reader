package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * A DNS-over-HTTPS resolver, or [OFF] for the system's own.
 *
 * Stored by [name] rather than by ordinal, so reordering or removing an entry cannot silently
 * repoint someone's setting at a different provider. An unrecognised stored value falls back to
 * [OFF] — the setting is a preference, not data worth failing a launch over.
 */
enum class DohProvider(val url: String?) {
    /** The system resolver. What the app has always used, and the default. */
    OFF(null),
    CLOUDFLARE("https://cloudflare-dns.com/dns-query"),
    GOOGLE("https://dns.google/dns-query"),
    ADGUARD("https://dns.adguard-dns.com/dns-query"),
    QUAD9("https://dns.quad9.net/dns-query"),
    ;

    companion object {
        fun fromName(value: String?): DohProvider =
            entries.firstOrNull { it.name == value } ?: OFF
    }
}

/**
 * Network settings the user can change from the Advanced screen.
 *
 * All three default to what the app did before they existed, which is what makes them safe to read
 * before DataStore has answered: see `NetworkSettings`, which holds the live values for the
 * interceptors that cannot suspend.
 */
class NetworkPreferences(private val dataStore: DataStore<Preferences>) {

    /**
     * The backing flow, with a read failure turned into "nothing stored" **and then retried**.
     *
     * DataStore surfaces a corrupt or unreadable file as an [IOException] *in the flow*, which
     * terminates it. `NetworkSettings` subscribes to each of these once at startup and never
     * re-subscribes, so a terminated flow pins its setting at the startup default for the rest of
     * the process — silently, with the settings screen still showing whatever the user chose.
     *
     * `retryWhen` rather than `catch`, and the difference is the whole point. `catch` runs its
     * block and then **completes the flow**: emitting a fallback there produces one value and
     * ends, which looks like a recovery and is not — the collector goes away and no later write is
     * ever delivered. This emits the same fallback and then resubscribes upstream, so the setting
     * comes back on its own once the read succeeds. `NetworkPreferencesResilienceTest` pins that
     * distinction, because the two are indistinguishable from the emitted value alone.
     *
     * The delay keeps a permanently unreadable file from becoming a spin loop; it costs nothing in
     * the ordinary case, where this never runs at all.
     *
     * Only [IOException] — anything else is a bug in this code rather than a disk problem, and
     * retrying past it would hide it.
     */
    private val data: Flow<Preferences> = dataStore.data.retryWhen { cause, _ ->
        if (cause is IOException) {
            emit(emptyPreferences())
            delay(RETRY_DELAY_MS)
            true
        } else {
            false
        }
    }

    /**
     * A User-Agent to send instead of the app's built-in one, or blank to use the built-in one.
     *
     * Blank rather than null-as-absent so that "reset" is an ordinary write. A stored empty string
     * and a never-written key mean the same thing, which is the behaviour a user resetting the
     * field expects.
     */
    val userAgent: Flow<String> = data.map { it[Keys.USER_AGENT].orEmpty() }

    val dohProvider: Flow<DohProvider> =
        data.map { DohProvider.fromName(it[Keys.DOH_PROVIDER]) }

    /** Whether to log request and response headers. Off by default; see `NetworkModule`. */
    val verboseLogging: Flow<Boolean> = data.map { it[Keys.VERBOSE_LOGGING] ?: false }

    suspend fun setUserAgent(value: String) = dataStore.edit { it[Keys.USER_AGENT] = value.trim() }

    suspend fun setDohProvider(value: DohProvider) =
        dataStore.edit { it[Keys.DOH_PROVIDER] = value.name }

    suspend fun setVerboseLogging(value: Boolean) =
        dataStore.edit { it[Keys.VERBOSE_LOGGING] = value }

    private companion object {
        /** Long enough that an unreadable file cannot spin, short enough to recover unnoticed. */
        const val RETRY_DELAY_MS = 1_000L
    }

    private object Keys {
        val USER_AGENT = stringPreferencesKey("network_user_agent")
        val DOH_PROVIDER = stringPreferencesKey("network_doh_provider")
        val VERBOSE_LOGGING = booleanPreferencesKey("network_verbose_logging")
    }
}
