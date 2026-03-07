# Tachiyomi Compatibility Module

This module provides compatibility with Tachiyomi extensions, allowing Otaku Reader to load and use Tachiyomi extension APKs.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Browse Screen (UI)                        │
│                   feature/browse                             │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                SourceRepository (Domain)                     │
│         domain/src/main/java/.../repository                  │
│              - getSources()                                  │
│              - getPopularManga()                             │
│              - getLatestUpdates()                            │
│              - searchManga()                                 │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│         SourceRepositoryImpl (Data)                          │
│         core/tachiyomi-compat/.../repository                 │
│              - Uses TachiyomiExtensionLoader                 │
│              - Caches manga lists                            │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│         TachiyomiSourceAdapter                               │
│              - Wraps Tachiyomi CatalogueSource               │
│              - Adapts to Otaku Reader MangaSource            │
│              - Handles RxJava to Coroutines                  │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│         TachiyomiExtensionLoader                             │
│              - Loads APK via DexClassLoader                  │
│              - Parses AndroidManifest.xml                    │
│              - Instantiates Source classes                   │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│         Tachiyomi Extension APK                              │
│              - Contains CatalogueSource classes              │
│              - Has metadata in manifest                      │
└─────────────────────────────────────────────────────────────┘
```

## Components

### TachiyomiExtensionLoader
- Loads Tachiyomi extension APKs using `DexClassLoader`
- Discovers and instantiates `CatalogueSource` classes
- Parses extension metadata from AndroidManifest.xml

### TachiyomiSourceAdapter
- Wraps a Tachiyomi `CatalogueSource`
- Implements Otaku Reader's `MangaSource` interface
- Converts between Tachiyomi and Otaku Reader models
- Handles RxJava Observable to Kotlin Coroutines conversion

### TachiyomiModelsAdapter
- Converts Tachiyomi models to Otaku Reader models:
  - `SManga` → `SourceManga`
  - `SChapter` → `SourceChapter`
  - `SPage` → `Page`
  - `MangasPage` → `MangaPage`

### TachiyomiManifestParser
- Extracts metadata from extension APK's AndroidManifest.xml
- Parses binary XML format
- Extracts source class names and extension info

### SourceRepositoryImpl
- Implements `SourceRepository` interface
- Manages loading and caching of sources
- Provides use case methods for fetching manga

## Usage

### Loading an Extension from File

```kotlin
val repository: SourceRepository = SourceRepositoryImpl(context)
val result = repository.loadExtension("/path/to/extension.apk")
```

### Loading an Extension from URL

```kotlin
val repository: SourceRepository = SourceRepositoryImpl(context)
val result = repository.loadExtensionFromUrl(
    "https://example.com/extension.apk"
)
```

### Getting Popular Manga

```kotlin
val result = repository.getPopularManga("source_id", page = 1)
result.onSuccess { mangaPage ->
    val manga = mangaPage.mangas
    val hasMore = mangaPage.hasNextPage
}
```

### Searching Manga

```kotlin
val result = repository.searchManga("source_id", "query", page = 1)
```

## Testing

Use the test utilities to load extensions and verify functionality:

```kotlin
// Load extension from URL
val result = TachiyomiTestUtils.installExtensionFromUrl(
    context,
    SuwayomiExtensionUrls.MANGADEX
)

// Test source
val mangaResult = TachiyomiTestUtils.testSourcePopular(context, "source_id")
mangaResult.onSuccess { titles ->
    println("Found manga: $titles")
}
```

## Extension Metadata

Tachiyomi extensions include metadata in their AndroidManifest.xml:

```xml
<meta-data
    android:name="tachiyomi.extension"
    android:value="true" />
<meta-data
    android:name="tachiyomi.extension.lang"
    android:value="en" />
<meta-data
    android:name="tachiyomi.extension.nsfw"
    android:value="false" />
<meta-data
    android:name="tachiyomi.extension.sources"
    android:value='[{"name":"MangaDex","class":"MangaDex","lang":"en"}]' />
```

## Dependencies

- RxJava 1.x (for Tachiyomi compatibility)
- OkHttp (shared with Tachiyomi)
- XMLPull (for manifest parsing)
- DexClassLoader (Android framework)

## Limitations

1. Tachiyomi extensions use RxJava 1.x which requires conversion to coroutines
2. Binary XML parsing is simplified; complex manifests may not parse correctly
3. Extension security relies on Android's package manager
4. Memory management requires careful handling of DexClassLoader

## Future Improvements

- Proper binary XML parsing library integration
- Extension signature verification
- Automatic extension updates
- Better error handling and recovery
- Extension preferences/settings support
