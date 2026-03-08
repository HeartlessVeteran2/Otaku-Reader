package app.otakureader.core.extension.data.local

import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mapper between Extension domain model and ExtensionEntity database model.
 */
class ExtensionMapper {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    
    fun toDomain(entity: ExtensionEntity): Extension {
        return Extension(
            id = entity.id,
            pkgName = entity.pkgName,
            name = entity.name,
            versionCode = entity.versionCode,
            versionName = entity.versionName,
            sources = parseSources(entity.sourcesJson),
            status = InstallStatus.valueOf(entity.status),
            apkPath = entity.apkPath,
            apkUrl = entity.apkUrl,
            iconUrl = entity.iconUrl,
            lang = entity.lang,
            isNsfw = entity.isNsfw,
            installDate = entity.installDate,
            signatureHash = entity.signatureHash,
            isEnabled = entity.isEnabled
        )
    }
    
    fun toEntity(domain: Extension): ExtensionEntity {
        return ExtensionEntity(
            id = domain.id,
            pkgName = domain.pkgName,
            name = domain.name,
            versionCode = domain.versionCode,
            versionName = domain.versionName,
            sourcesJson = serializeSources(domain.sources),
            status = domain.status.name,
            apkPath = domain.apkPath,
            apkUrl = domain.apkUrl,
            iconUrl = domain.iconUrl,
            lang = domain.lang,
            isNsfw = domain.isNsfw,
            installDate = domain.installDate,
            signatureHash = domain.signatureHash,
            remoteVersionCode = if (domain.hasUpdate) domain.versionCode else null,
            isEnabled = domain.isEnabled
        )
    }
    
    private fun parseSources(jsonString: String): List<ExtensionSource> {
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun serializeSources(sources: List<ExtensionSource>): String {
        return try {
            json.encodeToString(sources)
        } catch (e: Exception) {
            "[]"
        }
    }
}
