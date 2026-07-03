# Komikku Parity Methodology

Goal: Otaku Reader must look exactly like and function exactly like Komikku/Mihon — same screens,
layout, gestures, state behavior, and flows. New Otaku-exclusive features stay additive on top,
never replacing Komikku equivalents.

## Spec Location

- **Authoritative spec**: `/home/user/komikku-HV` — a Mihon-derived Komikku fork
  - Screens: `app/src/main/java/eu/kanade/presentation/...`
  - ScreenModels: `app/src/main/java/eu/kanade/tachiyomi/ui/...`
- **Do not edit** `/home/user/komikku-HV` — it is read-only reference only
- **All changes go to** `/home/user/Otaku-Reader`

## Per-Screen Methodology

For each screen area, follow this loop:

1. **Diff the spec** — open the Komikku screen + its ScreenModel; list every UI element,
   interaction (tap/long-press/swipe), state behavior (scroll/filter persistence, bulk-select),
   empty/loading/error state, and animation.
2. **Diff Otaku's version** — open the matching `feature/*` Screen + ViewModel + MVI.
3. **Produce a per-screen gap list** — visual gaps, behavior gaps, broken flows.
4. **Implement** to match exactly (Compose/M3, MVI rules). Keep Otaku-exclusive additions under
   clearly-labeled sections, never replacing Komikku equivalents.
5. **Verify** — build passes, unit tests green, visual/behavior check.

## Hard Constraints

- **Never modify** `source-api/` interface signatures — Tachiyomi extension API contract.
- **Never remove** the RxJava 1.x stubs in `core/tachiyomi-compat/` — extensions depend on them.
- **Additive only** — never delete existing routes, entities, or DataStore keys.
- **Room schema bumps** require an explicit migration + schema version increment (currently v40).
- **Never break Tachiyomi extension compatibility** — this is the highest-priority constraint.

## Backlog (in order)

| # | Area | Status | Notes |
|---|------|--------|-------|
| 1 | Browse: Extensions + Sources | Done (PR #1145) | Extension install → source appears fix |
| 2 | Library | Done (PR #1155) | Grid/list/comfortable/cover-only modes, tristate filters, filter sheet, RANDOM sort, bulk-select |
| 3 | Manga detail | Done (PR #1156, #1186) | Collapsing header, chapter list, tracker sheet, tag press, tap-to-search, cancel download, delete-downloads prompt, migrate action, source label |
| 4 | Reader | Done (verified pre-existing) | Diffed against Komikku's ViewerNavigation/PagerConfig/WebtoonConfig, ChapterNavigator, ReadingModePage — tap zones (exact NavigationRegion color/enum match), slider snap, chapter transitions w/ gap warnings, live-applied settings, rotation, volume keys, save/share all already at parity. No gaps found worth a PR. |
| 5 | Updates / History / Downloads | Done (PR #1187) | Updates/History already had J2K-style date grouping + swipe-to-dismiss (no gaps). Downloads queue was missing Komikku's Sort-by-upload-date/chapter-number menu — added, reusing existing reorderDownload API. Per-item drag-reorder (Komikku's legacy RecyclerView) not ported — out of scope for a pure-Compose project. |
| 6 | Browse: global search / migrate / feed ordering | Done (PR #1188) | Global search already matched Komikku. Migrate: fixed notes not being copied to the target manga (Komikku's NOTES flag equivalent); custom-cover migration and a full configurable migration-options dialog (matching Komikku's `MigrationFlag` toggles) remain unimplemented — Otaku migrates everything unconditionally, a reasonable default but not user-configurable — flagged as a possible future item, not blocking. Feed ordering: consolidated two competing saved-search concepts onto `SavedSourceSearch` (added `order` field, removed dead `FeedSavedSearch` UI wiring, added move-earlier/move-later reorder buttons) per user decision; `FeedRepository`/`FeedDao`/backup integration left untouched since it's a real Room-backed subsystem, not dead code. |
| 7 | Settings | Done (PR #1189) | Full screen-by-screen diff done (Appearance/Library/Reader/Downloads/Tracking/Security/Data/Browse/Advanced). Every live toggle already applies immediately via DataStore, no save/restart gaps found. Biggest find wasn't structural: **delete-after-reading was completely unwired** — the global toggle and per-manga override (Settings > Downloads, manga detail) persisted to DataStore and had a full UI, but nothing anywhere consumed them, so toggling it silently did nothing (a live "Never Stub Live UI" violation, same class of bug as the pre-#1114 `setDeleteAfterReadOverride` stub CLAUDE.md calls out). Fixed with `ReaderDeleteAfterReadDelegate` on the live in-session `saveCurrentProgress()` path, plus (per Gemini review — the initial `cleanupOnExit()` wiring turned out to be dead code, since `viewModelScope` is cancelled before `onCleared()` runs) a durable WorkManager counterpart in `RecordReadingHistoryWorker`, sharing one `resolveShouldDeleteAfterRead()` decision function in `core/preferences`. Also deleted a pair of dead `OpenAutoDownloadCategoryIncludePicker`/`ExcludePicker` events (`DownloadSettingsDelegate`). Remaining structural gaps catalogued but deliberately left out of scope (see below) since they either need substantial new infra Otaku doesn't have (cookie jar, DoH, user agent override, verbose-logging plumbing) or don't map onto Otaku's data model (Komikku's "protect bookmarked chapters from delete" — Otaku only has page-level bookmarks since PR #1130, not chapter-level). |
| 8 | More / stats / remaining screens | In progress (PR pending) | More tab: fixed (PR #1190) — live download-queue subtitle via `DownloadQueueDisplayState`. Stats screen: fixed (PR #1191) `completedMangaCount` and `totalChapterCount` gaps — `ReadingStats` now carries both, sourced from `MangaDao.getCompletedMangaCount()` (pre-existing query) and a new `ChapterDao.getTotalChapterCountForLibrary()` query; displayed in a new `LibraryDetailCard` on `StatisticsScreen` below the hero card. About screen: fixed (bundled into PR #1191) — Changelog/Open Source Licenses taps were dead (called a nav param the graph never supplied); pointed at the real VitePress changelog page and the CI-generated `docs/DEPENDENCY_LICENSES.md`. Update Errors: **major functional bug found and fixed** — `UpdatesState.updateErrors` had no producer anywhere in the codebase; `LibraryUpdateWorker` counted `failedUpdates++` on failure but discarded the exception, so the Update Errors dialog (and its top-bar badge, and the More-tab entry) could never show a real error no matter what actually failed, despite CLAUDE.md claiming this shipped in PR #1119. Fixed with a proper Komikku-style persistent subsystem: new `update_errors` Room table (schema v40, `MIGRATION_39_40`, PK = `mangaId` so a new failure replaces the old one, matching Komikku's "current unresolved errors" model), `UpdateErrorDao`/`UpdateErrorRepository`/`UpdateErrorRepositoryImpl`, wired into `LibraryUpdateWorker` (records the real exception message on failure, clears on next success) and `UpdatesViewModel` (`observeUpdateErrors()` replaces the old dead in-memory-only `ClearUpdateError`/`ClearAllUpdateErrors` handlers with real repository-backed ones). Still not built: `downloadCount` (no existing query — downloads aren't tracked as a `chapters` column, would need a query against the download-queue's own table) and the entire Trackers section (tracked-title count / mean score / tracker count — `TrackRepository` only has `observeEntriesForManga(mangaId)`, no cross-manga "all entries" query yet; would need a new DAO method). Also deferred: Komikku's Update Errors is a full dedicated screen (sticky-header grouping by error message, multi-select, migrate-selected, scroll-nav bottom bar) vs. Otaku's flat `AlertDialog`; and the More-tab "Update Errors" entry still lands on the plain Updates screen rather than auto-opening the error view (one extra tap, no longer a dead end now that errors are real) — both flagged to issue #1192 rather than risk a nav-graph restructure without a compiler to verify against. Onboarding: diffed, no code changes made this round — Otaku's swipeable-pager onboarding (Welcome/Name/Notifications/Battery/Appearance/Extensions) is structurally different from Komikku's step-wizard (Theme/Storage/Permission/Guides), too different to safely rewrite blind; two concrete functional gaps flagged to #1192 instead: no storage-location step gating onboarding completion (Komikku blocks finish until a writable directory is confirmed; Otaku's equivalent picker exists in Settings > Downloads but is never surfaced during onboarding), and no "install unknown apps" permission checklist item despite Otaku's extension system depending on the same APK-install mechanism Komikku flags this permission for. Remaining screens not yet diffed: QR Library Share/Scan. |

## Prior Session: Settings (item #7) — deferred gaps, not implemented

Catalogued via full screen diff but intentionally not built this round — flagged as possible
future items, not blockers:
- **Selective backup/restore content** — Komikku's `CreateBackupScreen`/`RestoreBackupScreen` let
  you check/uncheck exactly which data categories (library, tracking, history, settings, ...) go
  into a backup or get restored. Otaku's backup is all-or-nothing. Biggest real capability gap
  found; a substantial feature on its own.
- **Komikku's "Advanced" settings screen has no Otaku equivalent at all** — clear database, clear
  cookies/WebView data, DNS-over-HTTPS provider, user-agent override, verbose-logging toggle,
  extension-installer choice (System/Shizuku/Private). None of these have supporting
  infrastructure in Otaku today (no cookie jar is even configured on the shared OkHttpClient —
  it's `CookieJar.NO_COOKIES` by default); building the screen would mean building the
  infrastructure first, out of scope for a settings-parity pass. The two trivially-portable items
  (disable-battery-optimization shortcut, "Don't kill my app" link) already exist in Otaku's
  onboarding flow but aren't reachable again afterward — small, low-priority gap.
- Reader: no `removeAfterReadSlots` (keep last N read chapters), Library: no
  charging/network-metered auto-update restrictions (Wi-Fi-only boolean only), Tracking: no
  `trackOnAddingToLibrary`/`autoUpdateTrackOnMarkRead` opt-outs, Security: no
  `hideNotificationContent` — all real but small, independent gaps, none blocking.

## Prior Session Summaries (items #1–6)

See the Backlog table above for the one-line outcome of each completed item; full session
narratives have been trimmed from this doc to keep it manageable. Notable reusable findings:
- `prioritizeDownloads()`/`FeedRepository.updateSavedSearchOrder()`-style "move to front" APIs
  preserve each target's *existing* relative order — they can't express an arbitrary caller-supplied
  sort. Don't reach for them when building a "sort by field" or "reorder" feature; a full
  read-modify-write of the list (ideally Mutex-serialized) is the correct pattern instead.
- Per-item drag-to-reorder (Komikku's `DownloadQueueScreen`/`FeedOrderScreen` both use a legacy
  `RecyclerView`+`ItemTouchHelper` via `AndroidView`) has not been ported anywhere in Otaku — this
  is a pure-Compose project, and a custom drag-reorder `LazyColumn` would be a real, separate
  effort each time it comes up. Flagged as a recurring possible future item, not a blocker.
- `ViewModel.clear()` closes every tagged closeable — including `viewModelScope`'s
  `CloseableCoroutineScope` — *before* calling `onCleared()`. Any `viewModelScope.launch { }` (or
  a `@VisibleForTesting` helper only ever invoked by tests, never by `onCleared()` itself) is dead
  code in production once you're inside `onCleared()`; only something that outlives the ViewModel
  (WorkManager, an application-scoped `CoroutineScope`) reliably runs on reader/screen exit. Caught
  by Gemini review on PR #1189 — verify this ordering before trusting any "cleanup on exit" logic
  that lives inside `onCleared()`.

## Commit / PR Workflow

See `session-rules` skill for complete rules. Summary:
1. All work on branch `claude/otaku-reader-audit-c4b7uo`
2. After push: create draft PR if none exists
3. Merge when all CI checks are `"success"` (CodeQL flake is safe to ignore)
4. Start next item immediately — no pause for user confirmation

## Key Komikku File Paths (reference)

```text
komikku-HV/app/src/main/java/eu/kanade/presentation/
  library/          LibraryTab.kt, LibraryContent.kt, LibraryPager.kt
  manga/            MangaScreen.kt, components/
  browse/           source/SourcesScreen.kt, extension/ExtensionsScreen.kt
  updates/          UpdatesScreen.kt
  history/          HistoryScreen.kt

komikku-HV/app/src/main/java/eu/kanade/tachiyomi/ui/
  library/          LibraryScreenModel.kt
  manga/            MangaScreenModel.kt
  browse/source/    SourcesScreenModel.kt
```

## What Counts as Done

A screen area is done when:
- Build passes (`./gradlew :app:assembleDebug`)
- All CI checks green (Detekt, ktlint, unit tests, screenshot tests, coverage gate)
- The screen matches Komikku's layout, interactions, and state behaviors from the diff
- Otaku-exclusive features are preserved alongside (not replaced)
