package app.otakureader.domain.repository

import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.toSourceId
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing manga sources and fetching manga from them.
 */
interface SourceRepository {

    /**
     * Get all available sources
     */
    fun getSources(): Flow<List<MangaSource>>

    /**
     * Whether the initial source load is still in flight.
     *
     * An empty [getSources] list is a real answer only once this is false: a user with no
     * extensions installed and a user whose extensions have not finished loading both see an
     * empty list, and before this existed the UI showed the same "no sources" state for both.
     *
     * Only the *initial* load is reported. A later manual refresh republishes [getSources] when
     * it lands and does not flip this back to true.
     */
    fun isLoadingSources(): Flow<Boolean>

    /**
     * Get a source by its ID.
     *
     * Suspends until the initial source load has finished (bounded by a timeout), so a caller
     * that arrives during startup gets a real answer rather than a spurious null. See #1258.
     */
    suspend fun getSource(sourceId: String): MangaSource?

    /**
     * Get a source by the `Long` key that manga rows store in `Manga.sourceId`.
     *
     * That key is produced by [app.otakureader.sourceapi.toSourceId], which hashes the source's
     * string id — so it cannot be turned back into an id by stringifying it. The reverse has to
     * be a search over the loaded sources, which is what this does.
     *
     * Use this, never `getSource(manga.sourceId.toString())`.
     *
     * Like [getSource], suspends until the initial source load has finished (bounded by a
     * timeout) so a startup-time caller does not read an empty snapshot. See #1258.
     */
    suspend fun getSourceByKey(key: Long): MangaSource?

    /**
     * Get popular manga from a source
     */
    suspend fun getPopularManga(sourceId: String, page: Int): Result<MangaPage>

    /**
     * Get latest updates from a source
     */
    suspend fun getLatestUpdates(sourceId: String, page: Int): Result<MangaPage>

    /**
     * Search manga in a source
     */
    suspend fun searchManga(sourceId: String, query: String, page: Int): Result<MangaPage>

    /**
     * Search manga in a source with filters
     */
    suspend fun searchManga(
        sourceId: String,
        query: String,
        page: Int,
        filters: FilterList
    ): Result<MangaPage>

    /**
     * Get the available filters for a source
     */
    suspend fun getSourceFilters(sourceId: String): FilterList

    /**
     * Get manga details from a source
     */
    suspend fun getMangaDetails(sourceId: String, manga: SourceManga): Result<SourceManga>

    /**
     * Get chapter list for a manga from a source
     */
    suspend fun getChapterList(sourceId: String, manga: SourceManga): Result<List<SourceChapter>>

    /**
     * Get page list for a chapter from a source
     */
    suspend fun getPageList(sourceId: String, chapter: SourceChapter): Result<List<app.otakureader.sourceapi.Page>>
}

/**
 * Resolves the string id a source call needs from the `Long` key stored on a manga row, or null
 * when no loaded source owns that key (its extension is uninstalled, or it hasn't loaded yet).
 *
 * Every source call takes the string id; every manga row holds the `Long`. This is the only
 * correct bridge between them — see [SourceRepository.getSourceByKey] for why stringifying the
 * key does not work.
 */
suspend fun SourceRepository.resolveSourceId(key: Long): String? = getSourceByKey(key)?.id

/**
 * Indexes [this] by every `Long` key a manga row might hold for it.
 *
 * The rule has to match [SourceRepository.getSourceByKey], including its precedence: legacy
 * parsed keys are written first so the canonical hashed key overwrites them on a collision. A
 * map built from the canonical key alone would leave legacy rows unmatched here while
 * `getSourceByKey` still resolved them — the same source reachable one way and not the other.
 *
 * [id] extracts the source's string id, because callers index different types: `MangaSource`
 * holds it directly, `ExtensionSource` holds the raw Tachiyomi `Long` that has to be stringified
 * first.
 */
fun <T> Iterable<T>.associateBySourceKey(id: (T) -> String): Map<Long, T> {
    val items = this
    return buildMap {
        // Named receiver: inside buildMap a bare `forEach` binds to the map being built, not to
        // the iterable being indexed.
        items.forEach { item -> id(item).toLongOrNull()?.let { key -> put(key, item) } }
        items.forEach { item -> put(id(item).toSourceId(), item) }
    }
}

/**
 * The on-disk folder name used for a manga's downloads: the numeric source key, as a string.
 *
 * Not a [SourceRepository] extension, because it consults no source and pretending otherwise
 * implied a dependency that does not exist. It used to be one — `getSource(sourceId.toString())
 * ?.name` — but that lookup compared a hashed key's decimal against a source's real id and so
 * could never match. Every download on every device is already filed under the number, so making
 * the name resolve now would point every read at a folder that does not exist, orphaning
 * downloaded chapters. Switching to display names is a migration, not an edit; see #1256.
 *
 * Every download enqueue/read/delete call site must resolve through this so they all agree on
 * the same folder — never build a download path from a raw sourceId directly.
 */
fun downloadFolderNameFor(sourceId: Long): String = sourceId.toString()
