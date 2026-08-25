package app.otakureader.core.common.developer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Test

class DeveloperUnlockTest {

    /**
     * Locks the digest to the exact value `tools/devcode/devcode.sh` prints, so the script and the
     * app cannot drift apart.
     *
     * The constant below was produced by:
     *
     *     printf '%s' 'otaku-reader/developer/v1:hunter2' | sha256sum
     *
     * If the salt changes, this fails and the script has to change with it — which is the point.
     * Silent drift would make a correctly pasted hash never match, and that failure only shows up
     * when someone tries the passphrase on a device.
     */
    @Test
    fun `digest matches the generator script for a known passphrase`() {
        assertEquals(
            "1be49760ea9267d75084039f4cd8b0dc010a9c6505879f3ffb35db53cb824e69",
            DeveloperUnlock.digestOf("hunter2"),
        )
    }

    /**
     * The salt has to actually be applied, not merely present in the file.
     *
     * Compared against the bare SHA-256 of the same passphrase — verified independently with
     * `printf '%s' 'hunter2' | sha256sum` — because a test that only checked "the digest is 64 hex
     * characters" would pass with the salting removed.
     */
    @Test
    fun `digest is salted rather than a bare hash of the passphrase`() {
        val bareSha256OfHunter2 = "f52fbd32b2b3b86ff88ef6c490628285f482af15ddcb29541f94bcf526a3f6c7"
        assertFalse(DeveloperUnlock.digestOf("hunter2") == bareSha256OfHunter2)
    }

    /**
     * Holds in **every** build, configured or not.
     *
     * Kept separate from the unconfigured-only test below, which is the whole point: a build that
     * sets `passphraseSha256` as the documented workflow instructs must still have a green test
     * suite. An earlier version asserted `isConfigured` was false unconditionally, so following the
     * setup instructions broke the tests — a test that fails precisely when the feature is used as
     * designed.
     */
    @Test
    fun `a passphrase that is not the configured one is always refused`() {
        assertFalse(DeveloperUnlock.matches("definitely-not-the-configured-passphrase-9f3a2b"))
    }

    /**
     * The shipping default is blank, and blank must mean "no way in" rather than "the empty string
     * is the passphrase".
     *
     * Skipped rather than failed on a configured build: `assumeFalse` makes this an assertion about
     * the default state only. The empty string is checked specifically because it is what an
     * unconfigured gate is most likely to be handed — someone tapping OK on an empty prompt.
     */
    @Test
    fun `unconfigured build rejects every input including the empty string`() {
        assumeFalse(DeveloperUnlock.isConfigured)

        assertFalse(DeveloperUnlock.matches(""))
        assertFalse(DeveloperUnlock.matches("hunter2"))
        assertFalse(DeveloperUnlock.matches(DeveloperUnlock.digestOf("")))
    }
}
