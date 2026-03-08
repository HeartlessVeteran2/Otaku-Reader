package app.otakureader.core.extension.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents an installed extension package.
 * Extensions are APK files that provide manga sources.
 */
@Parcelize
data class Extension(
    /** Unique identifier for the extension */
    val id: Long,
    
    /** Android package name of the extension */
    val pkgName: String,
    
    /** Human-readable name of the extension */
    val name: String,
    
    /** Version code from the APK */
    val versionCode: Int,
    
    /** Version name for display */
    val versionName: String,
    
    /** List of source classes provided by this extension */
    val sources: List<ExtensionSource>,
    
    /** Installation status of the extension */
    val status: InstallStatus,
    
    /** Path to the installed APK file */
    val apkPath: String?,

    /** URL to download the APK from (for available extensions) */
    val apkUrl: String? = null,

    /** Icon URL or local path */
    val iconUrl: String?,
    
    /** Language code this extension supports */
    val lang: String,
    
    /** Whether this is a NSFW extension */
    val isNsfw: Boolean,
    
    /** Timestamp when the extension was installed */
    val installDate: Long?,
    
    /** Signature hash for verification */
    val signatureHash: String?,

    /** Whether the extension is enabled without uninstalling */
    val isEnabled: Boolean = true
) : Parcelable {
    
    val isInstalled: Boolean
        get() = status == InstallStatus.INSTALLED
    
    val hasUpdate: Boolean
        get() = status == InstallStatus.HAS_UPDATE
    
    val isTrusted: Boolean
        get() = signatureHash != null
}

@Parcelize
data class ExtensionSource(
    /** Unique identifier for this source within the extension */
    val id: Long,
    
    /** Display name of the source */
    val name: String,
    
    /** Language code */
    val lang: String,
    
    /** Base URL for the source */
    val baseUrl: String,
    
    /** Whether this source supports search */
    val supportsSearch: Boolean = true,
    
    /** Whether this source supports latest updates listing */
    val supportsLatest: Boolean = true
) : Parcelable

enum class InstallStatus {
    /** Extension is installed and up-to-date */
    INSTALLED,
    
    /** Extension has an update available */
    HAS_UPDATE,
    
    /** Extension is being installed */
    INSTALLING,
    
    /** Extension is being updated */
    UPDATING,
    
    /** Extension is being uninstalled */
    UNINSTALLING,
    
    /** Extension is not installed (available from remote) */
    AVAILABLE,
    
    /** Installation failed */
    ERROR
}
