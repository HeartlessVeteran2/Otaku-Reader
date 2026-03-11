package app.otakureader.feature.browse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented tests for verifying browse screen string resources.
 */
@RunWith(AndroidJUnit4::class)
class BrowseStringResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationProvider()

    private val supportedLocales = listOf(
        "ar", "de", "es", "fr", "ja", "ko", "pt", "ru", "zh-rCN"
    )

    @Test
    fun testBrowseStringsExist() {
        val browseStrings = listOf(
            R.string.browse_title,
            R.string.browse_search,
            R.string.browse_install_extension,
            R.string.browse_search_placeholder,
            R.string.browse_filters,
            R.string.browse_select_source,
            R.string.browse_load_more,
            R.string.browse_no_results,
            R.string.browse_no_sources_title,
            R.string.browse_no_sources_message
        )

        browseStrings.forEach { resId ->
            val stringRes = context.getString(resId)
            assertTrue("Browse string $resId should not be empty", stringRes.isNotEmpty())
        }
    }

    @Test
    fun testExtensionStringsExist() {
        val extensionStrings = listOf(
            R.string.extensions_nsfw_label,
            R.string.extensions_sort_name,
            R.string.extensions_sort_recently_added,
            R.string.extensions_sort_language,
            R.string.extensions_update_all,
            R.string.extensions_updating_all
        )

        extensionStrings.forEach { resId ->
            val stringRes = context.getString(resId)
            assertTrue("Extension string $resId should not be empty", stringRes.isNotEmpty())
        }
    }

    @Test
    fun testPluralsWork() {
        // Test plural string for updates available
        val oneUpdate = context.resources.getQuantityString(
            R.plurals.extensions_updates_available, 1, 1
        )
        assertTrue("Should mention 1 update", oneUpdate.contains("1"))

        val fiveUpdates = context.resources.getQuantityString(
            R.plurals.extensions_updates_available, 5, 5
        )
        assertTrue("Should mention 5 updates", fiveUpdates.contains("5"))
    }
}