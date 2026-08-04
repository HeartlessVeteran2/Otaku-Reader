package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.otakureader.core.database.entity.MangaMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaMetadataDao {

    /**
     * The cached metadata for a manga, or null when none has been fetched.
     *
     * A `Flow` per the repo convention, and it earns it here: the details screen subscribes before
     * the fetch has happened, so the first emission is null and the second is the metadata arriving
     * — no separate "now reload" signal needed.
     */
    @Query("SELECT * FROM manga_metadata WHERE mangaId = :mangaId")
    fun observeByMangaId(mangaId: Long): Flow<MangaMetadataEntity?>

    /** A one-shot read, for deciding whether the cached copy is still fresh enough to keep. */
    @Query("SELECT * FROM manga_metadata WHERE mangaId = :mangaId")
    suspend fun getByMangaId(mangaId: Long): MangaMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: MangaMetadataEntity)

    @Query("DELETE FROM manga_metadata WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: Long)

    /**
     * Drop everything older than [cutoff] (epoch millis).
     *
     * The TTL alone only prevents *stale reads*; it never reclaims space, because an entry for a
     * manga nobody opens again is never re-examined. This is the sweep that does, for a
     * maintenance worker to call.
     */
    @Query("DELETE FROM manga_metadata WHERE fetchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
