package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.otakureader.core.database.entity.MangaAniListLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaAniListLinkDao {

    /**
     * The stored link for a manga, or null when nothing has matched it yet.
     *
     * A `Flow` per the repo convention, and it earns it: the details screen subscribes before
     * auto-matching has run, so the first emission is null and the second is the link arriving.
     */
    @Query("SELECT * FROM manga_anilist_link WHERE mangaId = :mangaId")
    fun observeByMangaId(mangaId: Long): Flow<MangaAniListLinkEntity?>

    /** A one-shot read, for deciding whether auto-matching needs to run at all. */
    @Query("SELECT * FROM manga_anilist_link WHERE mangaId = :mangaId")
    suspend fun getByMangaId(mangaId: Long): MangaAniListLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: MangaAniListLinkEntity)

    @Query("DELETE FROM manga_anilist_link WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: Long)
}
