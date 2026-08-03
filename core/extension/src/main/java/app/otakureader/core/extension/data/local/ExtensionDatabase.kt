package app.otakureader.core.extension.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Room database entities and DAOs for storing extension metadata.
 */

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey
    val id: Long,
    
    @ColumnInfo(name = "pkg_name")
    val pkgName: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "version_code")
    val versionCode: Int,
    
    @ColumnInfo(name = "version_name")
    val versionName: String,
    
    @ColumnInfo(name = "sources_json")
    val sourcesJson: String, // JSON array of ExtensionSource
    
    @ColumnInfo(name = "status")
    val status: String, // InstallStatus.name
    
    @ColumnInfo(name = "apk_path")
    val apkPath: String?,

    @ColumnInfo(name = "apk_url")
    val apkUrl: String?,

    @ColumnInfo(name = "icon_url")
    val iconUrl: String?,
    
    @ColumnInfo(name = "lang")
    val lang: String,
    
    @ColumnInfo(name = "is_nsfw")
    val isNsfw: Boolean,
    
    @ColumnInfo(name = "install_date")
    val installDate: Long?,
    
    @ColumnInfo(name = "signature_hash")
    val signatureHash: String?,
    
    @ColumnInfo(name = "remote_version_code")
    val remoteVersionCode: Int?, // For tracking available updates

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    /**
     * The repository URL this extension was first installed from (#1019).
     * Recorded at install time and kept sticky across updates so a different
     * repository offering the same package name cannot silently replace the
     * extension via the update path. Null for rows created before this column
     * existed; backfilled on the next update check.
     */
    @ColumnInfo(name = "source_repo_url")
    val sourceRepoUrl: String? = null,

    /**
     * Whether this row is a JavaScript source rather than an APK extension.
     *
     * Persisted rather than derived, because uninstall reads the row back to decide which
     * backend to call. See `Extension.isJavaScript` for why a guess is not good enough.
     */
    // defaultValue must match the migration's `DEFAULT 0` exactly. Room compares the entity's
    // declared schema against the live database after a migration, and a column default present
    // in one and absent from the other is a mismatch — which surfaces as a crash on upgrade for
    // existing users only, never in a fresh install or a test that starts at the current version.
    @ColumnInfo(name = "is_javascript", defaultValue = "0")
    val isJavaScript: Boolean = false
)

@Dao
interface ExtensionDao {
    
    @Query("SELECT * FROM extensions WHERE status != 'AVAILABLE' ORDER BY name ASC")
    fun getInstalledExtensions(): Flow<List<ExtensionEntity>>
    
    @Query("SELECT * FROM extensions WHERE status = 'AVAILABLE' ORDER BY name ASC")
    fun getAvailableExtensions(): Flow<List<ExtensionEntity>>
    
    @Query("SELECT * FROM extensions WHERE status = 'HAS_UPDATE'")
    fun getExtensionsWithUpdates(): Flow<List<ExtensionEntity>>
    
    @Query("SELECT * FROM extensions WHERE pkg_name = :pkgName LIMIT 1")
    suspend fun getExtensionByPkgName(pkgName: String): ExtensionEntity?
    
    @Query("SELECT * FROM extensions WHERE id = :id LIMIT 1")
    suspend fun getExtensionById(id: Long): ExtensionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(entity: ExtensionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtensions(entities: List<ExtensionEntity>)
    
    @Delete
    suspend fun deleteExtension(entity: ExtensionEntity)
    
    @Query("DELETE FROM extensions WHERE pkg_name = :pkgName")
    suspend fun deleteByPkgName(pkgName: String)
    
    @Query("UPDATE extensions SET status = :status WHERE pkg_name = :pkgName")
    suspend fun updateStatus(pkgName: String, status: String)

    @Query("UPDATE extensions SET remote_version_code = :remoteVersion WHERE pkg_name = :pkgName")
    suspend fun updateRemoteVersion(pkgName: String, remoteVersion: Int)

    /** Backfills first-seen repo provenance for rows created before the column existed (#1019). */
    @Query("UPDATE extensions SET source_repo_url = :repoUrl WHERE pkg_name = :pkgName AND source_repo_url IS NULL")
    suspend fun backfillSourceRepoUrl(pkgName: String, repoUrl: String)

    @Query("UPDATE extensions SET is_enabled = :enabled WHERE pkg_name = :pkgName")
    suspend fun updateEnabled(pkgName: String, enabled: Boolean)

    @Query("UPDATE extensions SET signature_hash = :hash WHERE pkg_name = :pkgName")
    suspend fun updateSignatureHash(pkgName: String, hash: String)
    
    @Query("SELECT * FROM extensions WHERE name LIKE '%' || :query || '%' OR pkg_name LIKE '%' || :query || '%'")
    fun searchExtensions(query: String): Flow<List<ExtensionEntity>>

    @Query("DELETE FROM extensions")
    suspend fun clearAll()

    /**
     * Clears cached non-installed rows before a refresh. ERROR rows (failed installs)
     * are included: they represent nothing installed on disk, and clearing them lets
     * the fresh AVAILABLE row make the extension reinstallable again.
     */
    @Query("DELETE FROM extensions WHERE status IN ('AVAILABLE', 'ERROR')")
    suspend fun deleteAllAvailable()

    /**
     * Atomically replaces the cached AVAILABLE extension list with a fresh fetch.
     * Without the delete step, extensions from removed/changed repositories linger
     * in the database forever and keep showing in the Available tab.
     */
    @Transaction
    suspend fun replaceAllAvailable(entities: List<ExtensionEntity>) {
        deleteAllAvailable()
        insertExtensions(entities)
    }
}

@Database(
    entities = [ExtensionEntity::class],
    version = 5,
    exportSchema = false
)
abstract class ExtensionDatabase : RoomDatabase() {
    abstract fun extensionDao(): ExtensionDao
}
