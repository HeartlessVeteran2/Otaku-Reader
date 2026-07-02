package app.otakureader.domain.repository

import app.otakureader.sourceapi.MangaSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRepositoryExtTest {

    private val sourceRepository: SourceRepository = mockk()

    @Test
    fun `resolveDownloadFolderName returns the source display name when resolvable`() = runTest {
        val source = mockk<MangaSource> { every { name } returns "MangaDex" }
        coEvery { sourceRepository.getSource("1943584017") } returns source

        assertEquals("MangaDex", sourceRepository.resolveDownloadFolderName(1943584017L))
    }

    @Test
    fun `resolveDownloadFolderName falls back to the numeric sourceId when unresolvable`() = runTest {
        coEvery { sourceRepository.getSource("1943584017") } returns null

        assertEquals("1943584017", sourceRepository.resolveDownloadFolderName(1943584017L))
    }
}
