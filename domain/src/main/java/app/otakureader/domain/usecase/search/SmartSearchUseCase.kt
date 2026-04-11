package app.otakureader.domain.usecase.search

import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.search.CachedSmartSearch
import app.otakureader.domain.model.search.ParsedSearchQuery
import app.otakureader.domain.model.search.MatchMode
import app.otakureader.domain.model.search.SearchIntent
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.repository.PromptLoader
import app.otakureader.domain.repository.SmartSearchCacheRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for converting natural language queries into structured search intents.
 * Uses AI via [AiRepository] for NLP processing and caches results locally.
 * 
 * **H-7: Externalized Prompts**
 * The AI prompt template is loaded via [PromptLoader] from external assets,
 * allowing updates without code changes.
 */
@Singleton
class SmartSearchUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val cacheRepository: SmartSearchCacheRepository,
    private val promptLoader: PromptLoader
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Convert a natural language query to structured search intents.
     *
     * @param query The natural language search query
     * @param skipCache Whether to skip cache and force AI processing
     * @return Result containing the parsed search query or an error
     */
    suspend operator fun invoke(
        query: String,
        skipCache: Boolean = false
    ): Result<ParsedSearchQuery> {
        if (query.isBlank()) {
            return Result.failure(IllegalArgumentException("Query cannot be blank"))
        }

        val queryHash = hashQuery(query)

        // Check cache first
        if (!skipCache) {
            val cached = cacheRepository.getCachedSearch(queryHash).firstOrNull()
            if (cached != null && isCacheValid(cached)) {
                return Result.success(cached.parsedQuery)
            }
        }

        // Check if AI is available
        if (!aiRepository.isAvailable()) {
            return Result.failure(SmartSearchException.AiNotInitialized)
        }

        // Process with AI
        return processWithAi(query).also { result ->
            // Cache successful results
            result.getOrNull()?.let { parsedQuery ->
                cacheRepository.cacheSearch(
                    CachedSmartSearch(
                        queryHash = queryHash,
                        originalQuery = query,
                        parsedQuery = parsedQuery
                    )
                )
            }
        }
    }

    /**
     * Get recent cached smart searches.
     */
    suspend fun getRecentSearches(limit: Int = 10): List<CachedSmartSearch> {
        return cacheRepository.getRecentSearches(limit)
    }

    /**
     * Clear all cached searches.
     */
    suspend fun clearCache() {
        cacheRepository.clearAllCache()
    }

    /**
     * Process the query with AI.
     */
    private suspend fun processWithAi(query: String): Result<ParsedSearchQuery> {
        val prompt = buildPrompt(query)

        return aiRepository.generateContent(prompt).mapCatching { response ->
            parseAiResponse(query, response)
        }.recoverCatching { e ->
            throw when (e) {
                is SmartSearchException -> e
                // Map JSON/serialization exceptions to ParsingError
                is kotlinx.serialization.SerializationException ->
                    SmartSearchException.ParsingError(e.message ?: "JSON parsing failed")
                is IllegalArgumentException ->
                    SmartSearchException.ParsingError(e.message ?: "Invalid JSON structure")
                // Other errors are AI processing failures
                else -> SmartSearchException.AiProcessingError(e.message ?: "AI processing failed")
            }
        }
    }

    /**
     * Build the prompt for AI by loading the template from external assets.
     *
     * **H-7: Externalized Prompts**
     * The prompt template is loaded via [PromptLoader] from `assets/ai/smart_search_prompt.txt`,
     * allowing updates without code changes.
     *
     * @param query The user's search query to substitute into the template
     * @return The formatted prompt string
     */
    private suspend fun buildPrompt(query: String): String {
        val template = promptLoader.loadPrompt("smart_search")
            ?: throw SmartSearchException.PromptNotFound("Failed to load smart search prompt template")
        
        return template.replace("{{QUERY}}", query)
    }

    /**
     * Parse the AI response into a ParsedSearchQuery.
     */
    private fun parseAiResponse(originalQuery: String, response: String): ParsedSearchQuery {
        val cleanResponse = response.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val root = json.parseToJsonElement(cleanResponse).jsonObject
        val intentsArray = root["intents"]?.jsonArray
            ?: throw SmartSearchException.ParsingError("Missing 'intents' array")
        val intents = mutableListOf<SearchIntent>()

        for (element in intentsArray) {
            val intentObj = element.jsonObject
            val type = intentObj["type"]?.jsonPrimitive?.content ?: continue

            val intent = parseIntent(type, intentObj)
            intent?.let { intents.add(it) }
        }

        return ParsedSearchQuery(
            originalQuery = originalQuery,
            intents = intents,
            confidence = root["confidence"]?.jsonPrimitive?.float ?: 0.5f,
            isAmbiguous = root["isAmbiguous"]?.jsonPrimitive?.boolean ?: false,
            clarificationPrompt = root["clarificationPrompt"]
                ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Parse a single intent from a JSON object.
     */
    private fun parseIntent(type: String, obj: JsonObject): SearchIntent? {
        return try {
            when (type) {
                "title" -> SearchIntent.TitleSearch(
                    title = obj["title"]!!.jsonPrimitive.content,
                    fuzzyMatch = obj["fuzzyMatch"]?.jsonPrimitive?.boolean ?: true
                )
                "genre" -> {
                    // M-15: Validate that genres is a non-empty array before mapping.
                    val genreArray = obj["genres"]?.jsonArray
                        ?: throw SmartSearchException.ParsingError("genre intent missing 'genres' array")
                    val genres = genreArray.map { it.jsonPrimitive.content }.filter { it.isNotBlank() }
                    if (genres.isEmpty()) null
                    else {
                        // M-19: Use top-level MatchMode enum (inner enum was removed).
                        val matchModeStr = obj["matchMode"]?.jsonPrimitive?.content?.uppercase() ?: "ANY"
                        val matchMode = runCatching { MatchMode.valueOf(matchModeStr) }.getOrDefault(MatchMode.ANY)
                        SearchIntent.GenreSearch(genres = genres, matchMode = matchMode)
                    }
                }
                "author" -> SearchIntent.AuthorSearch(
                    author = obj["author"]!!.jsonPrimitive.content,
                    includeArtist = obj["includeArtist"]?.jsonPrimitive?.boolean ?: true
                )
                "description" -> {
                    // M-15: Validate that keywords is a non-empty array before mapping.
                    val keywordsArray = obj["keywords"]?.jsonArray
                        ?: throw SmartSearchException.ParsingError("description intent missing 'keywords' array")
                    val keywords = keywordsArray.map { it.jsonPrimitive.content }.filter { it.isNotBlank() }
                    if (keywords.isEmpty()) null
                    else {
                        // M-19: Use top-level MatchMode enum (inner enum was removed).
                        val matchModeStr = obj["matchMode"]?.jsonPrimitive?.content?.uppercase() ?: "ANY"
                        val matchMode = runCatching { MatchMode.valueOf(matchModeStr) }.getOrDefault(MatchMode.ANY)
                        SearchIntent.DescriptionSearch(keywords = keywords, matchMode = matchMode)
                    }
                }
                "mood" -> SearchIntent.MoodSearch(
                    mood = SearchIntent.MoodSearch.Mood.valueOf(
                        obj["mood"]!!.jsonPrimitive.content.uppercase()
                    )
                )
                "status" -> SearchIntent.StatusSearch(
                    status = MangaStatus.valueOf(
                        obj["status"]!!.jsonPrimitive.content.uppercase()
                    )
                )
                "popularity" -> {
                    val minRating = obj["minRating"]?.jsonPrimitive
                        ?.double?.takeIf { it >= 0 }?.toFloat()
                    val minPopularity = obj["minPopularity"]?.jsonPrimitive
                        ?.int?.takeIf { it >= 0 }
                    SearchIntent.PopularitySearch(
                        minRating = minRating,
                        minPopularity = minPopularity
                    )
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Hash the query for caching purposes.
     */
    private fun hashQuery(query: String): String {
        val normalized = query.lowercase().trim()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(normalized.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if a cached search is still valid.
     *
     * **I-11:** The cache now has a configurable expiry. The default is [CACHE_MAX_AGE_MS]
     * (24 hours). Previously the audit noted the cache never expired; this was already
     * fixed in the codebase (7-day TTL), but the magic number has been extracted to a
     * named constant and reduced to 24 hours to keep AI results fresh.
     */
    private fun isCacheValid(cached: CachedSmartSearch): Boolean =
        System.currentTimeMillis() - cached.timestamp < CACHE_MAX_AGE_MS

    companion object {
        /**
         * I-11: Maximum age of a cached smart search result before it is considered stale.
         * Set to 24 hours so that AI-generated search results are refreshed daily.
         */
        private const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000 // 24 hours
    }
}

/**
 * Exceptions for Smart Search.
 */
sealed class SmartSearchException(message: String) : Exception(message) {
    data object AiNotInitialized : SmartSearchException("AI client not initialized. Please configure API key in settings.")
    data class ParsingError(val details: String) : SmartSearchException("Failed to parse AI response: $details")
    data class AiProcessingError(val details: String) : SmartSearchException("AI processing failed: $details")
    data class PromptNotFound(val details: String) : SmartSearchException("Prompt template not found: $details")
}
