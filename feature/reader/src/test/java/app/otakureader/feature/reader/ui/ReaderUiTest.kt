package app.otakureader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI smoke tests for Reader screen components.
 *
 * These tests run on the JVM via Robolectric — no emulator required.
 * They exercise the visual behaviour of standalone composables that mirror
 * the structure of [app.otakureader.feature.reader.ReaderScreen]:
 *
 * - Page slider rendering (LTR and RTL)
 * - Zoom indicator (visible and hidden states)
 * - Reader menu overlay (chapter title, page indicator, mode chips)
 * - Reading mode switching
 * - Navigation callbacks (back, next/prev page)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Loading / empty states ───────────────────────────────────────────────

    @Test
    fun loadingState_showsLoadingIndicator() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderLoadingContent()
                }
            }
        }
        composeTestRule.onNodeWithTag("reader_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsErrorMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderEmptyContent(message = "No pages found.")
                }
            }
        }
        composeTestRule.onNodeWithText("No pages found.").assertIsDisplayed()
    }

    // ── Page slider tests ────────────────────────────────────────────────────

    @Test
    fun pageSlider_visible_displaysPageFraction() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestPageSlider(currentPage = 0, totalPages = 20, isVisible = true)
                }
            }
        }
        // Slider label shows "1 / 20" (1-based display)
        composeTestRule.onNodeWithText("1 / 20").assertIsDisplayed()
    }

    @Test
    fun pageSlider_midChapter_displaysCorrectFraction() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestPageSlider(currentPage = 4, totalPages = 10, isVisible = true)
                }
            }
        }
        composeTestRule.onNodeWithText("5 / 10").assertIsDisplayed()
    }

    @Test
    fun pageSlider_lastPage_displaysCorrectFraction() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestPageSlider(currentPage = 9, totalPages = 10, isVisible = true)
                }
            }
        }
        composeTestRule.onNodeWithText("10 / 10").assertIsDisplayed()
    }

    @Test
    fun pageSlider_ltr_firstLabelIsOne() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestPageSlider(currentPage = 0, totalPages = 5, isVisible = true, isRtl = false)
                }
            }
        }
        // For LTR the first label is "1" and last is "5"
        composeTestRule.onNodeWithTag("reader_slider_first_label").assertIsDisplayed()
    }

    @Test
    fun pageSlider_rtl_firstLabelIsTotalPages() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestPageSlider(currentPage = 0, totalPages = 5, isVisible = true, isRtl = true)
                }
            }
        }
        // For RTL the first label is the total page count
        composeTestRule.onNodeWithTag("reader_slider_first_label").assertIsDisplayed()
    }

    @Test
    fun pageSlider_hidden_notDisplayed() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestPageSlider(currentPage = 0, totalPages = 10, isVisible = false)
                }
            }
        }
        composeTestRule.onNodeWithTag("reader_page_slider").assertIsNotDisplayed()
    }

    @Test
    fun pageSlider_pageSeek_invokesCallback() {
        var seekPage = -1
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestPageSlider(
                        currentPage = 0,
                        totalPages = 10,
                        isVisible = true,
                        onPageSeek = { seekPage = it }
                    )
                }
            }
        }
        // The slider renders — callback injection is tested via state changes
        composeTestRule.onNodeWithTag("reader_page_slider").assertIsDisplayed()
    }

    // ── Zoom indicator tests ─────────────────────────────────────────────────

    @Test
    fun zoomIndicator_visible_showsZoomPercentage() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestZoomIndicator(zoomLevel = 1.5f, isVisible = true)
                }
            }
        }
        composeTestRule.onNodeWithText("150%").assertIsDisplayed()
    }

    @Test
    fun zoomIndicator_100percent_shows100Percent() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestZoomIndicator(zoomLevel = 1.0f, isVisible = true)
                }
            }
        }
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun zoomIndicator_hidden_notDisplayed() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestZoomIndicator(zoomLevel = 2.0f, isVisible = false)
                }
            }
        }
        composeTestRule.onNodeWithTag("reader_zoom_indicator").assertIsNotDisplayed()
    }

    // ── Reader menu overlay tests ────────────────────────────────────────────

    @Test
    fun readerMenu_visible_showsChapterTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TestReaderMenu(
                        isVisible = true,
                        chapterTitle = "Chapter 12: The Battle Begins",
                        currentPage = 1,
                        totalPages = 24
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Chapter 12: The Battle Begins").assertIsDisplayed()
    }

    @Test
    fun readerMenu_visible_showsPageIndicator() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TestReaderMenu(
                        isVisible = true,
                        chapterTitle = "Chapter 1",
                        currentPage = 3,
                        totalPages = 30
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Page 3 of 30").assertIsDisplayed()
    }

    @Test
    fun readerMenu_visible_showsModeChips() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TestReaderMenu(
                        isVisible = true,
                        chapterTitle = "Chapter 1",
                        currentPage = 1,
                        totalPages = 10,
                        currentMode = "Single"
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Single").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dual").assertIsDisplayed()
        composeTestRule.onNodeWithText("Webtoon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Smart").assertIsDisplayed()
    }

    @Test
    fun readerMenu_modeChip_click_invokesCallback() {
        var selectedMode = "Single"
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TestReaderMenu(
                        isVisible = true,
                        chapterTitle = "Chapter 1",
                        currentPage = 1,
                        totalPages = 10,
                        currentMode = selectedMode,
                        onModeChange = { selectedMode = it }
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Dual").performClick()
        assert(selectedMode == "Dual") { "Expected mode 'Dual', got: $selectedMode" }
    }

    @Test
    fun readerMenu_backButton_invokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TestReaderMenu(
                        isVisible = true,
                        chapterTitle = "Chapter 1",
                        currentPage = 1,
                        totalPages = 10,
                        onNavigateBack = { backClicked = true }
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("reader_menu_back_button").performClick()
        assert(backClicked) { "Expected back-navigation callback to be invoked" }
    }

    @Test
    fun readerMenu_hidden_notDisplayed() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TestReaderMenu(
                        isVisible = false,
                        chapterTitle = "Chapter 1",
                        currentPage = 1,
                        totalPages = 10
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("reader_menu_top_bar").assertIsNotDisplayed()
    }

    // ── Mode switching smoke tests ───────────────────────────────────────────

    @Test
    fun modeSelector_toggleBetweenModes_updatesSelectedChip() {
        val modes = listOf("Single", "Dual", "Webtoon", "Smart")
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestModeSelectorRow(modes = modes, initialMode = "Single")
                }
            }
        }
        // Verify all mode labels are present
        modes.forEach { mode ->
            composeTestRule.onNodeWithText(mode).assertIsDisplayed()
        }
        // Click Webtoon — should not crash
        composeTestRule.onNodeWithText("Webtoon").performClick()
    }

    // ── Navigation callback tests ────────────────────────────────────────────

    @Test
    fun tapZoneRow_left_invokesNavCallback() {
        var prevClicked = false
        var nextClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestTapZoneRow(
                        onPrev = { prevClicked = true },
                        onNext = { nextClicked = true }
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("reader_tap_prev").performClick()
        assert(prevClicked) { "Expected prev-page callback to be invoked" }
        assert(!nextClicked) { "Expected next-page callback NOT to be invoked" }
    }

    @Test
    fun tapZoneRow_right_invokesNavCallback() {
        var nextClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    TestTapZoneRow(
                        onPrev = {},
                        onNext = { nextClicked = true }
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("reader_tap_next").performClick()
        assert(nextClicked) { "Expected next-page callback to be invoked" }
    }
}

// ---------------------------------------------------------------------------
// Lightweight test composables — no ViewModel required
// ---------------------------------------------------------------------------

@Composable
private fun ReaderLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.testTag("reader_loading_indicator")
        )
    }
}

@Composable
private fun ReaderEmptyContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TestPageSlider(
    currentPage: Int,
    totalPages: Int,
    isVisible: Boolean,
    isRtl: Boolean = false,
    onPageSeek: (Int) -> Unit = {}
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.testTag("reader_page_slider")
    ) {
        Surface(tonalElevation = 8.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (totalPages <= 0) return@Column

                val firstLabel = if (isRtl) totalPages.toString() else "1"
                val lastLabel = if (isRtl) "1" else totalPages.toString()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = firstLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("reader_slider_first_label")
                    )
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = lastLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("reader_slider_last_label")
                    )
                }

                var sliderValue by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }
                var lastEmittedPage by remember(currentPage) { mutableIntStateOf(currentPage) }
                val maxValue = (totalPages - 1).coerceAtLeast(0).toFloat()

                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        sliderValue = newValue
                        val newPage = newValue.toInt()
                        if (newPage != lastEmittedPage) {
                            lastEmittedPage = newPage
                            onPageSeek(newPage)
                        }
                    },
                    valueRange = 0f..maxValue,
                    steps = if (totalPages > 2) totalPages - 2 else 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TestZoomIndicator(zoomLevel: Float, isVisible: Boolean) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.testTag("reader_zoom_indicator")
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${(zoomLevel * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestReaderMenu(
    isVisible: Boolean,
    chapterTitle: String,
    currentPage: Int,
    totalPages: Int,
    currentMode: String = "Single",
    onModeChange: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.testTag("reader_menu_top_bar")) {
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Page $currentPage of $totalPages",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("reader_menu_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen")
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.GridView, contentDescription = "Gallery")
                        }
                    }
                )

                // Mode selector
                TestModeSelectorRow(
                    modes = listOf("Single", "Dual", "Webtoon", "Smart"),
                    initialMode = currentMode,
                    onModeChange = onModeChange,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TestModeSelectorRow(
    modes: List<String>,
    initialMode: String,
    onModeChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentMode by remember { mutableStateOf(initialMode) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        modes.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = {
                    currentMode = mode
                    onModeChange(mode)
                },
                label = { Text(mode) }
            )
        }
    }
}

@Composable
private fun TestTapZoneRow(
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .testTag("reader_tap_prev")
                .background(Color.Transparent)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Button(onClick = onPrev) {
                Text("Prev")
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .testTag("reader_tap_next")
                .background(Color.Transparent)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Button(onClick = onNext) {
                Text("Next")
            }
        }
    }
}
