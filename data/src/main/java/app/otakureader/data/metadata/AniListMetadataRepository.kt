package app.otakureader.data.metadata

import app.otakureader.core.database.dao.MangaMetadataDao
import app.otakureader.core.database.entity.MangaMetadataEntity
import app.otakureader.core.common.network.browsableHostOrNull
import app.otakureader.core.common.network.isBrowsableHttpUrl
import app.otakureader.core.database.entity.StoredExternalLink
import app.otakureader.core.database.entity.StoredPerson
import app.otakureader.core.database.entity.StoredRelation
import app.otakureader.domain.model.MangaMetadata
import app.otakureader.domain.model.MangaMetadataExternalLink
import app.otakureader.domain.model.MangaMetadataPerson
import app.otakureader.domain.model.MangaMetadataRelation
import app.otakureader.domain.model.MangaMetadataTag
import app.otakureader.domain.repository.MangaMetadataRepository
import app.otakureader.domain.util.PlaceholderTitles
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fetches manga metadata from AniList and caches it in Room.
 *
 * ### The cache is the source of truth for reads
 *
 * [observeMetadata] only ever reads Room, so the details screen renders instantly and works
 * offline. [refreshMetadata] is the only path that touches the network, and it writes to Room —
 * which the flow is already watching, so a successful refresh reaches the screen without any
 * explicit "reload" hop.
 *
 * ### A failed refresh never disturbs the cache
 *
 * Nothing is deleted before a fetch, and nothing is written unless the fetch produced a whole
 * record. Losing yesterday's metadata because today's request timed out would be a strictly worse
 * outcome than showing yesterday's — this is a cache of facts that change slowly, not a live feed.
 */
@Singleton
class AniListMetadataRepository @Inject constructor(
    private val api: AniListMetadataApi,
    private val dao: MangaMetadataDao,
) : MangaMetadataRepository {

    override fun observeMetadata(mangaId: Long): Flow<MangaMetadata?> =
        dao.observeByMangaId(mangaId).map { it?.toDomain() }

    override suspend fun refreshMetadata(
        mangaId: Long,
        anilistId: Long,
        force: Boolean,
    ): Result<MangaMetadata> = lockFor(mangaId).withLock {
        try {
            val cached = dao.getByMangaId(mangaId)
            // A cached copy is reused when it is fresh *and* describes the media being asked for.
            // The second half matters: correcting a wrong match changes the anilistId while the
            // mangaId stays put, and a TTL check alone would keep serving the old manga's metadata
            // until it expired — the user's correction appearing to do nothing.
            if (!force && cached != null && cached.anilistId == anilistId && cached.isFresh()) {
                return@withLock Result.success(cached.toDomain())
            }

            val response = api.fetch(anilistId)
            // Errors before data, matching the tracker: GraphQL answers a rejected document with
            // HTTP 200, and can return `data` and `errors` together when one resolver fails.
            response.errors.firstOrNull()?.let {
                return@withLock Result.failure(AniListMetadataException(it.message))
            }
            val media = response.data?.media
                ?: return@withLock Result.failure(
                    AniListMetadataException("AniList returned no media for id $anilistId")
                )

            val entity = media.toEntity(mangaId = mangaId, fetchedAt = System.currentTimeMillis())
            dao.upsert(entity)
            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Covers the Room read as well as the fetch. A database failure during the cache
            // lookup used to escape this method entirely, so a caller that had been promised a
            // Result got an exception instead. The cache is deliberately left alone either way.
            Result.failure(e)
        }
    }

    /**
     * Serializes refreshes for a given manga.
     *
     * Read-decide-write across a network call is the shape that needs it: two refreshes for the
     * same manga with different `anilistId`s — which is exactly what correcting a wrong match
     * produces — would otherwise race, and the *slower* one wins because it writes last. The
     * anilistId-aware freshness check cannot help, since each call passes its own check before
     * either writes.
     *
     * Not one global lock, because the fetch happens inside it and the rate limiter can hold an
     * AniList response for up to 90 seconds (#1236) — that would queue every other manga behind
     * one slow request.
     *
     * **Striped rather than per-id.** A `ConcurrentHashMap<Long, Mutex>` was the obvious shape and
     * it leaks by design: an entry is added for every manga ever refreshed and never removed, so
     * the map tracks session history rather than the library. Evicting is worse than it sounds —
     * removing after unlocking races the next caller that already read the same instance, and a
     * bounded cache can evict a lock while it is held. Reference counting fixes that and is a
     * well-known source of subtle bugs.
     *
     * A fixed array sidesteps all of it. The same `mangaId` always maps to the same stripe, so
     * serialization is exact where it matters; two *different* manga can collide and serialize
     * unnecessarily, which costs nothing but a little parallelism and cannot cause a wrong result.
     * Memory is [STRIPE_COUNT] mutexes, forever, whatever the user does to their library.
     */
    private fun lockFor(mangaId: Long): Mutex = locks[Math.floorMod(mangaId, STRIPE_COUNT)]

    /** `floorMod`, not `%`: a negative id would otherwise index out of bounds. */
    private val locks = Array(STRIPE_COUNT) { Mutex() }

    /**
     * Fresh means the age is within the TTL **and not negative**.
     *
     * A row stamped in the future — a clock correction, a restored backup, a device whose time was
     * wrong when it was written — would otherwise read as fresh until the wall clock caught up,
     * which for a badly wrong clock is indefinitely. Treating a future timestamp as stale costs
     * one refetch; treating it as fresh can pin stale metadata forever.
     */
    private fun MangaMetadataEntity.isFresh(): Boolean =
        (System.currentTimeMillis() - fetchedAt) in 0..TTL_MS

    companion object {
        /**
         * How long a cached record is served without re-fetching.
         *
         * A week, because almost nothing here changes faster: description, tags, format and
         * country are effectively fixed, and the ones that do move — `chapters`, `averageScore`,
         * `popularity` — move slowly enough that a week-old value is not misleading. The cost of
         * being wrong is a slightly stale score; the cost of a shorter TTL is a request per manga
         * per visit against a rate limit this app has to wait out.
         */
        const val TTL_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * How many refresh locks exist, total.
         *
         * Comfortably above the concurrency this can actually reach — OkHttp's default dispatcher
         * allows 5 requests per host, and the library sync loop is sequential — so a collision
         * between two different manga is rare, and when it happens it only costs the parallelism
         * of one extra request.
         */
        const val STRIPE_COUNT = 64
    }
}

/** AniList refused the metadata query, or had nothing to return for it. */
class AniListMetadataException(message: String) : Exception(message)

private fun MetadataMedia.toEntity(mangaId: Long, fetchedAt: Long): MangaMetadataEntity {
    // Spoiler tags are dropped here, at the boundary, so nothing downstream has to remember to
    // check — a flag on the model would eventually be missed by one call site, and the failure
    // mode is spoiling a plot twist on the details screen.
    val visibleTags = tags.filterNot { it.isMediaSpoiler || it.isGeneralSpoiler }
    return MangaMetadataEntity(
        mangaId = mangaId,
        anilistId = id,
        description = description?.takeIf { it.isNotBlank() },
        bannerImage = bannerImage,
        coverImage = coverImage?.extraLarge ?: coverImage?.large,
        coverColor = coverImage?.color,
        genres = genres,
        tagNames = visibleTags.map { it.name },
        tagRanks = visibleTags.map { it.rank.toString() },
        averageScore = averageScore,
        popularity = popularity,
        favourites = favourites,
        format = format,
        countryOfOrigin = countryOfOrigin,
        status = status,
        chapters = chapters,
        startDate = startDate?.toIsoOrNull(),
        endDate = endDate?.toIsoOrNull(),
        synonyms = buildSynonyms(),
        characters = characters?.edges.orEmpty().toStoredPeople(),
        staff = staff?.edges.orEmpty().toStoredPeople(),
        relations = relations?.edges.orEmpty().toStoredRelations(),
        externalLinks = externalLinks.toStoredExternalLinks(),
        fetchedAt = fetchedAt,
    )
}

/**
 * Drops edges with no usable person and keeps the order AniList sorted them into.
 *
 * A nameless entry is the one that has to go: the carousel renders a name under each face, and
 * AniList occasionally returns an edge whose node is null or whose `name.full` is blank. Kept, it
 * would be an anonymous tile the user cannot act on. A missing *image* is fine and common — that
 * renders as a placeholder — so it is not a reason to drop the person.
 *
 * The list is not re-sorted. `characters` is requested `sort: [ROLE, RELEVANCE]`, which puts the
 * main cast first, and re-sorting locally would either duplicate that rule or quietly contradict
 * it.
 *
 * ### `distinct()`, not `distinctBy { it.id }`
 *
 * One person can legitimately appear twice in a staff connection — AniList returns one edge per
 * credit, so someone responsible for both Story and Art is two edges sharing an id, and both are
 * real credits the reader should see. Deduplicating on id alone would silently drop the second.
 * `distinct()` compares the whole record, so it collapses only edges that are identical in every
 * field, which carry no information the first copy does not.
 */
private fun List<MetadataPersonEdge>.toStoredPeople(): List<StoredPerson> = mapNotNull { edge ->
    val node = edge.node ?: return@mapNotNull null
    val name = node.name?.full?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    StoredPerson(
        id = node.id,
        name = name,
        imageUrl = node.image?.large,
        role = edge.role?.trim()?.takeIf { it.isNotEmpty() },
    )
}.distinct()

/**
 * Every name this manga is known by, de-duplicated and stripped of placeholders.
 *
 * Sources emit `"?"` and `"??"` for a missing localized title, and AniList's `synonyms` carries
 * whatever users have submitted. Left in, they would be offered to the user as alternative titles
 * and — more damagingly — fed to the matcher as evidence, where two placeholders match each other
 * perfectly.
 */
private fun MetadataMedia.buildSynonyms(): List<String> =
    (listOfNotNull(title?.english, title?.romaji, title?.native, title?.userPreferred) + synonyms)
        .map { it.trim() }
        .filter { PlaceholderTitles.isMeaningful(it) }
        .distinct()

/**
 * AniList's split date as `YYYY-MM-DD`, or null when it has no year.
 *
 * Partial dates are normal — an ongoing series often has a start year with no month — so the month
 * and day are padded only when present rather than defaulting to 01, which would invent a
 * precision AniList never claimed.
 */
private fun MetadataDate.toIsoOrNull(): String? {
    val year = year ?: return null
    val month = month?.toString()?.padStart(2, '0')
    val day = day?.toString()?.padStart(2, '0')
    return listOfNotNull(year.toString(), month, day).joinToString("-")
}

private fun MangaMetadataEntity.toDomain() = MangaMetadata(
    mangaId = mangaId,
    anilistId = anilistId,
    description = description,
    bannerImage = bannerImage,
    coverImage = coverImage,
    coverColor = coverColor,
    genres = genres,
    // `zip` truncates to the shorter list, which is the safe behaviour if the two columns ever
    // disagree in length: a tag with no rank is dropped rather than defaulting to a rank it never
    // had, and a rank with no name cannot become a nameless chip.
    tags = tagNames.zip(tagRanks).mapNotNull { (name, rank) ->
        rank.toIntOrNull()?.let { MangaMetadataTag(name, it) }
    },
    averageScore = averageScore,
    popularity = popularity,
    favourites = favourites,
    format = format,
    countryOfOrigin = countryOfOrigin,
    status = status,
    chapters = chapters,
    startDate = startDate,
    endDate = endDate,
    synonyms = synonyms,
    characters = characters.map { it.toDomain() },
    staff = staff.map { it.toDomain() },
    relations = relations.map { it.toDomain() },
    externalLinks = externalLinks.map { it.toDomain() },
    fetchedAt = fetchedAt,
)

private fun StoredPerson.toDomain() = MangaMetadataPerson(
    id = id,
    name = name,
    imageUrl = imageUrl,
    role = role,
)

/**
 * Related *manga*, dropping the anime AniList mixes in.
 *
 * A manga's relations routinely include its anime adaptation. This app has no anime surface at
 * all, so such a tile could only sit there doing nothing when tapped — and every relation here is
 * tappable, because the point of showing a sequel is being able to go and find it. Filtering at
 * the boundary means the UI never has to ask whether a given tile is actionable.
 *
 * A relation with no title goes too, for the same reason a nameless person does: the tile is the
 * title, and there would be nothing to render or search for — but only after trying every title
 * AniList offers. See [firstMeaningfulTitle].
 */
private fun List<MetadataRelationEdge>.toStoredRelations(): List<StoredRelation> = mapNotNull { edge ->
    val node = edge.node ?: return@mapNotNull null
    if (!node.type.equals("MANGA", ignoreCase = true)) return@mapNotNull null
    val title = node.title.firstMeaningfulTitle() ?: return@mapNotNull null
    StoredRelation(
        anilistId = node.id,
        title = title,
        coverImage = node.coverImage?.extraLarge ?: node.coverImage?.large,
        format = node.format,
        relationType = edge.relationType?.trim()?.takeIf { it.isNotEmpty() },
    )
}.distinct()

/**
 * Off-site links, keeping only the ones that could actually be opened.
 *
 * The scheme is checked *here* as well as at the tap site, and that is two deliberate call sites
 * of one shared predicate: this decides what is worth caching, while the tap site decides what is
 * safe to hand to an Intent. Storing a `javascript:` URL and refusing it later would leave a chip
 * that exists only to reject the user; storing nothing means the chip never appears. The tap-site
 * check still has to exist, because a row cached by an older build predates this filter.
 *
 * A link with no site name falls back to its host, so a nameless entry is still a usable chip
 * rather than a discarded one — AniList leaves `site` blank more often than it leaves `url` blank.
 */
private fun List<MetadataExternalLink>.toStoredExternalLinks(): List<StoredExternalLink> =
    mapNotNull { link ->
        val url = link.url?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        if (!url.isBrowsableHttpUrl()) return@mapNotNull null
        StoredExternalLink(
            url = url,
            site = link.site?.trim()?.takeIf { it.isNotEmpty() } ?: url.browsableHostOrNull() ?: url,
        )
    }.distinct()


private fun StoredRelation.toDomain() = MangaMetadataRelation(
    anilistId = anilistId,
    title = title,
    coverImage = coverImage,
    format = format,
    relationType = relationType,
)

private fun StoredExternalLink.toDomain() = MangaMetadataExternalLink(url = url, site = site)

/**
 * The first title AniList gives that actually says something.
 *
 * `userPreferred` first, because that is the whole point of the field — it already encodes the
 * viewer's title-language setting, defaulting to romaji when unauthenticated. But it is *derived*,
 * so an entry whose romaji is missing can leave it null while `english` or `native` are perfectly
 * good, and keying on it alone would drop a real relation from the carousel with no trace.
 *
 * Placeholders are filtered with the same rule the synonym set uses: sources and AniList both emit
 * `"?"` and `"N/A"` for a missing title, and a tile labelled `?` is no more useful than no tile —
 * worse, it is tappable, and searching for `?` finds nothing.
 */
private fun MetadataTitle?.firstMeaningfulTitle(): String? =
    listOfNotNull(this?.userPreferred, this?.romaji, this?.english, this?.native)
        .map { it.trim() }
        .firstOrNull { PlaceholderTitles.isMeaningful(it) }
