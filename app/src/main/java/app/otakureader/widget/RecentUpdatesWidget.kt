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

/**
 * Glance widget for displaying recent manga updates.
 */
class RecentUpdatesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // TODO: Load actual recent updates from repository
        val updates = getMockUpdates(context)
        val title = context.getString(R.string.widget_recent_updates_title)
        val emptyText = context.getString(R.string.widget_no_recent_updates)

        provideContent {
            GlanceTheme {
                RecentUpdatesContent(
                    title = title,
                    updates = updates,
                    emptyText = emptyText
                )
            }
        }
    }

    private fun getMockUpdates(context: Context): List<MangaUpdate> {
        // TODO: Replace with actual recent updates from repository
        return listOf(
            MangaUpdate(
                title = "One Piece",
                subtitle = context.getString(R.string.widget_chapter_time_format, "Chapter 1086", "2 hours ago")
            ),
            MangaUpdate(
                title = "My Hero Academia",
                subtitle = context.getString(R.string.widget_chapter_time_format, "Chapter 415", "5 hours ago")
            ),
            MangaUpdate(
                title = "Spy x Family",
                subtitle = context.getString(R.string.widget_chapter_time_format, "Chapter 87", "1 day ago")
            )
        )
    }
}

private data class MangaUpdate(
    val title: String,
    val subtitle: String
)

@Composable
private fun RecentUpdatesContent(
    title: String,
    updates: List<MangaUpdate>,
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

            updates.take(3).forEach { update ->
                UpdateItemWidget(update)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }

            if (updates.isEmpty()) {
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
private fun UpdateItemWidget(update: MangaUpdate) {
    Column(
        modifier = GlanceModifier.fillMaxWidth()
    ) {
        Text(
            text = update.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp
            ),
            maxLines = 1
        )
        Text(
            text = update.subtitle,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}