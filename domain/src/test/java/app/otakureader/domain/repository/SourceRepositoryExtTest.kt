package app.otakureader.domain.repository

import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.toSourceId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceRepositoryExtTest {

    private val sourceRepository: SourceRepository = mockk()

    @Test
    fun `resolveSourceId returns the source's own string id, not the stringified key`() = runTest {
        val source = mockk<MangaSource> { every { id } returns APK_SOURCE_ID }
        coEvery { sourceRepository.getSourceByKey(SOURCE_KEY) } returns source

        assertEquals(APK_SOURCE_ID, sourceRepository.resolveSourceId(SOURCE_KEY))
    }

    @Test
    fun `resolveSourceId returns null when no loaded source owns the key`() = runTest {
        coEvery { sourceRepository.getSourceByKey(SOURCE_KEY) } returns null

        assertNull(sourceRepository.resolveSourceId(SOURCE_KEY))
    }

    // ── associateBySourceKey ──────────────────────────────────────────────────
    //
    // This rule has to stay identical to SourceRepositoryImpl.getSourceByKey. A map that indexed
    // only the canonical key would leave legacy rows unresolved here while getSourceByKey still
    // matched them — the same source reachable one way and not the other, which is how the
    // library ended up with blank source names in the first place.

    @Test
    fun `associateBySourceKey indexes by the canonical hashed key`() {
        val map = listOf(APK_SOURCE_ID, "local").associateBySourceKey { it }

        assertEquals(APK_SOURCE_ID, map[APK_SOURCE_ID.toSourceId()])
        assertEquals("local", map["local".toSourceId()])
    }

    @Test
    fun `associateBySourceKey also indexes the legacy parsed key`() {
        val map = listOf(APK_SOURCE_ID).associateBySourceKey { it }

        assertEquals(APK_SOURCE_ID, map[APK_SOURCE_ID.toLong()])
    }

    /**
     * Same collision precedence as `getSourceByKey`: the source that genuinely owns the key today
     * must win over one that only matches it under the legacy rule. Ordering must not decide it,
     * so the legacy entry is deliberately first in the list.
     */
    @Test
    fun `associateBySourceKey prefers the canonical owner when both rules match one key`() {
        val canonical = "en.canonical"
        val key = canonical.toSourceId()
        val legacy = key.toString()

        val map = listOf(legacy, canonical).associateBySourceKey { it }

        assertEquals(canonical, map[key])
    }

    @Test
    fun `associateBySourceKey has no entry for a key no source owns`() {
        val map = listOf("en.one").associateBySourceKey { it }

        assertNull(map["en.two".toSourceId()])
    }

    private companion object {
        /** The shape an APK extension's id actually has: a Tachiyomi Long, stringified. */
        const val APK_SOURCE_ID = "2499283573021220255"
        const val SOURCE_KEY = -1874553621L
    }
}
