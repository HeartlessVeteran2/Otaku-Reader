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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.components.ZoomableImage
import app.otakureader.feature.reader.model.ReaderPage
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Dual page reader mode - displays two pages side by side (spreads).
 * Perfect for manga and western comics.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DualPageReader(
    pages: List<ReaderPage>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    isRtl: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Calculate spread indices (every 2 pages form a spread)
    val spreadCount = (pages.size + 1) / 2
    val currentSpread = currentPage / 2
    
    val pagerState = rememberPagerState(
        initialPage = currentSpread,
        pageCount = { spreadCount }
    )
    
    // Sync pager state with external current page
    LaunchedEffect(currentPage) {
        val targetSpread = currentPage / 2
        if (pagerState.currentPage != targetSpread) {
            pagerState.animateScrollToPage(targetSpread)
        }
    }
    
    // Notify external observer of page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { spread ->
                onPageChange(spread * 2)
            }
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { spreadIndex ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            val leftPageIndex = if (isRtl) {
                spreadIndex * 2 + 1
            } else {
                spreadIndex * 2
            }
            val rightPageIndex = if (isRtl) {
                spreadIndex * 2
            } else {
                spreadIndex * 2 + 1
            }
            
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left page
                val leftPage = pages.getOrNull(leftPageIndex)
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
                            onTap = onTap,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                
                // Right page
                val rightPage = pages.getOrNull(rightPageIndex)
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
                            onTap = onTap,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}
