package app.otakureader.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.model.DownloadBlockedException
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.AniListLinkRepository
import app.otakureader.domain.repository.AniListSearchRepository
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaMetadataRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.ReadingListRepository
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.core.preferences.DeleteAfterReadMode
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.usecase.UpdateMangaNoteUseCase
import app.otakureader.domain.usecase.SetMangaNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveDownloadFolderName
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.tracking.Tracker
import app.otakureader.domain.usecase.metadata.ResolveAniListMediaUseCase
import app.otakureader.sourceapi.SourceChapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

/**
 * ViewModel for Manga Details Screen following MVI pattern
 */
@HiltViewModel
@Suppress("LargeClass", "TooManyFunctions")
class DetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val categoryRepository: CategoryRepository,
    private val downloadRepository: DownloadRepository,
    private val sourceRepository: SourceRepository,
    private val downloadPreferences: DownloadPreferences,
    private val generalPreferences: GeneralPreferences,
    private val updateMangaNote: UpdateMangaNoteUseCase,
    private val setMangaNotifications: SetMangaNotificationsUseCase,
    private val statisticsRepository: StatisticsRepository,
    private val trackRepository: TrackRepository,
    private val readingListRepository: ReadingListRepository,
    private val metadataRepository: MangaMetadataRepository,
    private val linkRepository: AniListLinkRepository,
    private val searchRepository: AniListSearchRepository,
    private val resolveAniListMedia: ResolveAniListMediaUseCase,
    trackers: Set<@JvmSuppressWildcards Tracker>,
) : ViewModel() {

    private val mangaId: Long = savedStateHandle.get<Long>(MANGA_ID_ARG)
        ?: throw IllegalArgumentException("Manga ID is required")

    /**
     * Tracker display names, taken from the trackers themselves rather than a local `when`.
     *
     * A second copy of "AniList"/"MyAnimeList"/… in this module would be one more place to forget
     * when a tracker is added, and the name is already a property on [Tracker].
     */
    private val trackerNames: Map<Int, String> = trackers.associate { it.id to it.name }

    private val _state = MutableStateFlow(DetailsContract.State())
    val state: StateFlow<DetailsContract.State> = _state.asStateFlow()

    private val _effect = Channel<DetailsContract.Effect>(Channel.BUFFERED)
    val effect: Flow<DetailsContract.Effect> = _effect.receiveAsFlow()

    // Chapter sort/filter is restored from the manga's persisted chapterFlags exactly once per
    // screen visit (on the first non-null emission). Without this guard, every later emission
    // from loadMangaDetails() (e.g. a background metadata refresh) would re-decode and stomp
    // whatever sort/filter the user has since chosen in this session.
    private var hasAppliedChapterFlags = false

    // Thumbnail cache: chapterId -> Pair(thumbnailUrl, totalPages)
    // LRU bounded to 50 entries to prevent unbounded memory growth.
    // Wrapped in a synchronized map: this is an access-order LinkedHashMap (accessOrder=true), so
    // even get() structurally mutates it. It's read from the loadChapters collector and written
    // from parallel async blocks in fetchThumbnailsForDownloadedChapters, so unsynchronized access
    // risked ConcurrentModificationException / corrupted links. All call sites are single ops.
    private val thumbnailCache: MutableMap<Long, Pair<String?, Int>> =
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<Long, Pair<String?, Int>>(50, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<Long, Pair<String?, Int>>): Boolean {
                    return size > 50
                }
            }
        )

    init {
        _state.update { it.copy(trackerNames = trackerNames) }
        loadMangaDetails()
        loadChapters()
        loadNextUnreadChapter()
        observeStaticSettings()
        observeTrackEntries()
        observeMetadata()
        observeAniListLink()
        observeAniListMatchReadiness()
        loadMangaWebUrl()
        observeCategories()
        loadSourceName()
        observeReadingLists()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onEvent(event: DetailsContract.Event) {
        when (event) {
            is DetailsContract.Event.Refresh -> refreshData()
            is DetailsContract.Event.ToggleFavorite -> toggleFavorite()
            is DetailsContract.Event.ToggleDescription -> toggleDescription()
            is DetailsContract.Event.ToggleSortOrder -> toggleSortOrder()
            is DetailsContract.Event.ShowChapterFilter ->
                _state.update { it.copy(showChapterFilter = true) }
            is DetailsContract.Event.HideChapterFilter ->
                _state.update { it.copy(showChapterFilter = false) }
            is DetailsContract.Event.SetChapterFilter -> setChapterFilter(event.filter)
            is DetailsContract.Event.SetChapterSearchQuery ->
                _state.update { it.copy(chapterFilter = it.chapterFilter.copy(chapterSearchQuery = event.query)) }
            is DetailsContract.Event.StartReading -> startReading()
            is DetailsContract.Event.ContinueReading -> continueReading()
            is DetailsContract.Event.ChapterClick -> onChapterClick(event.chapterId)
            is DetailsContract.Event.ChapterLongClick -> onChapterLongClick(event.chapterId)
            is DetailsContract.Event.ToggleChapterRead -> toggleChapterRead(event.chapterId)
            is DetailsContract.Event.DownloadChapter -> downloadChapter(event.chapterId)
            is DetailsContract.Event.DownloadAllChapters -> downloadAllChapters(unreadOnly = false)
            is DetailsContract.Event.DownloadUnreadChapters -> downloadAllChapters(unreadOnly = true)
            is DetailsContract.Event.DeleteChapterDownload -> deleteChapterDownload(event.chapterId)
            is DetailsContract.Event.ExportChapterAsCbz -> exportChapterAsCbz(event.chapterId)
            is DetailsContract.Event.MarkPreviousAsRead -> markPreviousAsRead(event.chapterId)
            is DetailsContract.Event.ShareManga -> shareManga()
            is DetailsContract.Event.OpenDownloadFolder -> openDownloadFolder()
            is DetailsContract.Event.ClearMangaDownloads -> clearMangaDownloads()
            is DetailsContract.Event.SetDeleteAfterReadOverride -> setDeleteAfterReadOverride(event.mode)
            is DetailsContract.Event.ShowNoteEditor -> showNoteEditor()
            is DetailsContract.Event.HideNoteEditor -> hideNoteEditor()
            is DetailsContract.Event.UpdateNoteText -> updateNoteText(event.text)
            is DetailsContract.Event.SaveNote -> saveNote()
            is DetailsContract.Event.ShowChapterNoteEditor -> showChapterNoteEditor(event.chapterId)
            is DetailsContract.Event.HideChapterNoteEditor ->
                _state.update { it.copy(chapterNoteEditorChapterId = null) }
            is DetailsContract.Event.UpdateChapterNoteText ->
                _state.update { it.copy(chapterNoteEditorText = event.text) }
            is DetailsContract.Event.SaveChapterNote -> saveChapterNote()
            is DetailsContract.Event.ClearChapterSelection -> clearChapterSelection()
            is DetailsContract.Event.SelectAllChapters -> selectAllChapters()
            is DetailsContract.Event.InvertChapterSelection -> invertChapterSelection()
            is DetailsContract.Event.DownloadSelectedChapters -> downloadSelectedChapters()
            is DetailsContract.Event.DeleteSelectedChapters -> deleteSelectedChapters()
            is DetailsContract.Event.MarkSelectedAsRead -> markSelectedAsRead()
            is DetailsContract.Event.MarkSelectedAsUnread -> markSelectedAsUnread()
            is DetailsContract.Event.ToggleNotifications -> toggleNotifications()
            is DetailsContract.Event.ToggleUserCompleted -> toggleUserCompleted()
            is DetailsContract.Event.ToggleUserDropped -> toggleUserDropped()
            is DetailsContract.Event.CycleMangaThemeOverride -> cycleMangaThemeOverride()

            // Per-manga reader settings (#260)
            is DetailsContract.Event.SetReaderDirection -> setReaderDirection(event.direction)
            is DetailsContract.Event.SetReaderMode -> setReaderMode(event.mode)
            is DetailsContract.Event.SetReaderColorFilter -> setReaderColorFilter(event.filter)
            is DetailsContract.Event.SetReaderCustomTintColor -> setReaderCustomTintColor(event.color)
            is DetailsContract.Event.SetReaderBackgroundColor -> setReaderBackgroundColor(event.color)

            // Page preloading settings (#264)
            is DetailsContract.Event.SetPreloadPagesBefore -> setPreloadPagesBefore(event.count)
            is DetailsContract.Event.SetPreloadPagesAfter -> setPreloadPagesAfter(event.count)
            is DetailsContract.Event.ResetReaderSettings -> resetReaderSettings()
            
            // Chapter thumbnail loading
            is DetailsContract.Event.LoadChapterThumbnail -> loadChapterThumbnail(event.chapterId)

            // Source suggestions
            is DetailsContract.Event.LoadSourceSuggestions -> loadSourceSuggestions()
            is DetailsContract.Event.OnSourceSuggestionClick -> onSourceSuggestionClick(event.suggestion)
            
            // Panorama cover
            is DetailsContract.Event.TogglePanoramaCover -> togglePanoramaCover()

            is DetailsContract.Event.OpenTracking -> openTracking()

            is DetailsContract.Event.ShowAniListPicker -> showAniListPicker()
            is DetailsContract.Event.DismissAniListPicker ->
                _state.update { it.copy(anilistPicker = null) }
            is DetailsContract.Event.SetAniListPickerQuery -> _state.update { state ->
                state.copy(anilistPicker = state.anilistPicker?.copy(query = event.query))
            }
            is DetailsContract.Event.SubmitAniListPickerSearch -> searchAniListPicker()
            is DetailsContract.Event.SelectAniListCandidate -> selectAniListCandidate(event.mediaId)

            // Edit manga info (#998)
            is DetailsContract.Event.ShowEditInfoSheet ->
                _state.update { it.copy(isEditInfoSheetVisible = true) }
            is DetailsContract.Event.HideEditInfoSheet ->
                _state.update { it.copy(isEditInfoSheetVisible = false) }
            is DetailsContract.Event.SaveMangaInfo -> saveMangaInfo(event)
            is DetailsContract.Event.ResetMangaInfo -> resetMangaInfo()
            is DetailsContract.Event.SetCustomCover -> setCustomCover(event.imageUri)
            is DetailsContract.Event.RemoveCustomCover -> removeCustomCover()

            is DetailsContract.Event.GenreClick -> searchGenreInSource(event.genre)
            is DetailsContract.Event.GenreLongClick -> searchGlobally(event.genre)
            is DetailsContract.Event.SearchGlobally -> searchGlobally(event.query)
            is DetailsContract.Event.OpenWebView -> openInWebView()
            is DetailsContract.Event.OpenWebViewFallback -> openWebViewFallback()

            is DetailsContract.Event.DismissCategoryPicker ->
                _state.update { it.copy(showCategoryPickerDialog = false) }
            is DetailsContract.Event.ToggleCategoryPickerSelection ->
                toggleCategoryPickerSelection(event.categoryId)
            is DetailsContract.Event.ConfirmCategoryPicker -> confirmCategoryPicker()
            is DetailsContract.Event.MigrateManga -> migrateManga()
            is DetailsContract.Event.SourceClick -> onSourceClick()

            is DetailsContract.Event.ShowReadingListPicker -> showReadingListPicker()
            is DetailsContract.Event.DismissReadingListPicker ->
                _state.update { it.copy(showReadingListPickerDialog = false) }
            is DetailsContract.Event.ToggleReadingListPickerSelection ->
                toggleReadingListPickerSelection(event.listId)
        }
    }

    private fun migrateManga() {
        viewModelScope.launch {
            _effect.send(DetailsContract.Effect.NavigateToMigration(mangaId))
        }
    }

    /** Source name tap in the header: reuses the source-search navigation with an empty query. */
    private fun onSourceClick() {
        viewModelScope.launch {
            val manga = _state.value.manga ?: return@launch
            _effect.send(
                DetailsContract.Effect.NavigateToSourceSearch(sourceId = manga.sourceId.toString(), query = "")
            )
        }
    }

    private fun loadSourceName() {
        viewModelScope.launch {
            try {
                val manga = mangaRepository.getMangaById(mangaId) ?: return@launch
                val source = sourceRepository.getSource(manga.sourceId.toString())
                _state.update { it.copy(sourceName = source?.name) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Source name is a passive display detail — silently leave it unset on failure.
            }
        }
    }

    private fun openTracking() {
        viewModelScope.launch {
            val manga = _state.value.manga ?: return@launch
            _effect.send(
                DetailsContract.Effect.NavigateToTracking(
                    mangaId = mangaId,
                    mangaTitle = manga.title
                )
            )
        }
    }

    /** Tag short-press: browse the manga's own source filtered by [genre] (Mihon/Komikku). */
    private fun searchGenreInSource(genre: String) {
        viewModelScope.launch {
            val manga = _state.value.manga ?: return@launch
            _effect.send(
                DetailsContract.Effect.NavigateToSourceSearch(
                    sourceId = manga.sourceId.toString(),
                    query = genre,
                )
            )
        }
    }

    /** Tag long-press, or a title/author/artist tap: search [query] across all sources. */
    private fun searchGlobally(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _effect.send(DetailsContract.Effect.NavigateToGlobalSearch(query = query))
        }
    }

    /**
     * Keeps the tracker entries themselves, and uses the AniList one as the metadata key.
     *
     * The entries were already being collected here to produce a count; keeping the list is what
     * lets the screen show real per-tracker status/score/progress instead of a number. The second
     * job it does is supply [DetailsContract.State.anilistMediaId] — for a manga the user tracks on
     * AniList, that id is already known, so metadata needs no separate matching step.
     */
    private fun observeTrackEntries() {
        trackRepository.observeEntriesForManga(mangaId)
            .onEach { entries ->
                _state.update { it.copy(trackEntries = entries, hasLoadedTrackEntries = true) }
                _state.value.anilistMediaId?.let { requestMetadataRefresh(it, force = false) }
            }
            .launchIn(viewModelScope)
    }

    /** Watches the stored AniList link. Matching is triggered by [observeAniListMatchReadiness]. */
    private fun observeAniListLink() {
        linkRepository.observeLink(mangaId)
            .onEach { link ->
                _state.update { it.copy(anilistLink = link, hasLoadedLink = true) }
                // A stored link is a media id like any other, so it has to drive the metadata
                // fetch too — otherwise an untracked manga would resolve its id and then never
                // fetch anything with it, which is the entire point of the slice. Harmless to
                // call alongside observeTrackEntries: requestMetadataRefresh is guarded per id.
                _state.value.anilistMediaId?.let { requestMetadataRefresh(it, force = false) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Starts auto-matching the moment every input it reads has loaded and none supplied an id.
     *
     * Collecting [state] rather than any one source flow is what makes this correct: readiness is a
     * function of four independently-collected values, so whichever of them completes last is the
     * one that has to fire the trigger — and only a derived check knows which that was.
     *
     * `distinctUntilChanged` keeps this to the single transition into readiness, and
     * [hasAttemptedMatch] holds the line afterwards: a search that legitimately finds nothing
     * leaves the link null, so readiness stays true and every later state change would otherwise
     * search again — one AniList request per emission for a manga AniList has never heard of.
     */
    private fun observeAniListMatchReadiness() {
        state
            .map { it.isReadyToMatchAniList }
            .distinctUntilChanged()
            .filter { it }
            .onEach { matchAniListMedia() }
            .launchIn(viewModelScope)
    }

    /**
     * Searches AniList for this manga and stores the result, but only when it is confident.
     *
     * A guess is deliberately not persisted. Below `MatchAniListMediaUseCase.ACCEPT_THRESHOLD` the
     * matcher is saying it could not separate a work from its sequel, and writing that would attach
     * a wrong synopsis, wrong tags and a wrong score — all of which look authoritative and give the
     * user no reason to doubt them. Nothing renders instead, and the manga can be linked by hand.
     *
     * Search terms are the manga's title plus any synonyms a *previous* successful match cached. On
     * a first visit there are none, which is the common case and fine — they exist to rescue a
     * retry after a source renames something.
     */
    private fun matchAniListMedia() {
        if (hasAttemptedMatch) return
        hasAttemptedMatch = true
        val manga = _state.value.manga ?: return
        viewModelScope.launch {
            _state.update { it.copy(isMatchingAniList = true) }
            try {
                val match = resolveAniListMedia(
                    sourceTitle = manga.title,
                    alternativeTitles = _state.value.cachedMetadata?.synonyms.orEmpty(),
                ).getOrNull()
                if (match != null && match.confident) {
                    // saveAutoLink refuses to overwrite a user-confirmed link. That rule lives in
                    // the repository rather than here so no future caller can forget it.
                    linkRepository.saveAutoLink(mangaId, match.candidate.mediaId)
                }
            } finally {
                _state.update { it.copy(isMatchingAniList = false) }
            }
        }
    }

    /** One auto-match attempt per screen visit. See [observeAniListLink]. */
    private var hasAttemptedMatch = false

    /**
     * Opens the wrong-match picker, seeded with the manga's title, and searches immediately.
     *
     * Seeding and searching up front means the common case — the source title is close enough, the
     * matcher was just not confident enough to commit — costs one tap. The query stays editable
     * because the case that actually needs fixing is the one where the source's title is not what
     * AniList calls the work.
     */
    private fun showAniListPicker() {
        val title = _state.value.manga?.title.orEmpty()
        _state.update { it.copy(anilistPicker = DetailsContract.AniListPickerState(query = title)) }
        searchAniListPicker()
    }

    /**
     * Runs the picker's search, replacing any in-flight one.
     *
     * Cancel-and-replace rather than ignore-while-busy: the user retyping means the previous query
     * is no longer what they want, and letting a slow earlier search land afterwards would show
     * results for a query that is no longer in the box. Same reasoning as
     * [requestMetadataRefresh] — the new request waits for the old to finish cancelling before it
     * touches the spinner, so an outgoing job cannot switch it off underneath its replacement.
     */
    private fun searchAniListPicker() {
        val query = _state.value.anilistPicker?.query?.trim().orEmpty()
        if (query.isEmpty()) return
        val previous = pickerSearchJob
        pickerSearchJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            _state.update { state ->
                state.copy(anilistPicker = state.anilistPicker?.copy(isSearching = true, error = null))
            }
            val result = searchRepository.searchMedia(query)
            _state.update { state ->
                val picker = state.anilistPicker ?: return@update state
                state.copy(
                    anilistPicker = picker.copy(
                        isSearching = false,
                        results = result.getOrDefault(emptyList()),
                        // Unlike auto-matching, a failure here is shown. The user asked for this
                        // search and is waiting on it, so silence would read as "no results" —
                        // which is a different answer with a different next step.
                        error = result.exceptionOrNull()?.message,
                    )
                )
            }
        }
    }

    /**
     * Records the user's pick and refetches metadata for it.
     *
     * `force = true` because the cached row still describes the *old* media and is inside its
     * seven-day TTL, so an unforced refresh would decline to do anything. Until it lands,
     * [DetailsContract.State.metadata] already hides the stale row — its `anilistId` no longer
     * matches the link — so the screen shows nothing rather than the wrong thing.
     */
    private fun selectAniListCandidate(mediaId: Long) {
        _state.update { it.copy(anilistPicker = null) }
        viewModelScope.launch {
            linkRepository.saveUserLink(mangaId, mediaId)
            requestMetadataRefresh(mediaId, force = true)
        }
    }

    private var pickerSearchJob: Job? = null

    /**
     * Cache-only. Emits null until something has been fetched, and never triggers a fetch.
     *
     * What lands here is the raw row, which may describe a media the manga is no longer linked to —
     * the cache is keyed by `mangaId` and outlives a link change on purpose. The screen reads
     * [DetailsContract.State.metadata], which derives from this *and* the current tracker entries,
     * so the check happens where both are known rather than in whichever collector emits last.
     */
    private fun observeMetadata() {
        metadataRepository.observeMetadata(mangaId)
            .onEach { metadata -> _state.update { it.copy(cachedMetadata = metadata, hasLoadedMetadata = true) } }
            .launchIn(viewModelScope)
    }

    /**
     * Requests a metadata fetch for [anilistId], at most once per id unless [force] is set.
     *
     * The guard matters because [observeTrackEntries] re-emits on *every* tracker write — pushing
     * chapter progress after a read is enough — and without it each of those would launch another
     * refresh. The repository's TTL would turn most of them into a Room read rather than a request,
     * but launching a coroutine per progress update to discover that is waste.
     *
     * The cost of the guard is that a **failed** refresh is not retried while the screen is open.
     * That is deliberate rather than overlooked: retrying on the next tracker write would mean one
     * network attempt per chapter read on an offline device. Pull-to-refresh
     * ([DetailsContract.Event.Refresh]) is the explicit retry, and it passes `force = true`.
     *
     * [metadataRefreshJob] and [refreshedAnilistId] are only touched from `viewModelScope`, which is
     * `Dispatchers.Main.immediate` — single-threaded, so no lock is needed. The new job waits for
     * the previous one to finish cancelling before it flips [State.isMetadataLoading] on, so an
     * outgoing job's `finally` can never switch the spinner off underneath its replacement.
     */
    private fun requestMetadataRefresh(anilistId: Long, force: Boolean) {
        if (!force && refreshedAnilistId == anilistId) return
        refreshedAnilistId = anilistId
        val previous = metadataRefreshJob
        metadataRefreshJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            _state.update { it.copy(isMetadataLoading = true) }
            try {
                // The result is intentionally not surfaced. This is a cache-first section: on
                // failure the screen keeps rendering whatever was already cached, and on a first
                // visit with nothing cached it renders no metadata section at all. An error banner
                // for a supplementary third-party lookup would be noise on top of a page that is
                // already complete without it.
                metadataRepository.refreshMetadata(mangaId, anilistId, force)
            } finally {
                _state.update { it.copy(isMetadataLoading = false) }
            }
        }
    }

    private var metadataRefreshJob: Job? = null
    private var refreshedAnilistId: Long? = null

    private fun loadMangaWebUrl() {
        viewModelScope.launch {
            try {
                val manga = mangaRepository.getMangaById(mangaId) ?: return@launch
                if (manga.url.isEmpty()) return@launch
                val fullUrl = if (manga.url.startsWith("http")) {
                    manga.url
                } else {
                    val source = sourceRepository.getSource(manga.sourceId.toString()) ?: return@launch
                    val baseUrl = source.baseUrl.trimEnd('/')
                    if (baseUrl.isEmpty()) return@launch
                    "$baseUrl/${manga.url.removePrefix("/")}"
                }
                _state.update { it.copy(mangaWebUrl = fullUrl) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(mangaWebUrl = null) }
            }
        }
    }

    private fun openInWebView() {
        val url = _state.value.mangaWebUrl ?: return
        viewModelScope.launch {
            _effect.send(DetailsContract.Effect.OpenInBrowser(url))
        }
    }

    private fun openWebViewFallback() {
        val url = _state.value.mangaWebUrl ?: return
        val title = _state.value.manga?.title.orEmpty()
        viewModelScope.launch {
            _effect.send(DetailsContract.Effect.NavigateToWebViewFallback(url, title))
        }
    }

    private fun loadMangaDetails() {
        mangaRepository.getMangaByIdFlow(mangaId)
            .onEach { manga ->
                // The hasAppliedChapterFlags check+mutation must happen outside _state.update's
                // lambda: update() retries its lambda on a failed compare-and-set, and mutating
                // a class member as a side effect inside it means a retry would see the guard
                // already flipped to true and skip restoring the sort order/filter entirely.
                if (!hasAppliedChapterFlags && manga != null) {
                    hasAppliedChapterFlags = true
                    _state.update { state ->
                        state.copy(
                            manga = manga,
                            isLoading = false,
                            chapterSortOrder = chapterSortOrderFromFlags(manga.chapterFlags),
                            chapterFilter = chapterFilterFromFlags(
                                flags = manga.chapterFlags,
                                scanlator = state.chapterFilter.scanlator,
                                chapterSearchQuery = state.chapterFilter.chapterSearchQuery,
                            ),
                        )
                    }
                } else {
                    _state.update { state -> state.copy(manga = manga, isLoading = false) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadChapters() {
        chapterRepository.getChaptersByMangaId(mangaId)
            .onEach { chapters ->
                val enrichedChapters = chapters.map { chapter ->
                    val (thumbnailUrl, totalPages) = getChapterThumbnailInfo(chapter)
                    chapter.toChapterItem(thumbnailUrl, totalPages)
                }
                _state.update { state ->
                    state.copy(
                        chapters = enrichedChapters,
                        isLoading = false
                    )
                }
                
                // Fetch thumbnails for downloaded chapters in background
                fetchThumbnailsForDownloadedChapters(chapters)
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Get cached thumbnail info or return null/0 if not available.
     */
    private fun getChapterThumbnailInfo(chapter: Chapter): Pair<String?, Int> {
        return thumbnailCache[chapter.id] ?: (null to 0)
    }
    
    /**
     * Fetch thumbnails for downloaded chapters in the background.
     * Only fetches for chapters that have been downloaded to avoid excessive network requests.
     */
    @Suppress("CognitiveComplexMethod")
    private fun fetchThumbnailsForDownloadedChapters(chapters: List<Chapter>) {
        viewModelScope.launch {
            val chaptersNeedingThumbnails = chapters.filter { chapter ->
                !thumbnailCache.containsKey(chapter.id) && chapter.lastPageRead > 0
            }.take(10)

            if (chaptersNeedingThumbnails.isEmpty()) return@launch

            val manga = _state.value.manga ?: return@launch

            supervisorScope {
                chaptersNeedingThumbnails.map { chapter ->
                    async {
                        try {
                            val sourceChapter = SourceChapter(
                                url = chapter.url,
                                name = chapter.name,
                                dateUpload = chapter.dateUpload,
                                chapterNumber = chapter.chapterNumber,
                                scanlator = chapter.scanlator ?: ""
                            )

                            // Use repository instead of calling source directly (#587)
                            val pages = sourceRepository.getPageList(manga.sourceId.toString(), sourceChapter)
                                .getOrNull() ?: return@async
                            if (pages.isNotEmpty()) {
                                val firstPageUrl = pages.first().imageUrl
                                thumbnailCache[chapter.id] = firstPageUrl to pages.size

                                _state.update { state ->
                                    val updatedChapters = state.chapters.map { item ->
                                        if (item.id == chapter.id) {
                                            item.copy(thumbnailUrl = firstPageUrl, totalPages = pages.size)
                                        } else item
                                    }
                                    state.copy(chapters = updatedChapters)
                                }
                            }
                        } catch (_: Exception) {
                            // Silently fail — thumbnails are optional
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun loadNextUnreadChapter() {
        viewModelScope.launch {
            val nextChapter = chapterRepository.getNextUnreadChapter(mangaId)
            _state.update { it.copy(nextUnreadChapter = nextChapter) }
        }
    }

    private fun observeStaticSettings() {
        mangaRepository.isFavorite(mangaId)
            .onEach { isFavorite ->
                _state.update { it.copy(isFavorite = isFavorite) }
            }
            .launchIn(viewModelScope)

        downloadRepository.observeDownloads()
            .onEach { downloads ->
                _state.update { state ->
                    val updatedChapters = state.chapters.map { chapter ->
                        val matchingDownload = downloads.firstOrNull { it.chapterId == chapter.id }
                        when (matchingDownload?.status) {
                            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PAUSED -> {
                                chapter.copy(downloadStatus = DetailsContract.DownloadStatus.DOWNLOADING)
                            }
                            DownloadStatus.COMPLETED -> {
                                chapter.copy(downloadStatus = DetailsContract.DownloadStatus.DOWNLOADED)
                            }
                            else -> chapter.copy(downloadStatus = DetailsContract.DownloadStatus.NOT_DOWNLOADED)
                        }
                    }
                    state.copy(chapters = updatedChapters)
                }
            }
            .launchIn(viewModelScope)

        combine(
            downloadPreferences.deleteAfterReading,
            downloadPreferences.perMangaOverrides
        ) { global, overrides ->
            Pair(global, overrides[mangaId] ?: DeleteAfterReadMode.INHERIT)
        }
            .onEach { (global, override) ->
                _state.update { state ->
                    state.copy(
                        globalDeleteAfterRead = global,
                        deleteAfterReadOverride = override
                    )
                }
            }
            .launchIn(viewModelScope)

        generalPreferences.autoThemeColor
            .onEach { enabled ->
                _state.update { it.copy(autoThemeEnabled = enabled) }
            }
            .launchIn(viewModelScope)

        statisticsRepository.getAverageChapterDurationMs()
            .onEach { ms -> _state.update { it.copy(averageChapterDurationMs = ms) } }
            .launchIn(viewModelScope)
    }

    private fun refreshData() {
        _state.update { it.copy(isRefreshing = true) }
        // Pull-to-refresh is also the retry path for metadata: it bypasses both the once-per-id
        // guard and the repository's 7-day TTL, so a fetch that failed earlier gets another go and
        // a user who wants a fresh score can ask for one.
        _state.value.anilistMediaId?.let { requestMetadataRefresh(it, force = true) }
        viewModelScope.launch {
            try {
                val manga = mangaRepository.getMangaById(mangaId)
                _state.update { it.copy(manga = manga, isRefreshing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun toggleFavorite() {
        viewModelScope.launch {
            val wasFavorite = _state.value.isFavorite
            try {
                mangaRepository.toggleFavorite(mangaId)
                if (wasFavorite) {
                    // Mirrors Komikku: removing keeps downloads by default, but if there are
                    // any, offer to delete them via an action snackbar instead of the plain
                    // confirmation — deletion only happens if the user taps the action.
                    if (hasDownloadedOrDownloadingChapters()) {
                        _effect.send(DetailsContract.Effect.ShowDeleteDownloadsPrompt)
                    } else {
                        _effect.send(DetailsContract.Effect.ShowSnackbar("Removed from library"))
                    }
                } else {
                    _effect.send(DetailsContract.Effect.ShowSnackbar("Added to library"))
                    // Mirrors Komikku: right after adding to library (not on remove), offer to
                    // file the manga into a category if any exist, instead of always leaving it
                    // uncategorized.
                    showCategoryPickerIfNeeded()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update library: ${e.message}"))
            }
        }
    }

    private fun hasDownloadedOrDownloadingChapters(): Boolean =
        _state.value.chapters.any {
            it.downloadStatus == DetailsContract.DownloadStatus.DOWNLOADED ||
                it.downloadStatus == DetailsContract.DownloadStatus.DOWNLOADING
        }

    private fun observeCategories() {
        categoryRepository.getCategories()
            .onEach { categories -> _state.update { it.copy(libraryCategories = categories) } }
            .launchIn(viewModelScope)
    }

    private fun showCategoryPickerIfNeeded() {
        if (_state.value.libraryCategories.isEmpty()) return
        _state.update { it.copy(showCategoryPickerDialog = true, categoryPickerSelection = emptySet()) }
    }

    private fun toggleCategoryPickerSelection(categoryId: Long) {
        _state.update {
            val selection = it.categoryPickerSelection
            val updated = if (categoryId in selection) selection - categoryId else selection + categoryId
            it.copy(categoryPickerSelection = updated)
        }
    }

    private fun confirmCategoryPicker() {
        val selection = _state.value.categoryPickerSelection
        _state.update { it.copy(showCategoryPickerDialog = false) }
        if (selection.isEmpty()) return
        viewModelScope.launch {
            try {
                selection.forEach { categoryId -> categoryRepository.addMangaToCategory(mangaId, categoryId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to assign categories"))
            }
        }
    }

    private fun observeReadingLists() {
        readingListRepository.getAllLists()
            .onEach { lists -> _state.update { it.copy(readingLists = lists) } }
            .launchIn(viewModelScope)
    }

    private fun showReadingListPicker() {
        viewModelScope.launch {
            try {
                val currentListIds = readingListRepository.getListsForManga(mangaId).first()
                    .map { it.listId }
                    .toSet()
                _state.update {
                    it.copy(showReadingListPickerDialog = true, readingListPickerSelection = currentListIds)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to load reading lists"))
            }
        }
    }

    /**
     * Toggles membership immediately — checking/unchecking a list persists right away. Serialized
     * by [readingListMutex] so rapid taps on multiple lists can't race their DB writes (and any
     * failure-rollback) against each other and desync the picker's state from the database.
     */
    private fun toggleReadingListPickerSelection(listId: Long) {
        viewModelScope.launch {
            readingListMutex.withLock {
                val wasSelected = listId in _state.value.readingListPickerSelection
                _state.update {
                    val selection = it.readingListPickerSelection
                    val updated = if (wasSelected) selection - listId else selection + listId
                    it.copy(readingListPickerSelection = updated)
                }
                try {
                    if (wasSelected) {
                        readingListRepository.removeMangaFromList(listId, mangaId)
                    } else {
                        readingListRepository.addMangaToList(listId, mangaId)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Revert the optimistic toggle and let the user know.
                    _state.update {
                        val selection = it.readingListPickerSelection
                        val reverted = if (wasSelected) selection + listId else selection - listId
                        it.copy(readingListPickerSelection = reverted)
                    }
                    _effect.send(DetailsContract.Effect.ShowError("Failed to update reading list"))
                }
            }
        }
    }

    private fun toggleUserCompleted() {
        viewModelScope.launch {
            val current = _state.value.manga?.userCompleted ?: false
            try {
                mangaRepository.markUserCompleted(mangaId, !current)
                val message = if (!current) "Marked as completed" else "Removed completed mark"
                _effect.send(DetailsContract.Effect.ShowSnackbar(message))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update: ${e.message}"))
            }
        }
    }

    private fun toggleUserDropped() {
        viewModelScope.launch {
            val current = _state.value.manga?.userDropped ?: false
            try {
                mangaRepository.markUserDropped(mangaId, !current)
                val message = if (!current) "Marked as dropped" else "Removed dropped mark"
                _effect.send(DetailsContract.Effect.ShowSnackbar(message))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update: ${e.message}"))
            }
        }
    }

    private fun toggleDescription() {
        _state.update { it.copy(descriptionExpanded = !it.descriptionExpanded) }
    }

    private fun toggleSortOrder() {
        val newOrder = when (_state.value.chapterSortOrder) {
            DetailsContract.ChapterSortOrder.ASCENDING -> DetailsContract.ChapterSortOrder.DESCENDING
            DetailsContract.ChapterSortOrder.DESCENDING -> DetailsContract.ChapterSortOrder.ASCENDING
        }
        _state.update { it.copy(chapterSortOrder = newOrder) }
        persistChapterFlags(newOrder, _state.value.chapterFilter)
    }

    private fun setChapterFilter(filter: DetailsContract.ChapterFilter) {
        _state.update { it.copy(chapterFilter = filter, showChapterFilter = false) }
        persistChapterFlags(_state.value.chapterSortOrder, filter)
    }

    private fun persistChapterFlags(sortOrder: DetailsContract.ChapterSortOrder, filter: DetailsContract.ChapterFilter) {
        viewModelScope.launch {
            try {
                mangaRepository.updateChapterFlags(mangaId, chapterFlagsOf(sortOrder, filter))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // In-memory state already reflects the change; only the persisted copy failed
                // to save, so the choice won't survive re-opening this manga.
                _effect.send(DetailsContract.Effect.ShowSnackbar("Failed to save chapter sort/filter"))
            }
        }
    }

    private fun startReading() {
        viewModelScope.launch {
            val firstChapter = _state.value.sortedChapters.firstOrNull()
            
            if (firstChapter != null) {
                _effect.send(
                    DetailsContract.Effect.NavigateToReader(mangaId, firstChapter.id)
                )
            } else {
                _effect.send(DetailsContract.Effect.ShowError("No chapters available"))
            }
        }
    }

    private fun continueReading() {
        viewModelScope.launch {
            val nextUnread = _state.value.nextUnreadChapter
            val chapterId = if (nextUnread != null) {
                nextUnread.id
            } else {
                (_state.value.chapters.firstOrNull { !it.read }
                    ?: _state.value.chapters.lastOrNull())?.id
            }

            if (chapterId != null) {
                _effect.send(DetailsContract.Effect.NavigateToReader(mangaId, chapterId))
            } else {
                _effect.send(DetailsContract.Effect.ShowError("No chapters available"))
            }
        }
    }

    private fun onChapterClick(chapterId: Long) {
        if (_state.value.selectedChapters.isNotEmpty()) {
            toggleChapterSelection(chapterId)
        } else {
            viewModelScope.launch {
                _effect.send(DetailsContract.Effect.NavigateToReader(mangaId, chapterId))
            }
        }
    }

    private fun onChapterLongClick(chapterId: Long) {
        toggleChapterSelection(chapterId)
    }

    private fun toggleChapterSelection(chapterId: Long) {
        _state.update { state ->
            val currentSelection = state.selectedChapters
            val newSelection = if (currentSelection.contains(chapterId)) {
                currentSelection - chapterId
            } else {
                currentSelection + chapterId
            }
            state.copy(selectedChapters = newSelection)
        }
    }

    private fun clearChapterSelection() {
        _state.update { it.copy(selectedChapters = emptySet()) }
    }

    private fun selectAllChapters() {
        _state.update { state ->
            // Only the currently visible (filtered/searched) chapters — matches Mihon/Komikku,
            // so an active filter never selects chapters the user can't see.
            val visibleIds = state.sortedChapters.map { it.id }.toSet()
            state.copy(selectedChapters = state.selectedChapters + visibleIds)
        }
    }

    /**
     * Selects every visible chapter not currently selected and deselects the visible ones that
     * are (Komikku parity). Restricted to [State.sortedChapters] so a filter/search can't flip
     * the selection state of hidden chapters.
     */
    private fun invertChapterSelection() {
        _state.update { state ->
            val visibleIds = state.sortedChapters.map { it.id }.toSet()
            val keptHidden = state.selectedChapters - visibleIds
            state.copy(selectedChapters = keptHidden + (visibleIds - state.selectedChapters))
        }
    }

    private fun downloadSelectedChapters() {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedChapters
            val manga = _state.value.manga
            val chapters = _state.value.chapters.filter { selectedIds.contains(it.id) }
            val mangaTitle = manga?.title ?: "Manga"
            val sourceName = manga?.sourceId?.let { sourceRepository.resolveDownloadFolderName(it) } ?: ""

            val enqueuedCount = try {
                enqueueChapters(chapters, sourceName, mangaTitle)
            } catch (e: DownloadBlockedException) {
                _effect.send(DetailsContract.Effect.ShowSnackbar(e.message ?: "Download blocked"))
                return@launch
            }
            clearChapterSelection()
            val failCount = chapters.size - enqueuedCount
            if (failCount > 0) {
                _effect.send(DetailsContract.Effect.ShowSnackbar("$enqueuedCount chapter(s) added, $failCount failed"))
            } else {
                _effect.send(DetailsContract.Effect.ShowSnackbar("${chapters.size} chapter(s) added to download queue"))
            }
        }
    }

    private fun deleteSelectedChapters() {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedChapters
            val manga = _state.value.manga
            val chapters = _state.value.chapters.filter { selectedIds.contains(it.id) }

            if (manga != null) {
                val sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId)
                chapters.forEach { chapter ->
                    downloadRepository.deleteChapterDownload(
                        chapterId = chapter.id,
                        sourceName = sourceName,
                        mangaTitle = manga.title,
                        chapterTitle = chapter.name
                    )
                }
            }
            clearChapterSelection()
            _effect.send(DetailsContract.Effect.ShowSnackbar("Deleted ${chapters.size} download(s)"))
        }
    }

    private fun markSelectedAsRead() {
        viewModelScope.launch {
            try {
                val selectedIds = _state.value.selectedChapters.toList()
                if (selectedIds.isNotEmpty()) {
                    chapterRepository.updateChapterProgress(
                        chapterIds = selectedIds,
                        read = true,
                        lastPageRead = 0
                    )
                    clearChapterSelection()
                    _effect.send(DetailsContract.Effect.ShowSnackbar("Marked ${selectedIds.size} chapter(s) as read"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to mark chapters as read: ${e.message}"))
            }
        }
    }

    private fun markSelectedAsUnread() {
        viewModelScope.launch {
            try {
                val selectedIds = _state.value.selectedChapters.toList()
                if (selectedIds.isNotEmpty()) {
                    chapterRepository.updateChapterProgress(
                        chapterIds = selectedIds,
                        read = false,
                        lastPageRead = 0
                    )
                    clearChapterSelection()
                    _effect.send(DetailsContract.Effect.ShowSnackbar("Marked ${selectedIds.size} chapter(s) as unread"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to mark chapters as unread: ${e.message}"))
            }
        }
    }

    private fun toggleChapterRead(chapterId: Long) {
        viewModelScope.launch {
            try {
                val chapter = _state.value.chapters.find { it.id == chapterId }
                chapter?.let {
                    chapterRepository.updateChapterProgress(
                        chapterId = chapterId,
                        read = !it.read,
                        lastPageRead = if (!it.read) 0 else it.lastPageRead
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update chapter: ${e.message}"))
            }
        }
    }

    private fun downloadChapter(chapterId: Long) {
        viewModelScope.launch {
            val chapter = _state.value.chapters.firstOrNull { it.id == chapterId }
            val manga = _state.value.manga
            val mangaTitle = manga?.title ?: "Manga"
            val sourceName = manga?.sourceId?.let { sourceRepository.resolveDownloadFolderName(it) } ?: ""

            if (chapter != null) {
                try {
                    downloadRepository.enqueueChapter(
                        mangaId = chapter.mangaId,
                        chapterId = chapter.id,
                        sourceName = sourceName,
                        mangaTitle = mangaTitle,
                        chapterTitle = chapter.name
                    )
                    _effect.send(DetailsContract.Effect.ShowSnackbar("Download added to queue"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DownloadBlockedException) {
                    _effect.send(DetailsContract.Effect.ShowSnackbar(e.message ?: "Download blocked"))
                } catch (e: Exception) {
                    _effect.send(DetailsContract.Effect.ShowError("Failed to queue download: ${e.message}"))
                }
            }
        }
    }

    private fun downloadAllChapters(unreadOnly: Boolean) {
        viewModelScope.launch {
            val manga = _state.value.manga ?: return@launch
            val sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId)
            val chapters = if (unreadOnly) {
                _state.value.chapters.filter { !it.read }
            } else {
                _state.value.chapters
            }
            if (chapters.isEmpty()) return@launch

            val enqueuedCount = try {
                enqueueChapters(chapters, sourceName, manga.title)
            } catch (e: DownloadBlockedException) {
                _effect.send(DetailsContract.Effect.ShowSnackbar(e.message ?: "Download blocked"))
                return@launch
            }
            val label = if (unreadOnly) "unread" else "all"
            val failCount = chapters.size - enqueuedCount
            if (failCount > 0) {
                _effect.send(DetailsContract.Effect.ShowSnackbar("$enqueuedCount $label chapter(s) added, $failCount failed"))
            } else {
                _effect.send(DetailsContract.Effect.ShowSnackbar("${chapters.size} $label chapters added to queue"))
            }
        }
    }

    private suspend fun enqueueChapters(
        chapters: List<DetailsContract.ChapterItem>,
        sourceName: String,
        mangaTitle: String
    ): Int {
        var enqueuedCount = 0
        for (chapter in chapters) {
            try {
                downloadRepository.enqueueChapter(
                    mangaId = chapter.mangaId,
                    chapterId = chapter.id,
                    sourceName = sourceName,
                    mangaTitle = mangaTitle,
                    chapterTitle = chapter.name
                )
                enqueuedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadBlockedException) {
                throw e
            } catch (_: Exception) {
                // chapter failed to enqueue - continue with others
            }
        }
        return enqueuedCount
    }

    private fun deleteChapterDownload(chapterId: Long) {
        viewModelScope.launch {
            val chapter = _state.value.chapters.firstOrNull { it.id == chapterId }
            val manga = _state.value.manga
            if (chapter == null || manga == null) return@launch

            try {
                // The same download icon tap doubles as "cancel" while a chapter is
                // mid-download and as "delete" once it has finished — there's nothing
                // downloaded yet to delete while DOWNLOADING, so deleteChapterDownload()
                // would be a silent no-op there.
                if (chapter.downloadStatus == DetailsContract.DownloadStatus.DOWNLOADING) {
                    downloadRepository.cancelDownload(chapterId)
                    _effect.send(DetailsContract.Effect.ShowSnackbar("Download cancelled"))
                } else {
                    downloadRepository.deleteChapterDownload(
                        chapterId = chapterId,
                        sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId),
                        mangaTitle = manga.title,
                        chapterTitle = chapter.name
                    )
                    _effect.send(DetailsContract.Effect.ShowSnackbar("Download removed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update download: ${e.message}"))
            }
        }
    }

    private fun exportChapterAsCbz(chapterId: Long) {
        viewModelScope.launch {
            val chapter = _state.value.chapters.firstOrNull { it.id == chapterId }
            val manga = _state.value.manga
            if (chapter == null || manga == null) {
                _effect.send(DetailsContract.Effect.ShowError("Chapter not found"))
                return@launch
            }
            downloadRepository.exportChapterAsCbz(
                sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId),
                mangaTitle = manga.title,
                chapterTitle = chapter.name
            ).fold(
                onSuccess = { _effect.send(DetailsContract.Effect.ShowSnackbar("Exported as CBZ")) },
                onFailure = {
                    val reason = it.message ?: "Unknown error"
                    _effect.send(DetailsContract.Effect.ShowError("Export failed: $reason"))
                }
            )
        }
    }

    private fun markPreviousAsRead(chapterId: Long) {
        viewModelScope.launch {
            try {
                val chapters = _state.value.chapters
                val targetChapter = chapters.find { it.id == chapterId }
                targetChapter?.let { target ->
                    val chapterIdsToUpdate = chapters
                        .filter { it.chapterNumber < target.chapterNumber && !it.read }
                        .map { it.id }

                    if (chapterIdsToUpdate.isNotEmpty()) {
                        chapterRepository.updateChapterProgress(
                            chapterIds = chapterIdsToUpdate,
                            read = true,
                            lastPageRead = 0
                        )
                    }
                }
                _effect.send(DetailsContract.Effect.ShowSnackbar("Marked previous chapters as read"))
                // Exit selection mode like the other batch actions. Harmless no-op when invoked
                // from the per-chapter context menu (no selection is active there).
                clearChapterSelection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to mark chapters: ${e.message}"))
            }
        }
    }

    /**
     * Returns a fully-qualified shareable URL, or null if [manga.url] is a source-relative path
     * (i.e., does not start with "http://" or "https://").
     */
    private fun buildShareUrl(manga: Manga): String? {
        val url = manga.url
        return if (url.startsWith("http://") || url.startsWith("https://")) url else null
    }

    private fun shareManga() {
        viewModelScope.launch {
            val manga = _state.value.manga
            if (manga != null) {
                _effect.send(
                    DetailsContract.Effect.ShareManga(
                        title = manga.title,
                        url = buildShareUrl(manga) ?: ""
                    )
                )
            }
        }
    }

    private fun openDownloadFolder() {
        viewModelScope.launch {
            val manga = _state.value.manga ?: return@launch
            _effect.send(
                DetailsContract.Effect.OpenDownloadFolder(
                    sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId),
                    mangaTitle = manga.title,
                )
            )
        }
    }

    private fun clearMangaDownloads() {
        viewModelScope.launch {
            val state = _state.value
            val manga = state.manga ?: return@launch
            val downloadedChapters = state.chapters.filter {
                it.downloadStatus == DetailsContract.DownloadStatus.DOWNLOADED ||
                    it.downloadStatus == DetailsContract.DownloadStatus.DOWNLOADING
            }
            if (downloadedChapters.isEmpty()) {
                _effect.send(DetailsContract.Effect.ShowSnackbar("No downloaded chapters to clear"))
                return@launch
            }
            var cleared = 0
            val sourceName = sourceRepository.resolveDownloadFolderName(manga.sourceId)
            downloadedChapters.forEach { chapter ->
                try {
                    downloadRepository.deleteChapterDownload(
                        chapterId = chapter.id,
                        sourceName = sourceName,
                        mangaTitle = manga.title,
                        chapterTitle = chapter.name,
                    )
                    cleared++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { }
            }
            _effect.send(DetailsContract.Effect.ShowSnackbar("$cleared chapter download(s) cleared"))
        }
    }

    private fun setDeleteAfterReadOverride(mode: DeleteAfterReadMode) {
        viewModelScope.launch {
            downloadPreferences.setOverride(mangaId, mode)
        }
    }

    private fun showNoteEditor() {
        val currentNote = _state.value.manga?.notes ?: ""
        _state.update { it.copy(noteEditorVisible = true, noteEditorText = currentNote) }
    }

    private fun hideNoteEditor() {
        _state.update { it.copy(noteEditorVisible = false) }
    }

    private fun showChapterNoteEditor(chapterId: Long) {
        val current = _state.value.chapters.firstOrNull { it.id == chapterId }?.userNotes ?: ""
        _state.update { it.copy(chapterNoteEditorChapterId = chapterId, chapterNoteEditorText = current) }
    }

    private fun saveChapterNote() {
        val chapterId = _state.value.chapterNoteEditorChapterId ?: return
        viewModelScope.launch {
            val text = _state.value.chapterNoteEditorText.trim().ifEmpty { null }
            try {
                // The chapter Flow re-emits after this write, refreshing the list's note indicator.
                chapterRepository.updateChapterNotes(chapterId, text)
                _state.update { it.copy(chapterNoteEditorChapterId = null) }
                _effect.send(DetailsContract.Effect.ShowSnackbar("Note saved"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to save note: ${e.message}"))
            }
        }
    }

    private fun updateNoteText(text: String) {
        _state.update { it.copy(noteEditorText = text) }
    }

    private fun saveNote() {
        viewModelScope.launch {
            val text = _state.value.noteEditorText.trim().ifEmpty { null }
            try {
                updateMangaNote(mangaId, text)
                _state.update { it.copy(noteEditorVisible = false) }
                _effect.send(DetailsContract.Effect.ShowSnackbar("Note saved"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = buildString {
                    append("Failed to save note")
                    val detail = e.message
                    if (!detail.isNullOrBlank()) {
                        append(": ")
                        append(detail)
                    }
                }
                _effect.send(DetailsContract.Effect.ShowError(errorMessage))
            }
        }
    }

    /**
     * Tri-state cycle for the per-manga theme override:
     *   null (inherit) → true (force on) → false (force off) → null
     */
    private fun cycleMangaThemeOverride() {
        val manga = _state.value.manga ?: return
        val next: Boolean? = when (manga.mangaThemeOverride) {
            null -> true
            true -> false
            false -> null
        }
        // Optimistic update so the UI reflects the new state immediately
        _state.update { it.copy(manga = manga.copy(mangaThemeOverride = next)) }
        viewModelScope.launch {
            try {
                mangaRepository.updateMangaThemeOverride(manga.id, next)
                val message = when (next) {
                    null -> "Theme: following app setting"
                    true -> "Theme: cover colors"
                    false -> "Theme: app default"
                }
                _effect.send(DetailsContract.Effect.ShowSnackbar(message))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Roll back optimistic update on failure
                _state.update { it.copy(manga = manga) }
                _effect.send(DetailsContract.Effect.ShowError("Failed to update theme: ${e.message}"))
            }
        }
    }

    private fun toggleNotifications() {
        viewModelScope.launch {
            val manga = _state.value.manga ?: return@launch
            try {
                setMangaNotifications(manga.id, !manga.notifyNewChapters)
                val message = if (manga.notifyNewChapters) {
                    "Notifications muted for ${manga.title}"
                } else {
                    "Notifications enabled for ${manga.title}"
                }
                _effect.send(DetailsContract.Effect.ShowSnackbar(message))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update notification setting"))
            }
        }
    }

    // Per-manga reader settings (#260)
    private fun setReaderDirection(direction: Int?) {
        viewModelScope.launch {
            try {
                mangaRepository.updateReaderDirection(mangaId, direction)
                _effect.send(DetailsContract.Effect.ShowSnackbar("Reader direction updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update reader direction"))
            }
        }
    }

    private fun setReaderMode(mode: Int?) {
        viewModelScope.launch {
            try {
                mangaRepository.updateReaderMode(mangaId, mode)
                _effect.send(DetailsContract.Effect.ShowSnackbar("Reader mode updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update reader mode"))
            }
        }
    }

    private fun setReaderColorFilter(filter: Int?) {
        viewModelScope.launch {
            try {
                mangaRepository.updateReaderColorFilter(mangaId, filter)
                _effect.send(DetailsContract.Effect.ShowSnackbar("Color filter updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update color filter"))
            }
        }
    }

    private fun setReaderCustomTintColor(color: Long?) {
        viewModelScope.launch {
            try {
                mangaRepository.updateReaderCustomTintColor(mangaId, color)
                _effect.send(DetailsContract.Effect.ShowSnackbar("Custom tint color updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update tint color"))
            }
        }
    }

    private fun setReaderBackgroundColor(color: Long?) {
        viewModelScope.launch {
            try {
                mangaRepository.updateReaderBackgroundColor(mangaId, color)
                _effect.send(DetailsContract.Effect.ShowSnackbar("Background color updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update background color"))
            }
        }
    }

    // Page preloading settings (#264)
    private fun setPreloadPagesBefore(count: Int?) {
        viewModelScope.launch {
            try {
                mangaRepository.updatePreloadPagesBefore(mangaId, count)
                _effect.send(DetailsContract.Effect.ShowSnackbar("Preload pages (before) updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update preload setting"))
            }
        }
    }

    private fun setPreloadPagesAfter(count: Int?) {
        viewModelScope.launch {
            try {
                mangaRepository.updatePreloadPagesAfter(mangaId, count)
                _effect.send(DetailsContract.Effect.ShowSnackbar("Preload pages (after) updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to update preload setting"))
            }
        }
    }

    private fun resetReaderSettings() {
        viewModelScope.launch {
            try {
                mangaRepository.updateReaderDirection(mangaId, null)
                mangaRepository.updateReaderMode(mangaId, null)
                mangaRepository.updateReaderColorFilter(mangaId, null)
                mangaRepository.updateReaderCustomTintColor(mangaId, null)
                mangaRepository.updateReaderBackgroundColor(mangaId, null)
                mangaRepository.updatePreloadPagesBefore(mangaId, null)
                mangaRepository.updatePreloadPagesAfter(mangaId, null)
                _effect.send(DetailsContract.Effect.ShowSnackbar("Reader settings reset to global"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to reset reader settings"))
            }
        }
    }

    /**
     * Load thumbnail for a specific chapter on demand.
     * Called when user taps "Load preview" on a chapter without a thumbnail.
     */
    private fun loadChapterThumbnail(chapterId: Long) {
        viewModelScope.launch {
            val chapter = _state.value.chapters.find { it.id == chapterId } ?: return@launch
            val manga = _state.value.manga ?: return@launch
            
            // Don't reload if already in cache
            if (thumbnailCache.containsKey(chapterId)) return@launch
            
            _effect.send(DetailsContract.Effect.ShowSnackbar("Loading preview..."))

            try {
                val sourceChapter = SourceChapter(
                    url = chapter.url,
                    name = chapter.name,
                    dateUpload = chapter.dateUpload,
                    chapterNumber = chapter.chapterNumber,
                    scanlator = chapter.scanlator ?: ""
                )

                // Use repository to respect caching and abstraction layers (#587)
                val pages = sourceRepository.getPageList(manga.sourceId.toString(), sourceChapter)
                    .getOrElse {
                        _effect.send(DetailsContract.Effect.ShowError("Source not available"))
                        return@launch
                    }
                if (pages.isNotEmpty()) {
                    val firstPageUrl = pages.first().imageUrl
                    thumbnailCache[chapterId] = firstPageUrl to pages.size

                    // Update the chapter in state
                    _state.update { state ->
                        val updatedChapters = state.chapters.map { item ->
                            if (item.id == chapterId) {
                                item.copy(
                                    thumbnailUrl = firstPageUrl,
                                    totalPages = pages.size
                                )
                            } else item
                        }
                        state.copy(chapters = updatedChapters)
                    }

                    _effect.send(DetailsContract.Effect.ShowSnackbar("Preview loaded"))
                } else {
                    _effect.send(DetailsContract.Effect.ShowError("No pages found"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(
                    DetailsContract.Effect.ShowError("Failed to load preview: ${e.message ?: "Unknown error"}")
                )
            }
        }
    }

    companion object {
        const val MANGA_ID_ARG = "mangaId"
    }

    // --- Source Suggestions ---

    private fun loadSourceSuggestions() {
        viewModelScope.launch {
            val manga = _state.value.manga ?: return@launch

            _state.update { it.copy(isLoadingSourceSuggestions = true, sourceSuggestionsError = null) }

            try {
                val source = sourceRepository.getSource(manga.sourceId.toString())
                if (source == null) {
                    _state.update {
                        it.copy(
                            isLoadingSourceSuggestions = false,
                            sourceSuggestionsError = "Source not available"
                        )
                    }
                    return@launch
                }

                // Search by author name — the same strategy Komikku uses to surface
                // related titles without needing a dedicated getRelatedManga() API.
                // Falls back to title keywords when author is unknown.
                val author = manga.author?.takeIf { it.isNotBlank() }
                val query = author ?: manga.title
                val reason = if (author != null) "Same author" else "From ${source.name}"

                val result = sourceRepository.searchManga(
                    sourceId = manga.sourceId.toString(),
                    query = query,
                    page = 1,
                )

                result.onSuccess { mangaPage ->
                    val suggestions = mangaPage.mangas
                        .filter { it.url != manga.url }
                        .take(6)
                        .map { sourceManga ->
                            SourceSuggestion(
                                title = sourceManga.title,
                                thumbnailUrl = sourceManga.thumbnailUrl,
                                mangaUrl = sourceManga.url,
                                sourceId = source.id,
                                sourceName = source.name,
                                reason = reason,
                            )
                        }
                    _state.update {
                        it.copy(
                            sourceSuggestions = suggestions,
                            isLoadingSourceSuggestions = false,
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoadingSourceSuggestions = false,
                            sourceSuggestionsError = error.message ?: "Failed to load suggestions",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingSourceSuggestions = false,
                        sourceSuggestionsError = e.message ?: "Failed to load suggestions",
                    )
                }
            }
        }
    }

    private fun onSourceSuggestionClick(suggestion: SourceSuggestion) {
        viewModelScope.launch {
            // Navigate to global search with the suggestion title
            _effect.send(DetailsContract.Effect.NavigateToGlobalSearch(suggestion.title))
        }
    }

    private fun togglePanoramaCover() {
        _state.update { it.copy(showPanoramaCover = !it.showPanoramaCover) }
    }

    private fun saveMangaInfo(event: DetailsContract.Event.SaveMangaInfo) {
        viewModelScope.launch {
            try {
                mangaRepository.updateLocalOverrides(
                    id = mangaId,
                    title = event.title,
                    description = event.description,
                    author = event.author,
                    artist = event.artist,
                    thumbnailUrl = event.thumbnailUrl,
                    genres = event.genres,
                    status = event.status,
                )
                _state.update { it.copy(isEditInfoSheetVisible = false) }
                _effect.send(DetailsContract.Effect.ShowSnackbar("Manga info updated"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to save manga info: ${e.message}"))
            }
        }
    }

    private fun resetMangaInfo() {
        viewModelScope.launch {
            try {
                mangaRepository.clearLocalOverrides(mangaId)
                _state.update { it.copy(isEditInfoSheetVisible = false) }
                _effect.send(DetailsContract.Effect.ShowSnackbar("Manga info reset to source data"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to reset manga info: ${e.message}"))
            }
        }
    }

    // Serializes set/remove cover operations: each mutates files + DB, so two running
    // concurrently (rapid taps) could leave the DB pointing at a deleted file.
    private val coverMutex = Mutex()

    // Serializes reading-list picker toggles: each write (+ potential failure-rollback) must
    // complete before the next tap's write starts, or rapid clicks can race and desync the
    // picker's optimistic state from the database.
    private val readingListMutex = Mutex()

    private fun setCustomCover(imageUri: String) {
        viewModelScope.launch {
            try {
                coverMutex.withLock { mangaRepository.setCustomCover(mangaId, imageUri) }
                _effect.send(DetailsContract.Effect.ShowSnackbar("Custom cover set"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to set custom cover: ${e.message}"))
            }
        }
    }

    private fun removeCustomCover() {
        viewModelScope.launch {
            try {
                coverMutex.withLock { mangaRepository.removeCustomCover(mangaId) }
                _effect.send(DetailsContract.Effect.ShowSnackbar("Custom cover removed"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(DetailsContract.Effect.ShowError("Failed to remove custom cover: ${e.message}"))
            }
        }
    }
}
