package app.otakureader.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents a saved OPDS server configuration.
 *
 * Credentials ([username] / [password]) are kept only in memory for runtime
 * HTTP requests.  They are **not** serialised (marked [Transient]) and are
 * stored separately in encrypted storage — never in the Room database or
 * backup files.
 */
@Serializable
data class OpdsServer(
    val id: Long = 0,
    val name: String,
    val url: String,
    @Transient val username: String = "",
    @Transient val password: String = ""
)

/**
 * Represents an OPDS catalog feed (Atom feed).
 */
data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val links: List<OpdsLink>,
    val searchUrl: String? = null
)

/**
 * Represents an OPDS entry (either navigation or acquisition).
 */
data class OpdsEntry(
    val title: String,
    val id: String = "",
    val summary: String = "",
    val author: String = "",
    val updated: String = "",
    val thumbnailUrl: String? = null,
    val links: List<OpdsLink> = emptyList(),
    val content: String = ""
)

/**
 * Represents a link in an OPDS feed or entry.
 */
data class OpdsLink(
    val href: String,
    val type: String = "",
    val rel: String = "",
    val title: String = ""
) {
    val isNavigation: Boolean
        get() = type.contains("application/atom+xml") ||
                rel == "subsection" ||
                rel == "http://opds-spec.org/sort/popular" ||
                rel == "http://opds-spec.org/sort/new" ||
                rel == "start" ||
                rel == "self"

    val isAcquisition: Boolean
        get() = rel.startsWith("http://opds-spec.org/acquisition") ||
                type.contains("application/epub") ||
                type.contains("application/pdf") ||
                type.contains("application/x-cbz") ||
                type.contains("application/x-cbr") ||
                type.contains("application/zip") ||
                type.contains("image/")

    val isSearch: Boolean
        get() = rel == "search" &&
                type.contains("application/opensearchdescription+xml")

    val isThumbnail: Boolean
        get() = rel == "http://opds-spec.org/image/thumbnail" ||
                rel == "http://opds-spec.org/image"

    val isNextPage: Boolean
        get() = rel == "next"
}
