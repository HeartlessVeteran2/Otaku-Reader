package app.otakureader.core.js.client

import android.app.ActivityManager
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
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

        try {
            withTimeout(timeoutMs) {
                val bound = ensureBound()
                bound.loadSource(sourceId, entry.second, JsProtocol.json.encodeToString(entry.first))

                val pipe = bound.call(sourceId, method, JsProtocol.json.encodeToString(args))
                val payload = FileInputStream(pipe.fileDescriptor).use { it.readBytes() }
                    .toString(Charsets.UTF_8)

                JsProtocol.json.decodeFromString<JsCallResult>(payload)
            }
        } catch (e: TimeoutCancellationException) {
            // The script is still running over there and will not stop on its own.
            killEngine()
            JsCallResult(
                ok = false,
                error = "Source did not respond within ${timeoutMs}ms; engine restarted",
                errorKind = JsErrorKind.TIMEOUT,
            )
        } catch (e: Exception) {
            // A dead binder means the process went away underneath us — most often the OS
            // reclaiming it, which is expected for a background process, not an error.
            engine = null
            JsCallResult(
                ok = false,
                error = e.message ?: "JavaScript engine failed",
                errorKind = JsErrorKind.ENGINE_DIED,
            )
        }
    }

    private suspend fun ensureBound(): IJsEngine = connectionLock.withLock {
        engine?.let { return it }

        pendingBind?.let { return it.await() }

        val deferred = CompletableDeferred<IJsEngine>()
        pendingBind = deferred

        val intent = Intent(context, JsEngineService::class.java)
        val started = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        check(started) { "Could not bind the JavaScript engine service" }

        withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
    }

    /**
     * Terminate the engine process.
     *
     * `killBackgroundProcesses` is the only lever an app has over its own secondary process; a
     * plain `unbindService` would let a spinning script keep the CPU. The sources map survives,
     * so the next call transparently rebinds and replays.
     */
    private fun killEngine() {
        runCatching { context.unbindService(serviceConnection) }
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(context.packageName)
        }
        engine = null
        pendingBind = null
    }
}
