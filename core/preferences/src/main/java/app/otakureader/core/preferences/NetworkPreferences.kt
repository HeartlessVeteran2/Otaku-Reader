package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

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
     * The backing flow, with a read failure turned into "nothing stored".
     *
     * DataStore surfaces a corrupt or unreadable file as an [IOException] *in the flow*, which
     * terminates it. These three flows are collected once at startup by `NetworkSettings` and never
     * re-collected, so a terminated flow would pin its setting at the startup default for the rest
     * of the process — silently, with the settings screen still showing whatever the user last
     * chose. Emitting empty preferences instead means the same visible fallback but a flow that is
     * still alive to deliver the next write.
     *
     * Only [IOException] — anything else is a bug in this code rather than a disk problem, and
     * swallowing it would hide it.
     */
    private val data: Flow<Preferences> = dataStore.data.catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
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

    private object Keys {
        val USER_AGENT = stringPreferencesKey("network_user_agent")
        val DOH_PROVIDER = stringPreferencesKey("network_doh_provider")
        val VERBOSE_LOGGING = booleanPreferencesKey("network_verbose_logging")
    }
}
