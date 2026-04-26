package app.otakureader.data.loader

import android.content.Context
import app.otakureader.domain.repository.PromptLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [PromptLoader] that loads prompt templates from assets.
 *
 * Prompts are stored in `assets/ai/` directory with `.txt` extension.
 */
@Singleton
class AssetsPromptLoader @Inject constructor(
    @ApplicationContext private val context: Context
) : PromptLoader {

    companion object {
        private const val ASSET_PATH_PREFIX = "ai/"
        private const val FILE_EXTENSION = ".txt"
    }

    override suspend fun loadPrompt(name: String): String? = withContext(Dispatchers.IO) {
        try {
            val assetPath = "$ASSET_PATH_PREFIX$name$FILE_EXTENSION"
            context.assets.open(assetPath).use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: IOException) {
            // Asset not found or read error
            null
        }
    }
}
