package app.otakureader.crash

import android.content.Context
import android.content.SharedPreferences

/**
 * Custom [Thread.UncaughtExceptionHandler] that captures fatal crashes, writes the
 * full stack trace to [SharedPreferences], and then delegates to the default handler
 * so the system can terminate the process normally.
 *
 * Install once in [app.otakureader.OtakuReaderApplication.onCreate] via [CrashHandler.install].
 * On the next launch read—and clear—any saved report with [CrashHandler.getAndClearCrashReport].
 */
object CrashHandler {

    private const val PREFS_NAME = "crash_report_prefs"
    private const val KEY_CRASH_REPORT = "crash_report"

    // 64 KB cap – large enough for any real stack trace yet safe for SharedPreferences
    private const val MAX_STACK_TRACE_LENGTH = 65_536

    /**
     * Replace the default [Thread.UncaughtExceptionHandler] with one that persists
     * the crash report before handing control back to the original handler.
     *
     * Must be called early in [app.otakureader.OtakuReaderApplication.onCreate] so
     * crashes during Hilt graph construction or any other startup code are captured.
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveReport(appContext, buildReport(thread, throwable))
            } catch (_: Throwable) {
                // Never allow the crash handler itself to throw – always fall through.
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Returns the crash report that was saved during the previous run and immediately
     * removes it from [SharedPreferences] so it is shown exactly once.
     *
     * Returns `null` when no crash was recorded.
     */
    fun getAndClearCrashReport(context: Context): String? {
        val prefs = prefs(context)
        val report = prefs.getString(KEY_CRASH_REPORT, null) ?: return null

        // commit() instead of apply() is intentional: the clear must complete
        // synchronously before continuing so the same report is not shown again
        // if the app is killed or crashes during startup.
        return if (prefs.edit().remove(KEY_CRASH_REPORT).commit()) {
            report
        } else {
            null
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val trace = throwable.stackTraceToString()
        val body = if (trace.length > MAX_STACK_TRACE_LENGTH) {
            trace.take(MAX_STACK_TRACE_LENGTH) + "\n… (truncated)"
        } else {
            trace
        }
        return "Thread: ${thread.name}\n\n$body"
    }

    private fun saveReport(context: Context, report: String) {
        // commit() instead of apply() is intentional: the write must complete
        // synchronously before the process is killed by the default handler.
        prefs(context).edit().putString(KEY_CRASH_REPORT, report).commit()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
