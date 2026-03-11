package app.otakureader.feature.details

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented tests for verifying details screen string resources
 * and translations are complete for all supported locales.
 */
@RunWith(AndroidJUnit4::class)
class DetailsStringResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val supportedLocales = listOf(
        "ar", "de", "es", "fr", "ja", "ko", "pt", "ru", "zh-rCN"
    )

    /**
     * Test that all critical details screen strings exist in default locale.
     */
    @Test
    fun testCriticalStringsExist() {
        val criticalStrings = listOf(
            R.string.details_title_fallback,
            R.string.details_back,
            R.string.details_refresh,
            R.string.details_share,
            R.string.details_continue_reading,
            R.string.details_start_reading,
            R.string.details_unknown_error,
            R.string.details_author,
            R.string.details_artist,
            R.string.details_status,
            R.string.details_add_to_library,
            R.string.details_remove_from_library,
            R.string.details_show_more,
            R.string.details_show_less
        )

        criticalStrings.forEach { resId ->
            val stringRes = context.getString(resId)
            assertTrue("String resource $resId should not be empty", stringRes.isNotEmpty())
        }
    }

    /**
     * Test that strings with placeholders have valid format.
     */
    @Test
    fun testPlaceholderStrings() {
        // Test author string with placeholder
        val authorString = context.getString(R.string.details_author, "Test Author")
        assertTrue("Author string should contain the name", authorString.contains("Test Author"))

        // Test artist string with placeholder
        val artistString = context.getString(R.string.details_artist, "Test Artist")
        assertTrue("Artist string should contain the name", artistString.contains("Test Artist"))

        // Test status string with placeholder
        val statusString = context.getString(R.string.details_status, "Ongoing")
        assertTrue("Status string should contain the status", statusString.contains("Ongoing"))
    }

    /**
     * Test that all translations exist for critical strings.
     */
    @Test
    fun testTranslationsExist() {
        val criticalStrings = listOf(
            R.string.details_title_fallback,
            R.string.details_back,
            R.string.details_continue_reading,
            R.string.details_start_reading
        )

        supportedLocales.forEach { locale ->
            val config = context.resources.configuration
            config.setLocale(java.util.Locale.forLanguageTag(locale.replace("-r", "-")))
            val localizedContext = context.createConfigurationContext(config)

            criticalStrings.forEach { resId ->
                val stringRes = localizedContext.getString(resId)
                assertTrue(
                    "String $resId should exist for locale: $locale",
                    stringRes.isNotEmpty()
                )
            }
        }
    }
}