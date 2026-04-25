package app.otakureader.feature.reader.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.components.ZoomableImage
import app.otakureader.feature.reader.model.ImageQuality
import app.otakureader.feature.reader.model.ReaderPage

private const val MOUSE_SCROLL_MULTIPLIER = 120f

/**
 * Webtoon reader mode - vertical continuous scrolling.
 * Optimized for Korean webtoons and long-form comics.
 */
@Composable
fun WebtoonReader(
    pages: List<ReaderPage>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
    rotation: Float = 0f,
    cropBordersEnabled: Boolean = false,
    imageQuality: ImageQuality = ImageQuality.ORIGINAL,
    dataSaverEnabled: Boolean = false,
    pageGapDp: Int = 4,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentPage
    )
    val coroutineScope = rememberCoroutineScope()
    
    // Track current page based on scroll position
    val currentVisiblePage by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            
            if (visibleItems.isNotEmpty()) {
                // Get the most visible item
                val viewportCenter = layoutInfo.viewportEndOffset / 2
                visibleItems.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: currentPage
            } else {
                currentPage
            }
        }
    }
    
    // Notify external observer of page changes
    LaunchedEffect(currentVisiblePage) {
        if (currentVisiblePage != currentPage) {
            onPageChange(currentVisiblePage)
        }
    }
    
    // Scroll to page when external currentPage changes
    LaunchedEffect(currentPage) {
        if (currentVisiblePage != currentPage) {
            listState.animateScrollToItem(currentPage)
        }
    }
    
    val normalizedRotation = ((rotation % 360f) + 360f) % 360f
    val imageContentScale = if (normalizedRotation % 180f == 0f) {
        ContentScale.FillWidth
    } else {
        ContentScale.Fit
    }

    @OptIn(ExperimentalComposeUiApi::class)
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            // Precise mouse-wheel scroll for DeX / external mouse (default Compose delta is too coarse).
            // scrollBy (non-animating) is used intentionally — animateScrollBy queues animations on
            // every pointer event and feels sluggish for continuous mouse-wheel input.
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                coroutineScope.launch { listState.scrollBy(delta * MOUSE_SCROLL_MULTIPLIER) }
            },
        contentPadding = PaddingValues(vertical = pageGapDp.dp),
        verticalArrangement = Arrangement.spacedBy(pageGapDp.dp)
    ) {
        itemsIndexed(
            items = pages,
            key = { _, page -> page.id },
            contentType = { _, _ -> "manga_page" }
        ) { index, page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                ZoomableImage(
                    imageUrl = page.imageUrl,
                    contentDescription = stringResource(R.string.reader_page_content, page.pageNumber),
                    contentScale = imageContentScale,
                    rotation = normalizedRotation,
                    cropBordersEnabled = cropBordersEnabled,
                    imageQuality = imageQuality,
                    dataSaverEnabled = dataSaverEnabled,
                    onTap = onTap,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
