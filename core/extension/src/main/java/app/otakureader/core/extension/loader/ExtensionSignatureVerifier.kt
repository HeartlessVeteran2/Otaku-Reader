package app.otakureader.core.extension.loader

import android.content.pm.PackageInfo
import android.os.Build
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes signing-certificate hashes from extension APKs and delegates trust
 * decisions to [TrustedSignatureStore].
 *
 * Extracted from `ExtensionLoader` so the SHA-256 / signing-certificate handling can
 * be unit-tested independently from class loading and APK parsing. The
 * `getSignatureHash` implementation follows Tachiyomi/Komikku semantics: prefer
 * [PackageInfo.signingInfo] on Android P+, fall back to the deprecated
 * `signatures` array on older SDKs, and fail-closed (return `null`) on any error.
 */
@Singleton
class ExtensionSignatureVerifier @Inject constructor(
    private val trustedSignatureStore: TrustedSignatureStore,
) {

    /**
     * Override hook for tests: allows exercising the Android P+ `signingInfo`
     * branch on the JVM (where [Build.VERSION.SDK_INT] is `0`). Production code
     * always uses the real device SDK level.
     */
    @Volatile
    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    /**
     * Return a hex-encoded SHA-256 of the first signing certificate, or `null` if
     * the certificate cannot be read. Returning `null` lets callers treat the
     * extension as untrusted (fail-closed).
     */
    fun getSignatureHash(packageInfo: PackageInfo): String? {
        return try {
            if (sdkIntProvider() >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo ?: return null
                val cert = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners?.firstOrNull()
                } else {
                    signingInfo.signingCertificateHistory?.firstOrNull()
                }
                cert?.toByteArray()?.let { sha256Hex(it) }
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.firstOrNull()?.toByteArray()?.let { sha256Hex(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True if the user has previously approved the given hash. */
    fun isTrusted(signatureHash: String): Boolean = trustedSignatureStore.isTrusted(signatureHash)

    /** Permanently approve a signing certificate. */
    fun trust(signatureHash: String) = trustedSignatureStore.trust(signatureHash)

    /** Revoke a previously approved signing certificate. */
    fun revoke(signatureHash: String) = trustedSignatureStore.revoke(signatureHash)

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
