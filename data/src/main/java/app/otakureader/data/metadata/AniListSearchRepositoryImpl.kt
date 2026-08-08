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
        //
        // This returns success-with-nothing rather than a failure on purpose: the caller should
        // move on to the next title, which is exactly what an empty result already means to it.
        // Note that it makes "empty" mean "no candidates", not "AniList had none" — see the
        // interface docs.
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
            // A missing `Page` is not an empty page. `media: []` means AniList looked and found
            // nothing — a normal outcome that must let the cascade try the next title. A null
            // `data` or `Page` means the response was not the shape the query asked for, and
            // advancing on that would make four malformed requests where one already failed,
            // which is the same reasoning that makes ResolveAniListMediaUseCase abort on failure.
            // `refreshMetadata` already draws this line for a missing `Media`; search was the
            // inconsistent one.
            val page = response.data?.page
                ?: return Result.failure(
                    AniListMetadataException("AniList returned no result page for \"$title\"")
                )
            Result.success(page.media.map { it.toCandidate() })
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
