package app.otakureader.feature.reader.modes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.components.ZoomableImage
import app.otakureader.feature.reader.model.ImageQuality
import app.otakureader.feature.reader.model.ReaderPage
import kotlinx.coroutines.flow.distinctUntilChanged

private const val SPREAD_ASPECT_RATIO_THRESHOLD = 1.2f

/**
 * Builds a list of spread groups from a flat page list.
 *
 * A page is treated as a full-spread (solo) when:
 *  - its [ReaderPage.isSpread] flag is set by the source, OR
 *  - it has been detected as landscape via [detectedSpreads] (width/height > threshold).
 *
 * All other pages are paired left+right.
 */
private fun buildSpreadGroups(
    pages: List<ReaderPage>,
    detectedSpreads: Map<Int, Boolean>
): List<IntArray> {
    val groups = mutableListOf<IntArray>()
    var i = 0
    while (i < pages.size) {
        val page = pages[i]
        val isSolo = page.isSpread || (detectedSpreads[page.index] == true)
        if (isSolo) {
            groups.add(intArrayOf(i))
            i++
        } else {
            val nextPage = pages.getOrNull(i + 1)
            val nextIsSolo = nextPage != null &&
                (nextPage.isSpread || (detectedSpreads[nextPage.index] == true))
            if (nextPage == null || nextIsSolo) {
                groups.add(intArrayOf(i))
                i++
            } else {
                groups.add(intArrayOf(i, i + 1))
                i += 2
            }
        }
    }
    return groups
}

/**
 * Dual page reader mode - displays two pages side by side (spreads).
 * Auto-detects landscape images and displays them as full-width solo spreads.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DualPageReader(
    pages: List<ReaderPage>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    isRtl: Boolean = false,
    rotation: Float = 0f,
    cropBordersEnabled: Boolean = false,
    imageQuality: ImageQuality = ImageQuality.ORIGINAL,
    dataSaverEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Map from page.index → true if detected as a landscape spread.
    // Keyed on `pages` so the map is cleared automatically when a new chapter loads.
    val detectedSpreads = remember(pages) { mutableStateMapOf<Int, Boolean>() }

    val spreadGroups by remember(pages) { derivedStateOf { buildSpreadGroups(pages, detectedSpreads) } }

    val currentGroupIndex = remember(currentPage, spreadGroups) {
        spreadGroups.indexOfFirst { group -> group.any { it == currentPage } }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = currentGroupIndex,
        pageCount = { spreadGroups.size }
    )

    // Sync pager to external currentPage
    LaunchedEffect(currentPage, spreadGroups) {
        val target = spreadGroups.indexOfFirst { group -> group.any { it == currentPage } }
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Notify external observer on spread change
    LaunchedEffect(pagerState, spreadGroups) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { groupIdx ->
                val group = spreadGroups.getOrNull(groupIdx)
                if (group != null) onPageChange(group.first())
            }
    }

    HorizontalPager(
        state = pagerState,
        beyondBoundsPageCount = 1,
        modifier = modifier.fillMaxSize()
    ) { groupIndex ->
        val group = spreadGroups.getOrNull(groupIndex) ?: return@HorizontalPager

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (group.size == 1) {
                // Solo page (source-flagged spread or auto-detected landscape)
                val page = pages.getOrNull(group[0])
                if (page != null) {
                    ZoomableImage(
                        imageUrl = page.imageUrl,
                        contentDescription = stringResource(R.string.reader_page_content, page.pageNumber),
                        contentScale = ContentScale.Fit,
                        rotation = rotation,
                        cropBordersEnabled = cropBordersEnabled,
                        imageQuality = imageQuality,
                        dataSaverEnabled = dataSaverEnabled,
                        onTap = onTap,
                        onImageSizeKnown = { w, h ->
                            if (w > 0 && h > 0) {
                                detectedSpreads[page.index] = w.toFloat() / h > SPREAD_ASPECT_RATIO_THRESHOLD
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Two-page spread
                val leftIdx = if (isRtl) group[1] else group[0]
                val rightIdx = if (isRtl) group[0] else group[1]

                Row(modifier = Modifier.fillMaxSize()) {
                    val leftPage = pages.getOrNull(leftIdx)
                    if (leftPage != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                        ) {
                            ZoomableImage(
                                imageUrl = leftPage.imageUrl,
                                contentDescription = stringResource(R.string.reader_page_content, leftPage.pageNumber),
                                contentScale = ContentScale.Fit,
                                rotation = rotation,
                                cropBordersEnabled = cropBordersEnabled,
                                imageQuality = imageQuality,
                                dataSaverEnabled = dataSaverEnabled,
                                onTap = onTap,
                                onImageSizeKnown = { w, h ->
                                    if (w > 0 && h > 0) {
                                        detectedSpreads[leftPage.index] = w.toFloat() / h > SPREAD_ASPECT_RATIO_THRESHOLD
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight())
                    }

                    val rightPage = pages.getOrNull(rightIdx)
                    if (rightPage != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                        ) {
                            ZoomableImage(
                                imageUrl = rightPage.imageUrl,
                                contentDescription = stringResource(R.string.reader_page_content, rightPage.pageNumber),
                                contentScale = ContentScale.Fit,
                                rotation = rotation,
                                cropBordersEnabled = cropBordersEnabled,
                                imageQuality = imageQuality,
                                dataSaverEnabled = dataSaverEnabled,
                                onTap = onTap,
                                onImageSizeKnown = { w, h ->
                                    if (w > 0 && h > 0) {
                                        detectedSpreads[rightPage.index] = w.toFloat() / h > SPREAD_ASPECT_RATIO_THRESHOLD
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}
