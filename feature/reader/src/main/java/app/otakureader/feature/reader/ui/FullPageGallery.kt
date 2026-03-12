package app.otakureader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.model.ReaderPage
import coil3.compose.AsyncImage

/** Valid column counts for the gallery grid. */
private val GALLERY_COLUMN_OPTIONS = listOf(2, 3, 4)

/**
 * Full-screen gallery view with a configurable grid of page thumbnails.
 * Automatically scrolls to the current page when opened, and allows quick
 * navigation to any page in the chapter.
 *
 * @param pages        All pages in the current chapter.
 * @param currentPage  Zero-based index of the currently active page.
 * @param columns      Number of grid columns to display (2, 3, or 4).
 * @param onPageClick  Called with the zero-based index when the user taps a thumbnail.
 * @param onDismiss    Called when the user closes the gallery.
 * @param onColumnsChange Called with the new column count when the user selects a different layout.
 * @param isVisible    Controls the animated visibility of the gallery overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPageGallery(
    pages: List<ReaderPage>,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    isVisible: Boolean,
    columns: Int = 3,
    onColumnsChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text("Page Gallery (${pages.size} pages)")
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.reader_close_gallery))
                        }
                    },
                    actions = {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        GALLERY_COLUMN_OPTIONS.forEach { count ->
                            FilterChip(
                                selected = columns == count,
                                onClick = { onColumnsChange(count) },
                                label = { Text("$count columns") },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                )

                val gridState = rememberLazyGridState()

                // Scroll to the current page whenever the gallery opens or the active page changes.
                LaunchedEffect(isVisible, currentPage) {
                    if (isVisible && pages.isNotEmpty()) {
                        val target = currentPage.coerceIn(0, pages.lastIndex)
                        gridState.scrollToItem(target)
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns.coerceIn(2, 4)),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(pages) { index, page ->
                        GalleryThumbnailItem(
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
private fun GalleryThumbnailItem(
    page: ReaderPage,
    pageNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
        ) {
            AsyncImage(
                model = page.imageUrl ?: page.thumbnailUrl,
                contentDescription = stringResource(R.string.reader_page_number, pageNumber),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Selection overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "CURRENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Text(
            text = "Page $pageNumber",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
