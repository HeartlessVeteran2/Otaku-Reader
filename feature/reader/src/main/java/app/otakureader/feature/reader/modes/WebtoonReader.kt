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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.components.ZoomableImage
import app.otakureader.feature.reader.model.ReaderPage

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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentPage
    )
    
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

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(
            items = pages,
            key = { index, page -> page.id }
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
                    onTap = onTap,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
