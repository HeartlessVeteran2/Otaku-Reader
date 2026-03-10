package app.otakureader.domain.util

/**
 * Utility for normalizing manga titles to improve matching accuracy.
 * Handles common variations like romanizations, year markers, and formatting differences.
 */
object TitleNormalizer {

    /**
     * Normalize a title for matching by:
     * - Converting to lowercase
     * - Removing year markers
     * - Stripping common prefixes/suffixes
     * - Removing special characters and extra whitespace
     * - Handling common romanization patterns
     */
    fun normalize(title: String): String {
        var normalized = title.lowercase().trim()

        // Replace common special character variants with their ASCII equivalents
        // Add spaces around these to preserve word boundaries
        normalized = normalized.replace("×", " x ")  // Multiplication sign → x with spaces
        normalized = normalized.replace("–", " ")  // En dash → space
        normalized = normalized.replace("—", " ")  // Em dash → space

        // Remove year markers (e.g., "(2023)", "[2023]", "- 2023")
        normalized = normalized.replace(Regex("""\s*[(\[]\s*\d{4}\s*[)\]]\s*"""), " ")
        normalized = normalized.replace(Regex("""\s*-\s*\d{4}\s*$"""), "")

        // Remove common English articles and prefixes
        normalized = removeCommonPrefixes(normalized)

        // Replace special characters and punctuation with spaces (but keep alphanumeric and existing spaces)
        // Do this before suffix removal so we don't have leftover special chars
        normalized = normalized.replace(Regex("""[^\w\s]"""), " ")

        // Normalize whitespace before suffix removal
        normalized = normalized.replace(Regex("""\s+"""), " ").trim()

        // Remove common suffixes (after whitespace normalization)
        normalized = removeCommonSuffixes(normalized)

        // Final whitespace normalization
        normalized = normalized.replace(Regex("""\s+"""), " ").trim()

        return normalized
    }

    /**
     * Remove common article prefixes that don't affect title identity.
     */
    private fun removeCommonPrefixes(title: String): String {
        val prefixes = listOf(
            "the ",
            "a ",
            "an "
        )

        var result = title
        for (prefix in prefixes) {
            if (result.startsWith(prefix)) {
                result = result.substring(prefix.length)
                break
            }
        }
        return result
    }

    /**
     * Remove common suffixes like "Part 1", "Season 2", etc.
     */
    private fun removeCommonSuffixes(title: String): String {
        var result = title

        // Remove patterns like "part 1", "season 2", "vol 3", etc.
        result = result.replace(Regex("""\s*(?:part|season|vol|volume|ch|chapter)\s+\d+\s*$""", RegexOption.IGNORE_CASE), "")

        return result
    }

    /**
     * Check if two titles might be romanization variants of the same title.
     * Uses a mapping of common romanization patterns.
     */
    fun areRomanizationVariants(title1: String, title2: String): Boolean {
        val normalized1 = normalize(title1)
        val normalized2 = normalize(title2)

        // Check if either title contains a known romanization mapping
        for ((romanization, english) in romanizationMap) {
            val romanizationNorm = normalize(romanization)
            val englishNorm = normalize(english)

            if ((normalized1.contains(romanizationNorm) && normalized2.contains(englishNorm)) ||
                (normalized2.contains(romanizationNorm) && normalized1.contains(englishNorm))) {
                return true
            }
        }

        return false
    }

    /**
     * Common romanization to English title mappings.
     * This is a curated list of popular manga with known alternative titles.
     */
    private val romanizationMap = mapOf(
        // Popular series with known romanization differences
        "boku no hero academia" to "my hero academia",
        "shingeki no kyojin" to "attack on titan",
        "nanatsu no taizai" to "the seven deadly sins",
        "kimetsu no yaiba" to "demon slayer",
        "yakusoku no neverland" to "the promised neverland",
        "kaguya sama wa kokurasetai" to "kaguya sama love is war",
        "karakai jouzu no takagi san" to "teasing master takagi san",
        "tensei shitara slime datta ken" to "that time i got reincarnated as a slime",
        "overlord" to "overlord",
        "one punch man" to "one punch man",
        "tokyo ghoul" to "tokyo ghoul",
        "tokyo kushu" to "tokyo ghoul",
        "boku dake ga inai machi" to "erased",
        "kono subarashii sekai ni shukufuku wo" to "konosuba",
        "re zero kara hajimeru isekai seikatsu" to "re zero",
        "tate no yuusha no nariagari" to "the rising of the shield hero",
        "sword art online" to "sword art online",
        "sono bisque doll wa koi wo suru" to "my dress up darling",
        "spy x family" to "spy family",
        "chainsaw man" to "chainsaw man",
        "jujutsu kaisen" to "jujutsu kaisen",
        "black clover" to "black clover",
        "one piece" to "one piece",
        "naruto" to "naruto",
        "bleach" to "bleach",
        "hunter x hunter" to "hunter x hunter",
        "fairy tail" to "fairy tail",
        "gintama" to "gintama",
        "fullmetal alchemist" to "fullmetal alchemist",
        "hagane no renkinjutsushi" to "fullmetal alchemist",
        "steins gate" to "steins gate",
        "code geass" to "code geass",
        "death note" to "death note",
        "mob psycho" to "mob psycho",
        "made in abyss" to "made in abyss",
        "violet evergarden" to "violet evergarden",
        "your name" to "your name",
        "kimi no na wa" to "your name",
        "weathering with you" to "weathering with you",
        "tenki no ko" to "weathering with you",
        "a silent voice" to "a silent voice",
        "koe no katachi" to "a silent voice"
    )

    /**
     * Normalize author name for matching.
     * Handles different name ordering and formatting.
     */
    fun normalizeAuthor(author: String?): String {
        if (author.isNullOrBlank()) return ""

        var normalized = author.lowercase().trim()

        // Remove common suffixes like "sensei" with a dash or space
        normalized = normalized.replace(Regex("""[-\s]+(sensei|san|sama|kun|chan)\s*$"""), "")

        // Normalize whitespace
        normalized = normalized.replace(Regex("""\s+"""), " ").trim()

        return normalized
    }

    /**
     * Calculate genre overlap ratio between two genre lists.
     * Returns a value from 0.0 (no overlap) to 1.0 (complete overlap).
     */
    fun calculateGenreOverlap(genres1: List<String>, genres2: List<String>): Float {
        if (genres1.isEmpty() || genres2.isEmpty()) return 0f

        val normalized1 = genres1.map { it.lowercase().trim() }.toSet()
        val normalized2 = genres2.map { it.lowercase().trim() }.toSet()

        val intersection = normalized1.intersect(normalized2).size
        val union = normalized1.union(normalized2).size

        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
}
