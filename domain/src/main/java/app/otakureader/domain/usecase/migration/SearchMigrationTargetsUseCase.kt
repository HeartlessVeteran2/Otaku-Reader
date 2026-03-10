package app.otakureader.domain.usecase.migration

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.util.TitleNormalizer
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
     * Uses multiple matching strategies for improved accuracy:
     * 1. Title matching with normalization
     * 2. Author name matching
     * 3. Genre overlap checking
     * 4. Romanization variant detection
     *
     * @param sourceManga The manga to migrate
     * @param targetSourceId The target source ID to search in
     * @return Result with list of migration candidates sorted by combined similarity score
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

            // Convert search results to migration candidates with enhanced similarity scores
            val candidates = mangaPage.mangas.map { sourceMangaResult ->
                val similarityScore = calculateEnhancedSimilarity(
                    sourceManga,
                    sourceMangaResult
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
     * Calculate enhanced similarity score using multiple matching strategies.
     * Combines:
     * - Title similarity (weighted 0.6)
     * - Author matching (weighted 0.2)
     * - Genre overlap (weighted 0.2)
     * - Romanization variant bonus
     *
     * @param sourceManga The source manga to match
     * @param targetManga The target manga candidate
     * @return Combined similarity score from 0.0 to 1.0
     */
    private fun calculateEnhancedSimilarity(
        sourceManga: Manga,
        targetManga: SourceManga
    ): Float {
        // Base title similarity (60% weight)
        val titleScore = calculateTitleSimilarity(sourceManga.title, targetManga.title)
        var finalScore = titleScore * 0.6f

        // Author matching (20% weight)
        val authorScore = calculateAuthorSimilarity(sourceManga.author, targetManga.author)
        finalScore += authorScore * 0.2f

        // Genre overlap (20% weight)
        val targetGenres = targetManga.genre?.split(",")?.map { it.trim() } ?: emptyList()
        val genreScore = TitleNormalizer.calculateGenreOverlap(sourceManga.genre, targetGenres)
        finalScore += genreScore * 0.2f

        // Romanization bonus: if titles are known variants, boost the score
        if (TitleNormalizer.areRomanizationVariants(sourceManga.title, targetManga.title)) {
            // Add a significant bonus (0.3) but cap at 1.0
            finalScore = minOf(1.0f, finalScore + 0.3f)
        }

        return finalScore.coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculate title similarity with advanced normalization.
     * Uses multiple strategies:
     * 1. Exact match after normalization (returns 1.0)
     * 2. Levenshtein distance on normalized titles
     * 3. Levenshtein distance on original titles (fallback)
     */
    private fun calculateTitleSimilarity(title1: String, title2: String): Float {
        // Try exact match first
        if (title1.equals(title2, ignoreCase = true)) {
            return 1.0f
        }

        // Normalize both titles
        val normalized1 = TitleNormalizer.normalize(title1)
        val normalized2 = TitleNormalizer.normalize(title2)

        // Check for exact match after normalization
        if (normalized1 == normalized2 && normalized1.isNotEmpty()) {
            return 1.0f
        }

        // Calculate Levenshtein on original titles (with basic normalization)
        val originalScore = calculateSimilarity(
            title1.lowercase().trim(),
            title2.lowercase().trim()
        )

        // Calculate Levenshtein on normalized titles only when both are non-blank
        val normalizedScore = if (normalized1.isBlank() || normalized2.isBlank()) {
            0f
        } else {
            calculateSimilarity(normalized1, normalized2)
        }
        // Use the better score
        return maxOf(normalizedScore, originalScore)
    }

    /**
     * Calculate author name similarity.
     * Returns 1.0 for exact match, 0.5 for partial match, 0.0 otherwise.
     */
    private fun calculateAuthorSimilarity(author1: String?, author2: String?): Float {
        if (author1.isNullOrBlank() || author2.isNullOrBlank()) {
            return 0.0f
        }

        val normalized1 = TitleNormalizer.normalizeAuthor(author1)
        val normalized2 = TitleNormalizer.normalizeAuthor(author2)

        // Exact match after normalization
        if (normalized1 == normalized2) {
            return 1.0f
        }

        // Check if one author name contains the other (handles different name ordering)
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return 0.5f
        }

        return 0.0f
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
