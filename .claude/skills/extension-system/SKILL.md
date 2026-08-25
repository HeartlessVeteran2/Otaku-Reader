# Extension System Patterns

## Direction

**Two backends ship side by side: Tachiyomi APK extensions, and JavaScript sources from the Mangayomi ecosystem.**

This file said before that the APK backend was being retired. That plan was **cancelled on 2026-08-25**: the JavaScript half of the Mangayomi ecosystem is 16 usable sources against the hundreds the APK path serves, and removing it would also force a repo-wide library migration and orphan every downloaded chapter. See the *Extension System* section of `CLAUDE.md`, which is the authority. **Do not delete APK code**, and do not treat the JavaScript backend as a replacement — it is additive.

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

## APK extensions (`core/extension/`, `core/tachiyomi-compat/`)

A live backend, carrying the large majority of the catalogue.

**Neither module is APK-only anyway.** Both mix APK machinery with things the rest of the app depends on, which is worth knowing before touching either:

- `core/extension` holds the `Extension`/`ExtensionSource`/`InstallStatus` models, the repository contracts, the blocklist and `JsExtensionBackend` — used by the JavaScript path and the browse UI.
- `core/tachiyomi-compat` holds **`LocalSource`** (local manga folders — a shipped feature, wired into settings, navigation and preferences) and **`SourceHealthMonitor`** (which wraps *every* source call, JavaScript included). Its `eu.kanade.tachiyomi.network` package and the `Injekt` bootstrap in `OtakuReaderApplication`, by contrast, exist only so loaded APKs can resolve host dependencies — JavaScript sources go through `JsHttpBridge` instead.

The security properties that applied here still apply to the JavaScript path and are worth carrying forward:

- Source code never runs on the main thread.
- Source network calls go through the app's shared OkHttp client, so certificate pinning, the cookie jar, rate limiting and the user's proxy/VPN all apply.
- Sources cannot reach app data. For JavaScript this is stronger than it was for APKs: a QuickJS context starts with no I/O at all, capability arrives only through the globals `QuickJsHost` installs, and the sidecar process runs under a permission-less UID.

## Before any change that re-points a library row at a different source

Read *Source Identity → Why removing a backend runs straight into this* in `CLAUDE.md`. `Manga.sourceId` is `id.hashCode().toLong()` — one-way — so an entry whose source id changes points at a source that no longer exists, and `downloadFolderNameFor(sourceId)` files its downloads under the old numeric key. A guided migration (reusing `feature/migration/`) and an answer for the orphaned downloads (#1256) must ship *with* such a change, not after it. Removing a backend was the extreme case; a Tachiyomi backup import lands entries the same way.
