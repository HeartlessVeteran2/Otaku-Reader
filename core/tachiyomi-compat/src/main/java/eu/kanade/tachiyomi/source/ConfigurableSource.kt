package eu.kanade.tachiyomi.source

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface ConfigurableSource : Source {

    /**
     * Gets instance of [SharedPreferences] scoped to the specific source.
     *
     * @since extensions-lib 1.5
     */
    fun getSourcePreferences(): SharedPreferences =
        Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableSource.preferenceKey(): String = "source_$id"

/**
 * Legacy free-function form of [ConfigurableSource.getSourcePreferences], kept because
 * extensions built against older revisions of the lib link against this symbol directly.
 *
 * Do not change the signature — that would break the ABI those extensions were compiled
 * against. Delegating the body is safe and keeps both entry points on one code path, so a
 * source cannot observe different preferences depending on which form it happens to call.
 */
fun ConfigurableSource.sourcePreferences(): SharedPreferences = getSourcePreferences()

fun sourcePreferences(key: String): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)
