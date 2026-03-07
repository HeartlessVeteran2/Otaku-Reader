package app.otakureader.core.tachiyomi.test

import android.content.Context
import app.otakureader.core.tachiyomi.compat.TachiyomiExtensionLoader
import app.otakureader.core.tachiyomi.repository.SourceRepositoryImpl
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URL

/**
 * Test utilities for Tachiyomi extension loading.
 */
object TachiyomiTestUtils {

    /**
     * Download and install a Tachiyomi extension from a URL.
     * This is useful for testing with real extensions.
     *
     * @param context Application context
     * @param apkUrl URL to the extension APK
     * @return Result indicating success or failure
     */
    suspend fun installExtensionFromUrl(
        context: Context,
        apkUrl: String
    ): Result<String> {
        return try {
            // Download the APK
            val tempFile = File(context.cacheDir, "test_extension_${System.currentTimeMillis()}.apk")

            URL(apkUrl).openStream().use { input ->
                tempFile.outputStream().use { output -
                    input.copyTo(output)
                }
            }

            // Load the extension
            val extensionLoader = TachiyomiExtensionLoader(
                context.packageManager,
                context.cacheDir
            )

            val extension = extensionLoader.loadExtensionFromApk(tempFile.absolutePath)
                ?: return Result.failure(IllegalStateException("Failed to load extension"))

            // Clean up temp file
            tempFile.delete()

            Result.success("Loaded extension: ${extension.name} with ${extension.sources.size} sources")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load an extension from a local APK file.
     *
     * @param context Application context
     * @param apkPath Path to the local APK file
     * @return Result indicating success or failure
     */
    suspend fun installExtensionFromFile(
        context: Context,
        apkPath: String
    ): Result<String> {
        return try {
            val file = File(apkPath)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("APK file not found: $apkPath"))
            }

            val extensionLoader = TachiyomiExtensionLoader(
                context.packageManager,
                context.cacheDir
            )

            val extension = extensionLoader.loadExtensionFromApk(apkPath)
                ?: return Result.failure(IllegalStateException("Failed to load extension"))

            Result.success("Loaded extension: ${extension.name} with ${extension.sources.size} sources")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Test loading manga from a source.
     *
     * @param context Application context
     * @param sourceId The source ID to test
     * @return Result with manga list or error
     */
    suspend fun testSourcePopular(
        context: Context,
        sourceId: String
    ): Result<List<String>> {
        return try {
            val repository = SourceRepositoryImpl(context)
            val result = repository.getPopularManga(sourceId, 1)

            result.map { page ->
                page.mangas.map { it.title }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all loaded sources.
     */
    fun listLoadedSources(context: Context): List<String> {
        val extensionLoader = TachiyomiExtensionLoader(
            context.packageManager,
            context.cacheDir
        )

        return extensionLoader.getAllSources().map { source ->
            "${source.name} (${source.lang}) - ID: ${source.id}"
        }
    }
}

/**
 * Example Suwayomi extension URLs for testing.
 * These are placeholders - actual URLs need to be obtained from the Suwayomi repository.
 */
object SuwayomiExtensionUrls {
    // Base repository URL for Suwayomi extensions
    const val REPO_BASE = "https://raw.githubusercontent.com/Suwayomi/tachiyomi-extension/"

    // Example extensions (replace with actual URLs)
    const val MANGADEX = "https://github.com/tachiyomiorg/tachiyomi-extensions/raw/apk/repo/eu.kanade.tachiyomi.extension.all.mangadex.apk"
    const val MANGAPLUS = "https://github.com/tachiyomiorg/tachiyomi-extensions/raw/apk/repo/eu.kanade.tachiyomi.extension.all.mangaplus.apk"
}
