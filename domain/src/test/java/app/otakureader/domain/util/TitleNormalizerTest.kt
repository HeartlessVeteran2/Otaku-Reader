package app.otakureader.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun `normalize removes year markers`() {
        assertEquals(
            "tokyo ghoul",
            TitleNormalizer.normalize("Tokyo Ghoul (2011)")
        )
        assertEquals(
            "tokyo ghoul",
            TitleNormalizer.normalize("Tokyo Ghoul [2011]")
        )
        assertEquals(
            "tokyo ghoul",
            TitleNormalizer.normalize("Tokyo Ghoul - 2011")
        )
    }

    @Test
    fun `normalize removes common prefixes`() {
        assertEquals(
            "seven deadly sins",
            TitleNormalizer.normalize("The Seven Deadly Sins")
        )
        assertEquals(
            "promised neverland",
            TitleNormalizer.normalize("The Promised Neverland")
        )
        assertEquals(
            "manga title",
            TitleNormalizer.normalize("A Manga Title")
        )
    }

    @Test
    fun `normalize removes common suffixes`() {
        assertEquals(
            "one piece",
            TitleNormalizer.normalize("One Piece Part 1")
        )
        assertEquals(
            "naruto",
            TitleNormalizer.normalize("Naruto Season 2")
        )
        assertEquals(
            "bleach",
            TitleNormalizer.normalize("Bleach Vol 3")
        )
    }

    @Test
    fun `normalize removes special characters`() {
        assertEquals(
            "one piece",
            TitleNormalizer.normalize("One-Piece!")
        )
        assertEquals(
            "hunter x hunter",
            TitleNormalizer.normalize("Hunter×Hunter")
        )
    }

    @Test
    fun `normalize handles multiple transformations`() {
        assertEquals(
            "attack on titan",
            TitleNormalizer.normalize("The Attack on Titan (2013) - Part 1!")
        )
    }

    @Test
    fun `areRomanizationVariants detects known variants`() {
        assertTrue(
            TitleNormalizer.areRomanizationVariants(
                "Boku no Hero Academia",
                "My Hero Academia"
            )
        )
        assertTrue(
            TitleNormalizer.areRomanizationVariants(
                "Shingeki no Kyojin",
                "Attack on Titan"
            )
        )
        assertTrue(
            TitleNormalizer.areRomanizationVariants(
                "Kimetsu no Yaiba",
                "Demon Slayer"
            )
        )
    }

    @Test
    fun `areRomanizationVariants works bidirectionally`() {
        assertTrue(
            TitleNormalizer.areRomanizationVariants(
                "My Hero Academia",
                "Boku no Hero Academia"
            )
        )
    }

    @Test
    fun `normalizeAuthor removes honorifics`() {
        assertEquals(
            "oda eiichiro",
            TitleNormalizer.normalizeAuthor("Oda Eiichiro-sensei")
        )
        assertEquals(
            "kishimoto masashi",
            TitleNormalizer.normalizeAuthor("Kishimoto Masashi-san")
        )
    }

    @Test
    fun `normalizeAuthor handles null and blank`() {
        assertEquals("", TitleNormalizer.normalizeAuthor(null))
        assertEquals("", TitleNormalizer.normalizeAuthor(""))
        assertEquals("", TitleNormalizer.normalizeAuthor("   "))
    }

    @Test
    fun `calculateGenreOverlap returns correct overlap ratio`() {
        val genres1 = listOf("Action", "Adventure", "Fantasy")
        val genres2 = listOf("Action", "Adventure", "Drama")

        // Intersection: 2 (Action, Adventure)
        // Union: 4 (Action, Adventure, Fantasy, Drama)
        // Ratio: 2/4 = 0.5
        assertEquals(0.5f, TitleNormalizer.calculateGenreOverlap(genres1, genres2), 0.01f)
    }

    @Test
    fun `calculateGenreOverlap handles identical genres`() {
        val genres = listOf("Action", "Adventure", "Fantasy")

        assertEquals(1.0f, TitleNormalizer.calculateGenreOverlap(genres, genres), 0.01f)
    }

    @Test
    fun `calculateGenreOverlap handles no overlap`() {
        val genres1 = listOf("Action", "Adventure")
        val genres2 = listOf("Romance", "Drama")

        assertEquals(0.0f, TitleNormalizer.calculateGenreOverlap(genres1, genres2), 0.01f)
    }

    @Test
    fun `calculateGenreOverlap handles empty lists`() {
        val genres = listOf("Action", "Adventure")

        assertEquals(0.0f, TitleNormalizer.calculateGenreOverlap(genres, emptyList()), 0.01f)
        assertEquals(0.0f, TitleNormalizer.calculateGenreOverlap(emptyList(), genres), 0.01f)
        assertEquals(0.0f, TitleNormalizer.calculateGenreOverlap(emptyList(), emptyList()), 0.01f)
    }

    @Test
    fun `calculateGenreOverlap is case insensitive`() {
        val genres1 = listOf("Action", "ADVENTURE")
        val genres2 = listOf("action", "adventure")

        assertEquals(1.0f, TitleNormalizer.calculateGenreOverlap(genres1, genres2), 0.01f)
    }
}
