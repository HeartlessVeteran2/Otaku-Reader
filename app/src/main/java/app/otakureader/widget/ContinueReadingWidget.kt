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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otakureader.R
import app.otakureader.domain.repository.MangaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Glance widget for displaying "Continue Reading" manga.
 */
class ContinueReadingWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun mangaRepository(): MangaRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val mangaRepository = entryPoint.mangaRepository()

        val readingItems = try {
            mangaRepository.getLibraryManga()
                .first()
                .filter { it.favorite && it.lastRead != null }
                .sortedByDescending { it.lastRead }
                .take(3)
                .map { manga ->
                    ReadingItem(
                        title = manga.title,
                        subtitle = if (manga.unreadCount > 0) {
                            context.getString(R.string.widget_chapters_remaining, manga.unreadCount)
                        } else {
                            context.getString(R.string.widget_up_to_date)
                        }
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }

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

}

private data class ReadingItem(
    val title: String,
    val subtitle: String
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
                    color = GlanceTheme.colors.onSurface,
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
                        color = GlanceTheme.colors.onSurfaceVariant,
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
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp
            ),
            maxLines = 1
        )
        Text(
            text = item.subtitle,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}
