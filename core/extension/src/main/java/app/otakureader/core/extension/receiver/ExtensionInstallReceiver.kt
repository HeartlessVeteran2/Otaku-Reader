package app.otakureader.core.extension.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.loader.ExtensionLoadResult
import app.otakureader.core.extension.loader.ExtensionLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Broadcast receiver that listens for package installation/removal events to detect
 * when extensions are installed, updated, or removed — either via the system package
 * installer (shared extensions) or via app-internal private extension broadcasts.
 *
 * Private-extension events are sent by the installer using
 * [notifyAdded] / [notifyReplaced] / [notifyRemoved] helpers.
 *
 * A new [CoroutineScope] is created per broadcast so work is naturally bounded to
 * the lifetime of each [goAsync] pending result — no persistent scope that could leak.
 */
@AndroidEntryPoint
class ExtensionInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var extensionRepository: ExtensionRepository

    @Inject
    lateinit var extensionLoader: ExtensionLoader

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val packageName = getPackageNameFromIntent(intent) ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            ACTION_EXTENSION_ADDED,
            -> {
                if (isReplacing(intent)) return
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        handlePackageAdded(context, packageName)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            Intent.ACTION_PACKAGE_REPLACED,
            ACTION_EXTENSION_REPLACED,
            -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        handlePackageAdded(context, packageName)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            Intent.ACTION_PACKAGE_REMOVED,
            ACTION_EXTENSION_REMOVED,
            -> {
                if (isReplacing(intent)) return
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        handlePackageRemoved(packageName)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    /**
     * Returns true if this intent represents an in-progress package update
     * (i.e., the package is being replaced, not freshly installed/removed).
     */
    private fun isReplacing(intent: Intent): Boolean =
        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

    /**
     * Returns the package name encoded in the intent's data URI.
     */
    private fun getPackageNameFromIntent(intent: Intent): String? =
        intent.data?.schemeSpecificPart

    private suspend fun handlePackageAdded(context: Context, packageName: String) {
        try {
            // Try to load the installed package as a Tachiyomi-compatible extension
            val loadResult = extensionLoader.loadExtensionFromPkgName(packageName)
            if (loadResult is ExtensionLoadResult.Success) {
                extensionRepository.installExtension(packageName, loadResult.extension.apkPath ?: "")
            }
        } catch (e: Exception) {
            // Not an extension or failed to load — silently ignore
        }
    }

    private suspend fun handlePackageRemoved(packageName: String) {
        try {
            extensionRepository.uninstallExtension(packageName)
        } catch (e: Exception) {
            // Extension was not tracked — silently ignore
        }
    }

    companion object {
        private const val ACTION_EXTENSION_ADDED = "app.otakureader.ACTION_EXTENSION_ADDED"
        private const val ACTION_EXTENSION_REPLACED = "app.otakureader.ACTION_EXTENSION_REPLACED"
        private const val ACTION_EXTENSION_REMOVED = "app.otakureader.ACTION_EXTENSION_REMOVED"

        /**
         * Notify that a private extension was installed (not via the system package manager).
         * Should be called by [app.otakureader.core.extension.installer.ExtensionInstaller]
         * after successfully installing a new private extension APK.
         */
        fun notifyAdded(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_ADDED)
        }

        /**
         * Notify that a private extension was updated.
         */
        fun notifyReplaced(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REPLACED)
        }

        /**
         * Notify that a private extension was removed.
         */
        fun notifyRemoved(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REMOVED)
        }

        private fun notify(context: Context, pkgName: String, action: String) {
            val intent = Intent(action).apply {
                data = "package:$pkgName".toUri()
                `package` = context.packageName
            }
            context.sendBroadcast(intent)
        }

        /**
         * Create an [IntentFilter] covering both system package events and
         * app-internal private extension events.
         */
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(ACTION_EXTENSION_ADDED)
                addAction(ACTION_EXTENSION_REPLACED)
                addAction(ACTION_EXTENSION_REMOVED)
                addDataScheme("package")
            }
        }

        /**
         * Register the receiver programmatically (non-exported, app-local only).
         */
        fun register(context: Context, receiver: ExtensionInstallReceiver) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                createIntentFilter(),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }
}
