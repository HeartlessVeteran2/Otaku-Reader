package app.otakureader.feature.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.OcrTranslation
import app.otakureader.feature.reader.R

/**
 * Bottom sheet that lists Gemini Vision OCR translations for the current page.
 *
 * Pure stateless composable — visibility, page-keyed translations, and translation
 * progress are all driven from `ReaderState`. Used as a side panel rather than an
 * inpaint overlay (overlay rendering is explicitly out of scope for this PR — the
 * structured prompt captures position hints for a future overlay implementation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTranslationBottomSheet(
    isVisible: Boolean,
    pageIndex: Int,
    translations: List<OcrTranslation>,
    isTranslating: Boolean,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.reader_ocr_translation_title,
                    pageIndex + 1,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = stringResource(R.string.reader_ocr_translation_free_tier_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.padding(top = 12.dp))

            when {
                isTranslating && translations.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.padding(top = 12.dp))
                        Text(
                            text = stringResource(R.string.reader_ocr_translation_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                translations.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.reader_ocr_translation_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(translations, key = { it.originalText + "|" + it.positionHint }) { entry ->
                            TranslationRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationRow(entry: OcrTranslation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            entry.positionHint?.takeIf { it.isNotBlank() }?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                text = entry.originalText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = entry.translatedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
