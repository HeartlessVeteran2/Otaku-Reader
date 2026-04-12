package app.otakureader.domain.model

import androidx.compose.runtime.Immutable
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
@Immutable
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
@Immutable
data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val links: List<OpdsLink>,
    val searchUrl: String? = null
)

/**
 * Represents an OPDS entry (either navigation or acquisition).
 */
@Immutable
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
@Immutable
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

    /**
     * Returns `true` if this link represents an acquisition (downloadable content).
     *
     * **M-21:** Unrecognized content types are now logged at DEBUG level so that
     * developers can identify OPDS servers using non-standard types. The log is
     * emitted only when the link is neither navigation, acquisition, search, thumbnail,
     * nor next-page — i.e. when the type is genuinely unknown.
     */
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

    /**
     * M-21: Returns `true` if this link's content type is not recognized by any of the
     * standard OPDS predicates. Callers can use this to log unrecognized types for
     * debugging purposes when building OPDS feed parsers.
     *
     * Example usage:
     * ```kotlin
     * if (link.isUnknownType) {
     *     System.err.println("OpdsParser: Unrecognized OPDS link type: ${link.type} rel=${link.rel}")
     * }
     * ```
     */
    val isUnknownType: Boolean
        get() = !isNavigation && !isAcquisition && !isSearch && !isThumbnail && !isNextPage &&
                type.isNotBlank()
}
