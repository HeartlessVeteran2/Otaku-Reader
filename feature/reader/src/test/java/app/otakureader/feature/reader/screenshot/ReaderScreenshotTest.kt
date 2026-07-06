package app.otakureader.feature.reader.screenshot

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Snapshot tests for Reader screen states and theme variants.
 * Runs on the JVM via Robolectric — no emulator required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Loading state ────────────────────────────────────────────────────────

    @Test
    fun readerLoadingState_lightTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderLoadingContent()
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun readerLoadingState_darkTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderLoadingContent()
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── Empty/error state ────────────────────────────────────────────────────

    @Test
    fun readerEmptyState_lightTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderEmptyContent(message = "No pages found.")
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun readerEmptyState_darkTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderEmptyContent(message = "No pages found.")
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── Reader menu overlay state ────────────────────────────────────────────

    @Test
    fun readerMenu_lightTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderMenuContent(
                        chapterTitle = "Chapter 12: The Battle",
                        currentPage = 5,
                        totalPages = 24,
                        currentMode = "Single"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun readerMenu_darkTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderMenuContent(
                        chapterTitle = "Chapter 12: The Battle",
                        currentPage = 5,
                        totalPages = 24,
                        currentMode = "Webtoon"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── Zoom indicator ───────────────────────────────────────────────────────

    @Test
    fun zoomIndicator_lightTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface {
                    ZoomIndicatorContent(zoomLevel = 1.5f)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun zoomIndicator_darkTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    ZoomIndicatorContent(zoomLevel = 2.0f)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── Page slider ──────────────────────────────────────────────────────────

    @Test
    fun pageSlider_lightTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface {
                    PageSliderContent(currentPage = 4, totalPages = 20)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun pageSlider_darkTheme() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    PageSliderContent(currentPage = 4, totalPages = 20)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}

// ---------------------------------------------------------------------------
// Lightweight composables used only in tests (no ViewModel needed)
// ---------------------------------------------------------------------------

@Composable
private fun ReaderLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderMenuContent(
    chapterTitle: String,
    currentPage: Int,
    totalPages: Int,
    currentMode: String
) {
    val modes = listOf("Single", "Dual", "Webtoon", "Smart")

    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(text = chapterTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Page $currentPage of $totalPages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                modes.forEach { mode ->
                    FilterChip(
                        selected = currentMode == mode,
                        onClick = {},
                        label = { Text(mode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomIndicatorContent(zoomLevel: Float) {
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

@Composable
private fun PageSliderContent(currentPage: Int, totalPages: Int) {
    Surface(tonalElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "1", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "${currentPage + 1} / $totalPages",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(text = totalPages.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
