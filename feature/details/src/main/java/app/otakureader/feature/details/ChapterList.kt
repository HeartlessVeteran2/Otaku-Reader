@file:Suppress("MaxLineLength")
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import app.otakureader.core.common.estimateReadTimeMinutes
import app.otakureader.core.common.formatReadTime
import app.otakureader.core.common.estimatePageCount
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListItem(
    chapter: DetailsContract.ChapterItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleRead: () -> Unit = {},
    onDownload: () -> Unit = {},
    onDeleteDownload: () -> Unit = {},
    onMarkPreviousRead: () -> Unit = {},
    onExportAsCbz: () -> Unit = {},
    onLoadThumbnail: () -> Unit = {},
    onEditNote: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (chapter.read) 0.6f else 1f,
        label = "readAlpha"
    )
    val chapterNameFormat = stringResource(R.string.details_chapter_number_format)
    var showMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onToggleRead()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val bgColor = if (chapter.read) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
            val icon = if (chapter.read) {
                Icons.Default.Circle
            } else {
                Icons.Default.CheckCircle
            }
            val iconTint = if (chapter.read) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }
            val contentDesc = if (chapter.read) {
                stringResource(R.string.details_chapter_mark_as_unread)
            } else {
                stringResource(R.string.details_chapter_mark_as_read)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(CardDefaults.shape)
                    .background(bgColor),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = iconTint,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
    ) {
        Card(
            modifier = Modifier
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
            // Thumbnail (if available) or Load Preview button
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (chapter.thumbnailUrl != null) {
                    AsyncImage(
                        model = chapter.thumbnailUrl,
                        contentDescription = stringResource(R.string.details_chapter_thumbnail_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Show load preview button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        TextButton(
                            onClick = onLoadThumbnail,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = stringResource(R.string.details_chapter_load_preview),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

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
                    text = formatChapterName(chapter, chapterNameFormat),
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

                // Reading progress indicator
                if (chapter.lastPageRead > 0 && !chapter.read && chapter.totalPages > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.details_chapter_reading_progress, chapter.lastPageRead, chapter.totalPages),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Read time estimate
                if (!chapter.read) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val estPages = if (chapter.totalPages > 0) chapter.totalPages else estimatePageCount(chapter.chapterNumber, -1)
                    val estMinutes = estimateReadTimeMinutes(estPages)
                    Text(
                        text = stringResource(R.string.details_chapter_read_time, formatReadTime(estMinutes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Note preview (first line) when this chapter has a saved note
                chapter.userNotes?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.details_chapter_note_preview, note.lineSequence().first()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                            contentDescription = if (chapter.read) {
                                stringResource(R.string.details_chapter_mark_as_unread)
                            } else {
                                stringResource(R.string.details_chapter_mark_as_read)
                            },
                            tint = if (chapter.read) {
                                MaterialTheme.colorScheme.primary
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

    } // end SwipeToDismissBox content

    // Dropdown menu for chapter options
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        DropdownMenuItem(
            text = {
                val resId = if (chapter.read) R.string.details_chapter_mark_as_unread
                    else R.string.details_chapter_mark_as_read
                Text(stringResource(resId))
            },
            onClick = {
                onToggleRead()
                showMenu = false
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.details_chapter_mark_previous_as_read)) },
            onClick = {
                onMarkPreviousRead()
                showMenu = false
            }
        )
        DropdownMenuItem(
            text = {
                val resId = if (chapter.userNotes.isNullOrBlank()) R.string.details_chapter_add_note
                    else R.string.details_chapter_edit_note
                Text(stringResource(resId))
            },
            onClick = {
                onEditNote()
                showMenu = false
            }
        )
        HorizontalDivider()
        when (chapter.downloadStatus) {
            DetailsContract.DownloadStatus.NOT_DOWNLOADED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.details_chapter_download)) },
                    onClick = {
                        onDownload()
                        showMenu = false
                    }
                )
            }
            DetailsContract.DownloadStatus.DOWNLOADED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.details_chapter_delete_download)) },
                    onClick = {
                        onDeleteDownload()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.details_chapter_export_cbz)) },
                    onClick = {
                        onExportAsCbz()
                        showMenu = false
                    }
                )
            }
            DetailsContract.DownloadStatus.DOWNLOADING -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.details_chapter_cancel_download)) },
                    onClick = {
                        onDeleteDownload()
                        showMenu = false
                    }
                )
            }
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
                // Same callback as DOWNLOADED — the ViewModel branches on the chapter's actual
                // status to cancel an in-flight download instead of deleting finished files.
                DetailsContract.DownloadStatus.DOWNLOADING -> onDelete()
            }
        },
        modifier = modifier
    ) {
        val downloadContentDesc = when (status) {
            DetailsContract.DownloadStatus.NOT_DOWNLOADED -> stringResource(R.string.details_chapter_download)
            DetailsContract.DownloadStatus.DOWNLOADING -> stringResource(R.string.details_chapter_cancel_download)
            DetailsContract.DownloadStatus.DOWNLOADED -> stringResource(R.string.details_chapter_delete_download)
        }
        Icon(
            imageVector = icon,
            contentDescription = downloadContentDesc,
            tint = tint
        )
    }
}

private fun formatChapterName(chapter: DetailsContract.ChapterItem, chapterFormat: String): String {
    return when {
        chapter.chapterNumber >= 0 -> {
            val suffix = if (chapter.name.contains(":")) chapter.name.substringAfter(":") else ""
            String.format(Locale.US, chapterFormat, chapter.chapterNumber.toInt(), suffix).trim()
        }
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
