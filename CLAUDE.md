# CLAUDE.md — Otaku Reader

This file is the AI assistant reference for the Otaku Reader codebase. Read it before making any changes.

---

## What This Project Is

Otaku Reader is a production-grade Android manga reader built entirely in Kotlin and Jetpack Compose by a solo developer. It is a clean-architecture alternative to Mihon/Tachiyomi that inherits the Tachiyomi extension ecosystem. The app is feature-complete: all 35 parity issues, the hardening batch, and the post-beta polish pass have shipped.

**Status:** All phases shipped. Alpha (2026-05-25) → Beta parity (#926–#958) → Hardening (#1090–#1099) → P3 polish (#1114) → Post-P3 additions: QR library sharing (#1110/#1125), update-errors screen (#1119), stats improvements (#1122), in-chapter download button (#1127), page bookmarks + collections (PR #1130, merged 2026-06-20) → Komikku-parity deferred-gaps batch (issue #1192, 7 PRs #1197/#1202–#1207, merged 2026-07-05 → 2026-07-06 — statistics Trackers card, settings wiring batch, keep-last-N delete-after-read, onboarding storage step, dedicated Update Errors screen, configurable migration options + custom-cover migration, selective backup/restore; remaining deferred items spun out to #1208). **Current phase: cutting v1.0.0 release tag.** Project website: https://heartless-veteran.github.io/Otaku-Reader/ — VitePress landing page, download with live version lookup, docs, FAQ, auto-synced changelog. Deployed from `website/` via `pages.yml`.

**The developer is newer to Kotlin. Always explain what was wrong and why a fix works — never drop solutions without context.**

---

## Repository Layout

```
Otaku-Reader/
├── app/                    # Application entry point, Navigation host, Widgets, DI root
├── domain/                 # Pure business logic — UseCases, Repository interfaces
├── data/                   # Repository implementations, WorkManager workers, Backup/Sync
├── source-api/             # Tachiyomi extension API contracts (pure Kotlin/Java interfaces)
├── server/                 # Self-hosted Ktor sync server (optional, separate repo)
├── baselineprofile/        # Baseline profile generation for app startup performance
├── website/                # VitePress project site, deployed to GitHub Pages by pages.yml
├── build-logic/            # Gradle convention plugins
├── core/
│   ├── common/             # Shared utilities, Palette API, coroutine helpers
│   ├── database/           # Room entities, DAOs, migrations (current schema v41)
│   ├── network/            # OkHttp + Retrofit + Kotlinx Serialization setup
│   ├── preferences/        # DataStore preferences, encrypted credential storage
│   ├── ui/                 # Shared Compose components, Material 3 theme, Coil integration
│   ├── navigation/         # Type-safe Compose Navigation routing
│   ├── extension/          # Dynamic APK classloading for Tachiyomi extensions
│   ├── tachiyomi-compat/   # RxJava 1.x stubs and Tachiyomi interface bridges
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
    └── more/               # Bottom nav "More" section
```

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
- AniList metadata section (`AniListMetadataSection.kt`): community stats grid, rank-weighted tags ("Isekai 87%", tap → source search / long-press → global search), alternative titles. Read from `MangaMetadataRepository` as a **separate flow** — never folded into `Manga`. AniList's description and genres fill in only where the source left a gap; the two are never shown together. The screen renders `State.metadata`, which is *derived* — it hides `cachedMetadata` whose `anilistId` no longer matches the live AniList link, because the cache row is keyed by `mangaId` and deliberately survives both a failed refresh and a link change. The AniList media id comes from the AniList `TrackEntry.remoteId` when the manga is tracked there, and otherwise from a stored link in `manga_anilist_link` written by auto-matching (`ResolveAniListMediaUseCase` → `MatchAniListMediaUseCase`). The tracker entry always wins: `State.metadata` hides cached metadata whose `anilistId` disagrees with the live id, so a stale stored link shadowing a tracker entry would stop a corrected tracker link from invalidating the cache. Auto-matching runs once per screen visit and **only persists a `confident` match** — below `ACCEPT_THRESHOLD` nothing is stored and nothing renders, because a wrong synopsis and wrong tags look authoritative.
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
- Backup: export and import (native format + Tachiyomi import), with selective per-category backup/restore (`BackupOptions`, PR #1207)

### More tab (`feature/more/`)
- Bookmarks screen: page-level bookmarks, collection filtering, multi-select, export (PR #1130)
- History screen
- Statistics screen
- Feed screen (activity from followed manga)
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
- Current schema version: **v42** (adds `manga_anilist_link` — which AniList media a manga is, keyed by `mangaId`, `ON DELETE CASCADE` from `manga`, with a `userConfirmed` flag that auto-matching must never overwrite). Deliberately a second table rather than reusing `manga_metadata.anilistId`: the metadata row is a disposable 7-day cache that `clearMetadata` throws away, and a user's manual correction has to outlive it. v41 added `manga_metadata` — cached AniList metadata for the details screen, keyed by `mangaId`, `ON DELETE CASCADE` from `manga`, refreshed against a 7-day TTL). v40 added `update_errors` (per-manga current-unresolved library update failures, keyed by `mangaId`, replaced on each new failure and cleared on the manga's next successful update).
- **SQLite cannot `DROP COLUMN`** — to remove a column, CREATE TABLE new → INSERT INTO SELECT (omit removed column) → DROP TABLE old → RENAME new. When child tables have FK references to the table being recreated, wrap the entire block with `PRAGMA foreign_keys = OFF` (before) and `PRAGMA foreign_keys = ON` (after) to prevent `SQLITE_CONSTRAINT_FOREIGNKEY` on the DROP step.

---

## Extension System — Non-Negotiable

**Tachiyomi extension compatibility must never be broken.**

Extensions load dynamically as APKs with classloader isolation. The interfaces in `source-api/` and `core/tachiyomi-compat/` must match the Tachiyomi/Mihon extension API exactly — this gives Otaku Reader access to 500+ community-maintained sources without any changes to those extensions.

- Do not modify interface signatures in `source-api/`.
- Do not change or remove the Tachiyomi RxJava 1.x stubs in `core/tachiyomi-compat/`.
- Reference the Komikku fork in the same GitHub org when uncertain about the extension API.

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

**The check:** after writing a comment that asserts a property, re-read the code as if you had not written it and ask whether it actually has that property in *every* path — not just the one you had in mind. If the comment says "always" or "never", find the case where it doesn't.

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
4. **Extension loader failures** — ClassLoader issue, missing permission, or interface mismatch with Tachiyomi API.
5. **Gradle dependency conflicts** — Version mismatches between Compose BOM, Kotlin, Hilt, or KSP. Check `libs.versions.toml` first.
6. **Navigation crashes** — Missing destination, wrong argument type in NavGraph, or missing `@Serializable` on route class.
7. **Coroutine scope leaks** — `GlobalScope` used instead of `viewModelScope` or `lifecycleScope`. Always use structured concurrency.
8. **Stale undo from concurrent batches** — Guard the undo handler: `if (pendingBatchIds != incomingIds) return`.
9. **Flow recreated on recomposition** — Wrap with `remember(key) { flow }` when derived from a `@Singleton` injected dependency.
10. **Room migration FK violations** — Recreating a table (to DROP a column) while child tables have FK references to it causes `SQLITE_CONSTRAINT_FOREIGNKEY`. Wrap the CREATE/INSERT/DROP/RENAME block with `PRAGMA foreign_keys = OFF` before and `PRAGMA foreign_keys = ON` after.

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

- **Do not break Tachiyomi extension compatibility** — this is the most critical constraint.
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
- **Current priority: the source-system rebuild + AniList metadata backbone.** Stage 5a (AniList tracker correctness) shipped in #1232/#1234/#1235/#1236; Stage 5b is in progress. The v1.0.0 tag waits on that work.

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
