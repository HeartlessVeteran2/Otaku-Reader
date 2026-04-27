package app.otakureader.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otakureader.MainActivity
import app.otakureader.R
import app.otakureader.domain.repository.ChapterRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Glance widget that shows the most recently read manga chapter — the
 * "now reading" card inspired by Discord's Now Playing display.
 */
class NowReadingWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NowReadingEntryPoint {
        fun chapterRepository(): ChapterRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NowReadingEntryPoint::class.java
        )
        val chapterRepository = entryPoint.chapterRepository()

        val nowReading = try {
            chapterRepository.observeHistory()
                .first()
                .firstOrNull()
                ?.let { entry ->
                    NowReadingInfo(
                        mangaTitle = entry.mangaTitle ?: entry.chapter.name,
                        chapterName = entry.chapter.name,
                        chapterNumber = entry.chapter.chapterNumber,
                    )
                }
        } catch (_: Exception) {
            null
        }

        val labelNowReading = context.getString(R.string.widget_now_reading_title)
        val labelNotReading = context.getString(R.string.widget_now_reading_empty)

        provideContent {
            GlanceTheme {
                NowReadingContent(
                    info = nowReading,
                    labelTitle = labelNowReading,
                    labelEmpty = labelNotReading,
                )
            }
        }
    }
}

private data class NowReadingInfo(
    val mangaTitle: String,
    val chapterName: String,
    val chapterNumber: Float,
)

@Composable
private fun NowReadingContent(
    info: NowReadingInfo?,
    labelTitle: String,
    labelEmpty: String,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start,
        ) {
            Text(
                text = labelTitle,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (info == null) {
                Text(
                    text = labelEmpty,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                    ),
                )
            } else {
                Text(
                    text = info.mangaTitle,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = info.chapterName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
