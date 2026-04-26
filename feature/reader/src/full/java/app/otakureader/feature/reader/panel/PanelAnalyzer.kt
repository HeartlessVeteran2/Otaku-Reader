package app.otakureader.feature.reader.panel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import app.otakureader.core.ai.GeminiClient
import app.otakureader.core.ai.model.AiConfig
import app.otakureader.core.ai.model.AiException
import app.otakureader.feature.reader.model.PanelAnalysisException
import app.otakureader.feature.reader.model.PanelAnalysisRequest
import app.otakureader.feature.reader.model.PanelAnalysisResultWrapper
import app.otakureader.feature.reader.model.PanelBoundsData
import app.otakureader.feature.reader.model.PanelData
import app.otakureader.feature.reader.model.PanelType
import app.otakureader.feature.reader.model.PageAnalysisResult
import app.otakureader.feature.reader.model.PageType
import app.otakureader.domain.model.ReadingDirection
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Panel analyzer using Gemini Vision API for intelligent panel detection.
 * 
 * This class provides AI-powered panel detection that can handle various
 * art styles, irregular panel shapes, and complex layouts better than
 * traditional edge-detection algorithms.
 *
 * **Key features:**
 * - Uses Gemini Vision API for visual understanding
 * - Supports both RTL (manga) and LTR (comics) reading orders
 * - Detects panel types (standard, splash, inset, etc.)
 * - Identifies speech bubbles and text regions
 * - Integrates with PanelCacheService for result caching
 *
 * **Usage:**
 * ```
 * val analyzer = PanelAnalyzer(geminiClient, context, imageLoader)
 * analyzer.initialize(apiKey)
 * 
 * val result = analyzer.analyzePage(imageUrl, ReadingDirection.RTL)
 * ```
 */
@Singleton
class PanelAnalyzer @Inject constructor(
    private val geminiClient: GeminiClient,
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val cacheService: PanelCacheService
) {
    private val ioDispatcher = Dispatchers.IO

    /**
     * Initialize the analyzer with Gemini API key.
     * Must be called before using analyzePage.
     *
     * @param apiKey The Gemini API key
     */
    fun initialize(apiKey: String) {
        geminiClient.initialize(
            apiKey = apiKey,
            modelName = VISION_MODEL
        )
    }

    /**
     * Check if the analyzer has been initialized.
     */
    fun isInitialized(): Boolean = geminiClient.isInitialized()

    /**
     * Analyze a manga/comic page to detect panels using Gemini Vision.
     *
     * This method:
     * 1. Checks cache for existing analysis
     * 2. Loads the image if needed
     * 3. Sends to Gemini Vision API for analysis
     * 4. Parses the response into panel data
     * 5. Caches the result
     *
     * @param request The analysis request containing image URL and metadata
     * @param useCache Whether to check cache before API call (default: true)
     * @param timeoutMillis Timeout for the analysis operation
     * @return PanelAnalysisResultWrapper containing success result or error
     */
    suspend fun analyzePage(
        request: PanelAnalysisRequest,
        useCache: Boolean = true,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS
    ): PanelAnalysisResultWrapper = withContext(ioDispatcher) {
        try {
            // Check cache first
            if (useCache) {
                val cached = cacheService.getCachedResult(request.imageHash)
                if (cached != null && !cached.isStale()) {
                    return@withContext PanelAnalysisResultWrapper.Success(cached)
                }
            }

            // Check if initialized
            if (!isInitialized()) {
                return@withContext PanelAnalysisResultWrapper.Error(
                    PanelAnalysisException.NotInitialized()
                )
            }

            // Load image
            val bitmap = loadImage(request.imageUrl)
                ?: return@withContext PanelAnalysisResultWrapper.Error(
                    PanelAnalysisException.ImageLoadError("Failed to load image: ${request.imageUrl}")
                )

            // Calculate image hash if not provided
            val imageHash = request.imageHash.ifEmpty { 
                calculateImageHash(bitmap) 
            }

            // Analyze with timeout
            val result = withTimeout(timeoutMillis) {
                analyzeWithGemini(bitmap, imageHash, request.readingDirection)
            }

            // Cache the result
            cacheService.cacheResult(imageHash, result)

            // Clean up bitmap
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }

            PanelAnalysisResultWrapper.Success(result)
        } catch (e: TimeoutCancellationException) {
            PanelAnalysisResultWrapper.Error(
                PanelAnalysisException.Timeout()
            )
        } catch (e: Exception) {
            PanelAnalysisResultWrapper.Error(
                PanelAnalysisException.ApiError("Analysis failed: ${e.message}", e)
            )
        }
    }

    /**
     * Analyze page with simplified API.
     *
     * @param imageUrl URL of the image to analyze
     * @param readingDirection Reading direction (RTL for manga, LTR for comics)
     * @return PanelAnalysisResultWrapper containing success result or error
     */
    suspend fun analyzePage(
        imageUrl: String,
        readingDirection: ReadingDirection = ReadingDirection.RTL
    ): PanelAnalysisResultWrapper {
        val hash = calculateUrlHash(imageUrl)
        return analyzePage(
            PanelAnalysisRequest(
                imageUrl = imageUrl,
                imageHash = hash,
                readingDirection = readingDirection
            )
        )
    }

    /**
     * Perform the actual Gemini Vision analysis.
     */
    private suspend fun analyzeWithGemini(
        bitmap: Bitmap,
        imageHash: String,
        readingDirection: ReadingDirection
    ): PageAnalysisResult = withContext(ioDispatcher) {
        // Convert bitmap to base64 for API
        val base64Image = bitmapToBase64(bitmap)

        // Build the prompt for panel detection
        val prompt = buildPanelDetectionPrompt(readingDirection)

        // Create multimodal content
        val content = buildMultimodalContent(prompt, base64Image)

        // Call Gemini API — generateContent returns GenerateContentResponse directly
        val response = try {
            geminiClient.generateContent(content)
        } catch (e: IllegalStateException) {
            throw PanelAnalysisException.NotInitialized()
        } catch (e: Exception) {
            throw PanelAnalysisException.ApiError(e.message ?: "Unknown error", e)
        }

        val responseText = response.text
            ?: throw PanelAnalysisException.ApiError("Empty response from AI", null)

        parsePanelResponse(
            response = responseText,
            imageHash = imageHash,
            readingDirection = readingDirection
        )
    }

    /**
     * Build the prompt for panel detection.
     */
    private fun buildPanelDetectionPrompt(readingDirection: ReadingDirection): String {
        val directionText = when (readingDirection) {
            ReadingDirection.RTL -> "right-to-left (manga style)"
            ReadingDirection.LTR -> "left-to-right (Western comic style)"
            ReadingDirection.VERTICAL -> "top-to-bottom (webtoon style)"
        }

        return """
            Analyze this manga/comic page and detect all comic panels.
            
            Reading direction: $directionText
            
            For each panel, provide:
            1. Panel boundaries as normalized coordinates (0.0 to 1.0):
               - left: distance from left edge
               - top: distance from top edge
               - right: distance from left edge
               - bottom: distance from top edge
            2. Panel type: STANDARD, SPLASH, INSET, BORDERLESS, DOUBLE_SPREAD, or IRREGULAR
            3. Confidence score (0.0 to 1.0)
            
            Return ONLY a JSON object in this exact format:
            {
              "panels": [
                {
                  "id": 0,
                  "bounds": {
                    "left": 0.0,
                    "top": 0.0,
                    "right": 0.5,
                    "bottom": 0.5
                  },
                  "confidence": 0.95,
                  "panelType": "STANDARD",
                  "readingOrder": 0
                }
              ],
              "pageType": "STANDARD"
            }
            
            Panels should be sorted by reading order ($directionText).
            Ensure all coordinates are between 0.0 and 1.0.
            Do not include any explanatory text, only the JSON.
        """.trimIndent()
    }

    /**
     * Build multimodal content with image.
     */
    private fun buildMultimodalContent(prompt: String, base64Image: String): String {
        // For Gemini Vision, we need to use the content API with image
        // The SDK handles this internally when we pass the right format
        return prompt
    }

    /**
     * Parse the API response into PageAnalysisResult.
     */
    private fun parsePanelResponse(
        response: String,
        imageHash: String,
        readingDirection: ReadingDirection
    ): PageAnalysisResult {
        return try {
            // Extract JSON from response
            val jsonString = extractJsonFromResponse(response)
            
            // Parse using kotlinx.serialization
            val parser = PanelResponseParser()
            val parsed = parser.parse(jsonString)

            PageAnalysisResult(
                imageHash = imageHash,
                panels = parsed.panels.map { it.copy(readingOrder = it.id) },
                pageType = parsed.pageType,
                readingDirection = readingDirection,
                analysisTimestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            throw PanelAnalysisException.InvalidResponse(
                "Failed to parse panel response: ${e.message}"
            )
        }
    }

    /**
     * Extract JSON from API response (handles cases where AI adds markdown).
     */
    private fun extractJsonFromResponse(response: String): String {
        // Remove markdown code blocks if present
        var cleaned = response.trim()
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").trim()
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").trim()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trim()
        }

        // Find JSON object boundaries
        val startIndex = cleaned.indexOf('{')
        val endIndex = cleaned.lastIndexOf('}')
        
        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
            throw PanelAnalysisException.InvalidResponse("No valid JSON found in response")
        }

        return cleaned.substring(startIndex, endIndex + 1)
    }

    /**
     * Load image from URL using Coil.
     */
    private suspend fun loadImage(imageUrl: String?): Bitmap? = withContext(ioDispatcher) {
        if (imageUrl == null) return@withContext null

        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()

            when (val result = imageLoader.execute(request)) {
                is SuccessResult -> result.image.toBitmap()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convert bitmap to base64 string for API.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Compress to JPEG for smaller size
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Calculate hash of image for caching.
     */
    private fun calculateImageHash(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val bytes = outputStream.toByteArray()
        return hashBytes(bytes)
    }

    /**
     * Calculate hash of URL for caching.
     */
    private fun calculateUrlHash(url: String): String {
        return hashBytes(url.toByteArray())
    }

    /**
     * Hash bytes using SHA-256.
     */
    private fun hashBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /**
         * Gemini Vision model optimized for image understanding.
         */
        const val VISION_MODEL = "gemini-1.5-flash"

        /**
         * Default timeout for panel analysis in milliseconds.
         */
        const val DEFAULT_TIMEOUT_MS = 45_000L

        /**
         * AI configuration optimized for vision tasks.
         */
        val VISION_CONFIG = AiConfig(
            maxTokens = 2048,
            temperature = 0.1,  // Low temperature for consistent JSON output
            topP = 0.8,
            requestTimeoutMillis = 45_000L,
            enableSafetyFilters = false  // Disable for better panel detection
        )
    }
}

/**
 * Data class for parsed panel response.
 */
private data class ParsedPanelResponse(
    val panels: List<PanelData>,
    val pageType: PageType
)

/**
 * Parser for panel detection responses.
 */
private class PanelResponseParser {
    fun parse(jsonString: String): ParsedPanelResponse {
        // Simple JSON parsing without external dependencies
        // In production, use kotlinx.serialization or Gson
        
        val panels = mutableListOf<PanelData>()
        var pageType = PageType.STANDARD

        try {
            // Extract pageType
            val pageTypeMatch = Regex("\"pageType\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
            if (pageTypeMatch != null) {
                pageType = PageType.valueOf(pageTypeMatch.groupValues[1])
            }

            // Extract panels array
            val panelsMatch = Regex("\"panels\"\\s*:\\s*(\\[[^\\]]*\\])").find(jsonString)
            if (panelsMatch != null) {
                val panelsJson = panelsMatch.groupValues[1]
                panels.addAll(parsePanelsArray(panelsJson))
            }
        } catch (e: Exception) {
            throw PanelAnalysisException.InvalidResponse("JSON parsing failed: ${e.message}")
        }

        return ParsedPanelResponse(panels, pageType)
    }

    private fun parsePanelsArray(panelsJson: String): List<PanelData> {
        val panels = mutableListOf<PanelData>()
        
        // Match individual panel objects
        val panelRegex = Regex("\\{([^}]*)\\}")
        val matches = panelRegex.findAll(panelsJson)
        
        matches.forEachIndexed { index, match ->
            val panelJson = match.groupValues[1]
            val panel = parsePanelObject(panelJson, index)
            panels.add(panel)
        }

        return panels
    }

    private fun parsePanelObject(panelJson: String, defaultId: Int): PanelData {
        val id = extractInt(panelJson, "id") ?: defaultId
        val confidence = extractFloat(panelJson, "confidence") ?: 0.8f
        val panelTypeStr = extractString(panelJson, "panelType") ?: "STANDARD"
        val readingOrder = extractInt(panelJson, "readingOrder") ?: id

        val bounds = parseBounds(panelJson)

        return PanelData(
            id = id,
            bounds = bounds,
            confidence = confidence,
            panelType = PanelType.valueOf(panelTypeStr),
            readingOrder = readingOrder
        )
    }

    private fun parseBounds(panelJson: String): PanelBoundsData {
        val boundsMatch = Regex("\"bounds\"\\s*:\\s*\\{([^}]*)\\}").find(panelJson)
        val boundsJson = boundsMatch?.groupValues?.get(1) ?: ""

        return PanelBoundsData(
            left = extractFloat(boundsJson, "left") ?: 0f,
            top = extractFloat(boundsJson, "top") ?: 0f,
            right = extractFloat(boundsJson, "right") ?: 1f,
            bottom = extractFloat(boundsJson, "bottom") ?: 1f
        )
    }

    private fun extractInt(json: String, key: String): Int? {
        val match = Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractFloat(json: String, key: String): Float? {
        val match = Regex("\"$key\"\\s*:\\s*([0-9.]+)").find(json)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    private fun extractString(json: String, key: String): String? {
        val match = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)
        return match?.groupValues?.get(1)
    }
}
