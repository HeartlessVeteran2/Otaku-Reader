package app.otakureader.core.js.ipc;

import app.otakureader.core.js.ipc.IJsHttpBridge;

/**
 * The sidecar engine's interface, deliberately narrow.
 *
 * One generic [call] rather than a method per source operation: the source contract
 * (getPopular, search, getDetail, getPageList, …) is expected to grow, and every AIDL change
 * is a cross-process compatibility concern. Keeping the operation name in a parameter lets the
 * contract evolve without touching the IPC surface.
 */
interface IJsEngine {

    /**
     * Load a JavaScript source into the engine.
     *
     * @param sourceId stable identifier the caller uses in subsequent [call]s
     * @param script the extension's JavaScript
     * @param configJson source metadata (baseUrl, lang, preferences) exposed to the script
     */
    void loadSource(String sourceId, String script, String configJson);

    /** Discard a previously loaded source and free its QuickJS context. */
    void unloadSource(String sourceId);

    /**
     * Invoke a method on a loaded source.
     *
     * Results return through a pipe rather than as a String because binder transactions are
     * capped at roughly 1 MB, and page lists — and especially novel chapter HTML — routinely
     * exceed that. A String return would work in testing and then throw
     * TransactionTooLargeException on real content.
     *
     * @return read end of a pipe carrying the serialized result; never null
     */
    ParcelFileDescriptor call(String sourceId, String method, String argsJson);

    /** Install the main-process HTTP callback. Must be called before any [call]. */
    void setHttpBridge(IJsHttpBridge bridge);

    /** Liveness probe used to distinguish a hung script from a dead process. */
    boolean ping();
}
