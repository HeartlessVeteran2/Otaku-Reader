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
 * Smart Panels reader mode - navigates by detected comic panels.
 * Automatically detects and zooms into individual comic panels.
 * Falls back to single page mode if no panels detected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartPanelsReader(
    pages: List<ReaderPage>,
    currentPage: Int,
    currentPanel: Int,
    onPageChange: (Int) -> Unit,
    onPanelChange: (Int) -> Unit,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    rotation: Float = 0f,
    cropBordersEnabled: Boolean = false,
    dataSaverEnabled: Boolean = false,
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
        val page = pages[pageIndex]
        val panels = page.panels
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (panels.isNotEmpty()) {
                // Smart panel navigation
                SmartPanelView(
                    page = page,
                    currentPanel = if (pageIndex == currentPage) currentPanel else 0,
                    onPanelChange = onPanelChange,
                    onTap = onTap,
                    rotation = rotation,
                    cropBordersEnabled = cropBordersEnabled,
                    dataSaverEnabled = dataSaverEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback to regular zoomable image
                ZoomableImage(
                    imageUrl = page.imageUrl,
                    contentDescription = stringResource(R.string.reader_page_content, page.pageNumber),
                    contentScale = ContentScale.Fit,
                    rotation = rotation,
                    cropBordersEnabled = cropBordersEnabled,
                    dataSaverEnabled = dataSaverEnabled,
                    onTap = onTap,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SmartPanelView(
    page: ReaderPage,
    currentPanel: Int,
    onPanelChange: (Int) -> Unit,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    rotation: Float = 0f,
    cropBordersEnabled: Boolean = false,
    dataSaverEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    // For now, fall back to zoomable image with panel overlay
    // Advanced panel detection and navigation can be added later
    ZoomableImage(
        imageUrl = page.imageUrl,
        contentDescription = stringResource(R.string.reader_page_content, page.pageNumber),
        contentScale = ContentScale.Fit,
        rotation = rotation,
        cropBordersEnabled = cropBordersEnabled,
        dataSaverEnabled = dataSaverEnabled,
        onTap = onTap,
        modifier = modifier
    )
}
