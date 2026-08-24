package app.otakureader.core.extension.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

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

    /**
     * Whether this is a shared extension (installed via the system package installer)
     * or a private extension (stored in filesDir/exts/). Matches Komikku's isShared field.
     */
    val isShared: Boolean = true,

    /** Whether the extension is enabled without uninstalling */
    val isEnabled: Boolean = true,

    /** URL of the repository this extension belongs to */
    val repoUrl: String? = null,

    /**
     * Whether the extension has a README in its repository.
     * Populated from the repo index JSON (Keiyoushi/Komikku minified format: "hasReadme").
     */
    val hasReadme: Boolean = false,

    /**
     * Whether the extension has a CHANGELOG in its repository.
     * Populated from the repo index JSON (Keiyoushi/Komikku minified format: "hasChangelog").
     */
    val hasChangelog: Boolean = false,

    /**
     * Whether the extension requires Cloudflare bypass to function.
     * Populated from the repo index JSON (Keiyoushi/Komikku minified format: "hasCloudflare"
     * on the source level — aggregated to the extension level here as true if any source
     * has hasCloudflare == 1).
     */
    val hasCloudflare: Boolean = false,

    /**
     * Whether this extension is a JavaScript source rather than an APK.
     *
     * Install and uninstall route on this flag, so it is stored rather than inferred. Deriving it
     * from a `.js` suffix on [apkUrl] or a naming convention on [pkgName] would be right almost
     * always, and the failure when it was wrong would be a script handed to the APK installer —
     * or worse, an APK handed to the script store.
     *
     * When true:
     * - [pkgName] is the JavaScript source id (`JsSourceConfig.id`), not an Android package.
     * - [apkUrl] points at the `.js` file to download.
     * - [apkPath] is empty. There is no APK and no `PackageManager` entry; the script lives in
     *   the JS store under `filesDir/js-exts/`, keyed by [pkgName].
     * - [signatureHash] is null, so [isTrusted] is false. That is accurate rather than a gap —
     *   a JavaScript source carries no signature to verify, and HTTPS is the only transport
     *   control on it.
     */
    val isJavaScript: Boolean = false
) : Parcelable {
    
    val isInstalled: Boolean
        get() = status == InstallStatus.INSTALLED
    
    val hasUpdate: Boolean
        get() = status == InstallStatus.HAS_UPDATE
    
    val isTrusted: Boolean
        get() = signatureHash != null
}

@Serializable
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

    /**
     * API host, when the source has one distinct from [baseUrl]. Empty for scraping sources.
     *
     * Only JavaScript sources populate this — an APK extension builds its own requests in Kotlin
     * and never exposes such a URL to the app.
     */
    val apiUrl: String = "",

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
