package app.komikku.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room entity for a user-created library category. */
@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val order: Int = 0,
    val flags: Long = 0L
)
