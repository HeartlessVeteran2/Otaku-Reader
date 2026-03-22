package app.otakureader.domain.usecase.search

import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.search.CachedSmartSearch
import app.otakureader.domain.model.search.ParsedSearchQuery
import app.otakureader.domain.model.search.MatchMode
import app.otakureader.domain.model.search.SearchIntent
import app.otakureader.domain.repository.AiRepository
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
 */
@Singleton
class SmartSearchUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val cacheRepository: SmartSearchCacheRepository
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
     * Build the prompt for AI.
     */
    private fun buildPrompt(query: String): String {
        return """
            Analyze this manga search query and extract search intents. Return ONLY a JSON object.

            Query: "$query"

            Available intent types:
            - TitleSearch: {"type": "title", "title": "...", "fuzzyMatch": true/false}
            - GenreSearch: {"type": "genre", "genres": ["..."], "matchMode": "ANY" or "ALL"}
            - AuthorSearch: {"type": "author", "author": "...", "includeArtist": true/false}
            - DescriptionSearch: {"type": "description", "keywords": ["..."], "matchMode": "ANY" or "ALL"}
            - MoodSearch: {"type": "mood", "mood": "DARK|LIGHTHEARTED|ACTION_PACKED|ROMANTIC|MYSTERIOUS|COMEDY|DRAMATIC|HORROR|ADVENTURE|SLICE_OF_LIFE|PSYCHOLOGICAL|THRILLING|HEARTWARMING|EPIC|TRAGIC"}
            - StatusSearch: {"type": "status", "status": "ONGOING|COMPLETED|LICENSED|PUBLISHING_FINISHED|CANCELLED|ON_HIATUS|UNKNOWN"}
            - PopularitySearch: {"type": "popularity", "minRating": number/null, "minPopularity": number/null}

            Genre examples: ACTION, ADVENTURE, COMEDY, DRAMA, FANTASY, HORROR, ISEKAI, MECHA, MYSTERY, PSYCHOLOGICAL, ROMANCE, SCI_FI, SLICE_OF_LIFE, SPORTS, SUPERNATURAL, THRILLER

            Return format:
            {
                "intents": [...],
                "confidence": 0.0-1.0,
                "isAmbiguous": true/false,
                "clarificationPrompt": "..." or null
            }

            Example:
            Query: "dark fantasy with magic schools"
            Response: {
                "intents": [
                    {"type": "mood", "mood": "DARK"},
                    {"type": "genre", "genres": ["FANTASY"], "matchMode": "ANY"},
                    {"type": "description", "keywords": ["magic school"], "matchMode": "ANY"}
                ],
                "confidence": 0.92,
                "isAmbiguous": false,
                "clarificationPrompt": null
            }

            Example:
            Query: "something like Berserk but finished"
            Response: {
                "intents": [
                    {"type": "title", "title": "Berserk", "fuzzyMatch": true},
                    {"type": "mood", "mood": "DARK"},
                    {"type": "status", "status": "COMPLETED"}
                ],
                "confidence": 0.88,
                "isAmbiguous": false,
                "clarificationPrompt": null
            }

            Return ONLY the JSON object, no markdown formatting, no explanations.
        """.trimIndent()
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
                    if (genres.isEmpty()) return@try null
                    // M-19: Use top-level MatchMode enum (inner enum was removed).
                    val matchModeStr = obj["matchMode"]?.jsonPrimitive?.content?.uppercase() ?: "ANY"
                    val matchMode = runCatching { MatchMode.valueOf(matchModeStr) }.getOrDefault(MatchMode.ANY)
                    SearchIntent.GenreSearch(genres = genres, matchMode = matchMode)
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
                    if (keywords.isEmpty()) return@try null
                    // M-19: Use top-level MatchMode enum (inner enum was removed).
                    val matchModeStr = obj["matchMode"]?.jsonPrimitive?.content?.uppercase() ?: "ANY"
                    val matchMode = runCatching { MatchMode.valueOf(matchModeStr) }.getOrDefault(MatchMode.ANY)
                    SearchIntent.DescriptionSearch(keywords = keywords, matchMode = matchMode)
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
     * Check if a cached search is still valid (less than 7 days old).
     */
    private fun isCacheValid(cached: CachedSmartSearch): Boolean {
        val maxAgeMs = 7L * 24 * 60 * 60 * 1000 // 7 days
        return System.currentTimeMillis() - cached.timestamp < maxAgeMs
    }
}

/**
 * Exceptions for Smart Search.
 */
sealed class SmartSearchException(message: String) : Exception(message) {
    data object AiNotInitialized : SmartSearchException("AI client not initialized. Please configure API key in settings.")
    data class ParsingError(val details: String) : SmartSearchException("Failed to parse AI response: $details")
    data class AiProcessingError(val details: String) : SmartSearchException("AI processing failed: $details")
}
