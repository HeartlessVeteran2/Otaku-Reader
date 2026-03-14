package app.otakureader.data.opds

import app.otakureader.domain.model.OpdsEntry
import app.otakureader.domain.model.OpdsFeed
import app.otakureader.domain.model.OpdsLink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parses OPDS (Atom) XML feeds into domain models.
 */
object OpdsParser {

    fun parse(input: InputStream): OpdsFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var feedTitle = ""
        var searchUrl: String? = null
        val feedLinks = mutableListOf<OpdsLink>()
        val entries = mutableListOf<OpdsEntry>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfterLast(':')
                    when (tag) {
                        "title" -> {
                            // Only read feed-level title (not inside entry)
                            if (parser.depth == 2) {
                                feedTitle = parser.nextText().trim()
                            }
                        }
                        "link" -> {
                            if (parser.depth == 2) {
                                val link = parseLink(parser)
                                feedLinks.add(link)
                                if (link.isSearch) {
                                    searchUrl = link.href
                                }
                            }
                        }
                        "entry" -> {
                            entries.add(parseEntry(parser))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return OpdsFeed(
            title = feedTitle,
            entries = entries,
            links = feedLinks,
            searchUrl = searchUrl
        )
    }

    private fun parseEntry(parser: XmlPullParser): OpdsEntry {
        var title = ""
        var id = ""
        var summary = ""
        var author = ""
        var updated = ""
        var content = ""
        var thumbnailUrl: String? = null
        val links = mutableListOf<OpdsLink>()

        val entryDepth = parser.depth
        var eventType = parser.next()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfterLast(':')
                    when (tag) {
                        "title" -> title = parser.nextText().trim()
                        "id" -> id = parser.nextText().trim()
                        "summary" -> summary = parser.nextText().trim()
                        "content" -> content = parser.nextText().trim()
                        "updated" -> updated = parser.nextText().trim()
                        "name" -> {
                            // Inside <author><name>
                            if (author.isEmpty()) {
                                author = parser.nextText().trim()
                            }
                        }
                        "link" -> {
                            val link = parseLink(parser)
                            links.add(link)
                            if (link.isThumbnail && thumbnailUrl == null) {
                                thumbnailUrl = link.href
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == entryDepth && parser.name.substringAfterLast(':') == "entry") {
                        break
                    }
                }
            }
            eventType = parser.next()
        }

        // If no explicit thumbnail, try to find an image link
        if (thumbnailUrl == null) {
            thumbnailUrl = links.firstOrNull {
                it.rel.contains("image") || it.type.startsWith("image/")
            }?.href
        }

        return OpdsEntry(
            title = title,
            id = id,
            summary = summary,
            author = author,
            updated = updated,
            thumbnailUrl = thumbnailUrl,
            links = links,
            content = content
        )
    }

    private fun parseLink(parser: XmlPullParser): OpdsLink {
        val href = parser.getAttributeValue(null, "href") ?: ""
        val type = parser.getAttributeValue(null, "type") ?: ""
        val rel = parser.getAttributeValue(null, "rel") ?: ""
        val title = parser.getAttributeValue(null, "title") ?: ""
        return OpdsLink(href = href, type = type, rel = rel, title = title)
    }
}
