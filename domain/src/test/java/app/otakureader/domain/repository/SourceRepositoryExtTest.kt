package app.otakureader.domain.repository

import app.otakureader.sourceapi.MangaSource
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
        val source = mockk<MangaSource> { every { id } returns "2499283573021220255" }
        coEvery { sourceRepository.getSourceByKey(SOURCE_KEY) } returns source

        assertEquals("2499283573021220255", sourceRepository.resolveSourceId(SOURCE_KEY))
    }

    @Test
    fun `resolveSourceId returns null when no loaded source owns the key`() = runTest {
        coEvery { sourceRepository.getSourceByKey(SOURCE_KEY) } returns null

        assertNull(sourceRepository.resolveSourceId(SOURCE_KEY))
    }

    /**
     * The folder name is the numeric key and nothing else — see the KDoc on
     * [resolveDownloadFolderName]. Downloads on disk are already filed under it, so resolving a
     * display name here would point every read at a directory that does not exist.
     *
     * This asserts the *absence* of a lookup, not just the returned string, because a version
     * that looked the source up and happened to miss would return the same value while being one
     * successful lookup away from orphaning every download.
     */
    @Test
    fun `resolveDownloadFolderName is the numeric key and consults no source`() = runTest {
        assertEquals("1943584017", sourceRepository.resolveDownloadFolderName(1943584017L))

        coVerify(exactly = 0) { sourceRepository.getSource(any()) }
        coVerify(exactly = 0) { sourceRepository.getSourceByKey(any()) }
    }

    private companion object {
        const val SOURCE_KEY = -1874553621L
    }
}
