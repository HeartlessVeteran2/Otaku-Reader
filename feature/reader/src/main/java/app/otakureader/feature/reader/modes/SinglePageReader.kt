package app.otakureader.feature.reader.modes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.components.ZoomableImage
import app.otakureader.feature.reader.model.ReaderPage
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Single page reader mode - displays one page at a time.
 * Supports zoom, pan, and swipe navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SinglePageReader(
    pages: List<ReaderPage>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    onDoubleTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    onZoomChange: (Float) -> Unit,
    rotation: Float = 0f,
    cropBordersEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { pages.size }
    )
    
    // Sync pager state with external current page
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) {
            pagerState.animateScrollToPage(currentPage)
        }
    }
    
    // Notify external observer of page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onPageChange(page)
            }
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { pageIndex ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            val page = pages[pageIndex]
            
            ZoomableImage(
                imageUrl = page.imageUrl,
                contentDescription = stringResource(R.string.reader_page_content, page.pageNumber),
                contentScale = ContentScale.Fit,
                rotation = rotation,
                cropBordersEnabled = cropBordersEnabled,
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onZoomChange = onZoomChange,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
