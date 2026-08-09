package app.otakureader.data.repository

import app.otakureader.core.database.dao.MangaDao
import app.otakureader.core.database.dao.RecommendationDao
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.RecommendationEntity
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.Recommendation
import app.otakureader.domain.repository.RecommendationRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.resolveSourceId
import app.otakureader.sourceapi.toSourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class RecommendationRepositoryImpl @Inject constructor(
    private val recommendationDao: RecommendationDao,
    private val mangaDao: MangaDao,
    private val sourceRepository: SourceRepository,
) : RecommendationRepository {

    override fun getRecommendations(): Flow<List<Recommendation>> =
        recommendationDao.getRecommendations().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun refreshRecommendations(libraryManga: List<Manga>) {
        if (libraryManga.size < MIN_LIBRARY_SIZE) return

        val profile = buildGenreProfile(libraryManga)
        if (profile.isEmpty()) return

        // Seed the candidate pool with popular manga from the user's own sources so
        // recommendations work even before the user has manually browsed anything.
        seedCandidatesFromSources(libraryManga)

        val libraryIds = libraryManga.mapTo(mutableSetOf()) { it.id }
        val now = System.currentTimeMillis()

        val scored = mangaDao.getRecommendationCandidates(limit = CANDIDATE_POOL_LIMIT)
            .asSequence()
            .filter { it.id !in libraryIds }
            .mapNotNull { entity ->
                val genres = entity.genre?.split('|', ',', ';')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: return@mapNotNull null
                if (genres.isEmpty()) return@mapNotNull null
                val genreVector = genres.associateWith { 1f }
                val score = cosineSimilarity(profile, genreVector)
                if (score <= 0f) return@mapNotNull null
                RecommendationEntity(
                    mangaId = entity.id,
                    title = entity.title,
                    thumbnailUrl = entity.thumbnailUrl,
                    sourceId = entity.sourceId,
                    genresJson = Json.encodeToString(genres),
                    score = score,
                    lastComputed = now,
                )
            }
            .sortedByDescending { it.score }
            .take(MAX_RECOMMENDATIONS)
            .toList()

        recommendationDao.deleteAll()
        if (scored.isNotEmpty()) recommendationDao.upsertAll(scored)
    }

    override suspend fun dismissRecommendation(mangaId: Long) {
        recommendationDao.deleteById(mangaId)
    }

    private suspend fun seedCandidatesFromSources(libraryManga: List<Manga>) {
        // Seed from the sources the user's own library came from. These are the stored `Long`
        // keys, so each has to be resolved back to a real source id — stringifying the key
        // produced a hash's decimal that matched nothing, and `toLongOrNull` on it then discarded
        // whatever came back, so this loop never seeded a single candidate.
        //
        // Resolved once per distinct key rather than once per manga: a large library holds many
        // rows but few sources.
        val resolvedByKey = libraryManga.map { it.sourceId }.distinct()
            .mapNotNull { key -> sourceRepository.resolveSourceId(key)?.let { key to it } }
            .toMap()

        // What the user already owns, grouped by the *resolved* source rather than by the raw
        // key. That indirection is the point: a candidate row is written under the canonical key,
        // so a legacy library row holding the parsed key would not collide with it on the
        // (sourceId, url) unique index, and `insertIfNotExists` would happily add a second,
        // non-favourite copy of a manga the user already has — which then gets recommended back
        // to them. Comparing through the resolved source recognises the row either way.
        val ownedUrls = mutableMapOf<String, MutableSet<String>>()
        libraryManga.forEach { manga ->
            val sourceIdStr = resolvedByKey[manga.sourceId] ?: return@forEach
            ownedUrls.getOrPut(sourceIdStr) { mutableSetOf() }.add(manga.url)
        }

        // The cap is applied *after* resolving, not before: a key whose extension is uninstalled
        // can never be seeded from, and letting it consume one of the slots would mean a library
        // whose first few sources are gone seeds nothing while installed sources further down
        // the list are never reached.
        val seedSources = resolvedByKey.values.distinct().take(MAX_SOURCES_TO_SEED)
        val now = System.currentTimeMillis()
        val toInsert = mutableListOf<MangaEntity>()
        for (sourceIdStr in seedSources) {
            // The canonical key, not whichever one the library row happened to hold: every new
            // row this app writes uses the canonical convention, and seeding a legacy key would
            // spread it to manga that never had it.
            val sourceId = sourceIdStr.toSourceId()
            val owned = ownedUrls[sourceIdStr].orEmpty()
            sourceRepository.getPopularManga(sourceIdStr, page = 1)
                .getOrNull()
                ?.mangas
                ?.filter { !it.genre.isNullOrBlank() && it.url !in owned }
                ?.mapTo(toInsert) { sm ->
                    MangaEntity(
                        id = 0,
                        sourceId = sourceId,
                        url = sm.url,
                        title = sm.title,
                        thumbnailUrl = sm.thumbnailUrl,
                        author = sm.author,
                        genre = sm.genre,
                        favorite = false,
                        dateAdded = now,
                    )
                }
        }
        if (toInsert.isNotEmpty()) mangaDao.insertIfNotExists(toInsert)
    }

    private fun buildGenreProfile(manga: List<Manga>): Map<String, Float> {
        val profile = mutableMapOf<String, Float>()
        manga.forEach { m ->
            m.genre.forEach { genre -> profile[genre] = (profile[genre] ?: 0f) + 1f }
        }
        val magnitude = sqrt(profile.values.sumOf { (it * it).toDouble() }.toFloat())
        return if (magnitude == 0f) emptyMap()
        else profile.mapValues { it.value / magnitude }
    }

    private fun cosineSimilarity(a: Map<String, Float>, b: Map<String, Float>): Float {
        var dot = 0f
        var magB = 0f
        b.forEach { (genre, weight) ->
            dot += (a[genre] ?: 0f) * weight
            magB += weight * weight
        }
        val denominator = sqrt(magB)
        return if (denominator == 0f) 0f else dot / denominator
    }

    private fun RecommendationEntity.toDomain() = Recommendation(
        mangaId = mangaId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        sourceId = sourceId,
        score = score,
    )

    companion object {
        private const val MIN_LIBRARY_SIZE = 5
        private const val MAX_RECOMMENDATIONS = 20
        private const val CANDIDATE_POOL_LIMIT = 500
        private const val MAX_SOURCES_TO_SEED = 5
    }
}
