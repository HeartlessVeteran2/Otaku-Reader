package app.otakureader.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.dp
import androidx.glance.unit.sp
import app.otakureader.R

/**
 * Glance widget for displaying "Continue Reading" manga.
 */
class ContinueReadingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // TODO: Load actual reading data from repository
        val readingItems = getMockReadingItems()
        val title = context.getString(R.string.widget_continue_reading_title)
        val emptyText = context.getString(R.string.widget_no_manga_in_progress)

        provideContent {
            GlanceTheme {
                ContinueReadingContent(
                    title = title,
                    items = readingItems,
                    emptyText = emptyText
                )
            }
        }
    }

    private fun getMockReadingItems(): List<ReadingItem> {
        return listOf(
            ReadingItem("One Piece", "Chapter 1085", "80%"),
            ReadingItem("Jujutsu Kaisen", "Chapter 245", "45%"),
            ReadingItem("Chainsaw Man", "Chapter 145", "30%")
        )
    }
}

private data class ReadingItem(
    val title: String,
    val chapter: String,
    val progress: String
)

@Composable
private fun ContinueReadingContent(
    title: String,
    items: List<ReadingItem>,
    emptyText: String
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(16.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(GlanceTheme.colors.onSurface),
                    fontSize = 18.sp
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            items.take(3).forEach { item ->
                ReadingItemWidget(item)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }

            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    style = TextStyle(
                        color = ColorProvider(GlanceTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ReadingItemWidget(item: ReadingItem) {
    Column(
        modifier = GlanceModifier.fillMaxWidth()
    ) {
        Text(
            text = item.title,
            style = TextStyle(
                color = ColorProvider(GlanceTheme.colors.onSurface),
                fontSize = 14.sp
            ),
            maxLines = 1
        )
        Text(
            text = "${item.chapter} • ${item.progress}",
            style = TextStyle(
                color = ColorProvider(GlanceTheme.colors.onSurfaceVariant),
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}