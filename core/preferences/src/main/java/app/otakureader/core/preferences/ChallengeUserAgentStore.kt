package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otakureader.core.common.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The User-Agent that solved each host's Cloudflare challenge.
 *
 * Persisted, because the clearance cookie is persisted: Android's `CookieManager` keeps it across
 * restarts, so a User-Agent held only in memory would be forgotten while the cookie it belongs to
 * survives. The pair would then disagree on the next launch and the user would be challenged
 * again for a host they had already cleared — the bypass appearing to work, then silently
 * regressing every time the app restarts.
 *
 * Reads are served from an in-memory map rather than DataStore, because [userAgentFor] is called
 * from inside an OkHttp interceptor. That is a blocking call on a dispatcher thread, and a
 * suspending disk read there would put I/O in front of every image in every chapter.
 */
@Singleton
class ChallengeUserAgentStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {

    private val cache = ConcurrentHashMap<String, String>()

    init {
        // Hydrated by a live collection, so the cache follows the stored values without any
        // explicit invalidation. A request arriving in the moment before the first emission
        // sees no User-Agent and may be challenged once; that self-corrects, and it is the
        // cheaper failure than blocking startup on a disk read.
        scope.launch {
            dataStore.data.first().asMap().forEach { (key, value) ->
                val name = key.name
                if (name.startsWith(KEY_PREFIX) && value is String) {
                    cache[name.removePrefix(KEY_PREFIX)] = value
                }
            }
        }
    }

    /** Non-suspending by design — see the class note on the interceptor hot path. */
    fun userAgentFor(host: String): String? = cache[host]

    suspend fun store(host: String, userAgent: String) {
        // Cache first so the retry that follows a solved challenge sees it immediately; the
        // DataStore write is what makes it survive a restart, and is not on the critical path.
        cache[host] = userAgent
        dataStore.edit { it[stringPreferencesKey("$KEY_PREFIX$host")] = userAgent }
    }

    private companion object {
        const val KEY_PREFIX = "challenge_user_agent_"
    }
}
