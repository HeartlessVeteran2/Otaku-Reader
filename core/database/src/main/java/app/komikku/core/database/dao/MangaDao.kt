package app.komikku.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.komikku.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {

    @Query("SELECT * FROM manga WHERE isFavorite = 1 ORDER BY title ASC")
    fun getLibraryManga(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id")
    fun getMangaById(id: Long): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url")
    suspend fun getMangaBySource(sourceId: String, url: String): MangaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManga(manga: MangaEntity): Long

    @Update
    suspend fun updateManga(manga: MangaEntity)

    @Query("UPDATE manga SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteManga(manga: MangaEntity)

    @Query("DELETE FROM manga WHERE isFavorite = 0")
    suspend fun deleteNonFavorites()
}
