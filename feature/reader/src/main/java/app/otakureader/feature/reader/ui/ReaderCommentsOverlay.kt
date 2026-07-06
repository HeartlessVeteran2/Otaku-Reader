package app.otakureader.feature.reader.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.ReaderComment
import app.otakureader.feature.reader.ExternalDiscussionLink
import app.otakureader.feature.reader.R
import java.text.DateFormat
import java.util.Date

private const val TAB_CHAPTER = 0
private const val TAB_BOOK = 1

/**
 * Bottom-sheet overlay for private reader comments.
 *
 * Two tabs: Chapter (comments tied to the chapter on screen, plus the existing
 * chapter note) and Book (comments tied to the manga as a whole). External
 * tracker pages open in the browser via the buttons at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderCommentsOverlay(
    chapterComments: List<ReaderComment>,
    bookComments: List<ReaderComment>,
    chapterNote: String,
    externalLinks: List<ExternalDiscussionLink>,
    onAddComment: (body: String, chapterScoped: Boolean) -> Unit,
    onDeleteComment: (ReaderComment) -> Unit,
    onSaveChapterNote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(TAB_CHAPTER) }
    var composerText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.reader_comments_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == TAB_CHAPTER,
                    onClick = { selectedTab = TAB_CHAPTER },
                    text = { Text(stringResource(R.string.reader_comments_tab_chapter)) },
                )
                Tab(
                    selected = selectedTab == TAB_BOOK,
                    onClick = { selectedTab = TAB_BOOK },
                    text = { Text(stringResource(R.string.reader_comments_tab_book)) },
                )
            }

            val comments = if (selectedTab == TAB_CHAPTER) chapterComments else bookComments
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selectedTab == TAB_CHAPTER) {
                    item(key = "chapter_note") {
                        ChapterNoteCard(
                            note = chapterNote,
                            onSave = onSaveChapterNote,
                        )
                    }
                }
                if (comments.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.reader_comments_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        CommentRow(comment = comment, onDelete = { onDeleteComment(comment) })
                    }
                }
            }

            // Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = composerText,
                    onValueChange = { composerText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.reader_comments_hint)) },
                    maxLines = 4,
                )
                TextButton(
                    onClick = {
                        onAddComment(composerText, selectedTab == TAB_CHAPTER)
                        composerText = ""
                    },
                    enabled = composerText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.reader_comments_add))
                }
            }

            if (externalLinks.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.reader_comments_external_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    externalLinks.forEach { link ->
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri()))
                                }
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text(link.trackerName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterNoteCard(
    note: String,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(note) }
    // Re-sync the editor when the chapter (and therefore its saved note) changes.
    LaunchedEffect(note) { text = note }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.reader_comments_note_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.reader_comments_note_hint)) },
                maxLines = 3,
            )
            TextButton(
                onClick = { onSave(text) },
                enabled = text != note,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.reader_comments_note_save))
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: ReaderComment,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = remember(comment.createdAt) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(comment.createdAt))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.reader_comments_delete),
            )
        }
    }
}
