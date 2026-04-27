package app.otakureader.feature.library.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot regression tests for Library UI states using Roborazzi.
 *
 * Record:  ./gradlew :feature:library:recordRoborazziDebug
 * Verify:  ./gradlew :feature:library:verifyRoborazziDebug
 *
 * Reference PNGs land in feature/library/src/test/snapshots/images/ and
 * must be committed so CI can compare against them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalRoborazziApi::class)
class LibraryScreenshotTest {

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            roborazziOptions = RoborazziOptions(
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
            )
        )
    )

    // ── 1. Empty library ────────────────────────────────────────────────

    @Test
    fun libraryEmptyState() {
        captureRoboImage {
            MaterialTheme {
                Surface { LibraryEmptyContent() }
            }
        }
    }

    // ── 2. Loading state ────────────────────────────────────────────────

    @Test
    fun libraryLoadingState() {
        captureRoboImage {
            MaterialTheme {
                Surface { LibraryLoadingContent() }
            }
        }
    }

    // ── 3. Grid with placeholder cards ──────────────────────────────────

    @Test
    fun libraryGridState() {
        captureRoboImage {
            MaterialTheme {
                Surface { LibraryGridContent(itemCount = 9) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lightweight composables used only in screenshot tests (no ViewModel needed)
// ---------------------------------------------------------------------------

@Composable
private fun LibraryEmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Your library is empty.\nAdd manga from Browse.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun LibraryLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LibraryGridContent(itemCount: Int) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(itemCount) { index ->
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = "Manga ${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.items(
    count: Int,
    content: @Composable androidx.compose.foundation.lazy.grid.LazyGridItemScope.(Int) -> Unit,
) = repeat(count) { index -> item { content(index) } }
