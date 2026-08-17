# Extension System Patterns

## Direction

**The APK backend is being retired. JavaScript sources from the Mangayomi ecosystem are the future.**

This file previously documented only the Tachiyomi APK path and stated that its compatibility must never be broken. That rule is no longer in force — see the *Extension System* section of `CLAUDE.md`, which is the authority. Do not treat removal of APK code as a regression.

It also described a `Source` interface that never existed in this repository. The real contract is `MangaSource`, below. Anything you remember from the old version of this file should be re-checked against the code.

## The seam

`MangaSource` (in `source-api/`) is the only source contract the rest of the app knows. The reader, library, downloads, migration and every domain use-case speak it, so a backend is **one implementation of one interface**.

```kotlin
interface MangaSource {
    val id: String          // string id; Manga.sourceId is id.toSourceId() — one-way, see CLAUDE.md
    val name: String
    val lang: String
    val baseUrl: String
    val isNsfw: Boolean

    suspend fun fetchPopularManga(page: Int): MangaPage
    suspend fun fetchLatestUpdates(page: Int): MangaPage
    suspend fun fetchSearchManga(page: Int, query: String, filters: FilterList): MangaPage
    suspend fun fetchMangaDetails(manga: SourceManga): SourceManga
    suspend fun fetchChapterList(manga: SourceManga): List<SourceChapter>
    suspend fun fetchPageList(chapter: SourceChapter): List<Page>
}
```

Check the actual file before relying on this — it is reproduced here for orientation, not as a spec.

## JavaScript sources (`core/js-runtime/`)

```
Mangayomi extension (unmodified, from index.json)
   │  new Client() / new Document(html) / doc.selectFirst(s)?.text
   ▼
prelude.js            ← compatibility layer, written in JavaScript on purpose
   │  Client.get(...) / Document.parse(html) → integer handle
   ▼
QuickJsHost bindings  ← flat, primitives only
   │  JsProtocol JSON over a process boundary
   ▼
JsSource : MangaSource
```

### Flow

1. `JsExtensionRemoteDataSource` fetches the repository index — `/js/index.json` if served, otherwise `/index.json` (which is where Mangayomi publishes).
2. Entries are filtered to manga, to `sourceCodeLanguage == 1` (JavaScript, not Dart), and to a non-blank `sourceCodeUrl`.
3. `downloadScript` fetches the `.js` over HTTPS only, size-bounded.
4. `JsSourceStore` writes manifest and script as **one document**, so a single atomic rename is the whole update.
5. `QuickJsHost` evaluates the source config global, then `prelude.js`, then the script, then the invocation.

### Rules

- **Extensions run unmodified.** If a published source fails, the runtime is wrong, not the source.
- **The compatibility layer stays in JavaScript.** Having Kotlin bindings hand objects to JS would defeat the isolation the handle design exists for.
- **`MProvider` must exist before the script is evaluated** — `extends` resolves at class-definition time, so a missing base fails the whole script.
- **Release every parsed document in a `finally`.** The pool caps at 32 and the host refuses rather than evicts.
- **Preferences: stored wins, declared default (`getSourcePreferences()`) is the fallback.** Sources routinely read a preference nobody has set.
- **Verify against real sources.** The Mangayomi contributing guide says `getLatest`; all 18 sampled published sources define `getLatestUpdates`.

### Testing

The prelude cannot be unit-tested from the JVM — QuickJS is an Android artifact. `JsPreludeTest` guards packaging and global names only. Behaviour is covered by `tools/js-prelude-harness/`, a Node harness reproducing `QuickJsHost.call`. Check a failing site with `curl` before suspecting the code.

## APK extensions (`core/extension/`, `core/tachiyomi-compat/`) — retiring

Still present, still loading; not where new work goes. `core/extension` also holds the shared `Extension`/`ExtensionSource` models the JavaScript path currently reuses, so those move before the module is deleted.

The security properties that applied here still apply to the JavaScript path and are worth carrying forward:

- Source code never runs on the main thread.
- Source network calls go through the app's shared OkHttp client, so certificate pinning, the cookie jar, rate limiting and the user's proxy/VPN all apply.
- Sources cannot reach app data. For JavaScript this is stronger than it was for APKs: a QuickJS context starts with no I/O at all, capability arrives only through the globals `QuickJsHost` installs, and the sidecar process runs under a permission-less UID.

## Before removing the APK path

Read *Source Identity → The backend switch runs straight into this* in `CLAUDE.md`. Mangayomi ids are not Tachiyomi ids and `Manga.sourceId` is a one-way hash, so every library row would point at a source that no longer exists. A guided migration (reusing `feature/migration/`) and a resolution for orphaned downloads (#1256) must ship with the removal, not after it.
