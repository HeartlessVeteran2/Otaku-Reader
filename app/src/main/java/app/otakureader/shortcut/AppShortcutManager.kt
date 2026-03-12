package app.otakureader.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import app.otakureader.MainActivity
import app.otakureader.R
import app.otakureader.core.database.dao.ReadingHistoryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages dynamic app icon shortcuts (long-press on launcher icon).
 *
 * Three shortcuts are provided:
 * - **Library** – navigates directly to the library screen.
 * - **Updates** – navigates to the updates screen.
 * - **Continue Reading** – opens the reader at the last-read chapter (only shown when history exists).
 */
@Singleton
class AppShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readingHistoryDao: ReadingHistoryDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start observing reading history and keep shortcuts in sync.
     * Call once from [app.otakureader.OtakuReaderApplication.onCreate].
     */
    fun initialize() {
        scope.launch {
            readingHistoryDao.observeLastReadWithMangaTitle()
                .distinctUntilChangedBy { it?.chapterId }
                .collect { lastRead ->
                    updateShortcuts(lastRead?.mangaId, lastRead?.chapterId, lastRead?.mangaTitle)
                }
        }
    }

    private fun updateShortcuts(
        lastMangaId: Long?,
        lastChapterId: Long?,
        lastMangaTitle: String?
    ) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val shortcuts = mutableListOf<ShortcutInfo>()

        // 1. Library shortcut
        shortcuts.add(
            ShortcutInfo.Builder(context, SHORTCUT_ID_LIBRARY)
                .setShortLabel(context.getString(R.string.shortcut_library_short))
                .setLongLabel(context.getString(R.string.shortcut_library_long))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_library))
                .setIntent(
                    Intent(ACTION_SHORTCUT_LIBRARY).apply {
                        setClass(context, MainActivity::class.java)
                    }
                )
                .setRank(0)
                .build()
        )

        // 2. Updates shortcut
        shortcuts.add(
            ShortcutInfo.Builder(context, SHORTCUT_ID_UPDATES)
                .setShortLabel(context.getString(R.string.shortcut_updates_short))
                .setLongLabel(context.getString(R.string.shortcut_updates_long))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_updates))
                .setIntent(
                    Intent(ACTION_SHORTCUT_UPDATES).apply {
                        setClass(context, MainActivity::class.java)
                    }
                )
                .setRank(1)
                .build()
        )

        // 3. Continue Reading shortcut (only when there is history)
        if (lastMangaId != null && lastChapterId != null) {
            val label = lastMangaTitle
                ?: context.getString(R.string.shortcut_continue_reading_long)
            shortcuts.add(
                ShortcutInfo.Builder(context, SHORTCUT_ID_CONTINUE_READING)
                    .setShortLabel(context.getString(R.string.shortcut_continue_reading_short))
                    .setLongLabel(label)
                    .setIcon(
                        Icon.createWithResource(context, R.drawable.ic_shortcut_continue_reading)
                    )
                    .setIntent(
                        Intent(ACTION_SHORTCUT_CONTINUE_READING).apply {
                            setClass(context, MainActivity::class.java)
                            putExtra(EXTRA_MANGA_ID, lastMangaId)
                            putExtra(EXTRA_CHAPTER_ID, lastChapterId)
                        }
                    )
                    .setRank(2)
                    .build()
            )
        }

        shortcutManager.dynamicShortcuts = shortcuts
    }

    companion object {
        const val ACTION_SHORTCUT_LIBRARY = "app.otakureader.SHORTCUT_LIBRARY"
        const val ACTION_SHORTCUT_UPDATES = "app.otakureader.SHORTCUT_UPDATES"
        const val ACTION_SHORTCUT_CONTINUE_READING = "app.otakureader.SHORTCUT_CONTINUE_READING"

        const val EXTRA_MANGA_ID = "app.otakureader.EXTRA_MANGA_ID"
        const val EXTRA_CHAPTER_ID = "app.otakureader.EXTRA_CHAPTER_ID"

        private const val SHORTCUT_ID_LIBRARY = "shortcut_library"
        private const val SHORTCUT_ID_UPDATES = "shortcut_updates"
        private const val SHORTCUT_ID_CONTINUE_READING = "shortcut_continue_reading"
    }
}
