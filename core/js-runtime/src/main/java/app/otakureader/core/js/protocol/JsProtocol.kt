package app.otakureader.core.js.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire format between the app and the JavaScript engine sidecar.
 *
 * Both processes compile against these types, so they are the one place the two sides can
 * drift. Everything here is a plain serializable value with no Android or engine dependency,
 * which keeps the contract testable in isolation — the sidecar cannot be exercised in a JVM
 * unit test, but the encoding can.
 */
object JsProtocol {

    /** Lenient on purpose: a source's own JSON is untrusted input, not something we control. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Operation names accepted by `IJsEngine.call`. */
    object Method {
        const val POPULAR = "getPopular"
        const val LATEST = "getLatestUpdates"
        const val SEARCH = "search"
        const val DETAIL = "getDetail"
        const val PAGE_LIST = "getPageList"

        /** Novel text content. Stage 7 uses this; the engine supports it from the start. */
        const val HTML_CONTENT = "getHtmlContent"
        const val FILTER_LIST = "getFilterList"
    }
}

// ---------------------------------------------------------------------------------------
// Requests
// ---------------------------------------------------------------------------------------

/** Metadata handed to a source when it is loaded. */
@Serializable
data class JsSourceConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val lang: String,
    val isNsfw: Boolean = false,
    /** Persisted source preferences, exposed to the script as the `SharedPreferences` global. */
    val preferences: Map<String, String> = emptyMap(),
)

@Serializable
data class JsCallArgs(
    val page: Int? = null,
    val query: String? = null,
    val url: String? = null,
    val filters: String? = null,
)

// ---------------------------------------------------------------------------------------
// Responses
// ---------------------------------------------------------------------------------------

/**
 * Every call returns one of these. Errors travel in-band rather than as binder exceptions so
 * that a failing script is an ordinary source failure the caller can report, not a crash.
 */
@Serializable
data class JsCallResult(
    val ok: Boolean,
    val data: String? = null,
    val error: String? = null,
    val errorKind: JsErrorKind = JsErrorKind.NONE,
    /**
     * Source preferences as they stood when the call ended, when the script changed them.
     *
     * Sources routinely write a preference mid-call — resolving a mirror domain, caching a token
     * — and then read it back on the next call. The sidecar holds no storage of its own, so the
     * mutated map has to ride back across the process boundary for the main process to persist.
     *
     * Null means "unchanged", which is the overwhelmingly common case; sending the map on every
     * call would put a source's stored credentials through a serialize/deserialize round-trip
     * thousands of times for no reason.
     */
    val preferences: Map<String, String>? = null,
)

@Serializable
enum class JsErrorKind {
    NONE,

    /** The script threw. Usually a site layout change rather than a bug in the app. */
    @SerialName("script")
    SCRIPT_ERROR,

    /** The script ran past its wall-clock budget and the sidecar was killed. */
    @SerialName("timeout")
    TIMEOUT,

    /** The sidecar died — crash, OOM kill, or our own kill after a timeout. */
    @SerialName("engine_died")
    ENGINE_DIED,

    /** The requested source was never loaded, or was dropped when the engine restarted. */
    @SerialName("source_missing")
    SOURCE_MISSING,

    /** Malformed JSON from the script. */
    @SerialName("protocol")
    PROTOCOL_ERROR,
}

@Serializable
data class JsMangaListDto(
    val list: List<JsMangaDto> = emptyList(),
    val hasNextPage: Boolean = false,
)

@Serializable
data class JsMangaDto(
    val name: String = "",
    val link: String = "",
    val imageUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genre: List<String> = emptyList(),
    /** Mangayomi status codes; mapped to the app's own enum by the adapter. */
    val status: Int = 0,
)

@Serializable
data class JsChapterDto(
    val name: String = "",
    val url: String = "",
    val dateUpload: String? = null,
    val scanlator: String? = null,
)

@Serializable
data class JsMangaDetailDto(
    val name: String = "",
    val link: String = "",
    val imageUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genre: List<String> = emptyList(),
    val status: Int = 0,
    val chapters: List<JsChapterDto> = emptyList(),
)

/**
 * A page image.
 *
 * [headers] matters more than it looks: many hosts 403 an image request that arrives without a
 * Referer, so a source that supplies none has one synthesised from its baseUrl downstream.
 */
@Serializable
data class JsPageDto(
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
)

// ---------------------------------------------------------------------------------------
// HTTP bridge
// ---------------------------------------------------------------------------------------

@Serializable
data class JsHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

@Serializable
data class JsHttpResponse(
    val ok: Boolean,
    val code: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val error: String? = null,
)

// ---------------------------------------------------------------------------------------
// Pipe transport
// ---------------------------------------------------------------------------------------

/**
 * Hand a payload across the process boundary through a pipe.
 *
 * Binder transactions cap near 1 MB. Page lists, page HTML and novel chapter text all exceed
 * that routinely, and the failure mode is the worst kind: a String return works throughout
 * development and throws TransactionTooLargeException the first time it meets real content.
 *
 * The write happens on its own thread because a pipe buffer is only ~64 KB. Writing inline
 * would block until the reader drained it, and the reader cannot start until this call
 * returns — a deadlock rather than a slow path.
 */
fun writeToPipe(payload: String): android.os.ParcelFileDescriptor {
    val pipe = android.os.ParcelFileDescriptor.createPipe()
    val readSide = pipe[0]
    val writeSide = pipe[1]
    kotlin.concurrent.thread(name = "js-ipc-writer", isDaemon = true) {
        // AutoCloseOutputStream, not a bare FileOutputStream over the raw descriptor: the
        // latter closes the stream but leaves the ParcelFileDescriptor itself open until
        // finalization, so a burst of calls leaks descriptors and trips CloseGuard.
        runCatching {
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use {
                it.write(payload.toByteArray())
            }
        }.onFailure { runCatching { writeSide.close() } }
    }
    return readSide
}

/**
 * Drain a pipe produced by [writeToPipe], closing the descriptor with it.
 *
 * The AIDL contract transfers ownership of the descriptor to the caller, so the caller is the
 * one that has to release it — reading through AutoCloseInputStream makes that automatic
 * rather than something every call site has to remember.
 */
fun android.os.ParcelFileDescriptor.readPayload(): String =
    android.os.ParcelFileDescriptor.AutoCloseInputStream(this).use { it.readBytes() }
        .toString(Charsets.UTF_8)
