package app.otakureader.core.extension.loader

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the set of extension signing-certificate hashes that the user has explicitly
 * approved.  Extensions whose signature hash is not present here are returned as
 * [ExtensionLoadResult.Untrusted] by [ExtensionLoader], allowing the UI to prompt the user
 * before executing arbitrary code from an unknown signer.
 *
 * Private extensions installed by the user via the built-in installer are auto-trusted at
 * installation time (their signature is verified against the existing file at that point).
 *
 * Hashes are SHA-256 of the DER-encoded signing certificate, hex-lower-case encoded.
 */
@Singleton
class TrustedSignatureStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isTrusted(signatureHash: String): Boolean =
        prefs.getStringSet(KEY_TRUSTED_HASHES, emptySet())?.contains(signatureHash) == true

    fun trust(signatureHash: String) {
        val current = prefs.getStringSet(KEY_TRUSTED_HASHES, emptySet()).orEmpty().toMutableSet()
        current.add(signatureHash)
        prefs.edit { putStringSet(KEY_TRUSTED_HASHES, current) }
    }

    fun revoke(signatureHash: String) {
        val current = prefs.getStringSet(KEY_TRUSTED_HASHES, emptySet()).orEmpty().toMutableSet()
        current.remove(signatureHash)
        prefs.edit { putStringSet(KEY_TRUSTED_HASHES, current) }
    }

    fun trustedHashes(): Set<String> =
        prefs.getStringSet(KEY_TRUSTED_HASHES, emptySet()).orEmpty().toSet()

    companion object {
        private const val PREFS_NAME = "extension_trusted_signatures"
        private const val KEY_TRUSTED_HASHES = "trusted_hashes"
    }
}
