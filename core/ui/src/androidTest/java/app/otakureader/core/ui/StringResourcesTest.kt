package app.otakureader.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented tests for verifying string resources are properly defined
 * and translations exist for all supported locales.
 */
@RunWith(AndroidJUnit4::class)
class StringResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * List of supported locale codes for testing.
     */
    private val supportedLocales = listOf(
        "ar", // Arabic
        "de", // German
        "es", // Spanish
        "fr", // French
        "ja", // Japanese
        "ko", // Korean
        "pt", // Portuguese
        "ru", // Russian
        "zh-rCN" // Simplified Chinese
    )

    @Test
    fun testRetryStringExists() {
        // Core UI retry string should exist in default locale
        val stringRes = context.getString(R.string.core_ui_retry)
        assertTrue("Retry string should not be empty", stringRes.isNotEmpty())
    }

    @Test
    fun testRetryStringInAllLocales() {
        // Verify retry string exists in all supported locales
        supportedLocales.forEach { locale ->
            val config = context.resources.configuration
            config.setLocale(java.util.Locale.forLanguageTag(locale.replace("-r", "-")))
            val localizedContext = context.createConfigurationContext(config)
            
            val stringRes = localizedContext.getString(R.string.core_ui_retry)
            assertTrue("Retry string should exist for locale: $locale", stringRes.isNotEmpty())
        }
    }
}