package app.otakureader.domain.usecase

import app.otakureader.domain.repository.DownloadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeleteChapterUseCaseTest {

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var useCase: DeleteChapterUseCase

    @Before
    fun setUp() {
        downloadRepository = mockk()
        useCase = DeleteChapterUseCase(downloadRepository)
    }

    @Test
    fun invoke_delegatesToRepository() = runTest {
        coEvery {
            downloadRepository.deleteChapterDownload(any(), any(), any(), any())
        } returns Unit

        useCase(
            chapterId = 1L,
            sourceName = "MangaDex",
            mangaTitle = "One Piece",
            chapterTitle = "Chapter 1"
        )

        coVerify(exactly = 1) {
            downloadRepository.deleteChapterDownload(
                chapterId = 1L,
                sourceName = "MangaDex",
                mangaTitle = "One Piece",
                chapterTitle = "Chapter 1"
            )
        }
    }

    @Test
    fun invoke_passesAllParametersCorrectly() = runTest {
        coEvery {
            downloadRepository.deleteChapterDownload(any(), any(), any(), any())
        } returns Unit

        useCase(
            chapterId = 99L,
            sourceName = "NHentai",
            mangaTitle = "Attack on Titan",
            chapterTitle = "Final Chapter"
        )

        coVerify(exactly = 1) {
            downloadRepository.deleteChapterDownload(
                chapterId = 99L,
                sourceName = "NHentai",
                mangaTitle = "Attack on Titan",
                chapterTitle = "Final Chapter"
            )
        }
    }
}
