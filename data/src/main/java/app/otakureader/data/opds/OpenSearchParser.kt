package app.otakureader.data.opds

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parses OpenSearch description documents to extract the search URL template.
 */
object OpenSearchParser {

    /**
     * Parses an OpenSearch description XML and returns the Atom search template URL.
     * Returns null if no suitable template is found.
     */
    fun parseTemplate(input: InputStream): String? {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var atomTemplate: String? = null
        var fallbackTemplate: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name.substringAfterLast(':')
                if (tag == "Url") {
                    val type = parser.getAttributeValue(null, "type") ?: ""
                    val template = parser.getAttributeValue(null, "template") ?: ""
                    when {
                        type.contains("application/atom+xml") -> atomTemplate = template
                        template.isNotBlank() && fallbackTemplate == null -> fallbackTemplate = template
                    }
                }
            }
            eventType = parser.next()
        }

        return atomTemplate ?: fallbackTemplate
    }
}
