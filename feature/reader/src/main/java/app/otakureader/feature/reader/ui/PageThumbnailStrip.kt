package app.otakureader.feature.reader.ui

import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.animation.AnimatedVisibility
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.animation.fadeIn
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.animation.fadeOut
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.animation.slideInVertically
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.animation.slideOutVertically
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.background
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.clickable
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.Arrangement
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.Box
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.Column
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.PaddingValues
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.fillMaxWidth
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.height
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.padding
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.size
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.width
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.lazy.LazyRow
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.lazy.itemsIndexed
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.lazy.rememberLazyListState
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.shape.RoundedCornerShape
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.MaterialTheme
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.Surface
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.Text
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.Composable
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.LaunchedEffect
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.Alignment
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.Modifier
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.model.ReaderPage
import coil3.compose.AsyncImage

/**
 * Bottom thumbnail strip for quick page navigation.
 * Shows a horizontal scrollable row of page thumbnails.
 */
@Composable
fun PageThumbnailStrip(
    pages: List<ReaderPage>,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Header with expand button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${currentPage + 1} / ${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    
                    Text(
                        text = "Expand",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable(onClick = onExpandClick)
                            .padding(4.dp)
                    )
                }
                
                // Thumbnail row
                val listState = rememberLazyListState()
                
                LaunchedEffect(currentPage) {
                    listState.animateScrollToItem(
                        index = currentPage.coerceIn(0, pages.size - 1),
                        scrollOffset = -100
                    )
                }
                
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages) { index, page ->
                        PageThumbnailItem(
                            page = page,
                            pageNumber = index + 1,
                            isSelected = index == currentPage,
                            onClick = { onPageClick(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailItem(
    page: ReaderPage,
    pageNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
        ) {
            AsyncImage(
                model = page.imageUrl ?: page.thumbnailUrl,
                contentDescription = stringResource(R.string.reader_page_number, pageNumber),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
            }
        }
        
        // Page number
        Text(
            text = pageNumber.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
