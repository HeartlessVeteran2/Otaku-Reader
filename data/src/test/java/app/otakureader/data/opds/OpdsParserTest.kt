package app.otakureader.data.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for [OpdsParser]: exercises XML parsing of OPDS (Atom) feeds
 * against realistic feed fixtures.
 */
class OpdsParserTest {

    private fun parse(xml: String) = OpdsParser.parse(xml.trimIndent().byteInputStream())

    // ── Empty / minimal feed ─────────────────────────────────────────────────

    @Test
    fun `empty feed returns empty title and no entries`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Empty</title>
            </feed>
        """)
        assertEquals("Empty", feed.title)
        assertTrue(feed.entries.isEmpty())
        assertTrue(feed.links.isEmpty())
        assertNull(feed.searchUrl)
    }

    // ── Navigation feed ──────────────────────────────────────────────────────

    @Test
    fun `navigation feed parses title and entry titles`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:opds="http://opds-spec.org/2010/catalog">
              <title>My OPDS Catalog</title>
              <entry>
                <title>Popular</title>
                <id>urn:uuid:popular</id>
                <link rel="http://opds-spec.org/sort/popular"
                      type="application/atom+xml;profile=opds-catalog"
                      href="/catalog/popular"/>
              </entry>
              <entry>
                <title>New Releases</title>
                <id>urn:uuid:new</id>
                <link rel="http://opds-spec.org/sort/new"
                      type="application/atom+xml;profile=opds-catalog"
                      href="/catalog/new"/>
              </entry>
            </feed>
        """)

        assertEquals("My OPDS Catalog", feed.title)
        assertEquals(2, feed.entries.size)
        assertEquals("Popular", feed.entries[0].title)
        assertEquals("urn:uuid:popular", feed.entries[0].id)
        assertEquals("New Releases", feed.entries[1].title)
    }

    @Test
    fun `navigation entry links are classified as navigation`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Root</title>
              <entry>
                <title>Browse</title>
                <id>urn:browse</id>
                <link rel="subsection"
                      type="application/atom+xml;profile=opds-catalog"
                      href="/browse"/>
              </entry>
            </feed>
        """)

        val entry = feed.entries[0]
        assertEquals(1, entry.links.size)
        assertTrue(entry.links[0].isNavigation)
    }

    // ── Acquisition feed ─────────────────────────────────────────────────────

    @Test
    fun `acquisition feed parses book entries with download links`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:opds="http://opds-spec.org/2010/catalog">
              <title>Manga Catalog</title>
              <entry>
                <title>My Hero Academia</title>
                <id>urn:manga:mha</id>
                <author><name>Kohei Horikoshi</name></author>
                <summary>A boy without powers...</summary>
                <link rel="http://opds-spec.org/acquisition"
                      type="application/x-cbz"
                      href="/download/mha.cbz"
                      title="Download CBZ"/>
                <link rel="http://opds-spec.org/image/thumbnail"
                      type="image/jpeg"
                      href="/covers/mha.jpg"/>
              </entry>
            </feed>
        """)

        assertEquals("Manga Catalog", feed.title)
        assertEquals(1, feed.entries.size)
        val entry = feed.entries[0]
        assertEquals("My Hero Academia", entry.title)
        assertEquals("Kohei Horikoshi", entry.author)
        assertEquals("A boy without powers...", entry.summary)
        assertEquals("urn:manga:mha", entry.id)

        // Download link
        val downloadLink = entry.links.first { it.isAcquisition && it.type.contains("cbz") }
        assertEquals("/download/mha.cbz", downloadLink.href)
        assertEquals("Download CBZ", downloadLink.title)

        // Thumbnail
        assertEquals("/covers/mha.jpg", entry.thumbnailUrl)
    }

    @Test
    fun `acquisition links for epub and pdf are recognised`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Books</title>
              <entry>
                <title>Sample Book</title>
                <id>urn:sample</id>
                <link rel="http://opds-spec.org/acquisition"
                      type="application/epub+zip"
                      href="/book.epub"/>
                <link rel="http://opds-spec.org/acquisition"
                      type="application/pdf"
                      href="/book.pdf"/>
              </entry>
            </feed>
        """)

        val links = feed.entries[0].links
        assertTrue(links.all { it.isAcquisition })
        assertTrue(links.any { it.href == "/book.epub" })
        assertTrue(links.any { it.href == "/book.pdf" })
    }

    // ── Search link ──────────────────────────────────────────────────────────

    @Test
    fun `feed-level search link is extracted into searchUrl`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Searchable</title>
              <link rel="search"
                    type="application/opensearchdescription+xml"
                    href="/search.xml"/>
            </feed>
        """)

        assertEquals("/search.xml", feed.searchUrl)
        assertTrue(feed.links.first { it.rel == "search" }.isSearch)
    }

    // ── Next-page link ───────────────────────────────────────────────────────

    @Test
    fun `next-page link is classified as isNextPage`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Paged</title>
              <link rel="next"
                    type="application/atom+xml;profile=opds-catalog"
                    href="/catalog?page=2"/>
            </feed>
        """)

        val nextLink = feed.links.first { it.rel == "next" }
        assertTrue(nextLink.isNextPage)
    }

    // ── Thumbnail fallback ───────────────────────────────────────────────────

    @Test
    fun `entry thumbnail falls back to opds image rel when no thumbnail rel`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Catalog</title>
              <entry>
                <title>Berserk</title>
                <id>urn:berserk</id>
                <link rel="http://opds-spec.org/image"
                      type="image/jpeg"
                      href="/covers/berserk-full.jpg"/>
              </entry>
            </feed>
        """)

        assertEquals("/covers/berserk-full.jpg", feed.entries[0].thumbnailUrl)
    }

    @Test
    fun `entry with no image links has null thumbnail`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Catalog</title>
              <entry>
                <title>No Cover</title>
                <id>urn:nocover</id>
                <link rel="http://opds-spec.org/acquisition"
                      type="application/x-cbz"
                      href="/download/book.cbz"/>
              </entry>
            </feed>
        """)

        assertNull(feed.entries[0].thumbnailUrl)
    }

    // ── Multiple entries ─────────────────────────────────────────────────────

    @Test
    fun `feed with multiple entries parses all of them`() {
        val titles = listOf("One Piece", "Naruto", "Bleach", "Dragon Ball")
        val entriesXml = titles.joinToString("\n") { title ->
            "<entry><title>$title</title><id>urn:$title</id></entry>"
        }
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Shonen</title>
              $entriesXml
            </feed>
        """)

        assertEquals(4, feed.entries.size)
        assertEquals(titles, feed.entries.map { it.title })
    }

    // ── Namespaced elements ──────────────────────────────────────────────────

    @Test
    fun `namespaced opds link elements are parsed correctly`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:opds="http://opds-spec.org/2010/catalog">
              <opds:title>Namespaced Feed</opds:title>
              <entry>
                <opds:title>NS Entry</opds:title>
                <id>urn:ns</id>
                <link rel="subsection"
                      type="application/atom+xml"
                      href="/ns"/>
              </entry>
            </feed>
        """)

        // Parser strips namespace prefix → both depth-2 titles are found
        assertEquals(1, feed.entries.size)
        assertEquals("NS Entry", feed.entries[0].title)
    }

    // ── OpdsLink computed properties ─────────────────────────────────────────

    @Test
    fun `isUnknownType is true for unrecognised type when type is non-blank`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Unknown</title>
              <entry>
                <title>Strange</title>
                <id>urn:strange</id>
                <link rel="alternate"
                      type="application/x-proprietary"
                      href="/strange"/>
              </entry>
            </feed>
        """)

        val link = feed.entries[0].links[0]
        assertTrue(link.isUnknownType)
    }

    @Test
    fun `link with blank type is not classified as unknown`() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Blank</title>
              <entry>
                <title>Blank type link</title>
                <id>urn:blank</id>
                <link rel="alternate" href="/something"/>
              </entry>
            </feed>
        """)

        val link = feed.entries[0].links[0]
        assertTrue(!link.isUnknownType)
    }
}
