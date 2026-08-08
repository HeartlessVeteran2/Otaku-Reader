package app.otakureader.data.metadata

import app.otakureader.domain.model.AniListMediaCandidate
import app.otakureader.domain.repository.AniListSearchRepository
import app.otakureader.domain.util.PlaceholderTitles
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * AniList title search, mapped down to what the matcher scores against.
 */
@Singleton
class AniListSearchRepositoryImpl @Inject constructor(
    private val api: AniListMetadataApi,
) : AniListSearchRepository {

    override suspend fun searchMedia(title: String): Result<List<AniListMediaCandidate>> {
        // A blank or placeholder title cannot match anything, and sending it would spend a request
        // against the rate limit to be told so. Sources really do emit "?" and "??" for a missing
        // localized title — see PlaceholderTitles.
        if (!PlaceholderTitles.isMeaningful(title)) return Result.success(emptyList())

        return try {
            val response = api.searchMedia(title)
            // Errors before data, as everywhere else against this endpoint: GraphQL answers a
            // rejected document with HTTP 200, and can return `data` and `errors` together when one
            // resolver fails. Without this a refused search is indistinguishable from a search that
            // genuinely found nothing.
            response.errors.firstOrNull()?.let {
                return Result.failure(AniListMetadataException(it.message))
            }
            Result.success(response.data?.page?.media.orEmpty().map { it.toCandidate() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Keeps only meaningful titles, so a placeholder never becomes evidence.
 *
 * This matters more than it looks: two placeholders match each other perfectly, so an entry whose
 * native title is `"?"` would score 1.0 against a source whose title is also `"?"`. The romaji field
 * is non-null on the candidate model, so an entry with no usable romaji falls back to an empty
 * string and simply contributes nothing to scoring rather than contributing noise.
 */
private fun SearchMedia.toCandidate(): AniListMediaCandidate {
    fun String?.meaningful(): String? = this?.takeIf { PlaceholderTitles.isMeaningful(it) }
    return AniListMediaCandidate(
        mediaId = id,
        romaji = title?.romaji.meaningful().orEmpty(),
        english = title?.english.meaningful(),
        native = title?.native.meaningful(),
        synonyms = synonyms.filter { PlaceholderTitles.isMeaningful(it) },
    )
}
