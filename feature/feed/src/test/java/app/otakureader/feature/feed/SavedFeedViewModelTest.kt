package app.otakureader.feature.feed

import app.cash.turbine.test
import app.otakureader.domain.model.FeedSource
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.toSourceId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the source picker that replaced the free-text field.
 *
 * The old `addSource` hashed whatever the user typed (`sourceName.hashCode().toLong()`) and never
 * consulted [SourceRepository], so the id it stored could not match any real source and a typo
 * was indistinguishable from a correct entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedFeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var feedRepository: FeedRepository
    private lateinit var sourceRepository: SourceRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        feedRepository = mockk(relaxed = true)
        sourceRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `available sources are offered under the canonical key, not a hash of their name`() =
        runTest(testDispatcher) {
            every { feedRepository.getFeedSources() } returns flowOf(emptyList())
            every { sourceRepository.getSources() } returns flowOf(listOf(source(MANGADEX_ID, "MangaDex")))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val option = viewModel.state.value.availableSources.single()
            assertEquals(MANGADEX_ID.toSourceId(), option.sourceId)
            // The value the old code would have stored. It has to differ, or this proves nothing.
            assertTrue(option.sourceId != "MangaDex".hashCode().toLong())
        }

    /**
     * Not cosmetic: `insertFeedSource` is `OnConflictStrategy.REPLACE` against a unique index on
     * `sourceId`, so re-adding a source deletes its row and inserts a fresh one — wiping the
     * user's enabled toggle, item count and ordering. Filtering it out of the picker is what
     * prevents that from being reachable.
     */
    @Test
    fun `a source already in the feed is not offered again`() = runTest(testDispatcher) {
        val added = FeedSource(sourceId = MANGADEX_ID.toSourceId(), sourceName = "MangaDex")
        every { feedRepository.getFeedSources() } returns flowOf(listOf(added))
        every { sourceRepository.getSources() } returns flowOf(
            listOf(source(MANGADEX_ID, "MangaDex"), source("local", "Local")),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf("Local"), viewModel.state.value.availableSources.map { it.name })
    }

    @Test
    fun `removing a source puts it back on offer`() = runTest(testDispatcher) {
        val feedSources = MutableStateFlow(
            listOf(FeedSource(sourceId = MANGADEX_ID.toSourceId(), sourceName = "MangaDex")),
        )
        every { feedRepository.getFeedSources() } returns feedSources
        every { sourceRepository.getSources() } returns flowOf(listOf(source(MANGADEX_ID, "MangaDex")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.availableSources.isEmpty())

        feedSources.value = emptyList()
        advanceUntilIdle()

        assertEquals(listOf("MangaDex"), viewModel.state.value.availableSources.map { it.name })
    }

    @Test
    fun `adding a source stores the key the picker offered`() = runTest(testDispatcher) {
        every { feedRepository.getFeedSources() } returns flowOf(emptyList())
        every { sourceRepository.getSources() } returns flowOf(listOf(source(MANGADEX_ID, "MangaDex")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        val option = viewModel.state.value.availableSources.single()

        viewModel.onEvent(SavedFeedEvent.AddSource(option.sourceId, option.name))
        advanceUntilIdle()

        coVerify { feedRepository.addFeedSource(MANGADEX_ID.toSourceId(), "MangaDex") }
    }

    @Test
    fun `a failed add surfaces a snackbar rather than throwing`() = runTest(testDispatcher) {
        every { feedRepository.getFeedSources() } returns flowOf(emptyList())
        every { sourceRepository.getSources() } returns flowOf(emptyList())
        coEvery { feedRepository.addFeedSource(any(), any()) } throws IllegalStateException("disk full")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(SavedFeedEvent.AddSource(1L, "Whatever"))
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is SavedFeedEffect.ShowSnackbar)
        }
    }

    private fun createViewModel() = SavedFeedViewModel(feedRepository, sourceRepository)

    private fun source(id: String, name: String, lang: String = "en"): MangaSource =
        mockk(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.lang } returns lang
        }

    private companion object {
        /** An APK extension's id: a Tachiyomi Long, stringified. */
        const val MANGADEX_ID = "2499283573021220255"
    }
}
