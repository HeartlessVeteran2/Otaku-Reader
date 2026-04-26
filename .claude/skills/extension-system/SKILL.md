# Extension System Patterns

## Overview
Extensions are the core value proposition — access to 2000+ manga sources. They are dynamically loaded APKs that implement interfaces from the `source-api` module.

## Extension Types
1. **Remote extensions** — Downloaded from Keiyoushi or Komikku repos
2. **Private extensions** — Sideloaded by user, auto-trusted
3. **Shared extensions** — Sideloaded but must pass `TrustedSignatureStore` validation

## Key Interfaces
```kotlin
// Every extension implements this
interface Source {
    val id: Long
    val name: String
    val lang: String
    suspend fun getMangaList(page: Int, query: String?): MangasPage
    suspend fun getMangaDetails(manga: Manga): Manga
    suspend fun getChapterList(manga: Manga): List<Chapter>
    suspend fun getPageList(chapter: Chapter): List<Page>
}

// Extensions may also implement
interface ConfigurableSource : Source {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
```

## Extension Loader Flow
1. `ExtensionLoader` scans for APKs in extension directory
2. Validates APK signature against `TrustedSignatureStore`
3. Loads DEX and instantiates source classes via reflection
4. Wraps in `Extension` model with metadata

## Trust Model
```kotlin
// From ExtensionLoader
when (val trustResult = checkTrust(apkFile)) {
    is TrustResult.Trusted -> loadExtension()
    is TrustResult.Untrusted -> markAsUntrusted() // User must approve
    is TrustResult.NotInstalled -> showInstallPrompt()
}
```

## Extension API Versioning
- `source-api` defines the contract
- Extensions compiled against a specific API version
- Forward compatibility maintained where possible
- Breaking changes bump API version and require extension updates

## Adding New Extension Features
When modifying the extension system:
1. Update `source-api` interfaces first
2. Provide default implementations for backward compatibility
3. Update `ExtensionLoader` to handle new metadata
4. Test with both Keiyoushi and Komikku repo extensions
5. Update extension API version if breaking

## Security Rules
- Never execute extension code on the main thread
- Extension network calls go through app's OkHttp (respects user proxy/vpn)
- Extension storage is sandboxed — they can't access app's internal data
- Always validate signatures before loading — never trust unsigned shared extensions
