package app.otakureader.core.js.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.otakureader.core.js.engine.JsEngineService
import app.otakureader.core.js.ipc.IJsEngine
import app.otakureader.core.js.protocol.JsCallArgs
import app.otakureader.core.js.protocol.JsCallResult
import app.otakureader.core.js.protocol.JsErrorKind
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.js.protocol.JsSourceConfig
import app.otakureader.core.js.protocol.readPayload
import app.otakureader.core.js.store.JsSourcePreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the sidecar process from the app's side: binding, the wall-clock budget, and the kill.
 *
 * The kill is the **backstop**, not the first line of defence. `QuickJsHost` sets an in-VM
 * evaluation timeout that stops a non-yielding script from inside the engine, and it is
 * deliberately shorter than [DEFAULT_CALL_TIMEOUT_MS] so the cheap remedy gets to act first —
 * interrupting the VM leaves the engine usable, whereas killing the process forces a rebind and
 * a replay of every registered source.
 *
 * What the kill covers is the case the in-VM timeout cannot reach: the engine wedging somewhere
 * outside script evaluation. QuickJS is C, so a pathological input can spin or fault inside the
 * parser, the regex engine or GC, where no JavaScript-level interrupt is ever polled — and a
 * native fault takes down whatever process it happens in. Keeping that process separate is what
 * turns "the app died" into "one source failed".
 *
 * Because the sidecar holds no user data and performs no writes of its own, terminating it is
 * safe at any moment; the only cost is re-registering loaded sources on the next call.
 */
@Singleton
class JsEngineConnection @Inject constructor(
    // Hilt binds Context only behind a qualifier — an unqualified Context has no binding at all,
    // which is why the missing annotation fails the build rather than injecting the wrong one.
    // The application context is also the correct choice here on its own merits: this is a
    // @Singleton that binds a service for the process lifetime, so holding an Activity context
    // would leak it.
    @param:ApplicationContext private val context: Context,
    private val httpBridge: JsHttpBridge,
    private val preferencesStore: JsSourcePreferencesStore,
) {

    companion object {
        /**
         * Wall-clock budget for a single source call.
         *
         * Sized for a slow site over a slow connection plus parsing, not for a fast one — the
         * cost of being wrong in the tight direction is cancelling legitimate work, while being
         * wrong in the loose direction only delays recovery from a hang the user already sees.
         */
        const val DEFAULT_CALL_TIMEOUT_MS = 30_000L

        /** Binding is local and should be immediate; a long wait here means something is wrong. */
        private const val BIND_TIMEOUT_MS = 5_000L

        /**
         * Budget for writing preferences back to disk.
         *
         * Short, because this runs after the call's own watchdog has been cancelled: nothing
         * else is left to interrupt a stalled write, and the caller is a UI-driven source
         * operation. Losing a setting is recoverable; hanging Browse is not.
         */
        private const val PREFERENCE_WRITE_TIMEOUT_MS = 5_000L

        private const val TAG = "JsEngineConnection"
    }

    /**
     * Serialises preference persistence against [unregister].
     *
     * Held across the liveness check and the write together, so an uninstall cannot land between
     * them and have its clearing undone by a call that was already in flight.
     */
    private val preferenceLock = Mutex()

    /** Sources registered by the app, replayed after a restart so a kill is transparent. */
    private val registered = ConcurrentHashMap<String, Pair<JsSourceConfig, String>>()

    /** Set by the watchdog so the resulting binder failure is reported as a timeout. */
    private val timedOut = AtomicBoolean(false)

    private val connectionLock = Mutex()
    private var engine: IJsEngine? = null
    private var pendingBind: CompletableDeferred<IJsEngine>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bound = IJsEngine.Stub.asInterface(service)
            engine = bound
            runCatching { bound.setHttpBridge(httpBridge.binder) }
            pendingBind?.complete(bound)
            pendingBind = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Fires when we kill the process, and also if it crashes. Either way the loaded
            // sources are gone; drop the handle so the next call rebinds and replays them.
            engine = null
        }
    }

    /**
     * Register a source. Cheap and idempotent — the script is only pushed across when the
     * engine actually needs it, so registering never pays for a process start.
     */
    fun register(config: JsSourceConfig, script: String) {
        registered[config.id] = config to script
    }

    /**
     * Drop a source, optionally erasing what it stored.
     *
     * Deregistration and credential-clearing happen together, under [preferenceLock], because
     * they are only correct as one operation. Splitting them — clearing in the caller and
     * deregistering here — leaves a window in which the source is cleared but still registered,
     * so an in-flight call's write-back passes its liveness check and puts the credentials
     * straight back; a later reinstall of the same id then silently inherits them. Doing them
     * under two different locks is the same bug with extra steps.
     *
     * Once both are inside this lock the *order* between them no longer affects the race, since
     * `persistPreferences` takes the same lock and so cannot run between them either way. That
     * frees the ordering to be chosen for failure-safety instead: the clear goes first, because
     * it is the step that can throw. Removing the registration first would leave a failed
     * uninstall with the source deregistered but its script still on disk — not the wholly
     * installed, retryable state the caller documents, and not what a user retrying an uninstall
     * would expect to be acting on.
     *
     * [clearStoredPreferences] is opt-in because the two callers want different things. An
     * uninstall must erase credentials and must know if that failed. The refresh path only
     * reconciles the engine against disk and must never fail a whole source list over a
     * preferences write.
     */
    suspend fun unregister(sourceId: String, clearStoredPreferences: Boolean = false) {
        preferenceLock.withLock {
            // First, and outside any runCatching: a failure here must abort the uninstall with
            // the registration still intact.
            if (clearStoredPreferences) {
                preferencesStore.clear(sourceId)
            }
            registered.remove(sourceId)
            runCatching { engine?.unloadSource(sourceId) }
        }
    }

    /**
     * Ids currently registered, snapshotted.
     *
     * Returned as a copy so a caller can unregister while iterating — which is exactly what the
     * provider does when reconciling against what is still installed on disk.
     */
    fun registeredIds(): List<String> = registered.keys.toList()

    /**
     * Invoke a source method, enforcing the budget.
     *
     * Returns a [JsCallResult] rather than throwing, so a hung or broken source is an ordinary
     * source failure the caller can surface through the usual health-monitoring path instead of
     * an exception escaping into a ViewModel.
     */
    suspend fun call(
        sourceId: String,
        method: String,
        args: JsCallArgs,
        timeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    ): JsCallResult = withContext(Dispatchers.IO) {
        val entry = registered[sourceId]
            ?: return@withContext JsCallResult(
                ok = false,
                error = "Source $sourceId is not registered",
                errorKind = JsErrorKind.SOURCE_MISSING,
            )

        coroutineScope {
            // The budget CANNOT be enforced with withTimeout around the call.
            //
            // A binder transaction is a blocking native call and readBytes() blocks on a pipe;
            // neither observes coroutine cancellation. withTimeout would cancel the coroutine
            // while the underlying thread stayed blocked forever, so the kill would never fire
            // and the caller would hang — the very failure mode that moving out-of-process was
            // meant to solve, reproduced one layer up.
            //
            // Instead a separate watchdog kills the engine when the budget expires. Killing the
            // remote process is what unblocks the transaction: the pending call fails with
            // DeadObjectException, which surfaces here as an ordinary failure. The kill is not
            // cleanup after the timeout — it IS the timeout mechanism.
            val work = async { invoke(sourceId, method, args, entry) }

            val watchdog = launch {
                delay(timeoutMs)
                timedOut.set(true)
                killEngine()
            }

            try {
                work.await().also { watchdog.cancel() }.also { persistPreferences(sourceId, it) }
            } catch (e: Exception) {
                watchdog.cancel()
                if (timedOut.getAndSet(false)) {
                    JsCallResult(
                        ok = false,
                        error = "Source did not respond within ${timeoutMs}ms; engine was killed",
                        errorKind = JsErrorKind.TIMEOUT,
                    )
                } else {
                    // A dead binder without a timeout means the process went away underneath
                    // us — most often the OS reclaiming a background process, not an error.
                    engine = null
                    JsCallResult(
                        ok = false,
                        error = e.message ?: "JavaScript engine failed",
                        errorKind = JsErrorKind.ENGINE_DIED,
                    )
                }
            }
        }
    }

    /**
     * Persist preferences a script wrote during the call, and refresh the registered config.
     *
     * Updating [registered] is the half that is easy to miss and breaks quietly without. The
     * sidecar is stateless between calls, so every call re-pushes the config held here; if only
     * the store were updated, the very next call would ship the *old* preferences back into the
     * engine and silently undo the write. A source that resolves a mirror domain once per
     * session would re-resolve it forever, looking merely slow rather than broken.
     *
     * Four things here are about ordering rather than storage, and each is a real failure:
     *
     *  - **Only persist while the source is still registered.** An uninstall clears the stored
     *    preferences, but a call already in flight would then write them straight back — so
     *    reinstalling the same id would silently recover credentials the user had removed. The
     *    registration is the liveness check, and it must be read *inside* the lock that
     *    `unregister` also takes, or the check and the write straddle the uninstall.
     *  - **Merge, never replace.** Each call carries a full snapshot taken when it started, so
     *    two concurrent calls that each set a different key would see the later one's snapshot
     *    overwrite the earlier one's key. Merging over the current value keeps both. There is no
     *    delete in the script-facing API, so merge loses nothing.
     *  - **Bound the wait.** This runs after the watchdog has been cancelled, so without a
     *    timeout a stalled write would hang a Browse or reader call with nothing left to
     *    interrupt it.
     *  - **Let cancellation through.** `runCatching` swallows `CancellationException`, which
     *    would turn a cancelled screen into a successful source result and leave the caller's
     *    job un-cancellable.
     *
     * A genuine write failure *is* swallowed: the call succeeded and its results are good, so
     * failing it over a setting would discard a page the user can see for one they cannot.
     */
    private suspend fun persistPreferences(sourceId: String, result: JsCallResult) {
        val updated = result.preferences ?: return

        preferenceLock.withLock {
            val current = registered[sourceId] ?: return@withLock
            val merged = current.first.preferences + updated

            val stored = try {
                withTimeout(PREFERENCE_WRITE_TIMEOUT_MS) { preferencesStore.put(sourceId, merged) }
            } catch (e: CancellationException) {
                // Distinguishing the two matters: a timeout is ours and must not propagate, but
                // the caller's cancellation has to keep travelling or their job never ends.
                if (coroutineContext.isActive) false else throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to persist preferences for $sourceId", e)
                false
            }

            // Only mirror into the registered config once the store accepted it. Holding a value
            // in memory that was refused on disk — an oversized payload, say — would keep
            // shipping it to the engine on every call, which is exactly what the store's bound
            // exists to prevent.
            if (stored) {
                registered.computeIfPresent(sourceId) { _, (config, script) ->
                    config.copy(preferences = merged) to script
                }
            }
        }
    }

    /** The blocking half of a call. Runs on its own coroutine so the watchdog can outlive it. */
    private suspend fun invoke(
        sourceId: String,
        method: String,
        args: JsCallArgs,
        entry: Pair<JsSourceConfig, String>,
    ): JsCallResult = withContext(Dispatchers.IO) {
        val bound = ensureBound()
        bound.loadSource(sourceId, entry.second, JsProtocol.json.encodeToString(entry.first))

        val payload = bound.call(sourceId, method, JsProtocol.json.encodeToString(args))
            .readPayload()

        JsProtocol.json.decodeFromString<JsCallResult>(payload)
    }

    private suspend fun ensureBound(): IJsEngine = connectionLock.withLock {
        engine?.let { return it }

        pendingBind?.let { return it.await() }

        val deferred = CompletableDeferred<IJsEngine>()
        pendingBind = deferred

        val intent = Intent(context, JsEngineService::class.java)
        val started = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!started) {
            // Clear the pending bind before failing. Leaving it set would poison every later
            // call: each would await a deferred nothing can ever complete, burning its full
            // budget instead of simply retrying the bind.
            pendingBind = null
            runCatching { context.unbindService(serviceConnection) }
            error("Could not bind the JavaScript engine service")
        }

        try {
            withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            pendingBind = null
            runCatching { context.unbindService(serviceConnection) }
            throw e
        }
    }

    /**
     * Terminate the engine process.
     *
     * Unbinding is what actually ends it: an isolated process exists only to serve its bound
     * clients, so releasing the last binding makes the system destroy it — including a thread
     * spinning in native code. `killBackgroundProcesses` follows as a belt-and-braces measure
     * for the ordinary-process case, but it is not the primary mechanism and would not reach an
     * isolated process on its own.
     *
     * The registered-sources map deliberately survives, so the next call rebinds and replays
     * them and the restart is invisible to the caller.
     */
    private fun killEngine() {
        // Unbind only. killBackgroundProcesses was removed for two independent reasons: it
        // needs a permission the app does not hold, so it silently did nothing; and it kills
        // every background process of the package, which — if a call times out while the app
        // itself is backgrounded — could have taken down the main process and any in-flight
        // download with it. Collateral damage from a recovery path is worse than the hang.
        //
        // Unbinding is also the correct mechanism here: an isolated process exists solely to
        // serve its bound clients, so releasing the last binding makes the system destroy it,
        // native thread and all.
        runCatching { context.unbindService(serviceConnection) }
        engine = null
        pendingBind = null
    }
}
