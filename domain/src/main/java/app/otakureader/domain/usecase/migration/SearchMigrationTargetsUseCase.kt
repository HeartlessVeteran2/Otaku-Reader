package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.SourceManga
import javax.inject.Inject

/**
 * Use case for searching migration targets in a specific source.
 * Searches the target source for manga matching the source manga title,
 * and returns ranked candidates based on similarity.
 */
class SearchMigrationTargetsUseCase @Inject constructor(
    private val sourceRepository: SourceRepository
) {
    /**
     * Search for migration targets in the specified source.
     * @param sourceManga The manga to migrate
     * @param targetSourceId The target source ID to search in
     * @return Result with list of migration candidates sorted by similarity score
     */
    suspend operator fun invoke(
        sourceManga: Manga,
        targetSourceId: Long
    ): Result<List<MigrationCandidate>> {
        return try {
            // Search in the target source using the manga title
            val searchResult = sourceRepository.searchManga(
                sourceId = targetSourceId.toString(),
                query = sourceManga.title,
                page = 1
            )

            if (searchResult.isFailure) {
                return Result.failure(
                    searchResult.exceptionOrNull()
                        ?: Exception("Failed to search in target source")
                )
            }

            val mangaPage = searchResult.getOrNull()
            if (mangaPage == null || mangaPage.mangas.isEmpty()) {
                return Result.success(emptyList())
            }

            // Convert search results to migration candidates with similarity scores
            val candidates = mangaPage.mangas.map { sourceMangaResult ->
                val similarityScore = calculateSimilarity(
                    sourceManga.title,
                    sourceMangaResult.title
                )

                sourceMangaResult.toMigrationCandidate(
                    targetSourceId,
                    similarityScore
                )
            }.sortedByDescending { it.similarityScore }

            Result.success(candidates)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate similarity score between two titles using Levenshtein distance.
     * Returns a score from 0.0 (completely different) to 1.0 (identical).
     */
    private fun calculateSimilarity(title1: String, title2: String): Float {
        val normalized1 = title1.lowercase().trim()
        val normalized2 = title2.lowercase().trim()

        if (normalized1 == normalized2) return 1.0f

        // Simple similarity check: calculate Levenshtein distance
        val distance = levenshteinDistance(normalized1, normalized2)
        val maxLength = maxOf(normalized1.length, normalized2.length)

        return if (maxLength == 0) 1.0f
        else 1.0f - (distance.toFloat() / maxLength)
    }

    /**
     * Calculate Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[len1][len2]
    }

    private fun SourceManga.toMigrationCandidate(
        sourceId: Long,
        similarityScore: Float
    ) = MigrationCandidate(
        sourceId = sourceId,
        url = url,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author,
        artist = artist,
        description = description,
        genre = genre?.split(",")?.map { it.trim() } ?: emptyList(),
        status = MangaStatus.fromOrdinal(status),
        chapterCount = 0, // Will be fetched later if needed
        similarityScore = similarityScore
    )
}
