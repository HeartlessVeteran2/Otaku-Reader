package app.otakureader.core.extension.loader

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit tests for [ExtensionSignatureVerifier]. Verifies SHA-256 hashing,
 * fail-closed behaviour on missing signing info, and delegation of trust
 * decisions to [TrustedSignatureStore].
 */
class ExtensionSignatureVerifierTest {

    private lateinit var trustedSignatureStore: TrustedSignatureStore
    private lateinit var verifier: ExtensionSignatureVerifier

    @Before
    fun setUp() {
        trustedSignatureStore = mockk(relaxed = true)
        verifier = ExtensionSignatureVerifier(trustedSignatureStore)
    }

    @Test
    fun `getSignatureHash returns null when no signing info available`() {
        val pi = PackageInfo()
        // Both APIs return null/empty by default.
        @Suppress("DEPRECATION")
        pi.signatures = null

        val hash = verifier.getSignatureHash(pi)

        assertNull(hash)
    }

    @Test
    fun `getSignatureHash computes SHA-256 hex of legacy signature bytes`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val signature = mockk<Signature>()
        every { signature.toByteArray() } returns bytes

        val pi = PackageInfo()
        @Suppress("DEPRECATION")
        pi.signatures = arrayOf(signature)

        // On the JVM (test runtime) Build.VERSION.SDK_INT is 0, so the legacy
        // `signatures` branch is exercised — perfect for unit testing.
        val hash = verifier.getSignatureHash(pi)

        assertEquals(expected, hash)
    }

    @Test
    fun `getSignatureHash returns null when signature toByteArray throws`() {
        val signature = mockk<Signature>()
        every { signature.toByteArray() } throws RuntimeException("boom")

        val pi = PackageInfo()
        @Suppress("DEPRECATION")
        pi.signatures = arrayOf(signature)

        val hash = verifier.getSignatureHash(pi)

        assertNull(hash)
    }

    @Test
    fun `isTrusted delegates to TrustedSignatureStore`() {
        every { trustedSignatureStore.isTrusted("abc") } returns true
        every { trustedSignatureStore.isTrusted("def") } returns false

        assertTrue(verifier.isTrusted("abc"))
        assertFalse(verifier.isTrusted("def"))
    }

    @Test
    fun `trust delegates to TrustedSignatureStore`() {
        verifier.trust("hash-1")

        verify(exactly = 1) { trustedSignatureStore.trust("hash-1") }
    }

    @Test
    fun `revoke delegates to TrustedSignatureStore`() {
        verifier.revoke("hash-2")

        verify(exactly = 1) { trustedSignatureStore.revoke("hash-2") }
    }

    @Test
    fun `getSignatureHash output is deterministic for same input`() {
        val bytes = byteArrayOf(0x10, 0x20, 0x30)
        val signature = mockk<Signature>()
        every { signature.toByteArray() } returns bytes

        val pi = PackageInfo()
        @Suppress("DEPRECATION")
        pi.signatures = arrayOf(signature)

        val first = verifier.getSignatureHash(pi)
        val second = verifier.getSignatureHash(pi)

        assertNotNull(first)
        assertEquals(first, second)
        // SHA-256 hex is 64 chars
        assertEquals(64, first!!.length)
    }

    // -------------------------------------------------------------------------
    // Android P+ signingInfo branch — exercised via the testing seam so the
    // production-path code is covered without requiring Robolectric.
    // -------------------------------------------------------------------------

    @Test
    fun `getSignatureHash on P+ uses signingCertificateHistory for single signer`() {
        verifier.sdkIntProvider = { Build.VERSION_CODES.P }

        val bytes = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val signature = mockk<Signature>()
        every { signature.toByteArray() } returns bytes

        val signingInfo = mockk<SigningInfo>()
        every { signingInfo.hasMultipleSigners() } returns false
        every { signingInfo.signingCertificateHistory } returns arrayOf(signature)

        val pi = PackageInfo()
        pi.signingInfo = signingInfo

        assertEquals(expected, verifier.getSignatureHash(pi))
    }

    @Test
    fun `getSignatureHash on P+ uses apkContentsSigners for multiple signers`() {
        verifier.sdkIntProvider = { Build.VERSION_CODES.TIRAMISU }

        val bytes = byteArrayOf(0x11, 0x22, 0x33)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val signature = mockk<Signature>()
        every { signature.toByteArray() } returns bytes

        val signingInfo = mockk<SigningInfo>()
        every { signingInfo.hasMultipleSigners() } returns true
        every { signingInfo.apkContentsSigners } returns arrayOf(signature)

        val pi = PackageInfo()
        pi.signingInfo = signingInfo

        assertEquals(expected, verifier.getSignatureHash(pi))
    }

    @Test
    fun `getSignatureHash on P+ returns null when signingInfo is null`() {
        verifier.sdkIntProvider = { Build.VERSION_CODES.P }

        val pi = PackageInfo()
        pi.signingInfo = null

        assertNull(verifier.getSignatureHash(pi))
    }

    @Test
    fun `getSignatureHash on P+ returns null when certificate history is empty`() {
        verifier.sdkIntProvider = { Build.VERSION_CODES.P }

        val signingInfo = mockk<SigningInfo>()
        every { signingInfo.hasMultipleSigners() } returns false
        every { signingInfo.signingCertificateHistory } returns emptyArray()

        val pi = PackageInfo()
        pi.signingInfo = signingInfo

        assertNull(verifier.getSignatureHash(pi))
    }

    @Test
    fun `getSignatureHash on P+ returns null when toByteArray throws`() {
        verifier.sdkIntProvider = { Build.VERSION_CODES.P }

        val signature = mockk<Signature>()
        every { signature.toByteArray() } throws RuntimeException("boom")

        val signingInfo = mockk<SigningInfo>()
        every { signingInfo.hasMultipleSigners() } returns false
        every { signingInfo.signingCertificateHistory } returns arrayOf(signature)

        val pi = PackageInfo()
        pi.signingInfo = signingInfo

        assertNull(verifier.getSignatureHash(pi))
    }
}
