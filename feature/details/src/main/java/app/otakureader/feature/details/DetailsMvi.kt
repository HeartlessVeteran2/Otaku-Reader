@file:Suppress("MatchingDeclarationName")
package app.otakureader.feature.details

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.core.preferences.DeleteAfterReadMode
import app.otakureader.domain.model.AniListLink
import app.otakureader.domain.model.AniListMediaCandidate
import app.otakureader.domain.model.Category
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaMetadata
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.ReadingList
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackerType
import app.otakureader.core.ui.theme.OtakuColors
import androidx.annotation.StringRes

/**
 * Contract for MVI pattern in Details Screen
 */
object DetailsContract {

    /**
     * UI State for Details Screen
     */
    data class State(
        val isLoading: Boolean = true,
        val manga: Manga? = null,
        val chapters: List<ChapterItem> = emptyList(),
        val selectedChapters: Set<Long> = emptySet(),
        val isFavorite: Boolean = false,
        val descriptionExpanded: Boolean = false,
        val chapterSortOrder: ChapterSortOrder = ChapterSortOrder.DESCENDING,
        /** Active chapter list filter. */
        val chapterFilter: ChapterFilter = ChapterFilter(),
        val error: String? = null,
        val isRefreshing: Boolean = false,
        val nextUnreadChapter: Chapter? = null,
        val deleteAfterReadOverride: DeleteAfterReadMode = DeleteAfterReadMode.INHERIT,
        val globalDeleteAfterRead: Boolean = false,
        val noteEditorVisible: Boolean = false,
        val noteEditorText: String = "",
        /** Chapter whose note is being edited (per-chapter notes), or null if closed. */
        val chapterNoteEditorChapterId: Long? = null,
        val chapterNoteEditorText: String = "",

        /** Source suggestions (related titles from the source website). */
        val sourceSuggestions: List<SourceSuggestion> = emptyList(),
        /** True while loading source suggestions. */
        val isLoadingSourceSuggestions: Boolean = false,
        /** Error message for source suggestions loading failure. */
        val sourceSuggestionsError: String? = null,
        /** Whether the chapter filter bottom-sheet is currently visible. */
        val showChapterFilter: Boolean = false,
        /** Whether to show panorama cover (wide banner) instead of square thumbnail. */
        val showPanoramaCover: Boolean = false,
        /** Whether automatic theme color extraction from cover is enabled. */
        val autoThemeEnabled: Boolean = true,
        /** User's average per-chapter reading duration in milliseconds; used to estimate remaining time. */
        val averageChapterDurationMs: Long = 0L,
        /** Whether the Edit Manga Info bottom-sheet is open (#998). */
        val isEditInfoSheetVisible: Boolean = false,
        /**
         * One entry per tracker service this manga is linked to.
         *
         * This used to be an `Int` count, because the count was all the action row rendered. The
         * entries were already being collected and thrown away, so the status/score/progress chips
         * cost a state-shape change and nothing else.
         */
        val trackEntries: List<TrackEntry> = emptyList(),
        /**
         * Display names keyed by tracker id, resolved from the registered [Tracker] set.
         *
         * Kept beside [trackEntries] rather than duplicated into each one: it is the same handful
         * of names for every manga, and a [TrackEntry] is a domain model that has no business
         * carrying a UI label.
         */
        val trackerNames: Map<Int, String> = emptyMap(),
        /**
         * The raw cached AniList record for this manga, whatever media it happens to describe.
         *
         * **Do not render this — render [metadata].** The cache row is keyed by `mangaId` alone, so
         * it survives a change to which AniList media the manga is linked to. See [metadata].
         */
        val cachedMetadata: MangaMetadata? = null,
        /** True only while a metadata fetch is in flight. Never blocks what is already cached. */
        val isMetadataLoading: Boolean = false,
        /**
         * The stored AniList link for this manga, for the case where no tracker entry supplies one.
         *
         * Durable, unlike [cachedMetadata] — see `AniListLinkRepository`. Null means nothing has
         * matched this manga yet, which is also true while auto-matching is still running.
         */
        val anilistLink: AniListLink? = null,
        /** True while auto-matching is searching AniList for this manga's media id. */
        val isMatchingAniList: Boolean = false,
        /**
         * Whether each input that auto-matching depends on has produced its first value yet.
         *
         * These exist because `null` and `emptyList()` are ambiguous: they mean both "not loaded"
         * and "loaded, and there is nothing". Auto-matching has to tell those apart, or it decides
         * a manga is untracked and unlinked purely because two Room flows had not emitted yet.
         */
        val hasLoadedTrackEntries: Boolean = false,
        val hasLoadedLink: Boolean = false,
        val hasLoadedMetadata: Boolean = false,
        /** Non-null while the wrong-match picker is open. See [AniListPickerState]. */
        val anilistPicker: AniListPickerState? = null,
        /** Full web URL for this manga (source baseUrl + manga url). Null for local sources. */
        val mangaWebUrl: String? = null,
        /** Display name of the manga's source, shown next to the status (Komikku parity). */
        val sourceName: String? = null,

        /** User-defined library categories, for the "add to library" category picker. */
        val libraryCategories: List<Category> = emptyList(),
        /** Shown right after favoriting, so the manga can be filed into a category immediately. */
        val showCategoryPickerDialog: Boolean = false,
        /** Category IDs checked in the currently-open [showCategoryPickerDialog]. */
        val categoryPickerSelection: Set<Long> = emptySet(),

        /** User-defined reading lists, for the "add to reading list" picker. */
        val readingLists: List<ReadingList> = emptyList(),
        /** Whether the reading-list picker dialog is open. */
        val showReadingListPickerDialog: Boolean = false,
        /** IDs of the reading lists this manga currently belongs to; toggled live from the picker. */
        val readingListPickerSelection: Set<Long> = emptySet(),
    ) : UiState {

        /** Number of active tracker services for this manga. 0 = not tracked. */
        val trackingCount: Int
            get() = trackEntries.size

        /**
         * The AniList media id this manga is linked to, from whichever source knows it.
         *
         * **The tracker entry wins.** `TrackEntry.remoteId` for the AniList tracker *is* the AniList
         * media id — that is the id `AniListTracker.find()` queries `Media(id:)` with — so it is
         * authoritative: the user linked it there themselves, and it is the id progress is written
         * back against. A stored link is the fallback for manga that aren't tracked on AniList.
         *
         * The order matters beyond preference. [metadata] hides cached metadata whose `anilistId`
         * disagrees with this value, so if a stale stored link could shadow a live tracker entry,
         * correcting the tracker link would stop invalidating the cache.
         */
        val anilistMediaId: Long?
            get() = trackEntries.firstOrNull { it.trackerId == TrackerType.ANILIST }?.remoteId
                ?: anilistLink?.anilistId

        /**
         * Whether this manga still needs an AniList media id found for it.
         *
         * Both sources have to be absent, not just the link: a manga tracked on AniList already has
         * its id from the tracker, and searching for one would spend a request to rediscover
         * something authoritative that is already in hand.
         */
        val needsAniListMatch: Boolean
            get() = trackEntries.none { it.trackerId == TrackerType.ANILIST } && anilistLink == null

        /**
         * Whether auto-matching can start: every input it reads has loaded, and none supplied an id.
         *
         * Derived rather than checked inside one collector, and that is the whole point. Matching
         * reads four independently-collected things — the manga, the tracker entries, the stored
         * link and the cached synonyms — and a trigger living in any single collector fires on
         * whatever that one emitted, whether or not the others have. Two concrete failures:
         *
         * - The link flow emits its initial `null` before `manga` loads. A trigger in that
         *   collector finds no title, gives up, and nothing calls it again — the link flow has no
         *   reason to re-emit — so the manga stays unmatched for the whole screen visit.
         * - The metadata flow emits after matching starts, so [MangaMetadata.synonyms] are empty at
         *   search time and the fallback searches that exist to rescue a renamed source never run.
         *
         * Gating on all four first-emissions costs nothing: every one is a Room flow, so each is
         * guaranteed to emit exactly once up front.
         */
        val isReadyToMatchAniList: Boolean
            get() = manga != null &&
                hasLoadedTrackEntries &&
                hasLoadedLink &&
                hasLoadedMetadata &&
                needsAniListMatch

        /**
         * The cached metadata, but only when it still describes the media this manga is linked to.
         *
         * The cache row is keyed by `mangaId`, not by AniList id, and `AniListMetadataRepository`
         * deliberately never deletes it on a failed refresh — losing yesterday's metadata because
         * today's request timed out would be strictly worse than showing yesterday's. That is right
         * for a cache and wrong for a screen, and the two cases where it diverges are both ones the
         * user just caused:
         *
         * - **The link was corrected.** `remoteId` changes while `mangaId` stays put. Until the
         *   refetch lands — or forever, if the device is offline — the row still holds the *old*
         *   media, so the chips would say one thing and the tags, score and synonyms another.
         * - **AniList was unlinked entirely.** There is no live link at all, yet the row is still
         *   there, so an AniList section would keep rendering for a manga that is no longer tracked.
         *
         * Deriving it here rather than filtering inside the metadata collector is what makes it
         * safe: this is a pure function of two fields already in state, so it re-evaluates whenever
         * *either* changes. A point-in-time filter in one of the two collectors would be wrong in
         * both directions — metadata arriving before the tracker entries would be dropped and never
         * reconsidered, and a link change would leave already-accepted metadata sitting in state.
         *
         * The stale row is left in the database on purpose. It costs nothing, it is reclaimed by
         * the `ON DELETE CASCADE` from `manga`, and re-linking to the same media makes it usable
         * again instantly — so deleting it would only buy a redundant refetch.
         */
        val metadata: MangaMetadata?
            get() = cachedMetadata?.takeIf { it.anilistId == anilistMediaId }

        /** Estimated time remaining to finish all unread chapters of this manga. */
        val estimatedRemainingTimeMs: Long
            get() = averageChapterDurationMs * chapters.count { !it.read }
        
        val canStartReading: Boolean
            get() = chapters.isNotEmpty()
        
        val hasUnreadChapters: Boolean
            get() = chapters.any { !it.read }

        val hasStartedReading: Boolean
            get() = chapters.any { it.read || it.lastPageRead > 0 }
        
        val sortedChapters: List<ChapterItem>
            get() {
                val filtered = chapterFilter.apply(chapters)
                return when (chapterSortOrder) {
                    ChapterSortOrder.ASCENDING -> filtered.sortedBy { it.chapterNumber }
                    ChapterSortOrder.DESCENDING -> filtered.sortedByDescending { it.chapterNumber }
                }
            }
        
        val groupedChapters: Map<String?, List<ChapterItem>>
            get() = sortedChapters.groupBy { it.volume }

        val isDeleteAfterReadEnabled: Boolean
            get() = when (deleteAfterReadOverride) {
                DeleteAfterReadMode.ENABLED -> true
                DeleteAfterReadMode.DISABLED -> false
                DeleteAfterReadMode.INHERIT -> globalDeleteAfterRead
            }

        val missingChapterCount: Int
            get() {
                val nums = chapters
                    .map { it.chapterNumber }
                    .filter { it >= 0 }
                    .map { it.toInt() }
                    .distinct()
                    .sorted()
                if (nums.size < 2) return 0
                var missing = 0
                var prev = nums.first()
                for (curr in nums.drop(1)) {
                    if (curr > prev + 1) missing += curr - prev - 1
                    prev = curr
                }
                return missing
            }
    }

    /**
     * The wrong-match picker: a search box seeded with the manga's title, and what it found.
     *
     * Exists because auto-matching refuses to guess. Below `ACCEPT_THRESHOLD` nothing is stored and
     * nothing renders, which is the right default — a wrong synopsis and wrong tags look
     * authoritative and give the user no reason to doubt them — but it leaves the user with no
     * metadata and no recourse. This is the recourse.
     *
     * The query is editable rather than fixed to the manga's title, because the case that needs
     * fixing is precisely the one where the source's title is not what AniList calls the work.
     */
    data class AniListPickerState(
        val query: String = "",
        val results: List<AniListMediaCandidate> = emptyList(),
        val isSearching: Boolean = false,
        /**
         * True when the search itself failed, as opposed to finding nothing.
         *
         * A flag rather than the exception, and deliberately not its message. `Throwable.message`
         * is nullable, so storing the message turned a failure that carries none into "no results"
         * — a different answer with a different next step. Storing the throwable fixed that but
         * invited rendering it, and an exception message is written for a developer: "Unable to
         * resolve host …" is not an instruction, and this app has no logging sink that would make
         * keeping the detail worth the risk of putting it on screen.
         *
         * A boolean makes that impossible by construction rather than by remembering not to.
         */
        val searchFailed: Boolean = false,
    ) {
        /** True when a completed search genuinely returned nothing, so the UI can say so. */
        val isEmpty: Boolean
            get() = !isSearching && !searchFailed && results.isEmpty()
    }

    /**
     * Tri-state for chapter list filters: unset = show all, true = show only matching,
     * false = show only non-matching.
     */
    enum class TriState { ALL, ONLY, EXCLUDE }

    /**
     * Active chapter list filter configuration, matching Mihon's filter sheet options.
     */
    data class ChapterFilter(
        val read: TriState = TriState.ALL,
        val downloaded: TriState = TriState.ALL,
        /** When non-null, only chapters from this scanlator are shown. */
        val scanlator: String? = null,
        val chapterSearchQuery: String = ""
    ) {
        val isActive: Boolean
            get() = read != TriState.ALL || downloaded != TriState.ALL ||
                    scanlator != null || chapterSearchQuery.isNotBlank()

        fun apply(chapters: List<ChapterItem>): List<ChapterItem> = chapters.filter { ch ->
            val readOk = when (read) {
                TriState.ALL -> true
                TriState.ONLY -> ch.read
                TriState.EXCLUDE -> !ch.read
            }
            val downloadOk = when (downloaded) {
                TriState.ALL -> true
                TriState.ONLY -> ch.downloadStatus == DownloadStatus.DOWNLOADED
                TriState.EXCLUDE -> ch.downloadStatus != DownloadStatus.DOWNLOADED
            }
            val scanlatorOk = scanlator == null || ch.scanlator == scanlator
            val nameOk = chapterSearchQuery.isBlank() ||
                ch.name.contains(chapterSearchQuery, ignoreCase = true)
            readOk && downloadOk && scanlatorOk && nameOk
        }
    }

    /**
     * Chapter item for UI with computed properties
     */
    data class ChapterItem(
        val id: Long,
        val mangaId: Long,
        val url: String,
        val name: String,
        val chapterNumber: Float,
        val volume: String?,
        val scanlator: String?,
        val read: Boolean,
        val lastPageRead: Int,
        val dateUpload: Long,
        val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
        val thumbnailUrl: String? = null,
        val totalPages: Int = 0,
        val userNotes: String? = null,
    )

    /**
     * Download status for chapters
     */
    enum class DownloadStatus {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED
    }

    /**
     * Chapter sort order options
     */
    enum class ChapterSortOrder {
        ASCENDING,
        DESCENDING
    }

    /**
     * UI Events (user actions)
     */
    sealed interface Event : UiEvent {
        data object Refresh : Event
        data object ToggleFavorite : Event
        data object ToggleDescription : Event
        data object ToggleSortOrder : Event
        data object ShowChapterFilter : Event
        data object HideChapterFilter : Event
        data class SetChapterFilter(val filter: ChapterFilter) : Event
        data class SetChapterSearchQuery(val query: String) : Event
        data object StartReading : Event
        data object ContinueReading : Event
        data class ChapterClick(val chapterId: Long) : Event
        data class ChapterLongClick(val chapterId: Long) : Event
        data class ToggleChapterRead(val chapterId: Long) : Event
        data class DownloadChapter(val chapterId: Long) : Event
        data class DeleteChapterDownload(val chapterId: Long) : Event
        data class ExportChapterAsCbz(val chapterId: Long) : Event
        data class MarkPreviousAsRead(val chapterId: Long) : Event
        data object ShareManga : Event
        data object OpenDownloadFolder : Event
        data object ClearMangaDownloads : Event
        data class SetDeleteAfterReadOverride(val mode: DeleteAfterReadMode) : Event
        data object ShowNoteEditor : Event
        data object HideNoteEditor : Event
        data class UpdateNoteText(val text: String) : Event
        data object SaveNote : Event
        // Per-chapter notes (#936)
        data class ShowChapterNoteEditor(val chapterId: Long) : Event
        data object HideChapterNoteEditor : Event
        data class UpdateChapterNoteText(val text: String) : Event
        data object SaveChapterNote : Event
        data object ClearChapterSelection : Event
        data object SelectAllChapters : Event
        data object InvertChapterSelection : Event
        data object DownloadSelectedChapters : Event
        data object DownloadAllChapters : Event
        data object DownloadUnreadChapters : Event
        data object DeleteSelectedChapters : Event
        data object MarkSelectedAsRead : Event
        data object MarkSelectedAsUnread : Event
        data object ToggleNotifications : Event
        data object OpenTracking : Event

        // Wrong-match picker (Stage 5b slice 4c)
        /** Overflow menu "Link to AniList…" — opens the picker seeded with the manga's title. */
        data object ShowAniListPicker : Event
        data object DismissAniListPicker : Event
        data class SetAniListPickerQuery(val query: String) : Event
        data object SubmitAniListPickerSearch : Event
        /** The user picked a candidate; it becomes the confirmed link and wins over auto-matching. */
        data class SelectAniListCandidate(val mediaId: Long) : Event

        // Completed / Dropped user-state toggles (#946)
        data object ToggleUserCompleted : Event
        data object ToggleUserDropped : Event

        /** Cycles per-manga cover-theme override: null → true → false → null. (#947) */
        data object CycleMangaThemeOverride : Event

        // Per-manga reader settings (#260)
        data class SetReaderDirection(val direction: Int?) : Event
        data class SetReaderMode(val mode: Int?) : Event
        data class SetReaderColorFilter(val filter: Int?) : Event
        data class SetReaderCustomTintColor(val color: Long?) : Event
        data class SetReaderBackgroundColor(val color: Long?) : Event

        // Page preloading settings (#264)
        data class SetPreloadPagesBefore(val count: Int?) : Event
        data class SetPreloadPagesAfter(val count: Int?) : Event
        data object ResetReaderSettings : Event
        
        // Chapter thumbnail loading
        data class LoadChapterThumbnail(val chapterId: Long) : Event

        // Source suggestions
        data object LoadSourceSuggestions : Event
        data class OnSourceSuggestionClick(val suggestion: SourceSuggestion) : Event
        
        // Panorama cover
        data object TogglePanoramaCover : Event

        // Edit manga info (#998)
        data object ShowEditInfoSheet : Event
        data object HideEditInfoSheet : Event
        data class SaveMangaInfo(
            val title: String?,
            val description: String?,
            val author: String?,
            val artist: String?,
            val thumbnailUrl: String?,
            val genres: List<String>?,
            val status: MangaStatus?,
        ) : Event
        data object ResetMangaInfo : Event

        // Custom cover art
        /** [imageUri] is a content:// URI string returned by the system image picker. */
        data class SetCustomCover(val imageUri: String) : Event
        data object RemoveCustomCover : Event

        /** Genre/tag chip short-press: search this tag within the manga's own source. */
        data class GenreClick(val genre: String) : Event

        /** Genre/tag chip long-press: search this tag across all sources (global search). */
        data class GenreLongClick(val genre: String) : Event

        /** Title/author/artist tap in the header: search that text across all sources. */
        data class SearchGlobally(val query: String) : Event

        /** Source name tap in the header: browse the manga's own source (Komikku parity). */
        data object SourceClick : Event

        /** Opens the manga's source web page in the system browser. */
        data object OpenWebView : Event

        /** Error-state "try in browser" fallback — opens the in-app WebView viewer instead. */
        data object OpenWebViewFallback : Event

        // Category picker shown right after favoriting
        data object DismissCategoryPicker : Event
        data class ToggleCategoryPickerSelection(val categoryId: Long) : Event
        data object ConfirmCategoryPicker : Event

        // Reading-list picker, opened on demand from the overflow menu
        data object ShowReadingListPicker : Event
        data object DismissReadingListPicker : Event
        data class ToggleReadingListPickerSelection(val listId: Long) : Event

        /** Overflow menu "Migrate" — jump straight into the migration search for this manga. */
        data object MigrateManga : Event
    }

    /**
     * UI Effects (one-shot side effects)
     */
    sealed interface Effect : UiEffect {
        data class NavigateToReader(val mangaId: Long, val chapterId: Long) : Effect
        data class ShowSnackbar(val message: String) : Effect
        data class ShowError(val message: String) : Effect
        data class ShareManga(val title: String, val url: String) : Effect
        data class NavigateToTracking(val mangaId: Long, val mangaTitle: String) : Effect
        data class OpenInBrowser(val url: String) : Effect
        /** Navigate to the in-app WebView fallback (Route.WebViewFallback) after a load failure. */
        data class NavigateToWebViewFallback(val url: String, val title: String) : Effect
        data class NavigateToGlobalSearch(val query: String) : Effect
        /** Search [query] within a single source's browse listing (tag short-press). */
        data class NavigateToSourceSearch(val sourceId: String, val query: String) : Effect
        data class OpenDownloadFolder(val sourceName: String, val mangaTitle: String) : Effect

        /**
         * Shown right after removing a manga from the library when it has downloaded chapters.
         * The UI presents this as an action snackbar ("Delete downloaded chapters?" / Delete);
         * tapping the action sends [Event.ClearMangaDownloads] back. Downloads are kept by
         * default — this is opt-in deletion, not an undo of the removal itself.
         */
        data object ShowDeleteDownloadsPrompt : Effect

        /** Jump straight into the migration search pre-selected with this single manga. */
        data class NavigateToMigration(val mangaId: Long) : Effect
    }
}

// Bit layout matches Tachiyomi/Mihon's Manga.CHAPTER_SORT_*/CHAPTER_SHOW_* constants, so a
// native backup export of chapterFlags reflects the user's actual in-app sort/filter choice
// instead of an opaque, always-zero number, and importing a real Tachiyomi backup seeds our
// own sort/filter state meaningfully too.
private const val CHAPTER_SORT_ASC = 0x00000001
private const val CHAPTER_SORT_DIR_MASK = 0x00000001

private const val CHAPTER_SHOW_UNREAD = 0x00000002
private const val CHAPTER_SHOW_READ = 0x00000004
private const val CHAPTER_UNREAD_MASK = 0x00000006

private const val CHAPTER_SHOW_DOWNLOADED = 0x00000008
private const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010
private const val CHAPTER_DOWNLOADED_MASK = 0x00000018

/**
 * Packs [sortOrder] and the read/downloaded parts of [filter] into a single flags Int for
 * persistence on [app.otakureader.domain.model.Manga.chapterFlags]. The scanlator filter and
 * chapter search query are session-only (matching Mihon, which doesn't persist its search bar
 * either) and are intentionally not encoded here.
 */
fun chapterFlagsOf(sortOrder: DetailsContract.ChapterSortOrder, filter: DetailsContract.ChapterFilter): Int {
    var flags = if (sortOrder == DetailsContract.ChapterSortOrder.ASCENDING) CHAPTER_SORT_ASC else 0
    flags = flags or when (filter.read) {
        DetailsContract.TriState.ALL -> 0
        DetailsContract.TriState.ONLY -> CHAPTER_SHOW_READ
        DetailsContract.TriState.EXCLUDE -> CHAPTER_SHOW_UNREAD
    }
    flags = flags or when (filter.downloaded) {
        DetailsContract.TriState.ALL -> 0
        DetailsContract.TriState.ONLY -> CHAPTER_SHOW_DOWNLOADED
        DetailsContract.TriState.EXCLUDE -> CHAPTER_SHOW_NOT_DOWNLOADED
    }
    return flags
}

/** Decodes the sort direction bit packed by [chapterFlagsOf]. */
fun chapterSortOrderFromFlags(flags: Int): DetailsContract.ChapterSortOrder =
    if (flags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_ASC) {
        DetailsContract.ChapterSortOrder.ASCENDING
    } else {
        DetailsContract.ChapterSortOrder.DESCENDING
    }

/**
 * Decodes the read/downloaded filter bits packed by [chapterFlagsOf]. [scanlator] and
 * [chapterSearchQuery] aren't persisted, so the caller supplies whatever the current in-session
 * values are (normally defaults, on initial load).
 */
fun chapterFilterFromFlags(
    flags: Int,
    scanlator: String? = null,
    chapterSearchQuery: String = "",
): DetailsContract.ChapterFilter {
    val read = when (flags and CHAPTER_UNREAD_MASK) {
        CHAPTER_SHOW_UNREAD -> DetailsContract.TriState.EXCLUDE
        CHAPTER_SHOW_READ -> DetailsContract.TriState.ONLY
        else -> DetailsContract.TriState.ALL
    }
    val downloaded = when (flags and CHAPTER_DOWNLOADED_MASK) {
        CHAPTER_SHOW_DOWNLOADED -> DetailsContract.TriState.ONLY
        CHAPTER_SHOW_NOT_DOWNLOADED -> DetailsContract.TriState.EXCLUDE
        else -> DetailsContract.TriState.ALL
    }
    return DetailsContract.ChapterFilter(
        read = read,
        downloaded = downloaded,
        scanlator = scanlator,
        chapterSearchQuery = chapterSearchQuery,
    )
}

/**
 * Extension to convert domain Chapter to ChapterItem
 */
fun Chapter.toChapterItem(thumbnailUrl: String? = null, totalPages: Int = 0): DetailsContract.ChapterItem {
    // Extract volume from chapter name if available (e.g., "Vol. 1 Chapter 1")
    val volumeRegex = Regex("""Vol\.?\s*(\d+)""", RegexOption.IGNORE_CASE)
    val volume = volumeRegex.find(name)?.groupValues?.get(1)?.let { "Volume $it" }
    
    return DetailsContract.ChapterItem(
        id = id,
        mangaId = mangaId,
        url = url,
        name = name,
        chapterNumber = chapterNumber,
        volume = volume,
        scanlator = scanlator,
        read = read,
        lastPageRead = lastPageRead,
        dateUpload = dateUpload,
        downloadStatus = DetailsContract.DownloadStatus.NOT_DOWNLOADED,
        thumbnailUrl = thumbnailUrl,
        totalPages = totalPages,
        userNotes = userNotes,
    )
}

/**
 * Extension to get display status text resource ID
 */
@StringRes
fun MangaStatus.displayTextResId(): Int = when (this) {
    MangaStatus.UNKNOWN -> R.string.details_status_unknown
    MangaStatus.ONGOING -> R.string.details_status_ongoing
    MangaStatus.COMPLETED -> R.string.details_status_completed
    MangaStatus.LICENSED -> R.string.details_status_licensed
    MangaStatus.PUBLISHING_FINISHED -> R.string.details_status_publishing_finished
    MangaStatus.CANCELLED -> R.string.details_status_cancelled
    MangaStatus.ON_HIATUS -> R.string.details_status_on_hiatus
}

/**
 * Extension to get status color
 */
fun MangaStatus.colorValue(colors: OtakuColors): androidx.compose.ui.graphics.Color = when (this) {
    MangaStatus.UNKNOWN -> androidx.compose.ui.graphics.Color.Gray
    MangaStatus.ONGOING -> colors.statusOngoing
    MangaStatus.COMPLETED -> colors.statusCompleted
    MangaStatus.LICENSED -> colors.statusLicensed
    MangaStatus.PUBLISHING_FINISHED -> colors.statusPublishingFinished
    MangaStatus.CANCELLED -> colors.statusCancelled
    MangaStatus.ON_HIATUS -> colors.statusOnHiatus
}
