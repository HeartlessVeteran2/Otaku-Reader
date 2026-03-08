// Stub matching tachiyomiorg/extensions-lib — used at compile time only.
package eu.kanade.tachiyomi.source

@Suppress("unused")
interface SourceFactory {
    fun createSources(): List<Source>
}
