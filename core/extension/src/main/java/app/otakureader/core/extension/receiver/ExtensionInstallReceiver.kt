package app.otakureader.core.extension.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.core.extension.loader.ExtensionLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Broadcast receiver that listens for package installation/removal events
 * to detect when extensions are installed or updated outside the app.
 */
@AndroidEntryPoint
class ExtensionInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var extensionRepository: ExtensionRepository

    @Inject
    lateinit var extensionLoader: ExtensionLoader

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val packageName = intent.data?.schemeSpecificPart ?: return

        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REPLACED -> {
                        // Check if the installed/updated package is an extension
                        handlePackageAdded(context, packageName)
                    }
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        // Handle extension removal
                        handlePackageRemoved(packageName)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handlePackageAdded(context: Context, packageName: String) {
        try {
            // Try to load the extension to check if it's a valid Otaku extension
            val packageManager = context.packageManager
            val packageInfo = try {
                packageManager.getPackageInfo(packageName, 0)
            } catch (e: Exception) {
                return
            }

            // Get the APK path
            val apkPath = packageInfo.applicationInfo?.sourceDir ?: return

            // Try to load as an extension
            val loadResult = extensionLoader.loadExtension(apkPath)
            if (loadResult is ExtensionLoader.ExtensionLoadResult.Success) {
                // This is a valid extension, update repository
                extensionRepository.installExtension(
                    packageName,
                    apkPath
                )
            }
        } catch (e: Exception) {
            // Not an extension or failed to load
        }
    }

    private suspend fun handlePackageRemoved(packageName: String) {
        try {
            // Remove the extension from repository
            extensionRepository.uninstallExtension(packageName)
        } catch (e: Exception) {
            // Extension wasn't in repository
        }
    }

    companion object {
        /**
         * Create an intent filter for listening to package events.
         */
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
        }
    }
}
