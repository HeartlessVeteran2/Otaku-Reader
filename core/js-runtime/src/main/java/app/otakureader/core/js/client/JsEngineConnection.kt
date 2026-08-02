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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the sidecar process from the app's side: binding, the wall-clock budget, and the kill.
 *
 * The kill is the whole point. A script that never yields cannot be stopped from inside the VM
 * — no Android QuickJS binding exposes an interrupt handler — so when a call overruns its
 * budget this terminates the engine process outright. Because the sidecar holds no user data
 * and performs no writes of its own, that is safe to do at any moment; the only cost is
 * re-registering loaded sources on the next call.
 */
@Singleton
class JsEngineConnection @Inject constructor(
    private val context: Context,
    private val httpBridge: JsHttpBridge,
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
    }

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

    fun unregister(sourceId: String) {
        registered.remove(sourceId)
        runCatching { engine?.unloadSource(sourceId) }
    }

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
                work.await().also { watchdog.cancel() }
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

    /** The blocking half of a call. Runs on its own coroutine so the watchdog can outlive it. */
    private suspend fun invoke(
        sourceId: String,
        method: String,
        args: JsCallArgs,
        entry: Pair<JsSourceConfig, String>,
    ): JsCallResult = withContext(Dispatchers.IO) {
        val bound = ensureBound()
        bound.loadSource(sourceId, entry.second, JsProtocol.json.encodeToString(entry.first))

        val pipe = bound.call(sourceId, method, JsProtocol.json.encodeToString(args))
        val payload = FileInputStream(pipe.fileDescriptor).use { it.readBytes() }
            .toString(Charsets.UTF_8)

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
