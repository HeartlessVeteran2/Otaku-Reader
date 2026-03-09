package app.otakureader.domain.usecase

import app.otakureader.domain.repository.MangaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UpdateMangaNoteUseCaseTest {

    private lateinit var mangaRepository: MangaRepository
    private lateinit var useCase: UpdateMangaNoteUseCase

    @Before
    fun setUp() {
        mangaRepository = mockk()
        useCase = UpdateMangaNoteUseCase(mangaRepository)
    }

    @Test
    fun invoke_withNonNullNote_delegatesToRepository() = runTest {
        val mangaId = 42L
        val notes = "Great manga!"
        coEvery { mangaRepository.updateMangaNote(mangaId, notes) } returns Unit

        useCase(mangaId, notes)

        coVerify(exactly = 1) { mangaRepository.updateMangaNote(mangaId, notes) }
    }

    @Test
    fun invoke_withNullNote_delegatesToRepository() = runTest {
        val mangaId = 5L
        coEvery { mangaRepository.updateMangaNote(mangaId, null) } returns Unit

        useCase(mangaId, null)

        coVerify(exactly = 1) { mangaRepository.updateMangaNote(mangaId, null) }
    }

    @Test(expected = RuntimeException::class)
    fun invoke_propagatesRepositoryException() = runTest {
        val mangaId = 1L
        coEvery { mangaRepository.updateMangaNote(mangaId, any()) } throws RuntimeException("DB error")

        useCase(mangaId, "Some note")
    }
}
