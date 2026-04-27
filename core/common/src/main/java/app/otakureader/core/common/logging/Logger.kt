package app.otakureader.core.common.logging

/**
 * Minimal structured logger interface.
 *
 * Debug logs are compiled out in release builds; use [Logger.d] freely during development.
 * All prod-visible diagnostics should go through [Logger.i] / [Logger.w] / [Logger.e].
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/** Logger backed by [android.util.Log]. Debug calls are no-ops on release builds. */
class AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        if (DEBUG_ENABLED) android.util.Log.d(tag, message)
    }
    override fun i(tag: String, message: String) { android.util.Log.i(tag, message) }
    override fun w(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.w(tag, message, throwable)
    }
    override fun e(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.e(tag, message, throwable)
    }

    private companion object {
        // Build.DEBUG is true for debug builds; replaced by R8/ProGuard to `false` in release.
        val DEBUG_ENABLED: Boolean = app.otakureader.core.common.BuildConfig.DEBUG
    }
}

/** No-op logger for tests that do not care about log output. */
object NoOpLogger : Logger {
    override fun d(tag: String, message: String) = Unit
    override fun i(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}
