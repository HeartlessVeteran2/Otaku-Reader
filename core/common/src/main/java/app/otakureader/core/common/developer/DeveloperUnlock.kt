package app.otakureader.core.common.developer

import java.security.MessageDigest

/**
 * Gate for the hidden developer screen.
 *
 * ## What this is, and what it is not
 *
 * This is **obscurity, not security.** The hash below ships inside the APK, and an APK can be
 * decompiled, `strings`-dumped, or simply patched to skip the check. Anyone who wants to reach the
 * developer screen can reach it. What the gate buys is that the screen stays out of *casual*
 * reach — nobody lands on it by tapping around.
 *
 * That distinction is worth stating plainly, because the tempting belief ("only I know the code")
 * is false, and building on it would be a mistake. The rule that follows from it: **nothing behind
 * this gate may be a security boundary.** What is behind it today is a shortcut for filling in
 * extension repository URLs — a list any user can already populate by hand from
 * Browse → Extensions → Repositories. The gate saves typing; it does not grant capability.
 *
 * ## Setting your passphrase
 *
 * [passphraseSha256] is the hex SHA-256 of [SALT] + your passphrase. Generate it with
 * `tools/devcode/devcode.sh "your passphrase"` and paste the result below. The plaintext is
 * deliberately never stored in this repository.
 *
 * Until you do that the value is blank, [isConfigured] is false, and [matches] rejects *every*
 * input including the empty string. Failing closed matters here: a placeholder that happened to
 * accept some guessable default would be worse than shipping no gate at all.
 */
object DeveloperUnlock {

    /**
     * Fixed salt, deliberately not a secret.
     *
     * It exists so the stored digest is not a plain SHA-256 of a common word, which would fall to
     * any rainbow table in seconds. It does not make the hash private — see the class note.
     */
    private const val SALT = "otaku-reader/developer/v1:"

    /**
     * Hex SHA-256 of [SALT] + passphrase, or blank when unconfigured.
     *
     * Declared as `val` rather than `const val` on purpose: a `const` blank string lets the
     * compiler fold `isConfigured` to a constant `false` and warn on every call site as dead code,
     * which would be noise in a file whose blank state is the intended shipping default.
     */
    private val passphraseSha256: String = ""

    /** Taps on the About screen's version line that reveal the passphrase prompt. */
    const val REVEAL_TAP_COUNT = 7

    /** Whether a passphrase has been compiled into this build. */
    val isConfigured: Boolean get() = passphraseSha256.isNotBlank()

    /**
     * Whether [input] is the configured passphrase.
     *
     * Always false when [isConfigured] is false, so an unconfigured build has no way in — rather
     * than a way in that happens to be the empty string.
     */
    fun matches(input: String): Boolean {
        if (!isConfigured) return false
        return constantTimeEquals(digestOf(input), passphraseSha256)
    }

    /**
     * The value `tools/devcode/devcode.sh` must print for [passphrase].
     *
     * Public so a test can assert that the script and this object agree. If they ever drift, a
     * pasted hash would silently never match and the screen would be unreachable with the right
     * passphrase — a failure that is invisible until someone tries it.
     */
    fun digestOf(passphrase: String): String = sha256Hex(SALT + passphrase)

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val v = byte.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    /**
     * Comparison that does not return early on the first differing character.
     *
     * Timing is not a realistic attack on a local dialog. This is here so that if the digest is
     * ever checked somewhere timing *does* matter, the comparison does not have to be found and
     * fixed after the fact.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
