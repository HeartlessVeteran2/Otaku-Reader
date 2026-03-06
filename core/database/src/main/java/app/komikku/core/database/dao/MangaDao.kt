package app.komikku.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.komikku.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {

    @Query("SELECT * FROM manga WHERE favorite = 1 ORDER BY title ASC")
    fun observeLibrary(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE favorite = 1 AND title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchLibrary(query: String): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id")
    fun observeManga(id: Long): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE source_id = :sourceId AND url = :url LIMIT 1")
    suspend fun getMangaBySourceAndUrl(sourceId: String, url: String): MangaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(manga: MangaEntity): Long

    @Query("UPDATE manga SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM manga WHERE id = :id LIMIT 1")
    suspend fun getManga(id: Long): MangaEntity?
}
