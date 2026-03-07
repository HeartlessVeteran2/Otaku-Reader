package app.otakureader.core.extension.loader

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.ExtensionSource
import app.otakureader.core.extension.domain.model.InstallStatus
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Interface for sources provided by extensions.
 * The actual Source interface is defined in the source-api module.
 * This is a minimal representation for loading purposes.
 */
interface Source {
    val id: Long
    val name: String
    val lang: String
}

/**
 * Result of loading an extension.
 */
sealed class ExtensionLoadResult {
    data class Success(
        val extension: Extension,
        val sources: List<Source>
    ) : ExtensionLoadResult()
    
    data class Error(val message: String, val throwable: Throwable? = null) : ExtensionLoadResult()
}

/**
 * Loads APK extensions dynamically using DexClassLoader.
 * Extracts Source classes from the extension's APK.
 */
class ExtensionLoader(
    private val context: Context
) {
    
    companion object {
        private const val EXTENSION_FEATURE = "otaku.extension"
        private const val METADATA_SOURCE_CLASS = "otaku.extension.source"
        private const val DEX_OUTPUT_DIR = "extension_dex"
    }
    
    private val packageManager: PackageManager = context.packageManager
    
    /**
     * Load an extension from its APK file.
     * @param apkPath Path to the extension APK
     * @return ExtensionLoadResult containing the loaded extension info and sources
     */
    fun loadExtension(apkPath: String): ExtensionLoadResult {
        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                return ExtensionLoadResult.Error("APK file not found: $apkPath")
            }
            
            // Get package info from APK
            val packageInfo = packageManager.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES
            ) ?: return ExtensionLoadResult.Error("Failed to parse package info from APK")
            
            // Verify this is an Otaku extension
            if (!isValidExtension(packageInfo)) {
                return ExtensionLoadResult.Error("Not a valid Otaku extension")
            }
            
            // Create DexClassLoader for dynamic loading
            val classLoader = createClassLoader(apkPath)
            
            // Extract source class names from metadata
            val sourceClasses = getSourceClassNames(packageInfo)
            
            // Load source instances
            val sources = sourceClasses.mapNotNull { className ->
                loadSource(classLoader, className)
            }
            
            if (sources.isEmpty()) {
                return ExtensionLoadResult.Error("No valid sources found in extension")
            }
            
            // Build extension model
            val extension = buildExtension(apkPath, packageInfo, sources)
            
            ExtensionLoadResult.Success(extension, sources)
        } catch (e: Exception) {
            ExtensionLoadResult.Error("Failed to load extension: ${e.message}", e)
        }
    }
    
    /**
     * Verify that the APK is a valid Otaku extension.
     */
    private fun isValidExtension(packageInfo: PackageInfo): Boolean {
        val appInfo = packageInfo.applicationInfo ?: return false
        
        // Check for extension metadata
        val metaData = appInfo.metaData
        return metaData?.getString(METADATA_SOURCE_CLASS) != null
    }
    
    /**
     * Create a DexClassLoader for the extension APK.
     */
    private fun createClassLoader(apkPath: String): DexClassLoader {
        val dexOutputDir = File(context.codeCacheDir, DEX_OUTPUT_DIR)
        dexOutputDir.mkdirs()
        
        return DexClassLoader(
            apkPath,
            dexOutputDir.absolutePath,
            null,
            context.classLoader
        )
    }
    
    /**
     * Get source class names from package metadata.
     */
    private fun getSourceClassNames(packageInfo: PackageInfo): List<String> {
        val appInfo = packageInfo.applicationInfo ?: return emptyList()
        val metaData = appInfo.metaData ?: return emptyList()
        
        val sourceClass = metaData.getString(METADATA_SOURCE_CLASS)
            ?: return emptyList()
        
        // Support multiple sources separated by semicolon
        return sourceClass.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    /**
     * Load a source instance from its class name.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadSource(classLoader: DexClassLoader, className: String): Source? {
        return try {
            val clazz = classLoader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance()
            
            // Cast to Source interface
            instance as? Source
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Build Extension domain model from loaded APK info.
     */
    private fun buildExtension(
        apkPath: String,
        packageInfo: PackageInfo,
        sources: List<Source>
    ): Extension {
        val appInfo = packageInfo.applicationInfo
        
        return Extension(
            id = generateExtensionId(packageInfo.packageName),
            pkgName = packageInfo.packageName,
            name = appInfo?.loadLabel(packageManager)?.toString() 
                ?: packageInfo.packageName,
            versionCode = packageInfo.longVersionCode.toInt(),
            versionName = packageInfo.versionName ?: "unknown",
            sources = sources.map { it.toExtensionSource() },
            status = InstallStatus.INSTALLED,
            apkPath = apkPath,
            iconUrl = null,
            lang = sources.firstOrNull()?.lang ?: "en",
            isNsfw = false, // Should be determined from metadata
            installDate = System.currentTimeMillis(),
            signatureHash = getSignatureHash(packageInfo)
        )
    }
    
    /**
     * Generate a stable extension ID from package name.
     */
    private fun generateExtensionId(pkgName: String): Long {
        return pkgName.hashCode().toLong().and(0xFFFFFFFFL)
    }
    
    /**
     * Get signature hash for verification.
     */
    private fun getSignatureHash(packageInfo: PackageInfo): String? {
        return try {
            packageInfo.signatures?.firstOrNull()?.toCharsString()?.hashCode()?.toString()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert loaded Source to ExtensionSource model.
     */
    private fun Source.toExtensionSource(): ExtensionSource {
        // This assumes the actual Source implementation has the required properties
        // The real implementation would use reflection or interface methods
        return ExtensionSource(
            id = this.id,
            name = this.name,
            lang = this.lang,
            baseUrl = "", // Would be populated from actual source
            supportsSearch = true,
            supportsLatest = true
        )
    }
}
