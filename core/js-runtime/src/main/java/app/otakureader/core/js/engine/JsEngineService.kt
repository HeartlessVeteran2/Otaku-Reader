package app.otakureader.core.js.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import app.otakureader.core.js.ipc.IJsEngine
import app.otakureader.core.js.ipc.IJsHttpBridge
import app.otakureader.core.js.protocol.JsCallArgs
import app.otakureader.core.js.protocol.JsCallResult
import app.otakureader.core.js.protocol.JsErrorKind
import app.otakureader.core.js.protocol.JsHttpRequest
import app.otakureader.core.js.protocol.JsHttpResponse
import app.otakureader.core.js.protocol.JsProtocol
import app.otakureader.core.js.protocol.JsSourceConfig
import app.otakureader.core.js.protocol.readPayload
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Hosts the JavaScript engine in the `:jsengine` process.
 *
 * This service is **designed to be killed**. A runaway script is normally stopped by the in-VM
 * evaluation timeout in [QuickJsHost]; the client's wall-clock budget, enforced by terminating
 * this process, is the backstop for what that cannot reach — the engine wedging or faulting in
 * native code, outside any JavaScript frame where an interrupt would be polled.
 *
 * Everything here is therefore disposable: no user data is held, no writes are performed, and
 * a restart costs only the re-registration of loaded sources.
 *
 * Nothing in here may assume it will be allowed to finish.
 */
class JsEngineService : Service() {

    private companion object {
        const val TAG = "JsEngineService"
    }

    private data class LoadedSource(val config: JsSourceConfig, val script: String)

    private val sources = ConcurrentHashMap<String, LoadedSource>()

    @Volatile
    private var httpBridge: IJsHttpBridge? = null

    private val binder = object : IJsEngine.Stub() {

        override fun setHttpBridge(bridge: IJsHttpBridge?) {
            httpBridge = bridge
        }

        override fun loadSource(sourceId: String, script: String, configJson: String) {
            val config = JsProtocol.json.decodeFromString<JsSourceConfig>(configJson)
            sources[sourceId] = LoadedSource(config, script)
        }

        override fun unloadSource(sourceId: String) {
            sources.remove(sourceId)
        }

        override fun ping(): Boolean = true

        override fun call(sourceId: String, method: String, argsJson: String): ParcelFileDescriptor {
            val result = runCatching { execute(sourceId, method, argsJson) }
                .getOrElse { error ->
                    JsCallResult(
                        ok = false,
                        error = error.message ?: error::class.java.simpleName,
                        errorKind = JsErrorKind.SCRIPT_ERROR,
                    )
                }
            return app.otakureader.core.js.protocol.writeToPipe(JsProtocol.json.encodeToString(result))
        }
    }

    private fun execute(sourceId: String, method: String, argsJson: String): JsCallResult {
        val loaded = sources[sourceId]
            ?: return JsCallResult(
                ok = false,
                error = "Source $sourceId is not loaded",
                errorKind = JsErrorKind.SOURCE_MISSING,
            )

        val args = runCatching { JsProtocol.json.decodeFromString<JsCallArgs>(argsJson) }
            .getOrElse {
                return JsCallResult(
                    ok = false,
                    error = "Malformed arguments: ${it.message}",
                    errorKind = JsErrorKind.PROTOCOL_ERROR,
                )
            }

        val host = QuickJsHost(
            config = loaded.config,
            script = loaded.script,
            http = ::performHttp,
        )

        // Blocking is correct here: this is a binder thread, and the client is holding a
        // wall-clock budget over exactly this call. If the script never returns, the client
        // kills this process and the thread dies with it.
        val raw = runBlocking { host.call(method, args) }
        return JsCallResult(ok = true, data = raw)
    }

    /**
     * Forward an HTTP request to the main process.
     *
     * A failure here is returned to the script as a normal response rather than thrown, because
     * extensions routinely probe URLs that 404 and are written to handle that; turning it into
     * an exception would break sources that are behaving correctly.
     */
    private fun performHttp(request: JsHttpRequest): JsHttpResponse {
        val bridge = httpBridge
            ?: return JsHttpResponse(ok = false, error = "No HTTP bridge installed")

        return runCatching {
            val responseJson = bridge.execute(JsProtocol.json.encodeToString(request)).readPayload()
            JsProtocol.json.decodeFromString<JsHttpResponse>(responseJson)
        }.getOrElse {
            JsHttpResponse(ok = false, error = it.message ?: "HTTP bridge failed")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        sources.clear()
        httpBridge = null
        super.onDestroy()
    }
}
