package app.otakureader.core.tachiyomi.compat

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

/**
 * Parses Tachiyomi extension metadata from AndroidManifest.xml.
 *
 * Tachiyomi extensions contain specific metadata in their AndroidManifest.xml:
 * - tachiyomi.extension: Marks the package as an extension
 * - tachiyomi.extension.lang: Language code (en, ja, etc.)
 * - tachiyomi.extension.nsfw: Whether the extension contains NSFW content
 * - tachiyomi.extension.sources: JSON array of source definitions
 */
class TachiyomiManifestParser {

    /**
     * Data class representing parsed extension information
     */
    data class ExtensionInfo(
        val name: String?,
        val lang: String?,
        val isNsfw: Boolean,
        val sources: List<SourceInfo>
    )

    /**
     * Data class representing a source definition from manifest
     */
    data class SourceInfo(
        val name: String = "Unknown",
        val className: String,
        val sourceId: Long? = null,
        val lang: String? = null,
        val baseUrl: String? = null
    )

    companion object {
        const val MANIFEST_PATH = "AndroidManifest.xml"
        const val METADATA_EXTENSION = "tachiyomi.extension"
        const val METADATA_LANG = "tachiyomi.extension.lang"
        const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        const val METADATA_SOURCES = "tachiyomi.extension.sources"
        const val METADATA_HAS_README = "tachiyomi.extension.hasReadme"
        const val METADATA_HAS_CHANGELOG = "tachiyomi.extension.hasChangelog"
    }

    /**
     * Parse extension manifest from APK file
     */
    fun parse(apkFile: File): ExtensionInfo {
        val manifestXml = extractManifest(apkFile)
            ?: return ExtensionInfo(null, null, false, emptyList())

        return parseManifestXml(manifestXml)
    }

    /**
     * Parse extension manifest from XML string
     */
    fun parseManifestXml(manifestXml: String): ExtensionInfo {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(manifestXml))

        var name: String? = null
        var lang: String? = null
        var isNsfw = false
        var sourcesJson: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "meta-data") {
                        val attrCount = parser.attributeCount
                        var metaName: String? = null
                        var metaValue: String? = null

                        for (i in 0 until attrCount) {
                            val attrName = parser.getAttributeName(i)
                            val attrValue = parser.getAttributeValue(i)

                            when {
                                attrName.endsWith("name") -> metaName = attrValue
                                attrName.endsWith("value") -> metaValue = attrValue
                            }
                        }

                        when (metaName) {
                            METADATA_EXTENSION -> name = metaValue
                            METADATA_LANG -> lang = metaValue
                            METADATA_NSFW -> isNsfw = metaValue?.toBoolean() ?: false
                            METADATA_SOURCES -> sourcesJson = metaValue
                        }
                    }

                    // Also check application label for name
                    if (parser.name == "application") {
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).endsWith("label")) {
                                name = parser.getAttributeValue(i)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        // Parse sources from JSON if available
        val sources = sourcesJson?.let { parseSourcesJson(it) } ?: emptyList()

        return ExtensionInfo(
            name = name,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources
        )
    }

    /**
     * Parse sources JSON array
     * Expected format: [{"name": "SourceName", "class": "ClassName", "lang": "en"}]
     */
    private fun parseSourcesJson(json: String): List<SourceInfo> {
        val sources = mutableListOf<SourceInfo>()

        try {
            // Simple JSON parsing - in production, use a proper JSON library like Gson or Moshi
            val jsonArray = json.trim()
            if (!jsonArray.startsWith("[") || !jsonArray.endsWith("]")) {
                return sources
            }

            // Remove outer brackets and split by object boundaries
            val content = jsonArray.substring(1, jsonArray.length - 1)
            val objects = splitJsonObjects(content)

            for (obj in objects) {
                val map = parseJsonObject(obj)
                if (map.containsKey("class")) {
                    sources.add(
                        SourceInfo(
                            name = map["name"] ?: "Unknown",
                            className = map["class"]!!,
                            sourceId = map["id"]?.toLongOrNull(),
                            lang = map["lang"],
                            baseUrl = map["baseUrl"]
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }

        return sources
    }

    /**
     * Split JSON array content into individual objects
     */
    private fun splitJsonObjects(content: String): List<String> {
        val objects = mutableListOf<String>()
        var braceCount = 0
        var currentObject = StringBuilder()
        var inString = false
        var escapeNext = false

        for (char in content) {
            when {
                escapeNext -> {
                    currentObject.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    currentObject.append(char)
                    escapeNext = true
                }
                char == '"' -> {
                    currentObject.append(char)
                    inString = !inString
                }
                char == '{' && !inString -> {
                    braceCount++
                    currentObject.append(char)
                }
                char == '}' && !inString -> {
                    braceCount--
                    currentObject.append(char)
                    if (braceCount == 0) {
                        objects.add(currentObject.toString())
                        currentObject = StringBuilder()
                    }
                }
                else -> {
                    if (braceCount > 0) {
                        currentObject.append(char)
                    }
                }
            }
        }

        return objects
    }

    /**
     * Parse a simple JSON object into key-value map
     */
    private fun parseJsonObject(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val trimmed = json.trim().removeSurrounding("{", "}")

        var key: String? = null
        var value = StringBuilder()
        var inString = false
        var escapeNext = false

        for (char in trimmed) {
            when {
                escapeNext -> {
                    value.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    value.append(char)
                    escapeNext = true
                }
                char == '"' -> {
                    if (inString) {
                        if (key == null) {
                            key = value.toString()
                            value = StringBuilder()
                        } else {
                            map[key] = value.toString()
                            key = null
                            value = StringBuilder()
                        }
                    }
                    inString = !inString
                }
                char == ':' && !inString -> {
                    // Skip colon between key and value
                }
                char == ',' && !inString -> {
                    // Skip comma between pairs
                }
                !char.isWhitespace() || inString -> {
                    value.append(char)
                }
            }
        }

        // Handle last value if exists
        if (key != null && value.isNotEmpty()) {
            map[key] = value.toString()
        }

        return map
    }

    /**
     * Extract AndroidManifest.xml from APK (ZIP) file
     */
    private fun extractManifest(apkFile: File): String? {
        return try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(MANIFEST_PATH)
                entry?.let {
                    val bytes = zip.getInputStream(it).readBytes()
                    decompressXml(bytes)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decompress binary XML to readable XML string
     *
     * Android's AAPT compiles XML to a binary format. This is a simplified
     * decompressor. For production, consider using a library like android-apktool
     * or axmlprinter.
     */
    private fun decompressXml(xmlBytes: ByteArray): String? {
        return try {
            // Check for binary XML magic number
            if (xmlBytes.size < 4 || xmlBytes[0] != 0x03.toByte() || xmlBytes[1] != 0x00.toByte()) {
                // Might already be text XML
                return String(xmlBytes, Charsets.UTF_8)
            }

            // For now, return a basic parsed structure
            // In production, use a proper binary XML decoder
            parseBinaryXml(xmlBytes)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse binary XML format (simplified)
     *
     * This is a basic implementation that extracts string table and attributes.
     * For full compatibility, integrate with android-binary-xml library.
     */
    private fun parseBinaryXml(bytes: ByteArray): String? {
        // This is a placeholder implementation
        // Real implementation would parse the binary XML structure:
        // - String pool
        // - Resource IDs
        // - XML nodes and attributes

        // For now, try to extract strings and reconstruct basic structure
        val strings = extractStrings(bytes)

        // Build a basic XML structure from extracted data
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?\u003e\n")
        sb.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\u003e\n")
        sb.append("    <application\n")

        // Look for metadata in strings
        strings.forEach { str ->
            when {
                str.contains("tachiyomi.extension") -> {
                    sb.append("        <meta-data android:name=\"$str\" android:value=\"$str\" /\u003e\n")
                }
            }
        }

        sb.append("    </application\u003e\n")
        sb.append("</manifest>")

        return sb.toString()
    }

    /**
     * Extract strings from binary XML
     */
    private fun extractStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()

        try {
            // Parse string pool chunk
            var offset = 0

            // Skip header
            offset += 8

            // Read string pool chunk header
            val chunkType = readUInt16(bytes, offset)
            if (chunkType == 0x0001) { // String pool type
                val chunkSize = readUInt32(bytes, offset + 4)
                val stringCount = readUInt32(bytes, offset + 8)
                val styleCount = readUInt32(bytes, offset + 12)
                val flags = readUInt32(bytes, offset + 16)
                val stringsStart = readUInt32(bytes, offset + 20)
                val stylesStart = readUInt32(bytes, offset + 24)

                // Read string offsets
                val stringOffsets = (0 until stringCount).map { i ->
                    readUInt32(bytes, offset + 28 + i * 4)
                }

                // Read strings
                for (strOffset in stringOffsets) {
                    val absOffset = offset + stringsStart.toInt() + strOffset.toInt()
                    val str = readString(bytes, absOffset, flags)
                    if (str.isNotEmpty()) {
                        strings.add(str)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }

        return strings
    }

    /**
     * Read 16-bit unsigned integer
     */
    private fun readUInt16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    /**
     * Read 32-bit unsigned integer
     */
    private fun readUInt32(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toInt() and 0xFF).toLong() or
                ((bytes[offset + 1].toInt() and 0xFF).toLong() shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF).toLong() shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF).toLong() shl 24)
    }

    /**
     * Read UTF-8 or UTF-16 string from binary XML
     */
    private fun readString(bytes: ByteArray, offset: Int, flags: Long): String {
        return try {
            val isUtf8 = (flags and 0x100) != 0L

            if (isUtf8) {
                // UTF-8 format
                val len1 = bytes[offset].toInt() and 0xFF
                val len2 = bytes[offset + 1].toInt() and 0xFF
                val length = if ((len1 and 0x80) != 0) {
                    ((len1 and 0x7F) shl 8) or len2
                } else {
                    len1
                }

                val strOffset = if ((len1 and 0x80) != 0) offset + 2 else offset + 1
                String(bytes, strOffset, length, Charsets.UTF_8)
            } else {
                // UTF-16 format
                val length = readUInt16(bytes, offset)
                val sb = StringBuilder()
                for (i in 0 until length) {
                    val char = readUInt16(bytes, offset + 2 + i * 2)
                    sb.append(char.toChar())
                }
                sb.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }
}
