# Extension Loader Consolidation

## Overview

This document describes the consolidation work done to reduce code duplication between `ExtensionLoader` and `TachiyomiExtensionLoader` classes.

## Problem

There were two independent classes performing similar extension APK loading:
1. `core/extension/src/main/java/app/otakureader/core/extension/loader/ExtensionLoader.kt`
2. `core/tachiyomi-compat/src/main/java/app/otakureader/core/tachiyomi/compat/TachiyomiExtensionLoader.kt`

Both classes had significant code duplication in:
- Metadata reading (METADATA_SOURCE_CLASS, METADATA_SOURCE_FACTORY, METADATA_NSFW)
- Class name resolution (`resolveClassName()`)
- Class instantiation (`instantiateClass()`)
- DexClassLoader creation
- Source resolution from metadata

## Solution

### Shared Utilities

Created `ExtensionLoadingUtils` in `core/extension/src/main/java/app/otakureader/core/extension/loader/ExtensionLoadingUtils.kt` containing shared logic:

1. **Constants**: All metadata keys (EXTENSION_FEATURE, METADATA_SOURCE_CLASS, METADATA_SOURCE_FACTORY, METADATA_NSFW)
2. **isPackageAnExtension()**: Check if PackageInfo is an extension
3. **createClassLoader()**: Create DexClassLoader with proper configuration
4. **resolveClassName()**: Expand relative class names
5. **instantiateClass()**: Instantiate classes by name
6. **resolveSourcesFromMetadata()**: Parse metadata and instantiate Source classes
7. **isNsfw()**: Read NSFW flag
8. **fixBasePaths()**: Fix Android 13+ ApplicationInfo paths

### ExtensionLoader Updates

`ExtensionLoader` now uses `ExtensionLoadingUtils` for all common operations:
- Constants reference the shared constants
- Removed duplicate implementation of helper methods
- Uses shared utilities throughout

### TachiyomiExtensionLoader Status

**Important**: `TachiyomiExtensionLoader` currently maintains its own implementation to avoid circular dependencies.

**Circular Dependency Issue**:
- `core:extension` depends on `core:tachiyomi-compat` (line 18 in extension/build.gradle.kts)
- Adding a dependency from `core:tachiyomi-compat` to `core:extension` creates a circular dependency
- Gradle build fails with: "Circular dependency between the following tasks"

**Attempted Solutions**:
1. ✗ Moving ExtensionLoadingUtils to `core:common` - requires Tachiyomi Source classes not available there
2. ✗ Moving to `source-api` - would pollute the API module with Android-specific loading logic
3. ✗ Adding bidirectional dependency - causes circular dependency errors

**Current Approach**:
- `TachiyomiExtensionLoader` maintains its own implementation
- Code duplication is documented and intentional
- Constants remain synchronized manually
- Future refactoring could extract to a new shared module if needed

## Key Differences Maintained

### ExtensionLoader
- **Purpose**: Installation and verification workflow
- **Return Type**: `ExtensionLoadResult` (Success/Error with detailed messages)
- **Error Handling**: Explicit error messages and throwables
- **Caching**: None (stateless, single-use)
- **Version Validation**: Yes (1.2-1.5)
- **Signature Verification**: Yes (SHA-256 hash)
- **Source Filtering**: Accepts all Source types
- **Domain Models**: Returns `Extension` domain objects
- **Use Case**: Used by ExtensionInstaller for APK installation/updates

### TachiyomiExtensionLoader
- **Purpose**: Runtime source discovery and management
- **Return Type**: `LoadedExtension?` (nullable, no error details)
- **Error Handling**: Silent failures (returns null)
- **Caching**: Full (maintains loaded extensions map)
- **Version Validation**: No
- **Signature Verification**: No
- **Source Filtering**: CatalogueSource only
- **Domain Models**: Returns `TachiyomiSourceAdapter` wrapped sources
- **Use Case**: Used by SourceRepositoryImpl for loading extensions at runtime
- **Additional Features**: reload/unload operations

## Testing

Both loaders have been tested to ensure:
1. The build compiles successfully without circular dependencies
2. ExtensionLoader uses shared utilities correctly
3. TachiyomiExtensionLoader maintains its functionality independently
4. No regressions in extension loading behavior

## Future Improvements

1. **Create shared module**: Extract common code to a new `core:extension-loading` module
   - Both `core:extension` and `core:tachiyomi-compat` would depend on it
   - Eliminates current circular dependency issue
   - Fully consolidates duplicate code

2. **Unify APIs**: Consider whether both loaders could use the same result type
   - Would require careful analysis of use cases
   - May not be desirable given their different purposes

3. **Automated synchronization**: Add linting or tests to ensure constants stay synchronized
   - Could use reflection to verify constant values match
   - Would catch drift between implementations

## Maintenance Notes

If modifying extension loading logic:
1. Update `ExtensionLoadingUtils` first
2. Verify changes work with `ExtensionLoader`
3. Manually apply equivalent changes to `TachiyomiExtensionLoader` if needed
4. Keep constants synchronized between files
5. Document any intentional divergence

## See Also

- `ExtensionLoader` documentation in source file
- `TachiyomiExtensionLoader` documentation in source file
- `ExtensionLoadingUtils` API documentation
- Issue: "Consolidate ExtensionLoader and TachiyomiExtensionLoader classes"
