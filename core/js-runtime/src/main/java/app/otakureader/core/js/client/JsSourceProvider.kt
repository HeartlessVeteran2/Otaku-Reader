package app.otakureader.core.js.client

import app.otakureader.core.js.protocol.JsSourceConfig
import app.otakureader.core.js.store.JsSourcePreferencesStore
import app.otakureader.core.js.store.JsSourceStore
import app.otakureader.sourceapi.MangaSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns installed `.js` files into ready-to-use [MangaSource]s.
 *
 * This is the whole public surface of the JavaScript backend. `SourceRepositoryImpl` calls
 * [loadSources] and gets back things that satisfy the same interface an APK-backed source does,
 * so nothing downstream — reader, library, downloads, migration — can tell the difference.
 */
@Singleton
class JsSourceProvider @Inject constructor(
    private val store: JsSourceStore,
    private val preferencesStore: JsSourcePreferencesStore,
    private val connection: JsEngineConnection,
) {

    /**
     * Load every installed JavaScript source and register it with the engine.
     *
     * Registration is deliberately eager while the *engine* stays lazy: `register` only records
     * the script in a map, so calling this for twenty sources does not start the sidecar process
     * or evaluate any JavaScript. The process starts on the first actual source call. That split
     * is what makes it safe to do this on every refresh.
     */
    suspend fun loadSources(): List<MangaSource> = mutationLock.withLock {
        val installed = store.installed()

        // Registrations for sources that are no longer installed have to be dropped, or an
        // uninstalled source stays callable for the life of the process — and its stored
        // credentials stay live in the engine's map.
        val installedIds = installed.map { it.config.id }.toSet()
        connection.registeredIds().filterNot { it in installedIds }.forEach { connection.unregister(it) }

        installed.map { (config, script) ->
            // Preferences live in their own encrypted store, not in the manifest on disk: the
            // manifest is replaced wholesale when a source updates, and merging the user's
            // stored settings in here is what stops an update from wiping them.
            val withPreferences = config.copy(preferences = preferencesStore.get(config.id))
            connection.register(withPreferences, script)
            JsSource(withPreferences, connection)
        }
    }

    /** Install a source and make it immediately callable, without waiting for a refresh. */
    suspend fun install(config: JsSourceConfig, script: String) = mutationLock.withLock {
        store.install(config, script)
        connection.register(config.copy(preferences = preferencesStore.get(config.id)), script)
    }

    /**
     * Remove a source, its script, and anything it stored.
     *
     * Preferences go too. They routinely hold the user's login for the site, so leaving them
     * behind would mean uninstalling a source did not actually remove the credentials the user
     * gave it — and a later reinstall would silently inherit them.
     */
    suspend fun uninstall(sourceId: String) = mutationLock.withLock {
        connection.unregister(sourceId)
        store.uninstall(sourceId)
        preferencesStore.clear(sourceId)
    }

    /**
     * Serialises the three operations that move sources between disk and the engine.
     *
     * They read disk and then act on the engine, so running concurrently they interleave into
     * states neither intended: a refresh that snapshots the directory before an install can
     * unregister the source that install just added, and a refresh holding a stale snapshot can
     * re-register — and republish — a source that was uninstalled underneath it. Both leave the
     * engine disagreeing with disk until something forces another refresh.
     */
    private val mutationLock = Mutex()
}
