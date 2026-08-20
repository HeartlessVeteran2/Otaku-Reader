# CLAUDE.md — Otaku Reader

This file is the AI assistant reference for the Otaku Reader codebase. Read it before making any changes.

---

## What This Project Is

Otaku Reader is a production-grade Android manga reader built entirely in Kotlin and Jetpack Compose by a solo developer. It is a clean-architecture alternative to Mihon/Tachiyomi. The feature set is complete: all 35 parity issues, the hardening batch, and the post-beta polish pass have shipped.

**The source system is mid-rebuild.** The app is moving off dynamically-loaded Tachiyomi APK extensions and onto **JavaScript sources from the Mangayomi ecosystem**, run by QuickJS in an isolated sidecar process. This is the single largest active change in the codebase and it reverses a rule this file previously called non-negotiable — read *Extension System* before touching anything source-related.

**Status:** All phases shipped. Alpha (2026-05-25) → Beta parity (#926–#958) → Hardening (#1090–#1099) → P3 polish (#1114) → Post-P3 additions: QR library sharing (#1110/#1125), update-errors screen (#1119), stats improvements (#1122), in-chapter download button (#1127), page bookmarks + collections (PR #1130, merged 2026-06-20) → Komikku-parity deferred-gaps batch (issue #1192, 7 PRs #1197/#1202–#1207, merged 2026-07-05 → 2026-07-06 — statistics Trackers card, settings wiring batch, keep-last-N delete-after-read, onboarding storage step, dedicated Update Errors screen, configurable migration options + custom-cover migration, selective backup/restore; remaining deferred items spun out to #1208). **Current phase: the JavaScript source-system rebuild (see *Extension System*). The v1.0.0 tag waits on it** — cutting a 1.0 on the APK backend that is about to be removed would ship a release whose sources stop working on the next update. Project website: https://heartless-veteran.github.io/Otaku-Reader/ — VitePress landing page, download with live version lookup, docs, FAQ, auto-synced changelog. Deployed from `website/` via `pages.yml`.

**The developer is newer to Kotlin. Always explain what was wrong and why a fix works — never drop solutions without context.**

---

## Repository Layout

```
Otaku-Reader/
├── app/                    # Application entry point, Navigation host, Widgets, DI root
├── domain/                 # Pure business logic — UseCases, Repository interfaces
├── data/                   # Repository implementations, WorkManager workers, Backup/Sync
├── source-api/             # The MangaSource contract every backend implements (pure Kotlin)
├── baselineprofile/        # Baseline profile generation for app startup performance
├── website/                # VitePress project site, deployed to GitHub Pages by pages.yml
├── build-logic/            # Gradle convention plugins
├── tools/
│   ├── extension-smoke-test/   # Live-network source loading check (manual only, never gates a PR)
│   └── js-prelude-harness/     # Node harness running real JS extensions against prelude.js
├── core/
│   ├── common/             # Shared utilities, Palette API, coroutine helpers
│   ├── database/           # Room entities, DAOs, migrations (current schema v45)
│   ├── network/            # OkHttp + Retrofit + Kotlinx Serialization setup
│   ├── preferences/        # DataStore preferences, encrypted credential storage
│   ├── ui/                 # Shared Compose components, Material 3 theme, Coil integration
│   ├── navigation/         # Type-safe Compose Navigation routing
│   ├── js-runtime/         # JavaScript sources: QuickJS sidecar, JsProtocol, prelude.js  ← the future
│   ├── webview/            # WebView host (Cloudflare challenges, OAuth flows)
│   ├── extension/          # APK classloading — RETIRING, see Extension System
│   ├── tachiyomi-compat/   # RxJava 1.x stubs — RETIRING, see Extension System
│   └── discord/            # Discord Rich Presence (native, no external library)
└── feature/
    ├── library/            # Main manga collection, categories, filtering
    ├── reader/             # Multi-mode reader (single/dual/webtoon/smart panels)
    ├── browse/             # Source browsing and global search (Paging 3)
    ├── details/            # Manga detail page, chapter list, tracker status
    ├── updates/            # New chapter updates list
    ├── history/            # Reading history timeline
    ├── settings/           # All app settings, backup/restore, tracker auth
    ├── statistics/         # Reading stats dashboard
    ├── migration/          # Source migration wizard
    ├── tracking/           # Tracker integrations (MAL, AniList, Kitsu, MangaUpdates, Shikimori)
    ├── onboarding/         # First-run setup wizard
    ├── about/              # About screen, credits, version info
    ├── opds/               # OPDS catalog support (Komga/Kavita)
    ├── feed/               # Recommendations and activity feed
    ├── webview/            # WebView screen (Cloudflare challenges, tracker OAuth)
    └── more/               # Bottom nav "More" section
```

There is **no `server/` module** in this repository. It was listed here for a long time and does not exist — the self-hosted Ktor sync server lives in its own repo.

---

## Shipped Feature Inventory

**Do not re-implement any feature listed here.** If you think something is missing, search the codebase first.

### Reader (`feature/reader/`)
- 4 reading modes: single-page, dual-page, webtoon (vertical scroll), smart-panels (auto-crop)
- Page-level bookmarks (toggle per page, persisted in `page_bookmarks` DB table)
- Bookmark collections (group bookmarks, filter by collection in BookmarksScreen)
- Reader comments / notes per chapter (`reader_comments` DB table, PR #1098; opened via the comment icon in the reader's top bar)
- Gesture controls: tap-zones, swipe navigation, volume-key paging
- Download while reading (per-chapter download button in top bar, PR #1127)
- Chapter list overlay with progress indicator
- Discord Rich Presence (native JNI, `core/discord/`)

### Library (`feature/library/`)
- Grid and list view toggle, sort by title / last read / latest chapter / date added
- Custom categories with drag-to-reorder
- Multi-select with bulk actions: move category, mark read, download, delete, remove from library
- Library update scheduler (global and per-source intervals)
- Download badges, unread count badges per entry
- QR code library sharing / scanning (PR #1110/#1125)

### Browse (`feature/browse/`)
- Per-source manga list + global search across all sources (Paging 3)
- Source list with installed/uninstalled state, language filter
- OPDS catalog support (Komga, Kavita)

### Manga Details (`feature/details/`)
- Chapter list with filters (read/unread, bookmarked/not), sort (ascending/descending), search
- Multi-select chapters: mark read, download, delete, bookmark (chapter-level bookmarks removed in PR #1130)
- Tracker count button in the action row, plus per-tracker status/score/progress chips (`TrackerChips.kt`). `DetailsViewModel.observeTrackEntries()` keeps the whole `observeEntriesForManga` list; `State.trackingCount` is derived from it.
- AniList metadata section (`AniListMetadataSection.kt`): community stats grid, rank-weighted tags ("Isekai 87%", tap → source search / long-press → global search), alternative titles, plus characters and staff carousels (`PersonCarousel.kt`), a related-manga carousel (`MangaRelationCarousel.kt`, tap → global search for the title, because a relation is an AniList media id with no local record) and external-link chips (`ExternalLinkChips.kt`). Read from `MangaMetadataRepository` as a **separate flow** — never folded into `Manga`. AniList's description and genres fill in only where the source left a gap; the two are never shown together. The screen renders `State.metadata`, which is *derived* — it hides `cachedMetadata` whose `anilistId` no longer matches the live AniList link, because the cache row is keyed by `mangaId` and deliberately survives both a failed refresh and a link change. The AniList media id comes from the AniList `TrackEntry.remoteId` when the manga is tracked there, and otherwise from a stored link in `manga_anilist_link` written by auto-matching (`ResolveAniListMediaUseCase` → `MatchAniListMediaUseCase`). The tracker entry always wins: `State.metadata` hides cached metadata whose `anilistId` disagrees with the live id, so a stale stored link shadowing a tracker entry would stop a corrected tracker link from invalidating the cache. Auto-matching runs once per screen visit and **only persists a `confident` match** — below `ACCEPT_THRESHOLD` nothing is stored and nothing renders, because a wrong synopsis and wrong tags look authoritative. The recourse is the wrong-match picker (`AniListPickerDialog.kt`, overflow → "Link to AniList…"), which records the pick as `userConfirmed` so auto-matching will not overwrite it, and forces a metadata refetch for the new id. Characters and staff share one `MangaMetadataPerson` model and one carousel, but their `role` is stored **raw** and formatted by the caller: a character's is an AniList enum (`MAIN`) that wants prettifying, a staff member's is free text kept verbatim ("Story & Art"), and normalising them on the way in would make a staff credit reading "Main" indistinguishable from the enum.
- Track manga on multiple services simultaneously
- Add to Reading List (overflow menu, live-toggle checkbox picker against `feature/library/readinglist/`)

### Tracking (`feature/tracking/`)
- MAL, AniList, Kitsu, MangaUpdates, Shikimori all fully integrated
- OAuth / token auth per tracker
- Score, status, chapter progress sync both directions

### Downloads (`feature/reader/`, `data/workers/`)
- Per-source download queue with pause/resume
- Auto-download new chapters (global on/off + per-manga override)
- Delete-after-read (global on/off + per-manga override, PR #1114)
- Download manager screen under More → Downloads

### History (`feature/history/`)
- Timeline of chapters read with timestamps
- Swipe-to-delete single entry, bulk delete with undo snackbar
- Tap to resume reading at last page

### Updates (`feature/updates/`)
- New-chapter update list from last library refresh
- Unread badge on bottom nav tab
- Bulk mark-as-read with undo

### Statistics (`feature/statistics/`)
- Total chapters read, total reading time, average per day
- Per-manga reading stats, streak tracking (PR #1122)
- Reading achievements — earned badges shown in a grid on the Statistics screen, checked live on every chapter read (`data/worker/AchievementCheckWorker.kt`)
- Stats summary widget on MoreScreen (Glance)

### Settings (`feature/settings/`)
- Theme: system / light / dark + dynamic color
- Reader defaults: mode, reading direction, background color
- Download location, concurrent download limit
- Auto-update schedule: interval, wifi-only
- Tracker auth management
- Extension management (install/uninstall/trust)
- Backup: export and import (native format + Tachiyomi import), with selective per-category backup/restore (`BackupOptions`, PR #1207). `BackupData` is plain versioned JSON (currently v4), which is why it is also the app's data-portability story — anything that needs to read a user's library outside this codebase reads that, not the Room database. **Tachiyomi import carries the same source-id hazard as the backend switch**: an imported backup names Tachiyomi sources, so once the APK path is gone those entries land pointing at sources that do not exist and need the same migration recourse (see *Source Identity*).

### More tab (`feature/more/`)
- Bookmarks screen: page-level bookmarks, collection filtering, multi-select, export (PR #1130)
- History screen
- Statistics screen
- Feed screen — new chapters from library updates. `LibraryUpdateWorker` writes the rows (`FeedRepository.addFeedItems`); before that nothing anywhere inserted a `FeedItemEntity`, so the tab was permanently empty while `FeedRefreshWorker` purged rows older than 30 days that never arrived. The screen reads `getFeedItems(limit)` **unfiltered by feed source**, so the `feed_sources` table does not gate what is shown. `FeedBuilderBottomSheet` is a picker over installed sources (`SavedFeedViewModel` combines `getFeedSources()` with `getSources()`), keyed by `id.toSourceId()`; it previously hashed free text into an id that could never match a real source. Sources already in the feed are filtered out of the picker, and that filter is load-bearing — `insertFeedSource` is `OnConflictStrategy.REPLACE` against a unique index on `sourceId`, so re-adding one would delete its row and reset the user's enabled toggle, item count and order. A saved row whose key no loaded source owns renders as **not installed** (`SavedFeedSourceRow.isInstalled`) with its toggle disabled: that covers both a legacy row hashed from typed text and a source whose extension was uninstalled, which are indistinguishable from the row alone and want the same action. Such a row deliberately does *not* suppress its source in the picker — matching it by display name would repeat the guessing that created it, and would leave the user unable to add the working source.
- First-run Onboarding wizard
- About screen (version, credits, links)
- Update Errors screen (PR #1119/#1205; dedicated screen with sticky-header grouping by error message, long-press multi-select, migrate-selected — replaces the original dialog) — reachable from the More tab entry or the badge icon on the Updates screen's top bar
- QR Library Share / Scan (PR #1110/#1125)

### Home-Screen Widgets (`app/src/main/java/app/otakureader/widget/`)
- 4 Glance-based Android home-screen widgets: `HomeWidget`, `NowReadingWidget`, `ContinueReadingWidget`, `RecentUpdatesWidget`
- Configured via Settings → Widgets; placed via Android's native "Add Widget" home-screen flow
- Distinct from the in-app Statistics summary card on MoreScreen (also Glance, but that's a Compose card, not a home-screen widget)

### Security
- Certificate pinning (`cert-pin-check.yml` CI gate)
- Encrypted credential storage for tracker tokens (`AndroidX Security Crypto`)
- No Firebase, no analytics SDK, no crash tooling

---

## Architecture

### Layer Overview

```
UI (Jetpack Compose)
  └─ collectAsStateWithLifecycle()
       └─ ViewModel (@HiltViewModel)
            └─ StateFlow<UiState> + Channel<Effect>
                 └─ UseCases (Domain layer)
                      └─ Repository interfaces (Domain layer)
                           └─ Repository implementations (Data layer)
                                └─ Room DAOs + Retrofit + DataStore
```

### MVI Pattern — Non-Negotiable

Every screen follows Model-View-Intent:

- **State**: Immutable data class, exposed as `StateFlow<UiState>` from the ViewModel.
- **Event**: Sealed class representing user actions. All UI changes go through an Event → Reducer cycle.
- **Effect**: One-shot events (navigation, toasts) via a `Channel<Effect>` (consumed exactly once).

Rules:
- Never mutate state directly.
- Never use `LiveData` — only `StateFlow` and `SharedFlow`.
- Composables must be stateless where possible; hoist state up.
- Collect state with `collectAsStateWithLifecycle()`, not `collectAsState()`.
- Use `LaunchedEffect` only for one-time side effects on composition.
- Use `rememberCoroutineScope()` only for user-triggered async actions.

**Pattern example** (every feature looks like this):
```
LibraryMvi.kt       — sealed class LibraryState, LibraryEvent, LibraryEffect
LibraryViewModel.kt — @HiltViewModel, produces StateFlow<LibraryState>
LibraryScreen.kt    — stateless composable consuming state
```

### Source Identity — Two Ids, One Direction

A source has a **string id** (`MangaSource.id`) and every manga row stores a **`Long` key**
(`Manga.sourceId`). The key is `id.toSourceId()`, which is `id.hashCode().toLong()`.

**Hashing is one-way.** `manga.sourceId.toString()` is the decimal of a hash and matches no
source's id — it is *not* a way back. To go from a key to a source, search the loaded sources:

```kotlin
sourceRepository.getSourceByKey(manga.sourceId)          // the MangaSource
sourceRepository.resolveSourceId(manga.sourceId)         // its string id, for a source call
```

Getting this wrong is the highest-impact bug the project has had. `getSource(manga.sourceId.toString())`
was in reading, library update, recommendations, migration, the details screen, prefetch and
download-ahead; all of them failed with "Source not found" for every library entry, and the
details header's source name was permanently blank. Three *different* wrong conventions were in
use at once (`toSourceId()`, `toLongOrNull()`, and the raw `ExtensionSource.id`), which is why
some manga worked and others didn't depending on which screen added them. Fixed across the repo;
`getSourceByKey` also matches the legacy `toLongOrNull()` rows, because a `Long` on disk carries
no record of which convention wrote it and so no migration can tell them apart.

Two things are deliberately *not* this key:

- **`resolveDownloadFolderName`** returns the numeric key as a string and consults no source. Every
  download on disk is already filed under the number, so resolving a display name would orphan
  them. Changing it is a data migration — see #1256.
- **`Route.SourceListing.sourceId`** is already the string id. Browse never went through the key,
  which is why browsing worked while reading from the library did not.

#### The backend switch runs straight into this

Retiring the APK backend is **not** a code-only change, and this is the section that says why. A Mangayomi source's id is not its Tachiyomi equivalent, and `Manga.sourceId` is `id.hashCode().toLong()` — one-way. So on the day the APK sources go, **every existing library row points at a source that no longer exists**, which reproduces the exact "Source not found" failure described above, across the entire library at once, for every user.

Two things must ship *with* the removal, not after it:

- **A guided library migration.** Reuse `feature/migration/` — the wizard already does source-to-source entry migration. This is the recourse; do not write a new one.
- **A decision on downloads.** `downloadFolderNameFor(sourceId)` files every downloaded chapter under the numeric key, so re-pointing an entry at a new source orphans its files on disk. Tracked as #1256; it has to be resolved as part of this work.

### Clean Architecture Layer Rules

| Layer | Contains | Rules |
|-------|----------|-------|
| `domain/` | UseCases, Repository interfaces, domain models | Pure Kotlin, no Android imports, no DI annotations beyond `@Inject` |
| `data/` | Repository implementations, DAOs (via core/database), Workers | Implements domain interfaces; entities ≠ domain models — always map |
| `feature/*` | ViewModel, Composables, MVI state | Depends on domain, never on data directly |
| `core/*` | Shared infrastructure | No feature-level dependencies |

---

## Dependency Injection (Hilt)

- All ViewModels: `@HiltViewModel`
- All Repositories: `@Singleton` (unless there is a specific reason otherwise)
- UseCases: `@Reusable` or unscoped
- Always verify `@InstallIn` scope matches the injection site
- KSP runs the Hilt processor — if a binding appears missing, check `@InstallIn` before blaming the ViewModel

---

## Database (Room)

- **Every DAO read function returns `Flow<T>`** — never a plain value.
- Migrations must be explicit. **Never use `fallbackToDestructiveMigration()` in production.**
- Entities are separate from domain models. Always write and use mapper functions.
- For DAO tests, use in-memory Room databases. **Migrations are tested with `MigrationTestHelper`** in `core/database/src/test/.../DatabaseMigrationTest.kt` — this file used to say not to, which was simply wrong about the code. `runMigrationsAndValidate` against the exported schema is the assertion that catches a column-type mismatch, and that class of bug fails *only on upgrade*, never on a fresh install.
- Current schema version: **v45** (dedupes `feed_items` and adds a unique index on `(mangaId, chapterId)`, from #1253. The dedupe has to run *before* the index is created or the `CREATE UNIQUE INDEX` fails on any database that already holds duplicate rows — which is every database that ran a library update under the previous build. Note the interaction with `FeedBuilderBottomSheet` described under the Feed screen: `insertFeedSource` is `OnConflictStrategy.REPLACE` against a unique index, so a re-add silently resets the user's row.) v44 added `relations` and `externalLinks` to `manga_metadata`, same JSON-text encoding as v43. Relations are filtered to `type == MANGA` at the mapper boundary — AniList returns anime adaptations among a manga's relations and this app has no anime surface, so such a tile could only do nothing when tapped. External links are filtered to `http`/`https` **twice**: once in the repository, deciding what is worth caching, and again in `ExternalLinkChips` before a URL becomes an Intent, because a row cached by an older build or restored from a backup never passed through today's mapper.) v43 added `characters` and `staff` to `manga_metadata` — the cast and credits carousels. Stored as **JSON text via a `kotlinx.serialization` TypeConverter**, not as the parallel delimited columns the tag fields use: a person has four fields and there are two such lists, so the delimited encoding would mean eight columns and eight chances for the length invariant to slip. `DatabaseConverters.fromPersonList` returns an empty list on a parse failure rather than throwing — safe *only* because this table is a disposable cache with a re-fetchable upstream; do not copy that to a table that owns its data. Both columns declare `@ColumnInfo(defaultValue = "[]")`, which must match the migration's `DEFAULT '[]'` or Room's validation fails on upgrade only.) v42 added `manga_anilist_link` — which AniList media a manga is, keyed by `mangaId`, `ON DELETE CASCADE` from `manga`, with a `userConfirmed` flag that auto-matching must never overwrite). Deliberately a second table rather than reusing `manga_metadata.anilistId`: the metadata row is a disposable 7-day cache — a fetch overwrites it wholesale, and one happens as soon as it goes stale or the AniList id changes — and a user's manual correction has to outlive it. v41 added `manga_metadata` — cached AniList metadata for the details screen, keyed by `mangaId`, `ON DELETE CASCADE` from `manga`, refreshed against a 7-day TTL). v40 added `update_errors` (per-manga current-unresolved library update failures, keyed by `mangaId`, replaced on each new failure and cleared on the manga's next successful update).
- **SQLite cannot `DROP COLUMN`** — to remove a column, CREATE TABLE new → INSERT INTO SELECT (omit removed column) → DROP TABLE old → RENAME new. When child tables have FK references to the table being recreated, wrap the entire block with `PRAGMA foreign_keys = OFF` (before) and `PRAGMA foreign_keys = ON` (after) to prevent `SQLITE_CONSTRAINT_FOREIGNKEY` on the DROP step.

---

## Extension System — Being Rebuilt on JavaScript

**The APK backend is being retired. JavaScript sources are the future of this app, and the target ecosystem is Mangayomi.**

This reverses what this file said until 2026-08-17. The old rule — *"Tachiyomi extension compatibility must never be broken"* — is **no longer in force**. It was removed deliberately, not by accident. Do not reinstate it, and do not treat the removal of APK code as a regression to revert.

### Why the direction changed

Mangayomi publishes its sources in **both JavaScript and Dart** (`/javascript` and `/dart` in `kodjodevf/mangayomi-extensions`, with separate contributing guides). The JavaScript half needs no Dart runtime, so Kotlin can consume it directly — no Flutter port, no `d4rt`, no platform channel. That makes a single cross-backend source model reachable without giving up the language the app is written in.

### The seam

`MangaSource` (in `source-api/`) is the only contract the rest of the app knows. The reader, library, downloads, migration and every domain use-case speak it, so **a backend is one implementation of one interface**, not a change to any of them. `source-api/` stays. It is no longer "the Tachiyomi API" — it is this app's own source contract.

### Current state

| Module | Status |
|---|---|
| `core/js-runtime/` | **The backend going forward.** QuickJS in a sidecar process, behind the `JsProtocol` JSON wire format. |
| `source-api/` | **Keep.** The `MangaSource` seam. |
| `core/extension/` | **Partly** retiring — the APK loader, classloader, signature verifier, trust store, installer and install receiver go. It also holds the `Extension`/`ExtensionSource`/`InstallStatus` models, the `ExtensionRepository`/`ExtensionRepoRepository` contracts, the blocklist and the `JsExtensionBackend` interface — **all of which the JavaScript path and the browse UI use**. Those stay; gut the module rather than deleting it. |
| `core/tachiyomi-compat/` | **Partly** retiring. The `eu.kanade.tachiyomi.source.*` surface (the Tachiyomi `Source`/`CatalogueSource`/`HttpSource` API and its RxJava-facing models) and the `compat/Tachiyomi*Adapter` classes go with the APK loader. **Three things in it must survive and be extracted first** — see below. |

#### What must be extracted before either module is deleted

An earlier version of this table said "nothing but the APK path uses these". That was wrong, and deleting on that basis would have removed a shipped feature:

| Survivor | Currently at | Why it stays |
|---|---|---|
| `LocalSource` | `core/tachiyomi-compat/src/main/java/app/otakureader/core/tachiyomi/local/` | **Local manga folders — a shipped feature.** Wired into `feature/settings` (`LocalSourceBrowserScreen`), `core/navigation` (`Route`), `core/preferences` (`LocalSourcePreferences`) and the app's NavHost. Nothing to do with APKs. |
| `SourceHealthMonitor` | `core/tachiyomi-compat/src/main/java/app/otakureader/core/tachiyomi/health/` | `SourceRepositoryImpl` routes **every** source call through it, JavaScript included — `JsSource`'s own comment relies on this for hung or broken scripts. |
| `eu.kanade.tachiyomi.network.*` | `core/tachiyomi-compat/src/main/java/eu/kanade/tachiyomi/network/` — `NetworkHelper`, `RateLimitInterceptor`, `AndroidCookieJar`, progress bodies | `OtakuReaderApplication` initialises `NetworkHelper` at startup. Check each class for live use before assuming it goes with the APK path. |

So the sequence is **extract, then delete** — never delete first. Verify with `grep -rn "import eu\.kanade\.tachiyomi" --include=*.kt .` and confirm the only remaining consumers are inside the two retiring modules.

### Rules for the JavaScript backend

- **Extensions run unmodified.** Anything a published Mangayomi source needs is the app's job to provide, not the source's job to change. If a real source fails, fix the runtime.
- **`prelude.js` is the compatibility layer**, and it is written in JavaScript on purpose. `QuickJsHost` installs flat, primitives-only bindings so no host object reference ever crosses into JS; extensions expect an object API (`new Client()`, `new Document(html)`, `doc.selectFirst(s)?.text`). Rebuilding that in JS keeps the isolation property. **Do not "simplify" this by having the Kotlin bindings hand out objects** — that trades the boundary away for the same result.
- **`MProvider` must exist before the extension script is evaluated.** Extensions name it in an `extends` clause, which resolves at class-definition time, so its absence fails the entire script rather than one method. This was broken for the whole life of the JS backend: `buildInvocation` referenced `MProvider` in a comment and nothing ever defined it.
- **Release every parsed document.** The handle pool caps at 32 and the host *refuses* rather than evicts. A selector that returns without releasing only fails against a page with enough rows to exhaust the pool, which is exactly the size of input a test does not use.
- **Preferences: stored value wins, declared default is the fallback.** Sources read preferences the user has never set — a mirror or base-URL override is near-universal — and ship the working value as that preference's declared default in `getSourcePreferences()`. Reading the declared default lazily (rather than seeding a copy at install) is what lets a source's *updated* default reach a user who never touched the setting.
- **Verify against real sources, not against the docs.** The Mangayomi contributing guide documents `getLatest`; every one of 18 sampled published sources defines `getLatestUpdates`. See the harness below.

### Testing the JavaScript runtime

The prelude **cannot be unit-tested from the JVM** — QuickJS ships as an Android artifact, so there is no engine to evaluate it in. `JsPreludeTest` therefore only guards packaging and the published global names.

Behaviour is covered by **`tools/js-prelude-harness/`**, a Node harness that reproduces `QuickJsHost.call` exactly (same binding signatures, same handle discipline, same 32-document cap, cheerio in place of Jsoup) and runs real extensions off the live index. It is deliberately not a Gradle module and never gates a PR, because it needs live network and third-party sites.

When a source misbehaves, **check the site with `curl` before suspecting this code.** In the sweep that validated the layer, every hard failure was external: a Cloudflare interstitial, a site that had moved domain since its extension was published, an upstream error, and a proxy block.

---

## Build System

### Convention Plugins (in `build-logic/`)

| Plugin ID | Applies to |
|-----------|-----------|
| `otakureader.android.application` | `app/` |
| `otakureader.android.library` | Most `core/*` and `data/`, `domain/` |
| `otakureader.android.feature` | All `feature/*` modules (auto-adds `core:ui`, `core:navigation`, `domain`) |
| `otakureader.android.hilt` | Any module needing DI |
| `otakureader.android.room` | Any module with Room entities/DAOs |
| `otakureader.android.library.compose` | Modules with Compose components |
| `otakureader.kotlin.library` | Pure JVM modules (server, domain utilities) |

### Key SDK & Kotlin Versions

| Setting | Value |
|---------|-------|
| Kotlin | 2.3.21 |
| AGP | 9.1.1 |
| KSP | 2.3.7 |
| compileSdk | 36 |
| minSdk | 26 |
| targetSdk | 36 |
| JVM target | 17 |
| Compose BOM | 2026.04.01 |

### Build Commands

The build is a **single flat artifact** — no product flavors.

| Command | Output |
|---------|--------|
| `./gradlew :app:assembleDebug` | Debug APK (fastest, development) |
| `./gradlew :app:assembleRelease` | Signed release APK (requires keystore) |

**Note:** The `full`/`foss` flavor dimension was removed. AI features are planned for a separate repo. See [Otaku-Reader-AI](https://github.com/Heartless-Veteran/Otaku-Reader-AI).

---

## Key Libraries

| Purpose | Library |
|---------|----------|
| UI | Jetpack Compose + Material 3 |
| Async | Kotlin Coroutines 1.10.2 + Flow |
| DI | Hilt 2.59.2 (KSP-processed) |
| Database | Room 2.8.4 |
| Preferences | DataStore 1.2.1 |
| Encryption | AndroidX Security Crypto 1.1.0 |
| HTTP | OkHttp 4.12.0 + Retrofit 3.0.0 |
| Serialization | Kotlinx Serialization 1.11.0 |
| Image loading | Coil 3 (3.4.0) |
| Paging | Paging 3 (3.4.2) |
| Background work | WorkManager 2.11.2 |
| Widgets | Glance 1.1.1 |
| Self-hosted server | Ktor 3.4.2 |
| Static analysis | Detekt 1.23.8 |
| Screenshot tests | Roborazzi |

---

## Testing

### Frameworks

| Tool | Role |
|------|------|
| JUnit 4 (primary) / JUnit 5 | Test runner |
| MockK 1.14.9 | Kotlin mocking DSL |
| Turbine 1.2.1 | Flow assertion (`.test { awaitItem() }`) |
| Robolectric 4.16.1 | Android environment simulation for unit tests |
| Roborazzi | Compose screenshot regression tests |
| `androidx-test` | AndroidX testing utilities |

### Patterns

```kotlin
@Test
fun `removeSelectedFromHistory emits ShowUndoBatchSnackbar`() = runTest {
    val viewModel = HistoryViewModel(mockRepository, testDispatcher)
    viewModel.effect.test {
        viewModel.onEvent(HistoryEvent.RemoveSelectedFromHistory)
        val effect = awaitItem()
        assertTrue(effect is HistoryEffect.ShowUndoBatchSnackbar)
        assertEquals(2, (effect as HistoryEffect.ShowUndoBatchSnackbar).count)
        // advanceUntilIdle() runs through the delay so DB calls fire
        advanceUntilIdle()
        coVerify { mockRepository.removeFromHistory(any()) }
    }
}
```

- Use `runTest { }` for all suspend functions.
- Use Turbine's `.test { }` for all Flow assertions.
- Mock all external dependencies with MockK (`mockk { }` or `every { }`).
- Use in-memory Room databases for DAO tests.
- Modules that need Android resources set `unitTests.isIncludeAndroidResources = true`.
- When a ViewModel emits an Effect inside a delayed coroutine, call `advanceUntilIdle()` before asserting DB calls — this runs through any `delay()` in the pending job.

---

## Proven Patterns (learned building this app)

### Undo Snackbar — Pattern A: Immediate-delete + Re-add (Library bulk delete)

Delete from DB immediately, show Undo snackbar. On undo, call the same toggle function again to re-add. Works when the underlying operation is a boolean toggle (favorite/unfavorite).

```kotlin
private fun removeSelectedFromLibrary() {
    val ids = selection.snapshotAndClear()
    if (ids.isEmpty()) return
    viewModelScope.launch {
        ids.forEach { runCatching { toggleFavoriteManga(it) } }   // delete immediately
        _effect.send(LibraryEffect.ShowUndoLibraryDelete(count = ids.size, mangaIds = ids))
    }
}

private fun undoLibraryDelete(mangaIds: Set<Long>) {
    viewModelScope.launch {
        mangaIds.forEach { runCatching { toggleFavoriteManga(it) } }  // re-add via same toggle
    }
}
```

### Undo Snackbar — Pattern B: Delayed-delete + Pending Filter (History batch delete)

Add IDs to `pendingDeleteIds` immediately so the UI filters them out (items visually disappear). Start a 4-second delay job, then delete from DB. On undo, cancel the job and remove from pending (items reappear). Track `pendingBatchDeleteIds` to guard against a stale snackbar's undo cancelling the wrong batch.

```kotlin
private var pendingBatchDeleteJob: Job? = null
private var pendingBatchDeleteIds: Set<Long>? = null
private val pendingDeleteIds = MutableStateFlow<Set<Long>>(emptySet())

private fun removeSelectedFromHistory() {
    val selectedIds = _state.value.selectedItems
    if (selectedIds.isEmpty()) return
    clearSelection()
    // Commit any previous pending batch first
    val previousIds = pendingBatchDeleteIds
    if (previousIds != null) {
        pendingBatchDeleteJob?.cancel()
        viewModelScope.launch {
            previousIds.forEach { chapterRepository.removeFromHistory(it) }
            pendingDeleteIds.update { it - previousIds }
        }
    }
    pendingBatchDeleteIds = selectedIds
    pendingDeleteIds.update { it + selectedIds }          // hide from UI immediately
    pendingBatchDeleteJob = viewModelScope.launch {
        _effect.send(HistoryEffect.ShowUndoBatchSnackbar(...))
        delay(UNDO_TIMEOUT_MS)
        selectedIds.forEach { chapterRepository.removeFromHistory(it) }
        pendingDeleteIds.update { it - selectedIds }
        pendingBatchDeleteIds = null
    }
}

private fun undoBatchRemoveFromHistory(chapterIds: Set<Long>) {
    if (pendingBatchDeleteIds != chapterIds) return       // stale undo guard
    pendingBatchDeleteJob?.cancel()
    pendingBatchDeleteIds = null
    pendingDeleteIds.update { it - chapterIds }           // restore items to UI
}
```

### Stable Flow in Compose

Wrap a flow derived inside a composable with `remember(key)` to prevent a new flow instance on every recomposition:

```kotlin
// Good — stable, only recreates when repository instance changes
val activeDownloadCount by remember(downloadRepository) {
    downloadRepository.observeDownloads()
        .map { downloads -> downloads.count { it.isActive } }
        .distinctUntilChanged()
}.collectAsStateWithLifecycle(initialValue = 0)

// Bad — new flow instance on every recomposition → excessive resubscription
val activeDownloadCount by downloadRepository.observeDownloads()
    .map { it.count { d -> d.isActive } }
    .collectAsStateWithLifecycle(0)
```

### Bottom Nav Badge Pattern

To add a badge to a nav tab, follow the Updates tab in `OtakuReaderBottomBar.kt`:
1. Add `count: Int = 0` parameter to `OtakuReaderBottomBar()`.
2. Wrap the tab icon in `BadgedBox { Badge { Text(...) }; Icon(...) }` when `count > 0`.
3. Use `stringResource(R.string.badge_count_overflow)` for values > 99 — never hardcode "99+".
4. Collect the count in `OtakuReaderApp()` using `remember(repository) { flow }.collectAsStateWithLifecycle(0)`.

### Never Stub Live UI

If a UI element exists (preference, button, tab), wire it to the real implementation. Never send a "not supported" snackbar for a feature that has a working backing implementation. The `setDeleteAfterReadOverride` stub (fixed in PR #1114) is the canonical example of what not to do.

---

## Self-Review Checklist — The Mistakes That Actually Get Made Here

These are not hypotheticals. Each one shipped in this repo, passed local tests, detekt and ktlint, and was caught by a review bot afterwards. They repeat, so check for them before opening a PR.

### 1. The comment describes the goal; the code achieves part of it

**This is the most common defect by a wide margin.** A rationale gets written for what the code is *meant* to do, the code implements most of it, and the gap is invisible afterwards — because the comment reads convincingly, nobody re-derives it. Real examples:

| What the comment claimed | What the code did |
|---|---|
| "The two backends share nothing" | Isolation ran one direction; an APK failure silently dropped every JS source |
| "Writing the manifest last means a half-finished install is invisible" | True for a fresh install, false for an update — where both files already exist |
| A test docstring: "locks the precedence rule down" | The test would have passed with the rule inverted |
| "Per-source scoping is a security boundary" | Those credentials were written to disk in cleartext |
| "The extension declares `class DefaultExtension extends MProvider`" | `MProvider` was never defined anywhere — the comment was the only mention of it in the repository, so every extension failed at script evaluation |
| "Mirrors the Mangayomi index shape… those sources work here unmodified" | The field *names* matched; `id` and `itemType` were numbers where the DTO wanted strings, and nothing read `sourceCodeLanguage`, so the index decoded to nothing or fed Dart files to a JavaScript engine |

**The check:** after writing a comment that asserts a property, re-read the code as if you had not written it and ask whether it actually has that property in *every* path — not just the one you had in mind. If the comment says "always" or "never", find the case where it doesn't.

**A sharper version of the same check, learned from the two rows above:** when a comment claims compatibility with something external — an index format, an extension API, a published contract — go and read the external thing. Both of those claims were written by someone reasoning about the format from memory, and both were *nearly* right, which is why they survived review. Fetching the actual `index.json` and 18 actual extensions took minutes and falsified both. See also checklist item 4.

### 2. Single-threaded reasoning about concurrent code

Four separate races shipped in one PR: a shared temp-file path, an uninstall undone by an in-flight call, concurrent calls overwriting each other's writes, and a refresh interleaving with an install.

**The check:** for anything touching shared state, disk, or a registry, ask "what if two of these run at once?" and "what if this one is halfway done when that one starts?" Read-then-act sequences need a lock across *both* steps, not on each separately — a liveness check and the write it guards must be inside the same lock.

### 3. A passing test that asserts the wrong thing

Twice a test was green while the behaviour was broken, both times because it asserted a **return value** when the bug was in **state left behind**. `refreshSources` returned the expected `Result.failure` while having emptied the source list.

**The check:** after asserting what a function returned, also assert what it left behind. And for any test whose name claims a rule, construct the case where the rule actually bites — a precedence test with no collision in it proves nothing.

### 4. Asserting a fact about a dependency from a convenient source

A claim about a library's published versions was taken from a search API that only listed pre-releases; the authoritative `maven-metadata.xml` said otherwise. An architecture decision was then made on the wrong premise.

**The check:** for a load-bearing claim about a dependency, read the artifact or the authoritative metadata, not a summary. `unzip` the AAR and check the bytes.

---

## Common Bug Areas — Check These First

1. **Hilt binding errors** — Missing `@Provides`, wrong scope, missing `@InstallIn`. Check the DI module before assuming the ViewModel is wrong.
2. **Room DAO not connected** — DAO not injected into repo, repo not injected into UseCase. Trace the chain.
3. **MVI state not updating UI** — `StateFlow` not collected in Compose, or reducer emitting same reference. Use `copy()`.
4. **JavaScript source failures** — check the site with `curl` first; most are Cloudflare, a moved domain, or the site being down. Then check, in order: is `MProvider` defined (a missing global fails the whole script, not one method); is the source reading a preference nobody set (declared defaults come from `getSourcePreferences()`); did a selector path leak a document handle. Reproduce with `tools/js-prelude-harness/`. *(APK loader failures — ClassLoader, permissions, interface mismatch — still exist while `core/extension` does, but that path is being removed and is not where new work goes.)*
5. **"Source not found" for a library manga** — someone passed `manga.sourceId.toString()` where a source id was wanted. Use `getSourceByKey` / `resolveSourceId`. See *Source Identity* above.
6. **Gradle dependency conflicts** — Version mismatches between Compose BOM, Kotlin, Hilt, or KSP. Check `libs.versions.toml` first.
7. **Navigation crashes** — Missing destination, wrong argument type in NavGraph, or missing `@Serializable` on route class.
8. **Coroutine scope leaks** — `GlobalScope` used instead of `viewModelScope` or `lifecycleScope`. Always use structured concurrency.
9. **Stale undo from concurrent batches** — Guard the undo handler: `if (pendingBatchIds != incomingIds) return`.
10. **Flow recreated on recomposition** — Wrap with `remember(key) { flow }` when derived from a `@Singleton` injected dependency.
11. **Room migration FK violations** — Recreating a table (to DROP a column) while child tables have FK references to it causes `SQLITE_CONSTRAINT_FOREIGNKEY`. Wrap the CREATE/INSERT/DROP/RENAME block with `PRAGMA foreign_keys = OFF` before and `PRAGMA foreign_keys = ON` after.

---

## Code Style Conventions

- Prefer extension functions over utility classes.
- Use `sealed class` for UI state, event, and effect modeling.
- Keep ViewModels thin — business logic belongs in UseCases.
- No hardcoded strings — use `strings.xml` resources.
- No magic numbers — use named constants in `companion object`.
- No XML layouts — this is a pure Jetpack Compose project.
- No `GlobalScope`.
- No `LiveData`.

---

## What NOT To Do

- **Do not reinstate the Tachiyomi APK backend.** This used to be the most critical constraint in this file and is now the opposite: the APK path is being retired in favour of JavaScript sources. See *Extension System*.
- **Do not edit an extension to make it work.** Published sources run unmodified; a failure is the runtime's to fix.
- **Do not let the Kotlin JS bindings hand host objects to JavaScript** — the compatibility layer belongs in `prelude.js`, which is what keeps the boundary primitives-only.
- **Do not implement AI features in core** — AI features belong in the separate Otaku-Reader-AI repo.
- **Do not add Firebase analytics or crash tooling** unless explicitly requested.
- **Do not use `fallbackToDestructiveMigration()`** in Room database setup.
- **Do not use `GlobalScope`** — use `viewModelScope`, `lifecycleScope`, or a provided `CoroutineScope`.
- **Do not use `LiveData`** — StateFlow only.
- **Do not write XML layouts** — Compose only.
- **Do not mutate ViewModel state directly** — all changes through Event → Reducer.
- **Do not stub UI features** — if a UI element exists, wire it up. Never send a "not supported" snackbar when a real implementation exists.
- **Do not skip undo on destructive bulk actions** — Library bulk delete, History batch delete, and Updates bulk mark-as-read all have undo snackbars; keep that standard going forward.

---

## CI/CD

| Workflow | Trigger | What It Does |
|----------|---------|--------------|
| `ci.yml` | PR to `main`, manual | **The only PR gate.** Security check, detekt, ktlint, unit tests, coverage gate, screenshot tests, assembleDebug + preview-APK PR comment, license report |
| `release.yml` | Tag push (`v*`) | Signed release APK, GitHub release |
| `benchmark.yml` | Manual | Baseline profile generation |
| `cert-pin-check.yml` | Monthly cron, manual | Certificate pinning verification |
| `extension-smoke-test.yml` | Manual only | Live-network extension loading check; never gates a PR |
| `pages.yml` | Push to `main` | Deploy VitePress website to GitHub Pages |

CodeQL runs through GitHub's **default setup** — there is no workflow file for it, so a failing `Analyze (java-kotlin)` check cannot be debugged from `.github/workflows/`.

**Third-party checks that are not gates.** A PR also shows `CodeFactor`, `sourcery-ai` and `submit-gradle`, none of which come from `.github/workflows/` and none of which are required. CodeFactor in particular reports issue *counts* on the check and renders the detail only in a client-side page, so its findings cannot be read from the API — open the linked page in a browser. Do not block on these; the required set is the `ci.yml` jobs listed above.

**Removed (do not re-add):**
- `build.yml` and `build_preview.yml` both ran `assembleDebug`, so every PR built the app three times for one artifact. That work is consolidated into `ci.yml`'s `assemble` job, which also posts the preview-APK comment (updating it in place instead of adding one per push).
- `label.yml` — low value, and it used the `pull_request_target` trigger, which runs with a write-scoped token against untrusted fork code.
- `review-on-mention.yml` — declared `permissions: reactions: write`, which **is not a valid GitHub Actions permission scope**. An invalid scope makes the whole workflow file fail to load, so it never ran its `issue_comment` trigger even once, and instead produced a failed run named by file path on every `push`. If you ever add a workflow that needs to react to comments, note that reactions are covered by `issues: write` / `pull-requests: write` — there is no separate `reactions` scope.

**Valid `permissions:` scopes** (an invalid key silently breaks the entire workflow): `actions`, `attestations`, `checks`, `contents`, `deployments`, `discussions`, `id-token`, `issues`, `models`, `packages`, `pages`, `pull-requests`, `repository-projects`, `security-events`, `statuses`.

CI uses JDK 21. Gradle setup/caching is handled by `gradle/actions/setup-gradle`. All actions are pinned to commit SHAs — keep it that way.

**Known CI flake:** `Analyze (java-kotlin)` (CodeQL) occasionally fails with "CodeQL could not process any code written in Java/Kotlin" — this is an intermittent GitHub infra issue unrelated to code correctness. The Gradle build itself succeeds; only the CodeQL database finalization fails. GitHub typically retries the workflow automatically and the second run succeeds. If the concurrent successful `Analyze (java-kotlin)` check is green, the stale failure is safe to ignore. All other checks (Unit Tests, Detekt, Ktlint, Assemble, Coverage Gate, Screenshot Tests) must be green before merging.

---

## Developer Context

- Solo developer, veteran background, newer to Kotlin — explain fixes, don't just drop code.
- Multi-agent workflow: Claude (architecture + debugging), Copilot (day-to-day), Gemini Code Assist, Kimi Claw (bulk GitHub tasks).
- **Current priority: the JavaScript source-system rebuild.** The AniList metadata backbone is done — Stage 5a shipped in #1232/#1234/#1235/#1236, and the details-screen metadata layer in #1238–#1250. The v1.0.0 tag waits on the source rebuild.

  Where it stands:

  | Milestone | State |
  |---|---|
  | 1. Run Mangayomi JS extensions unmodified | Shipped — PR #1262 (`prelude.js`, `MProvider`, real-index decoding, declared preference defaults) |
  | 2. Retire `core/extension` + `core/tachiyomi-compat` | Not started (~7,700 LOC to remove) |
  | 3. Library migration + the downloads decision (#1256) | Not started — **must ship with milestone 2**, see *Source Identity* |

  Milestone 1 is verified against real sources but is not the same as "the ecosystem works": only ~114 of the 363 entries in the Mangayomi index are JavaScript. Source coverage relative to the current APK set is an open question and should be measured before milestone 2 commits.

---

## Audit Workflow (Archived)

The full-systems audit from 2026-05-24 has been completed and its artifacts archived in `.github/audit-archive/`. The audit validated alpha readiness (all gates green) and informed the beta feature parity backlog.

**Current workflow:** Features are tracked as individual GitHub issues. See [ROADMAP.md](ROADMAP.md) for the full release history.

**Legacy audit files (reference only):**
- `.github/audit-archive/AUDIT_MASTER.md`
- `.github/audit-archive/AUDIT_ARCHITECTURE.md`
- `.github/audit-archive/AUDIT_CODE_SMELLS.md`
- `.github/audit-archive/AUDIT_FEATURES.md`
- `.github/audit-archive/AUDIT_PERFORMANCE.md`
- `.github/audit-archive/AUDIT_SECURITY.md`
- `.github/audit-archive/AUDIT_TESTING.md`
- `.github/audit-archive/AUDIT_UI.md`
- `.github/audit-archive/PATCH_QUEUE.md`

---

*CLAUDE.md maintained by the core team. For release planning, see [ROADMAP.md](ROADMAP.md).*
