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
 * Glance widget for displaying recent manga updates.
 */
class RecentUpdatesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // TODO: Load actual recent updates from repository
        val updates = getMockUpdates()

        provideContent {
            GlanceTheme {
                RecentUpdatesContent(updates = updates)
            }
        }
    }

    private fun getMockUpdates(): List<MangaUpdate> {
        return listOf(
            MangaUpdate("One Piece", "Chapter 1086", "2 hours ago"),
            MangaUpdate("My Hero Academia", "Chapter 415", "5 hours ago"),
            MangaUpdate("Spy x Family", "Chapter 87", "1 day ago")
        )
    }
}

data class MangaUpdate(
    val title: String,
    val chapter: String,
    val timeAgo: String
)

@Composable
private fun RecentUpdatesContent(updates: List<MangaUpdate>) {
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
                text = "Recent Updates",
                style = TextStyle(
                    color = ColorProvider(GlanceTheme.colors.onSurface),
                    fontSize = androidx.glance.unit.TextUnit(18f)
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
                    text = "No recent updates",
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
private fun UpdateItemWidget(update: MangaUpdate) {
    Column(
        modifier = GlanceModifier.fillMaxWidth()
    ) {
        Text(
            text = update.title,
            style = TextStyle(
                color = ColorProvider(GlanceTheme.colors.onSurface),
                fontSize = androidx.glance.unit.TextUnit(14f)
            ),
            maxLines = 1
        )
        Text(
            text = "${update.chapter} • ${update.timeAgo}",
            style = TextStyle(
                color = ColorProvider(GlanceTheme.colors.primary),
                fontSize = androidx.glance.unit.TextUnit(12f)
            ),
            maxLines = 1
        )
    }
}