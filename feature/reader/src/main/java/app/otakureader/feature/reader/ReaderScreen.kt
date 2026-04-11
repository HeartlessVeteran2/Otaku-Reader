package app.otakureader.feature.reader

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.component.EmptyScreen
import app.otakureader.core.ui.component.LoadingScreen
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.model.ColorFilterMode
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.modes.DualPageReader
import app.otakureader.feature.reader.modes.SinglePageReader
import app.otakureader.feature.reader.modes.SmartPanelsReader
import app.otakureader.feature.reader.modes.WebtoonReader
import app.otakureader.feature.reader.ui.BatteryTimeOverlay
import app.otakureader.feature.reader.ui.BrightnessSliderOverlay
import app.otakureader.feature.reader.ui.FullPageGallery
import app.otakureader.feature.reader.ui.PageSlider
import app.otakureader.feature.reader.ui.PageThumbnailStrip
import app.otakureader.feature.reader.ui.ReadingTimerOverlay
import app.otakureader.feature.reader.ui.ReaderMenuOverlay
import app.otakureader.feature.reader.ui.SimpleTapZoneOverlay
import app.otakureader.feature.reader.ui.ZoomIndicator
import app.otakureader.feature.reader.viewmodel.ReaderEffect
import app.otakureader.feature.reader.viewmodel.ReaderEvent
import app.otakureader.feature.reader.viewmodel.TapZone
import app.otakureader.feature.reader.viewmodel.UltimateReaderViewModel
import kotlinx.coroutines.delay
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
@Composable
fun ReaderScreen(
    mangaId: Long,
    chapterId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UltimateReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    
    // UI state for overlays
    var showZoomIndicator by remember { mutableStateOf(false) }
    var showBrightnessSlider by remember { mutableStateOf(false) }
    
    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReaderEffect.NavigateBack -> onNavigateBack()
                is ReaderEffect.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
                is ReaderEffect.NavigateToChapter -> {
                    // Handle chapter navigation
                    snackbarHostState.showSnackbar(context.getString(R.string.reader_navigating_to_chapter))
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
    BackHandler(enabled = state.isGalleryOpen || state.isMenuVisible) {
        when {
            state.isGalleryOpen -> viewModel.onEvent(ReaderEvent.ToggleGallery)
            state.isMenuVisible -> viewModel.onEvent(ReaderEvent.ToggleMenu)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!state.volumeKeysEnabled) return@onPreviewKeyEvent false
                if (event.key != Key.VolumeUp && event.key != Key.VolumeDown) return@onPreviewKeyEvent false

                // Consume both down/up to suppress system volume UI
                if (event.type == KeyEventType.KeyDown) {
                    val navigateNext = (event.key == Key.VolumeDown && !state.volumeKeysInverted) ||
                        (event.key == Key.VolumeUp && state.volumeKeysInverted)
                    val readerEvent = if (navigateNext) ReaderEvent.NextPage else ReaderEvent.PrevPage
                    viewModel.onEvent(readerEvent)
                }
                true
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
                    }
                )
            }
        }
        
        // Tap zone overlay for navigation
        if (!state.isLoading && state.pages.isNotEmpty() && !state.isMenuVisible) {
            SimpleTapZoneOverlay(
                onLeftTap = { viewModel.onEvent(ReaderEvent.PrevPage) },
                onCenterTap = { viewModel.onEvent(ReaderEvent.ToggleMenu) },
                onRightTap = { viewModel.onEvent(ReaderEvent.NextPage) },
                isRtl = state.readingDirection == app.otakureader.feature.reader.model.ReadingDirection.RTL,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Menu overlay
        ReaderMenuOverlay(
            isVisible = state.isMenuVisible,
            chapterTitle = state.chapterTitle,
            currentPage = state.displayPageNumber,
            totalPages = state.totalPages,
            currentMode = state.mode,
            zoomLevel = state.zoomLevel,
            brightness = state.brightness,
            colorFilterMode = state.colorFilterMode,
            customTintColor = state.customTintColor,
            readerBackgroundColor = state.readerBackgroundColor,
            pageRotation = state.pageRotation,
            onBrightnessChange = { viewModel.onEvent(ReaderEvent.OnBrightnessChange(it)) },
            onModeChange = { viewModel.onEvent(ReaderEvent.OnModeChange(it)) },
            onColorFilterChange = { viewModel.onEvent(ReaderEvent.SetColorFilterMode(it)) },
            onCustomTintColorChange = { viewModel.onEvent(ReaderEvent.SetCustomTintColor(it)) },
            onReaderBackgroundColorChange = { viewModel.onEvent(ReaderEvent.SetReaderBackgroundColor(it)) },
            onRotateCW = { viewModel.onEvent(ReaderEvent.RotateCW) },
            onResetRotation = { viewModel.onEvent(ReaderEvent.ResetRotation) },
            onZoomIn = { viewModel.onEvent(ReaderEvent.ZoomIn) },
            onZoomOut = { viewModel.onEvent(ReaderEvent.ZoomOut) },
            onResetZoom = { viewModel.onEvent(ReaderEvent.ResetZoom) },
            onToggleGallery = { viewModel.onEvent(ReaderEvent.ToggleGallery) },
            onNavigateBack = onNavigateBack,
            onToggleFullscreen = { viewModel.onEvent(ReaderEvent.ToggleFullscreen) }
        )
        
        // Bottom thumbnail strip
        PageThumbnailStrip(
            pages = state.pages,
            currentPage = state.currentPage,
            onPageClick = { viewModel.jumpToPage(it) },
            onExpandClick = { viewModel.onEvent(ReaderEvent.ToggleGallery) },
            isVisible = !state.isMenuVisible && !state.isGalleryOpen && !state.isLoading,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
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
        
        // Brightness slider overlay
        BrightnessSliderOverlay(
            brightness = state.brightness,
            onBrightnessChange = { viewModel.onEvent(ReaderEvent.OnBrightnessChange(it)) },
            isVisible = showBrightnessSlider,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // Page slider — shown when the menu is visible so users can quickly scrub pages
        PageSlider(
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            onPageSeek = { viewModel.onEvent(ReaderEvent.OnPageChange(it)) },
            readingDirection = state.readingDirection,
            isVisible = state.isMenuVisible && state.pages.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )

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

        // SFX translate FAB – shown when the feature is enabled and the main menu is hidden
        if (state.sfxTranslationEnabled && !state.isMenuVisible && !state.isGalleryOpen && !state.isLoading) {
            FloatingActionButton(
                onClick = { viewModel.onEvent(ReaderEvent.OpenSfxDialog) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 80.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = stringResource(R.string.reader_sfx_translate),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // SFX Translation Dialog
        if (state.sfxTranslationEnabled && state.showSfxDialog) {
            SfxTranslationDialog(
                sfxTranslations = state.sfxTranslations,
                isSfxTranslating = state.isSfxTranslating,
                onTranslate = { viewModel.onEvent(ReaderEvent.TranslateSfx(it)) },
                onDismiss = { viewModel.onEvent(ReaderEvent.CloseSfxDialog) }
            )
        }
    }
}

@Composable
private fun ReaderContent(
    state: app.otakureader.feature.reader.viewmodel.ReaderState,
    onPageChange: (Int) -> Unit,
    onPanelChange: (Int) -> Unit,
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onZoomChange: (Float) -> Unit
) {
    // Use the per-manga background color if set, otherwise default to black
    val backgroundColor = if (state.readerBackgroundColor != null) {
        Color(state.readerBackgroundColor.toInt())
    } else {
        Color.Black
    }

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
        when (state.mode) {
            ReaderMode.SINGLE_PAGE -> {
                SinglePageReader(
                    pages = state.pages,
                    currentPage = state.currentPage,
                    onPageChange = onPageChange,
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                    onZoomChange = onZoomChange,
                    rotation = state.pageRotation.degrees,
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
                    isRtl = state.readingDirection == app.otakureader.feature.reader.model.ReadingDirection.RTL,
                    rotation = state.pageRotation.degrees,
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
                    rotation = state.pageRotation.degrees,
                    cropBordersEnabled = state.cropBordersEnabled,
                    imageQuality = state.imageQuality,
                    dataSaverEnabled = state.dataSaverEnabled,
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

/**
 * Dialog that lets the user type a manga sound effect (SFX) and see its AI translation.
 *
 * Previously translated SFX are shown in a scrollable list for quick reference.
 */
@Composable
private fun SfxTranslationDialog(
    sfxTranslations: Map<String, String>,
    isSfxTranslating: Boolean,
    onTranslate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var sfxInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.reader_sfx_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = sfxInput,
                    onValueChange = { sfxInput = it },
                    label = { Text(stringResource(R.string.reader_sfx_input_label)) },
                    placeholder = { Text(stringResource(R.string.reader_sfx_input_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSfxTranslating
                )

                if (isSfxTranslating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.reader_sfx_translating),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Show the latest translation result if available
                val latestTranslation = sfxTranslations[sfxInput.trim()]
                if (latestTranslation != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = latestTranslation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // History of previous translations
                if (sfxTranslations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.reader_sfx_history_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.height(120.dp)) {
                        sfxTranslations.entries.toList().reversed().forEach { (sfx, translation) ->
                            item(key = sfx) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = sfx,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = translation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onTranslate(sfxInput.trim()) },
                enabled = sfxInput.isNotBlank() && !isSfxTranslating
            ) {
                Text(stringResource(R.string.reader_sfx_translate_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_sfx_close))
            }
        }
    )
}
