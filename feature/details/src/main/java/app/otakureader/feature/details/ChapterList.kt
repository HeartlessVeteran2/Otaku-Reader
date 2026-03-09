package app.otakureader.feature.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chapter list with grouping, sorting, and chapter actions
 */
@Composable
fun ChapterList(
    chapters: List<DetailsContract.ChapterItem>,
    selectedChapters: Set<Long>,
    groupedChapters: Map<String?, List<DetailsContract.ChapterItem>>,
    sortOrder: DetailsContract.ChapterSortOrder,
    onSortOrderChange: () -> Unit,
    onChapterClick: (Long) -> Unit,
    onChapterLongClick: (Long) -> Unit,
    onToggleRead: (Long) -> Unit,
    onToggleBookmark: (Long) -> Unit,
    onDownloadChapter: (Long) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onMarkPreviousRead: (Long) -> Unit,
    onExportAsCbz: (Long) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDownloadSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onMarkSelectedAsRead: () -> Unit = {},
    onMarkSelectedAsUnread: () -> Unit = {},
    onBookmarkSelected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header with sort option and selection actions
        ChapterListHeader(
            chapterCount = chapters.size,
            selectedCount = selectedChapters.size,
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
            onClearSelection = onClearSelection,
            onSelectAll = onSelectAll,
            onDownloadSelected = onDownloadSelected,
            onDeleteSelected = onDeleteSelected,
            onMarkSelectedAsRead = onMarkSelectedAsRead,
            onMarkSelectedAsUnread = onMarkSelectedAsUnread,
            onBookmarkSelected = onBookmarkSelected
        )

        // Chapter list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            groupedChapters.forEach { (volume, volumeChapters) ->
                // Volume header
                volume?.let {
                    item(key = "volume_$it") {
                        VolumeHeader(volume = it)
                    }
                }

                // Chapters in this volume
                items(
                    items = volumeChapters,
                    key = { "chapter_${it.id}" }
                ) { chapter ->
                    ChapterListItem(
                        chapter = chapter,
                        isSelected = selectedChapters.contains(chapter.id),
                        onClick = { onChapterClick(chapter.id) },
                        onLongClick = { onChapterLongClick(chapter.id) },
                        onToggleRead = { onToggleRead(chapter.id) },
                        onToggleBookmark = { onToggleBookmark(chapter.id) },
                        onDownload = { onDownloadChapter(chapter.id) },
                        onDeleteDownload = { onDeleteDownload(chapter.id) },
                        onMarkPreviousRead = { onMarkPreviousRead(chapter.id) },
                        onExportAsCbz = { onExportAsCbz(chapter.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterListHeader(
    chapterCount: Int,
    selectedCount: Int,
    sortOrder: DetailsContract.ChapterSortOrder,
    onSortOrderChange: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMarkSelectedAsRead: () -> Unit,
    onMarkSelectedAsUnread: () -> Unit,
    onBookmarkSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedCount > 0) {
                // Selection mode header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear selection"
                        )
                    }
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Action buttons
                Row {
                    IconButton(onClick = onDownloadSelected) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download selected"
                        )
                    }
                    IconButton(onClick = onMarkSelectedAsRead) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Mark as read"
                        )
                    }
                    IconButton(onClick = onBookmarkSelected) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Bookmark selected"
                        )
                    }
                }
            } else {
                // Normal mode header
                Text(
                    text = "$chapterCount Chapters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row {
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Select all"
                        )
                    }
                    IconButton(onClick = onSortOrderChange) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort chapters"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = when (sortOrder) {
                                    DetailsContract.ChapterSortOrder.ASCENDING -> Icons.Default.KeyboardArrowUp
                                    DetailsContract.ChapterSortOrder.DESCENDING -> Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeHeader(
    volume: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = volume,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ChapterListItem(
    chapter: DetailsContract.ChapterItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleRead: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onDownload: () -> Unit = {},
    onDeleteDownload: () -> Unit = {},
    onMarkPreviousRead: () -> Unit = {},
    onExportAsCbz: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (chapter.read) 0.6f else 1f,
        label = "readAlpha"
    )

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .alpha(alpha)
            .combinedClickable(onClick = onClick, onLongClick = {
                showMenu = true
                onLongClick()
            }),
        colors = CardDefaults.cardColors(
            containerColor = if (chapter.read) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show checkbox when selected
            if (isSelected) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Chapter info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatChapterName(chapter),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Scanlator
                    chapter.scanlator?.let { scanlator ->
                        if (scanlator.isNotBlank()) {
                            Text(
                                text = scanlator,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }

                    // Upload date
                    if (chapter.dateUpload > 0) {
                        Text(
                            text = formatDate(chapter.dateUpload),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Actions (hidden when selected)
            if (!isSelected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Read indicator
                    IconButton(onClick = onToggleRead) {
                        Icon(
                            imageVector = if (chapter.read) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Circle
                            },
                            contentDescription = if (chapter.read) "Mark as unread" else "Mark as read",
                            tint = if (chapter.read) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }

                    // Bookmark
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (chapter.bookmark) {
                                Icons.Default.Bookmark
                            } else {
                                Icons.Default.BookmarkBorder
                            },
                            contentDescription = if (chapter.bookmark) "Remove bookmark" else "Add bookmark",
                            tint = if (chapter.bookmark) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }

                    // Download status
                    DownloadIcon(
                        status = chapter.downloadStatus,
                        onDownload = onDownload,
                        onDelete = onDeleteDownload
                    )
                }
            }
        }
    }

    // Dropdown menu for chapter options
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text(if (chapter.read) "Mark as unread" else "Mark as read") },
            onClick = {
                onToggleRead()
                showMenu = false
            }
        )
        DropdownMenuItem(
            text = { Text(if (chapter.bookmark) "Remove bookmark" else "Bookmark chapter") },
            onClick = {
                onToggleBookmark()
                showMenu = false
            }
        )
        DropdownMenuItem(
            text = { Text("Mark previous as read") },
            onClick = {
                onMarkPreviousRead()
                showMenu = false
            }
        )
        HorizontalDivider()
        when (chapter.downloadStatus) {
            DetailsContract.DownloadStatus.NOT_DOWNLOADED -> {
                DropdownMenuItem(
                    text = { Text("Download chapter") },
                    onClick = {
                        onDownload()
                        showMenu = false
                    }
                )
            }
            DetailsContract.DownloadStatus.DOWNLOADED -> {
                DropdownMenuItem(
                    text = { Text("Delete download") },
                    onClick = {
                        onDeleteDownload()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Export as CBZ") },
                    onClick = {
                        onExportAsCbz()
                        showMenu = false
                    }
                )
            }
            else -> { /* Downloading - no action */ }
        }
    }
}

@Composable
private fun DownloadIcon(
    status: DetailsContract.DownloadStatus,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (status) {
        DetailsContract.DownloadStatus.NOT_DOWNLOADED -> Icons.Default.Download
        DetailsContract.DownloadStatus.DOWNLOADING -> Icons.Default.Downloading
        DetailsContract.DownloadStatus.DOWNLOADED -> Icons.Default.DownloadDone
    }

    val tint = when (status) {
        DetailsContract.DownloadStatus.NOT_DOWNLOADED -> MaterialTheme.colorScheme.outline
        DetailsContract.DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DetailsContract.DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary
    }

    IconButton(
        onClick = {
            when (status) {
                DetailsContract.DownloadStatus.NOT_DOWNLOADED -> onDownload()
                DetailsContract.DownloadStatus.DOWNLOADED -> onDelete()
                else -> { /* Do nothing while downloading */ }
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = when (status) {
                DetailsContract.DownloadStatus.NOT_DOWNLOADED -> "Download chapter"
                DetailsContract.DownloadStatus.DOWNLOADING -> "Downloading"
                DetailsContract.DownloadStatus.DOWNLOADED -> "Delete download"
            },
            tint = tint
        )
    }
}

/**
 * Format chapter name for display
 */
private fun formatChapterName(chapter: DetailsContract.ChapterItem): String {
    return when {
        chapter.chapterNumber >= 0 -> "Chapter ${chapter.chapterNumber.toInt()}${if (chapter.name.contains(":")) chapter.name.substringAfter(":") else ""}".trim()
        else -> chapter.name
    }
}

/**
 * Format timestamp to readable date
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
