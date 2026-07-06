package app.otakureader.feature.reader

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.component.EmptyScreen
import app.otakureader.core.ui.component.LoadingScreen
import app.otakureader.feature.reader.R
import app.otakureader.domain.model.ColorFilterMode
import app.otakureader.domain.model.ReaderMode
import app.otakureader.domain.model.ReaderOrientation
import app.otakureader.feature.reader.modes.DualPageReader
import app.otakureader.feature.reader.modes.SinglePageReader
import app.otakureader.feature.reader.modes.SmartPanelsReader
import app.otakureader.feature.reader.modes.WebtoonReader
import app.otakureader.feature.reader.ui.BatteryTimeOverlay
import app.otakureader.feature.reader.ui.BrightnessSliderOverlay
import app.otakureader.feature.reader.ui.ChapterFilterBottomSheet
import app.otakureader.feature.reader.ui.ReaderCommentsOverlay
import app.otakureader.feature.reader.ui.FullPageGallery
import app.otakureader.feature.reader.ui.PageSlider
import app.otakureader.feature.reader.ui.PageThumbnailStrip
import app.otakureader.feature.reader.ui.ReadingTimerOverlay
import app.otakureader.feature.reader.ui.ReaderContentOverlay
import app.otakureader.feature.reader.ui.ReaderBottomBar
import app.otakureader.feature.reader.ui.ChapterListOverlay
import app.otakureader.feature.reader.ui.ChapterTransition
import app.otakureader.feature.reader.ui.ReaderSettingsOverlay
import app.otakureader.feature.reader.ui.NavigationOverlay
import app.otakureader.feature.reader.ui.ZoomIndicator
import app.otakureader.feature.reader.ReaderEffect
import app.otakureader.feature.reader.ReaderEvent
import app.otakureader.feature.reader.ReaderSetting
import app.otakureader.feature.reader.ReaderViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import kotlinx.coroutines.launch


/**
 * Ultimate Reader Screen with full gallery view, tap zones, and all 4 reading modes.
 * 
 * Features:
 * - 4 reading modes: Single Page, Dual Page (spreads), Webtoon (vertical scroll), Smart Panels
 * - Pinch zoom with double-tap zoom support
 * - Tap zones for navigation (left=prev, center=menu, right=next)
 * - Bottom thumbnail strip with expandable gallery
 * - Brightness and zoom controls
 * - Settings persistence
 */

private const val VOLUME_HOLD_SKIP_PAGES = 5
// Index of the first chapter in reading order; used to gate the previous/next-chapter buttons.
private const val FIRST_CHAPTER_INDEX = 0
private const val UNKNOWN_CHAPTER_NUMBER = -1f
private const val DIRECTION_INDICATOR_DURATION_MS = 2_000L
private val DIRECTION_INDICATOR_BOTTOM_PADDING = 80.dp
private val DIRECTION_INDICATOR_CORNER_RADIUS = 8.dp
private val DIRECTION_INDICATOR_HORIZONTAL_PADDING = 12.dp
private val DIRECTION_INDICATOR_VERTICAL_PADDING = 8.dp
private val DIRECTION_INDICATOR_ICON_SIZE = 18.dp
private val DIRECTION_INDICATOR_ICON_SPACING = 6.dp
private const val DIRECTION_INDICATOR_SCRIM_ALPHA = 0.7f

private const val MODE_OVERLAY_DURATION_MS = 1_500L
private val PAGE_NUMBER_BOTTOM_PADDING = 16.dp
private val PAGE_NUMBER_CORNER_RADIUS = 16.dp
private val PAGE_NUMBER_HORIZONTAL_PADDING = 12.dp
private val PAGE_NUMBER_VERTICAL_PADDING = 6.dp

/** Maps a domain [ReaderOrientation] to the matching Android `ActivityInfo` orientation flag. */
private fun ReaderOrientation.toActivityInfo(): Int = when (this) {
    ReaderOrientation.DEFAULT -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ReaderOrientation.FREE -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ReaderOrientation.LOCKED_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    ReaderOrientation.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    ReaderOrientation.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
}

private data class ChapterNavData(
    val hasPreviousChapter: Boolean,
    val hasNextChapter: Boolean,
    val currentChapterName: String,
    val currentChapterNumber: Float,
    val prevChapterName: String?,
    val prevChapterNumber: Float?,
    val nextChapterName: String?,
    val nextChapterNumber: Float?,
)

@Composable
@Suppress("UnusedParameter")
fun ReaderScreen(
    mangaId: Long,
    chapterId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    
    // UI state for overlays
    var showZoomIndicator by remember { mutableStateOf(false) }
    var showBrightnessSlider by remember { mutableStateOf(false) }
    var showChapterFilterSheet by remember { mutableStateOf(false) }
    var showDirectionIndicator by remember { mutableStateOf(false) }
    var showModeOverlay by remember { mutableStateOf(false) }
    
    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReaderEffect.NavigateBack -> onNavigateBack()
                is ReaderEffect.ShowSnackbar -> {
                    scope.launch {
                        val text = effect.messageResId?.let { resId -> context.getString(resId) }
                            ?: effect.message
                            ?: return@launch
                        snackbarHostState.showSnackbar(text)
                    }
                }
                is ReaderEffect.NavigateToChapter -> {
                    // Handle chapter navigation
                    snackbarHostState.showSnackbar(context.getString(R.string.reader_navigating_to_chapter))
                }
                is ReaderEffect.SharePage -> {
                    scope.launch {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, effect.pageUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }
                }
            }
        }
    }
    
    // Keep screen on if enabled
    DisposableEffect(state.keepScreenOn) {
        val activity = context as? Activity
        if (state.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Prevent screenshots when secure mode is enabled (user-opt-in)
    DisposableEffect(state.secureScreen) {
        val activity = context as? Activity
        if (state.secureScreen) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Apply display-cutout mode so content can extend into the camera notch when enabled.
    // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES was added in API 28; on API 26-27 the block
    // runs but the constant is present as a stub, so there is no crash — just no effect.
    DisposableEffect(state.showContentInCutout) {
        val activity = context as? Activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = activity?.window?.attributes
            if (params != null) {
                params.layoutInDisplayCutoutMode = if (state.showContentInCutout) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
                activity.window.attributes = params
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val params = activity?.window?.attributes
                if (params != null) {
                    params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    activity.window.attributes = params
                }
            }
        }
    }

    // Apply the reader's screen-orientation lock whenever it changes, and restore the system
    // default only when leaving the reader. Splitting application (LaunchedEffect) from
    // restoration (DisposableEffect) avoids a brief reset-to-default flicker on each change.
    val readerActivity = context as? Activity
    LaunchedEffect(readerActivity, state.readerOrientation) {
        readerActivity?.requestedOrientation = state.readerOrientation.toActivityInfo()
    }
    DisposableEffect(readerActivity) {
        onDispose {
            readerActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    
    // Reading direction indicator — brief arrow overlay on direction change
    LaunchedEffect(state.readingDirection) {
        showDirectionIndicator = true
        delay(DIRECTION_INDICATOR_DURATION_MS)
        showDirectionIndicator = false
    }

    // Guard on pages.isNotEmpty(): mode transitions from default→saved before pages load, causing a spurious flash without it.
    LaunchedEffect(state.mode) {
        if (state.pages.isNotEmpty() && state.showReadingModeOverlay) {
            showModeOverlay = true
            delay(MODE_OVERLAY_DURATION_MS)
        }
        showModeOverlay = false
    }

    // Handle zoom indicator visibility
    LaunchedEffect(state.zoomLevel) {
        if (state.zoomLevel != 1f) {
            showZoomIndicator = true
            delay(1500)
            showZoomIndicator = false
        }
    }

    // Ensure reader gains focus for hardware key handling
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.isMenuVisible, state.isGalleryOpen) {
        focusRequester.requestFocus()
    }

    // Handle back gestures: close gallery or menu before navigating away.
    // A single BackHandler with explicit priority ordering is used so that the
    // gallery takes precedence over the menu (both could theoretically be open).
    // This integrates with the predictive back API (enabled via
    // android:enableOnBackInvokedCallback="true" in AndroidManifest.xml) so
    // that when an overlay is open the back gesture dismisses it rather than
    // triggering a full screen transition.
    BackHandler(
        enabled = state.isGalleryOpen || state.isMenuVisible ||
            state.isSettingsOverlayVisible || state.isChapterListOverlayVisible
    ) {
        when {
            state.isGalleryOpen -> viewModel.onEvent(ReaderEvent.ToggleGallery)
            state.isChapterListOverlayVisible -> viewModel.onEvent(ReaderEvent.ToggleChapterListOverlay)
            state.isSettingsOverlayVisible -> viewModel.onEvent(ReaderEvent.ToggleSettingsOverlay)
            state.isMenuVisible -> viewModel.onEvent(ReaderEvent.ToggleMenu)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Volume key navigation — single tap = 1 page, hold = 5 pages.
                if (event.key == Key.VolumeUp || event.key == Key.VolumeDown) {
                    if (!state.volumeKeysEnabled) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        val navigateNext = (event.key == Key.VolumeDown && !state.volumeKeysInverted) ||
                            (event.key == Key.VolumeUp && state.volumeKeysInverted)
                        val isHeld = event.nativeKeyEvent.repeatCount > 0
                        val readerEvent = when {
                            isHeld && navigateNext -> ReaderEvent.SkipPages(VOLUME_HOLD_SKIP_PAGES)
                            isHeld -> ReaderEvent.SkipPages(-VOLUME_HOLD_SKIP_PAGES)
                            navigateNext -> ReaderEvent.NextPage
                            else -> ReaderEvent.PrevPage
                        }
                        viewModel.onEvent(readerEvent)
                    }
                    return@onPreviewKeyEvent true
                }

                // DeX / physical keyboard shortcuts — only act on key-down to avoid double firing.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isRtl = state.readingDirection == app.otakureader.domain.model.ReadingDirection.RTL
                when (event.key) {
                    Key.DirectionRight, Key.D, Key.PageDown, Key.Spacebar -> {
                        viewModel.onEvent(if (isRtl) ReaderEvent.PrevPage else ReaderEvent.NextPage); true
                    }
                    Key.DirectionLeft, Key.A, Key.PageUp -> {
                        viewModel.onEvent(if (isRtl) ReaderEvent.NextPage else ReaderEvent.PrevPage); true
                    }
                    Key.MoveHome -> {
                        viewModel.onEvent(ReaderEvent.FirstPage); true
                    }
                    Key.MoveEnd -> {
                        viewModel.onEvent(ReaderEvent.LastPage); true
                    }
                    Key.F -> {
                        viewModel.onEvent(ReaderEvent.ToggleFullscreen); true
                    }
                    Key.M, Key.Menu -> {
                        viewModel.onEvent(ReaderEvent.ToggleMenu); true
                    }
                    Key.Escape -> {
                        if (state.isMenuVisible) viewModel.onEvent(ReaderEvent.ToggleMenu)
                        else onNavigateBack()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Main content based on reading mode
        when {
            state.isLoading -> LoadingScreen(Modifier.fillMaxSize())
            state.pages.isEmpty() -> EmptyScreen(
                message = state.error ?: "No pages found.",
                modifier = Modifier.fillMaxSize()
            )
            else -> {
                ReaderContent(
                    state = state,
                    onPageChange = { viewModel.onEvent(ReaderEvent.OnPageChange(it)) },
                    onPanelChange = { viewModel.onEvent(ReaderEvent.OnPanelChange(it)) },
                    onTap = { viewModel.onEvent(ReaderEvent.ToggleMenu) },
                    onDoubleTap = { offset ->
                        // Double tap zoom handled by ZoomableImage
                    },
                    onZoomChange = { zoom ->
                        viewModel.onEvent(ReaderEvent.OnZoomChange(zoom))
                    },
                    onLongPress = { pageUrl ->
                        viewModel.onEvent(ReaderEvent.OnPageLongPress(pageUrl))
                    }
                )
            }
        }
        
        val chapterNavData = remember(chapters, state.currentChapterId, state.chapterTitle) {
            val ordered = chapters.sortedBy { it.chapterNumber }
            val currentIndex = ordered.indexOfFirst { it.id == state.currentChapterId }
            val current = ordered.getOrNull(currentIndex)
            val prev = if (currentIndex > FIRST_CHAPTER_INDEX) ordered[currentIndex - 1] else null
            val next = if (currentIndex >= FIRST_CHAPTER_INDEX && currentIndex < ordered.lastIndex) ordered[currentIndex + 1] else null
            ChapterNavData(
                hasPreviousChapter = currentIndex > FIRST_CHAPTER_INDEX,
                hasNextChapter = currentIndex >= FIRST_CHAPTER_INDEX && currentIndex < ordered.lastIndex,
                currentChapterName = current?.name ?: state.chapterTitle,
                currentChapterNumber = current?.chapterNumber ?: UNKNOWN_CHAPTER_NUMBER,
                prevChapterName = prev?.name,
                prevChapterNumber = prev?.chapterNumber,
                nextChapterName = next?.name,
                nextChapterNumber = next?.chapterNumber,
            )
        }

        // Tap zone overlay for navigation
        if (!state.isLoading && state.pages.isNotEmpty() && !state.isMenuVisible) {
            val isRtl = state.readingDirection == app.otakureader.domain.model.ReadingDirection.RTL
            val isWebtoon = state.mode == app.otakureader.domain.model.ReaderMode.WEBTOON
            NavigationOverlay(
                onPrev = { viewModel.onEvent(if (isRtl) ReaderEvent.NextPage else ReaderEvent.PrevPage) },
                onNext = { viewModel.onEvent(if (isRtl) ReaderEvent.PrevPage else ReaderEvent.NextPage) },
                onToggleMenu = { viewModel.onEvent(ReaderEvent.ToggleMenu) },
                onLongPress = if (state.showActionsOnLongTap) null
                              else ({ viewModel.onEvent(ReaderEvent.ToggleSettingsOverlay) }),
                navigationMode = if (isWebtoon) state.navigationModeWebtoon else state.navigationModePager,
                tapInvertMode = if (isWebtoon) state.tapInvertModeWebtoon else state.tapInvertModePager,
                smallerTapZone = state.smallerTapZone,
                showDebugOverlay = state.showTapZonesOverlay,
                modifier = Modifier.fillMaxSize(),
            )
        }
        
        ChapterTransition(
            isVisible = !state.isLoading && state.pages.isNotEmpty() &&
                state.alwaysShowChapterTransition && !state.isMenuVisible &&
                (state.isLastPage || state.isFirstPage),
            isTransitionToNext = state.isLastPage,
            currentChapterTitle = chapterNavData.currentChapterName,
            currentChapterNumber = chapterNavData.currentChapterNumber,
            isCurrentChapterDownloaded = state.isCurrentChapterDownloaded,
            adjacentChapterTitle = if (state.isLastPage) chapterNavData.nextChapterName else chapterNavData.prevChapterName,
            adjacentChapterNumber = if (state.isLastPage) chapterNavData.nextChapterNumber else chapterNavData.prevChapterNumber,
            onTap = { viewModel.onEvent(ReaderEvent.ToggleMenu) },
            modifier = Modifier.fillMaxSize(),
        )

        // Clean Mihon/Komikku-style reader top app bar. Page scrubbing and chapter navigation
        // live in the bottom bar (PageSlider) and thumbnails in PageThumbnailStrip, so this
        // overlay deliberately carries only the top bar to avoid duplicating those controls.
        ReaderContentOverlay(
            title = state.mangaTitle,
            chapterTitle = state.chapterTitle,
            isVisible = state.isMenuVisible && !state.isGalleryOpen && !state.isLoading,
            onDismiss = onNavigateBack,
            onTitleClick = { viewModel.onEvent(ReaderEvent.ToggleChapterListOverlay) },
            onDownloadChapter = { viewModel.onEvent(ReaderEvent.DownloadCurrentChapter) },
            isCurrentChapterDownloaded = state.isCurrentChapterDownloaded,
            onBookmarkPage = { viewModel.onEvent(ReaderEvent.ToggleBookmark) },
            isCurrentPageBookmarked = state.isCurrentPageBookmarked,
            onOpenComments = { viewModel.onEvent(ReaderEvent.ToggleCommentsOverlay) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (state.isCommentsOverlayVisible) {
            val chapterComments by viewModel.chapterComments.collectAsStateWithLifecycle()
            val bookComments by viewModel.bookComments.collectAsStateWithLifecycle()
            val chapterNote by viewModel.currentChapterNote.collectAsStateWithLifecycle()
            val externalLinks by viewModel.externalDiscussionLinks.collectAsStateWithLifecycle()
            ReaderCommentsOverlay(
                chapterComments = chapterComments,
                bookComments = bookComments,
                chapterNote = chapterNote,
                externalLinks = externalLinks,
                onAddComment = { body, chapterScoped -> viewModel.addComment(body, chapterScoped) },
                onDeleteComment = { viewModel.deleteComment(it) },
                onSaveChapterNote = { viewModel.saveChapterNote(it) },
                onDismiss = { viewModel.onEvent(ReaderEvent.ToggleCommentsOverlay) },
            )
        }

        if (showChapterFilterSheet) {
            ChapterFilterBottomSheet(
                skipReadChapters = state.skipReadChapters,
                skipFilteredChapters = state.skipFilteredChapters,
                skipDuplicateChapters = state.skipDuplicateChapters,
                onToggleSkipRead = { viewModel.onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SKIP_READ_CHAPTERS)) },
                onToggleSkipFiltered = { viewModel.onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SKIP_FILTERED_CHAPTERS)) },
                onToggleSkipDuplicate = { viewModel.onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SKIP_DUPLICATE_CHAPTERS)) },
                onDismiss = { showChapterFilterSheet = false },
            )
        }
        
        // Full page gallery
        FullPageGallery(
            pages = state.pages,
            currentPage = state.currentPage,
            columns = state.galleryColumns,
            onPageClick = { viewModel.jumpToPage(it) },
            onDismiss = { viewModel.onEvent(ReaderEvent.ToggleGallery) },
            onColumnsChange = { viewModel.onEvent(ReaderEvent.SetGalleryColumns(it)) },
            isVisible = state.isGalleryOpen
        )

        // Zoom indicator
        ZoomIndicator(
            zoomLevel = state.zoomLevel,
            isVisible = showZoomIndicator,
            modifier = Modifier.align(Alignment.Center)
        )

        // Reading mode overlay — fades in/out at center on mode change (Komikku parity)
        ReadingModeOverlay(
            mode = state.mode,
            isVisible = showModeOverlay && !state.isMenuVisible,
            modifier = Modifier.align(Alignment.Center)
        )

        // Page number indicator — persistent bottom-center pill; hidden when menu is open
        PageNumberIndicator(
            currentPage = state.displayPageNumber,
            totalPages = state.totalPages,
            isVisible = state.showPageNumber && !state.isMenuVisible &&
                !state.isGalleryOpen && state.pages.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PAGE_NUMBER_BOTTOM_PADDING)
        )

        // Brightness slider overlay
        BrightnessSliderOverlay(
            brightness = state.brightness,
            onBrightnessChange = { viewModel.onEvent(ReaderEvent.OnBrightnessChange(it)) },
            isVisible = showBrightnessSlider,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // Bottom section: thumbnail strip → chapter navigator/slider → icon bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageThumbnailStrip(
                pages = state.pages,
                currentPage = state.currentPage,
                onPageClick = { viewModel.jumpToPage(it) },
                isVisible = state.showPageThumbnailStrip && state.isMenuVisible && !state.isGalleryOpen && !state.isLoading,
                modifier = Modifier,
            )
            PageSlider(
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                onPageSeek = { viewModel.onEvent(ReaderEvent.OnPageChange(it)) },
                onPreviousChapter = { viewModel.onEvent(ReaderEvent.PrevChapter) },
                onNextChapter = { viewModel.onEvent(ReaderEvent.NextChapter) },
                hasPreviousChapter = chapterNavData.hasPreviousChapter,
                hasNextChapter = chapterNavData.hasNextChapter,
                readingDirection = state.readingDirection,
                isVisible = state.isMenuVisible && state.pages.isNotEmpty() && !state.isGalleryOpen && !state.isLoading,
                modifier = Modifier,
            )
            ReaderBottomBar(
                state = state,
                onChapterList = { viewModel.onEvent(ReaderEvent.ToggleChapterListOverlay) },
                onModeClick = {
                    val modes = ReaderMode.entries
                    val next = modes[(state.mode.ordinal + 1) % modes.size]
                    viewModel.onEvent(ReaderEvent.OnModeChange(next))
                },
                onOrientationClick = {
                    val orientations = ReaderOrientation.entries
                    val next = orientations[(state.readerOrientation.ordinal + 1) % orientations.size]
                    viewModel.onEvent(ReaderEvent.OnOrientationChange(next))
                },
                onToggleCropBorders = { viewModel.onEvent(ReaderEvent.ToggleSetting(ReaderSetting.CROP_BORDERS)) },
                onToggleThumbnailStrip = { viewModel.onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SHOW_PAGE_THUMBNAIL_STRIP)) },
                onSettings = { viewModel.onEvent(ReaderEvent.ToggleSettingsOverlay) },
                isVisible = state.isMenuVisible && !state.isGalleryOpen && !state.isLoading,
            )
        }

        // Overlays in the top-right corner (stacked vertically)
        Column(
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            // Reading Timer Overlay - top item
            ReadingTimerOverlay(
                isVisible = state.showReadingTimer && !state.isMenuVisible && !state.isGalleryOpen,
                sessionStartMs = viewModel.sessionStartMs,
                modifier = Modifier
            )

            // Battery/Time Overlay - below the timer when both are visible
            BatteryTimeOverlay(
                isVisible = state.showBatteryTime && !state.isMenuVisible && !state.isGalleryOpen,
                modifier = Modifier
            )
        }

        // Reading direction indicator — bottom-start, fades after 2 s
        ReadingDirectionIndicator(
            readingDirection = state.readingDirection,
            isVisible = showDirectionIndicator && !state.isMenuVisible,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = DIRECTION_INDICATOR_BOTTOM_PADDING)
        )

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Reader settings dialog — 3-tab sheet (Reading mode / General / Color filter)
        ReaderSettingsOverlay(
            isVisible = state.isSettingsOverlayVisible,
            state = state,
            onEvent = { viewModel.onEvent(it) },
            onDismiss = { viewModel.onEvent(ReaderEvent.ToggleSettingsOverlay) },
        )

        // Chapter-list overlay — right-side sliding panel
        ChapterListOverlay(
            isVisible = state.isChapterListOverlayVisible,
            chapters = chapters,
            currentChapterId = state.currentChapterId,
            onChapterClick = { targetChapterId ->
                viewModel.onEvent(ReaderEvent.LoadChapter(targetChapterId))
                viewModel.onEvent(ReaderEvent.ToggleChapterListOverlay)
            },
            onDismiss = { viewModel.onEvent(ReaderEvent.ToggleChapterListOverlay) },
        )

        // Page long-press context menu
        if (state.isPageContextMenuVisible) {
            PageContextMenuBottomSheet(
                onSaveImage = { viewModel.onEvent(ReaderEvent.SavePageImage) },
                onSetAsCover = { viewModel.onEvent(ReaderEvent.SetPageAsCover) },
                onShare = { viewModel.onEvent(ReaderEvent.SharePage) },
                onDismiss = { viewModel.onEvent(ReaderEvent.DismissPageContextMenu) },
            )
        }
    }
}

@Composable
private fun ReaderContent(
    state: app.otakureader.feature.reader.ReaderState,
    onPageChange: (Int) -> Unit,
    onPanelChange: (Int) -> Unit,
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onZoomChange: (Float) -> Unit,
    onLongPress: ((String) -> Unit)? = null,
) {
    // Prefer per-manga color override; fall back to the user's global background preference.
    // backgroundColor index: 0=Black (default), 1=White, 2=Grey, 3=Auto (system surface).
    val backgroundColor = when {
        state.readerBackgroundColor != null -> Color(state.readerBackgroundColor.toInt())
        state.backgroundColor == 1 -> Color.White
        state.backgroundColor == 2 -> Color.Gray
        state.backgroundColor == 3 -> Color.Transparent
        else -> Color.Black
    }

    val webtoonSidePaddingDp = when (state.webtoonSidePadding) {
        1 -> 8
        2 -> 16
        3 -> 32
        else -> 0
    }

    // Apply pageLayout to pager modes: Single forces SINGLE_PAGE, Double forces DUAL_PAGE,
    // Automatic picks based on orientation. Webtoon/SmartPanels are unaffected.
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val effectiveMode = if (state.mode != ReaderMode.WEBTOON && state.mode != ReaderMode.SMART_PANELS) {
        when (state.pageLayout) {
            ReaderState.PAGE_LAYOUT_SINGLE -> ReaderMode.SINGLE_PAGE
            ReaderState.PAGE_LAYOUT_DUAL -> ReaderMode.DUAL_PAGE
            ReaderState.PAGE_LAYOUT_AUTOMATIC -> if (isLandscape) ReaderMode.DUAL_PAGE else ReaderMode.SINGLE_PAGE
            else -> state.mode
        }
    } else {
        state.mode
    }
    // landscapeZoomScaleType=1 (Zoom In): override scale to FillWidth when in landscape pager mode.
    val effectiveScaleType = if (state.landscapeZoomScaleType == 1 && isLandscape &&
        (effectiveMode == ReaderMode.SINGLE_PAGE || effectiveMode == ReaderMode.DUAL_PAGE)) 1
    else state.readerScale

    // CompositingStrategy.Offscreen ensures blend modes in the Canvas overlay work correctly
    // against the already-rendered page content below them.
    val boxModifier = Modifier
        .fillMaxSize()
        .background(backgroundColor)
        .let { base ->
            if (state.colorFilterMode != ColorFilterMode.NONE) {
                base.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            } else {
                base
            }
        }

    Box(
        modifier = boxModifier
    ) {
        when (effectiveMode) {
            ReaderMode.SINGLE_PAGE -> {
                SinglePageReader(
                    pages = state.pages,
                    currentPage = state.currentPage,
                    onPageChange = onPageChange,
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                    onZoomChange = onZoomChange,
                    onLongPress = if (state.showActionsOnLongTap) onLongPress else null,
                    rotation = state.pageRotation.degrees,
                    scaleType = effectiveScaleType,
                    animatePageTransitions = state.animatePageTransitions,
                    cropBordersEnabled = state.cropBordersEnabled,
                    imageQuality = state.imageQuality,
                    dataSaverEnabled = state.dataSaverEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ReaderMode.DUAL_PAGE -> {
                DualPageReader(
                    pages = state.pages,
                    currentPage = state.currentPage,
                    onPageChange = onPageChange,
                    onTap = onTap,
                    onLongPress = if (state.showActionsOnLongTap) onLongPress else null,
                    isRtl = state.readingDirection == app.otakureader.domain.model.ReadingDirection.RTL,
                    rotation = state.pageRotation.degrees,
                    scaleType = effectiveScaleType,
                    animatePageTransitions = state.animatePageTransitions,
                    cropBordersEnabled = state.cropBordersEnabled,
                    imageQuality = state.imageQuality,
                    dataSaverEnabled = state.dataSaverEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ReaderMode.WEBTOON -> {
                WebtoonReader(
                    pages = state.pages,
                    currentPage = state.currentPage,
                    onPageChange = onPageChange,
                    onTap = onTap,
                    onLongPress = if (state.showActionsOnLongTap) onLongPress else null,
                    rotation = state.pageRotation.degrees,
                    cropBordersEnabled = state.cropBordersEnabled,
                    imageQuality = state.imageQuality,
                    dataSaverEnabled = state.dataSaverEnabled,
                    pageGapDp = state.webtoonGapDp,
                    sidePaddingDp = webtoonSidePaddingDp,
                    disableZoomOut = state.webtoonDisableZoomOut,
                    pinchToZoomEnabled = state.webtoonPinchToZoomEnabled,
                    scaleType = state.webtoonScaleType,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ReaderMode.SMART_PANELS -> {
                SmartPanelsReader(
                    pages = state.pages,
                    currentPage = state.currentPage,
                    currentPanel = state.currentPanel,
                    onPageChange = onPageChange,
                    onPanelChange = onPanelChange,
                    onTap = onTap,
                    onLongPress = if (state.showActionsOnLongTap) onLongPress else null,
                    rotation = state.pageRotation.degrees,
                    cropBordersEnabled = state.cropBordersEnabled,
                    imageQuality = state.imageQuality,
                    dataSaverEnabled = state.dataSaverEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Color filter overlay drawn on top of the page content.
        // Uses BlendMode to affect the composited result.
        if (state.colorFilterMode != ColorFilterMode.NONE) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (state.colorFilterMode) {
                    ColorFilterMode.SEPIA ->
                        drawRect(color = Color(0xA0704214), blendMode = BlendMode.Color)
                    ColorFilterMode.GRAYSCALE ->
                        drawRect(color = Color(0xFF808080), blendMode = BlendMode.Saturation)
                    ColorFilterMode.INVERT ->
                        drawRect(color = Color.White, blendMode = BlendMode.Difference)
                    ColorFilterMode.CUSTOM_TINT ->
                        drawRect(color = Color(state.customTintColor), blendMode = BlendMode.SrcOver)
                    ColorFilterMode.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun ReadingDirectionIndicator(
    readingDirection: app.otakureader.domain.model.ReadingDirection,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val (icon, label) = when (readingDirection) {
            app.otakureader.domain.model.ReadingDirection.LTR ->
                Icons.AutoMirrored.Filled.ArrowForward to stringResource(R.string.reader_direction_ltr)
            app.otakureader.domain.model.ReadingDirection.RTL ->
                Icons.AutoMirrored.Filled.ArrowBack to stringResource(R.string.reader_direction_rtl)
            app.otakureader.domain.model.ReadingDirection.VERTICAL ->
                Icons.Default.ArrowDownward to stringResource(R.string.reader_direction_vertical)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = DIRECTION_INDICATOR_SCRIM_ALPHA),
                    RoundedCornerShape(DIRECTION_INDICATOR_CORNER_RADIUS),
                )
                .padding(
                    horizontal = DIRECTION_INDICATOR_HORIZONTAL_PADDING,
                    vertical = DIRECTION_INDICATOR_VERTICAL_PADDING,
                )
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(DIRECTION_INDICATOR_ICON_SIZE))
            Spacer(Modifier.width(DIRECTION_INDICATOR_ICON_SPACING))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ReadingModeOverlay(
    mode: ReaderMode,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val label = when (mode) {
            ReaderMode.SINGLE_PAGE -> stringResource(R.string.reader_mode_single)
            ReaderMode.DUAL_PAGE -> stringResource(R.string.reader_mode_dual)
            ReaderMode.WEBTOON -> stringResource(R.string.reader_mode_webtoon)
            ReaderMode.SMART_PANELS -> stringResource(R.string.reader_mode_smart)
        }
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = DIRECTION_INDICATOR_SCRIM_ALPHA),
                    RoundedCornerShape(DIRECTION_INDICATOR_CORNER_RADIUS),
                )
                .padding(
                    horizontal = DIRECTION_INDICATOR_HORIZONTAL_PADDING,
                    vertical = DIRECTION_INDICATOR_VERTICAL_PADDING,
                )
        )
    }
}

@Composable
private fun PageNumberIndicator(
    currentPage: Int,
    totalPages: Int,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.reader_page_of_total, currentPage, totalPages),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = DIRECTION_INDICATOR_SCRIM_ALPHA),
                    RoundedCornerShape(PAGE_NUMBER_CORNER_RADIUS),
                )
                .padding(
                    horizontal = PAGE_NUMBER_HORIZONTAL_PADDING,
                    vertical = PAGE_NUMBER_VERTICAL_PADDING,
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageContextMenuBottomSheet(
    onSaveImage: () -> Unit,
    onSetAsCover: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_page_menu_save_image)) },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onSaveImage)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_page_menu_set_as_cover)) },
                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onSetAsCover)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_page_menu_share)) },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onShare)
            )
        }
    }
}
