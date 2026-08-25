package app.otakureader.core.common.developer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
     * If the salt changes, this test fails and the script has to change with it — which is the
     * point. Silent drift would make a correctly pasted hash never match, and that failure only
     * shows up when someone tries the passphrase on a device.
     */
    @Test
    fun `digest matches the generator script for a known passphrase`() {
        assertEquals(
            "1be49760ea9267d75084039f4cd8b0dc010a9c6505879f3ffb35db53cb824e69",
            DeveloperUnlock.digestOf("hunter2"),
        )
    }

    @Test
    fun `digest is salted rather than a bare hash of the passphrase`() {
        // Bare SHA-256 of "hunter2". The salt exists so the stored value is not this.
        val bare = "f52fbd32b2b3b86ff88ef6c490628285f482af15ddcb29541f94bcf526a3f6c7"
        assertFalse(DeveloperUnlock.digestOf("hunter2") == bare)
    }

    /**
     * The shipping default is blank, and blank must mean "no way in" rather than "the empty string
     * is the passphrase". Asserted for the empty string specifically because that is the input an
     * unconfigured gate is most likely to be handed — someone tapping OK on an empty prompt.
     */
    @Test
    fun `unconfigured build rejects every input including the empty string`() {
        assertFalse(DeveloperUnlock.isConfigured)
        assertFalse(DeveloperUnlock.matches(""))
        assertFalse(DeveloperUnlock.matches("hunter2"))
        assertFalse(DeveloperUnlock.matches(DeveloperUnlock.digestOf("")))
    }
}
