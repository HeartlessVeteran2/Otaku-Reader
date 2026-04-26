package app.otakureader.feature.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R

/**
 * Bottom sheet that lets the user search for text within the current chapter's pages.
 *
 * Results are shown as they become available: pages are indexed lazily in the background
 * by [app.otakureader.feature.reader.viewmodel.delegate.ReaderOcrDelegate] and the list
 * updates automatically as each page's OCR result arrives.
 *
 * @param isVisible Whether the bottom sheet is shown.
 * @param query Current search query.
 * @param matchingPageIndices Zero-based indices of pages that match the query.
 * @param totalPages Total number of pages in the chapter.
 * @param indexedPageCount Number of pages whose text has been recognized so far.
 * @param isOcrRunning Whether background OCR scanning is still in progress.
 * @param onQueryChange Called when the user changes the search text.
 * @param onPageClick Called when the user taps a result row to jump to that page.
 * @param onDismiss Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSearchBottomSheet(
    isVisible: Boolean,
    query: String,
    matchingPageIndices: List<Int>,
    totalPages: Int,
    indexedPageCount: Int,
    isOcrRunning: Boolean,
    onQueryChange: (String) -> Unit,
    onPageClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
) {
    if (!isVisible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reader_ocr_search_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.reader_ocr_search_close),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.reader_ocr_search_label)) },
                placeholder = { Text(stringResource(R.string.reader_ocr_search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (isOcrRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Progress label: how many pages have been scanned
            val progressText = if (isOcrRunning) {
                stringResource(R.string.reader_ocr_scanning_progress, indexedPageCount, totalPages)
            } else {
                stringResource(R.string.reader_ocr_scanning_done, totalPages)
            }
            Text(
                text = progressText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider()

            // Results list
            if (query.isBlank()) {
                // Prompt the user to type something
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.reader_ocr_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (matchingPageIndices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.reader_ocr_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val resultLabel = pluralStringResource(
                    R.plurals.reader_ocr_result_count,
                    matchingPageIndices.size,
                    matchingPageIndices.size,
                )
                Text(
                    text = resultLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                LazyColumn {
                    items(matchingPageIndices) { pageIndex ->
                        OcrResultRow(
                            pageNumber = pageIndex + 1,
                            onClick = { onPageClick(pageIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrResultRow(
    pageNumber: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDesc = stringResource(R.string.reader_page_number, pageNumber)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .semantics { contentDescription = contentDesc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.reader_page_number, pageNumber),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
