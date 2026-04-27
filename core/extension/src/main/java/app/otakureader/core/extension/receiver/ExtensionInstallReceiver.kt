package app.otakureader.core.extension.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.otakureader.core.common.di.ApplicationScope
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.loader.ExtensionLoadResult
import app.otakureader.core.extension.loader.ExtensionLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
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
 * Each handler is launched as a child coroutine of the [ApplicationScope] so the work
 * is visible to test cancellation and no unmanaged scopes accumulate. [goAsync] keeps
 * the receiver alive until the coroutine completes.
 */
@AndroidEntryPoint
class ExtensionInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var extensionRepository: ExtensionRepository

    @Inject
    lateinit var extensionLoader: ExtensionLoader

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val packageName = getPackageNameFromIntent(intent) ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            ACTION_EXTENSION_ADDED,
            -> {
                if (isReplacing(intent)) return
                launchAsync { handlePackageAdded(context, packageName) }
            }

            Intent.ACTION_PACKAGE_REPLACED,
            ACTION_EXTENSION_REPLACED,
            -> launchAsync { handlePackageAdded(context, packageName) }

            Intent.ACTION_PACKAGE_REMOVED,
            ACTION_EXTENSION_REMOVED,
            -> {
                if (isReplacing(intent)) return
                launchAsync { handlePackageRemoved(packageName) }
            }
        }
    }

    /**
     * Keeps the receiver alive via [goAsync] for the duration of [block],
     * then finishes the pending result regardless of outcome.
     */
    private inline fun launchAsync(crossinline block: suspend () -> Unit) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isReplacing(intent: Intent): Boolean =
        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

    private fun getPackageNameFromIntent(intent: Intent): String? =
        intent.data?.schemeSpecificPart

    private suspend fun handlePackageAdded(context: Context, packageName: String) {
        try {
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

        fun notifyAdded(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_ADDED)
        }

        fun notifyReplaced(context: Context, pkgName: String) {
            notify(context, pkgName, ACTION_EXTENSION_REPLACED)
        }

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
