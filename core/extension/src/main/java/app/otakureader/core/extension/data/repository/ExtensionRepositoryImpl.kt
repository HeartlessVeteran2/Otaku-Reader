package app.otakureader.core.extension.data.repository

import app.otakureader.core.extension.data.local.ExtensionDao
import app.otakureader.core.extension.data.local.ExtensionMapper
import app.otakureader.core.extension.data.remote.ExtensionRemoteDataSource
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of ExtensionRepository.
 * Coordinates between local database and remote API.
 */
class ExtensionRepositoryImpl(
    private val localDataSource: ExtensionDao,
    private val remoteDataSource: ExtensionRemoteDataSource,
    private val mapper: ExtensionMapper = ExtensionMapper()
) : ExtensionRepository {
    
    override fun getInstalledExtensions(): Flow<List<Extension>> {
        return localDataSource.getInstalledExtensions()
            .map { entities ->
                entities.map { mapper.toDomain(it) }
            }
    }
    
    override fun getAvailableExtensions(): Flow<List<Extension>> {
        return localDataSource.getAvailableExtensions()
            .map { entities ->
                entities.map { mapper.toDomain(it) }
            }
    }
    
    override fun getExtensionsWithUpdates(): Flow<List<Extension>> {
        return localDataSource.getExtensionsWithUpdates()
            .map { entities ->
                entities.map { mapper.toDomain(it) }
            }
    }
    
    override suspend fun getExtension(pkgName: String): Extension? {
        return localDataSource.getExtensionByPkgName(pkgName)?.let {
            mapper.toDomain(it)
        }
    }
    
    override suspend fun getExtensionById(id: Long): Extension? {
        return localDataSource.getExtensionById(id)?.let {
            mapper.toDomain(it)
        }
    }
    
    override suspend fun installExtension(pkgName: String, apkPath: String): Result<Extension> {
        return try {
            // Update status to installing
            localDataSource.updateStatus(pkgName, InstallStatus.INSTALLING.name)
            
            // Get existing or create new extension record
            val existing = localDataSource.getExtensionByPkgName(pkgName)
            val extension = existing?.copy(
                status = InstallStatus.INSTALLED.name,
                apkPath = apkPath,
                installDate = System.currentTimeMillis()
            ) ?: throw IllegalStateException("Extension not found: $pkgName")
            
            localDataSource.insertExtension(extension)
            Result.success(mapper.toDomain(extension))
        } catch (e: Exception) {
            localDataSource.updateStatus(pkgName, InstallStatus.ERROR.name)
            Result.failure(e)
        }
    }
    
    override suspend fun uninstallExtension(pkgName: String): Result<Unit> {
        return try {
            localDataSource.updateStatus(pkgName, InstallStatus.UNINSTALLING.name)
            localDataSource.deleteByPkgName(pkgName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateExtension(pkgName: String, apkPath: String): Result<Extension> {
        return try {
            localDataSource.updateStatus(pkgName, InstallStatus.UPDATING.name)
            
            val existing = localDataSource.getExtensionByPkgName(pkgName)
                ?: throw IllegalStateException("Extension not found: $pkgName")
            
            val updated = existing.copy(
                status = InstallStatus.INSTALLED.name,
                apkPath = apkPath,
                versionCode = existing.remoteVersionCode ?: existing.versionCode
            )
            
            localDataSource.insertExtension(updated)
            Result.success(mapper.toDomain(updated))
        } catch (e: Exception) {
            localDataSource.updateStatus(pkgName, InstallStatus.ERROR.name)
            Result.failure(e)
        }
    }
    
    override suspend fun checkForUpdates(): Int {
        return try {
            val remoteResult = remoteDataSource.fetchAvailableExtensions()
            if (remoteResult.isFailure) return 0
            
            val remoteExtensions = remoteResult.getOrThrow()
            val installed = localDataSource.getInstalledExtensions().first()
            
            var updateCount = 0
            
            installed.forEach { localExt ->
                val remoteExt = remoteExtensions.find { it.pkgName == localExt.pkgName }
                if (remoteExt != null && remoteExt.versionCode > localExt.versionCode) {
                    localDataSource.updateRemoteVersion(localExt.pkgName, remoteExt.versionCode)
                    localDataSource.updateStatus(localExt.pkgName, InstallStatus.HAS_UPDATE.name)
                    updateCount++
                }
            }
            
            updateCount
        } catch (e: Exception) {
            0
        }
    }
    
    override suspend fun setExtensionStatus(pkgName: String, status: InstallStatus) {
        localDataSource.updateStatus(pkgName, status.name)
    }

    override suspend fun setExtensionEnabled(pkgName: String, enabled: Boolean) {
        localDataSource.updateEnabled(pkgName, enabled)
    }
    
    override suspend fun refreshAvailableExtensions(): Result<Unit> {
        return try {
            val remoteResult = remoteDataSource.fetchAvailableExtensions()
            if (remoteResult.isFailure) {
                return Result.failure(remoteResult.exceptionOrNull()!!)
            }
            
            val remoteExtensions = remoteResult.getOrThrow()
            val installed = localDataSource.getInstalledExtensions().first()
            val installedPkgNames = installed.map { it.pkgName }.toSet()
            
            // Filter out already installed extensions, mark as AVAILABLE
            val availableExtensions = remoteExtensions
                .filter { it.pkgName !in installedPkgNames }
                .map { 
                    mapper.toEntity(it.copy(status = InstallStatus.AVAILABLE))
                }
            
            // Clear old available extensions and insert new ones
            localDataSource.insertExtensions(availableExtensions)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun searchExtensions(query: String): Flow<List<Extension>> {
        return localDataSource.searchExtensions(query)
            .map { entities ->
                entities.map { mapper.toDomain(it) }
            }
    }
}
