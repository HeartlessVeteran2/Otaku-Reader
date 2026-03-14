package app.otakureader

import android.net.Uri
import androidx.lifecycle.ViewModel
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * ViewModel for resolving deep link base URLs to installed source IDs.
 *
 * Because Tachiyomi-backed sources use numeric-string IDs (tachiyomiSource.id.toString()),
 * the sourceId cannot be inferred from the URL alone. This ViewModel matches the host of the
 * incoming [deepLinkBaseUrl] against the [MangaSource.baseUrl] of every installed source.
 */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val getSourcesUseCase: GetSourcesUseCase
) : ViewModel() {

    /**
     * Resolves the installed source ID whose [MangaSource.baseUrl] host matches the given
     * [deepLinkBaseUrl]. Returns `null` if no matching source is found within 5 seconds or if
     * the URL cannot be parsed. The [withTimeoutOrNull] ensures [first] does not hang indefinitely
     * if the source list never becomes non-empty (e.g. extensions not yet loaded).
     */
    suspend fun resolveSourceId(deepLinkBaseUrl: String): String? {
        val targetHost = Uri.parse(deepLinkBaseUrl).host?.lowercase() ?: return null
        return withTimeoutOrNull(5_000L) {
            getSourcesUseCase()
                .first { sources -> sources.isNotEmpty() }
                .find { source ->
                    val sourceHost = Uri.parse(source.baseUrl).host?.lowercase()
                        ?: return@find false
                    // Match exact host, or either being a subdomain of the other
                    sourceHost == targetHost ||
                        targetHost.endsWith(".$sourceHost") ||
                        sourceHost.endsWith(".$targetHost")
                }?.id
        }
    }
}
