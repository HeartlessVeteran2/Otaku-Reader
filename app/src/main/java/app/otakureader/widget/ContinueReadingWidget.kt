package app.otakureader.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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

/**
 * Glance widget for displaying "Continue Reading" manga.
 */
class ContinueReadingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // TODO: Load actual reading data from repository
        val readingItems = getMockReadingItems()

        provideContent {
            GlanceTheme {
                ContinueReadingContent(items = readingItems)
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

data class ReadingItem(
    val title: String,
    val chapter: String,
    val progress: String
)

@Composable
private fun ContinueReadingContent(items: List<ReadingItem>) {
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
                text = "Continue Reading",
                style = TextStyle(
                    color = ColorProvider(GlanceTheme.colors.onSurface),
                    fontSize = androidx.glance.unit.TextUnit(18f)
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
                    text = "No manga in progress",
                    style = TextStyle(
                        color = ColorProvider(GlanceTheme.colors.onSurfaceVariant),
                        fontSize = androidx.glance.unit.TextUnit(14f)
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
                fontSize = androidx.glance.unit.TextUnit(14f)
            ),
            maxLines = 1
        )
        Text(
            text = "${item.chapter} • ${item.progress}",
            style = TextStyle(
                color = ColorProvider(GlanceTheme.colors.onSurfaceVariant),
                fontSize = androidx.glance.unit.TextUnit(12f)
            ),
            maxLines = 1
        )
    }
}