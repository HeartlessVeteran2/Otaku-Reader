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
- **Room schema bumps** require an explicit migration + schema version increment (currently v39).
- **Never break Tachiyomi extension compatibility** — this is the highest-priority constraint.

## Backlog (in order)

| # | Area | Status | Notes |
|---|------|--------|-------|
| 1 | Browse: Extensions + Sources | Done (PR #1145) | Extension install → source appears fix |
| 2 | Library | Done (PR #1155) | Grid/list/comfortable/cover-only modes, tristate filters, filter sheet, RANDOM sort, bulk-select |
| 3 | Manga detail | Done (PR #1156, #1186) | Collapsing header, chapter list, tracker sheet, tag press, tap-to-search, cancel download, delete-downloads prompt, migrate action, source label |
| 4 | Reader | Done (verified pre-existing) | Diffed against Komikku's ViewerNavigation/PagerConfig/WebtoonConfig, ChapterNavigator, ReadingModePage — tap zones (exact NavigationRegion color/enum match), slider snap, chapter transitions w/ gap warnings, live-applied settings, rotation, volume keys, save/share all already at parity. No gaps found worth a PR. |
| 5 | Updates / History / Downloads | Done (PR #1187) | Updates/History already had J2K-style date grouping + swipe-to-dismiss (no gaps). Downloads queue was missing Komikku's Sort-by-upload-date/chapter-number menu — added, reusing existing reorderDownload API. Per-item drag-reorder (Komikku's legacy RecyclerView) not ported — out of scope for a pure-Compose project. |
| 6 | Browse: global search / migrate / feed ordering | Partially done (PR pending) | Global search: already matches Komikku (per-source sections, independent loading/error states). Migrate: fixed a real gap — user notes weren't copied to the target manga on migration (now fixed, guarded against clobbering an existing target's notes). Custom-cover migration and a Komikku-style configurable migration-options dialog (toggle chapters/categories/tracking/notes/custom-cover/remove-old-download individually, matching `MigrationFlag`) are NOT implemented — Otaku's `MigrateMangaUseCase` always migrates everything unconditionally with no per-field opt-out UI. Feed ordering: found `FeedRepository.updateSavedSearchOrder()` and `updateFeedSourceOrder()` already exist but are entirely unused/orphaned — no UI anywhere calls them. Also found two overlapping, competing "saved search" concepts in Browse: `SavedSourceSearch` (no order field, IS wired to UI as chips) and `FeedSavedSearch` (has an `order` field, observed into `BrowseState.savedSearches` but never rendered or wired to any button). Needs a decision on which concept to keep/consolidate before building reorder UI — flagged rather than guessed. |
| 7 | Settings | Pending | Match Komikku's settings tree, immediate-apply semantics |
| 8 | More / stats / remaining screens | Pending | |

## Open Question: two competing saved-search concepts in Browse (blocks feed-ordering reorder UI)

`feature/browse/BrowseMvi.kt`/`BrowseViewModel.kt` carry two parallel "saved search" features:
- `SavedSourceSearch` (`namedSavedSearches` in state) — no `order` field, rendered as chips in
  `BrowseScreen.kt`, fully wired (apply/delete work from the UI).
- `FeedSavedSearch` (`savedSearches` in state) — HAS an `order` field and a working
  `FeedRepository.updateSavedSearchOrder()`, but `BrowseEvent.ApplySavedSearch`/`DeleteSavedSearch`
  are never dispatched from any UI element, and `state.savedSearches` is never rendered anywhere.
  Same story for `FeedRepository.updateFeedSourceOrder()` / `FeedSource` — plumbing exists,
  nothing calls it.

Before building Komikku's `FeedOrderScreen`-equivalent reorder UI, need a decision: consolidate
onto one saved-search concept (likely delete the unused `FeedSavedSearch` path and add an `order`
field to `SavedSourceSearch` instead, since that's the one actually wired to the UI), or determine
`FeedSavedSearch` was meant for a different, not-yet-built screen and should stay separate. Ask the
user before removing or repurposing either — this is exactly the kind of architectural fork that
needs their call, not a guess.

## Current Session: Settings (item #7)

Komikku spec files to read:
- `eu.kanade.presentation.more.settings.screen.*` — the full settings tree (Appearance, Library,
  Reader, Downloads, Tracking, Backup, Browse, Security, Advanced, etc.)
- Check immediate-apply semantics: Komikku settings changes apply live (no "Save" button, no
  restart needed) — verify every Otaku settings screen matches this.

Key elements to check:
- Does Otaku's settings tree structure/grouping match Komikku's screen-by-screen (missing
  sections, extra sections not clearly marked Otaku-exclusive)?
- Do all toggles/pickers apply immediately, matching Komikku's DataStore-backed live-apply pattern?
- Backup: does export/import cover the same fields Komikku's backup format does?

Gap areas likely in Otaku:
- `feature/settings/` — all screens under it

## Previous Session: Browse — global search / migrate / feed ordering (item #6) — partially done

Global search already matches Komikku (per-source sections, independent per-source loading/error
states, results grouped correctly). Migrate: fixed a real gap in `MigrateMangaUseCase` — user
notes weren't being copied to the target manga (Komikku's `NOTES` migration flag equivalent); now
copied, guarded so it never overwrites notes already present on an existing target. Two migrate
gaps remain, deliberately deferred as bigger scope: (1) custom-cover migration (would need file
I/O across manga IDs, harder to verify without a device), (2) a configurable migration-options
dialog matching Komikku's `MigrationFlag` (CHAPTER/CATEGORY/TRACK/CUSTOM_COVER/NOTES/
REMOVE_DOWNLOAD) — Otaku always migrates everything unconditionally today, which is a reasonable
default but not user-configurable like Komikku's dialog. Feed ordering: investigation surfaced an
architectural question (see "Open Question" above) rather than a simple gap — deferred pending
user input rather than guessed at.

## Previous Session: Updates / History / Downloads (item #5) — done

Updates already had J2K-style date grouping (`buildJk2UiModel`/`Jk2DateHeader`/`Jk2UpdateItem`) and
swipe-to-dismiss via `SwipeToDismissBox` — matches Komikku, no gap. History already had date-bucket
grouping (`historyDateBucket`/`HistoryDateHeader`) and swipe-to-dismiss — matches Komikku, no gap.
Downloads queue was missing Komikku's `DownloadQueueScreen` "Sort" menu (order by upload date
newest/oldest, order by chapter number asc/desc) — added in PR #1187 via `DownloadsViewModel
.sortQueue()`, which looks up each queued chapter's metadata and reassigns sequential priorities
through the existing `DownloadRepository.reorderDownload()` call (confirmed `prioritizeDownloads()`
can't be reused for this — it preserves each target's *existing* relative priority order rather than
accepting a caller-supplied order, so it can't express an arbitrary sort). Per-item manual
drag-to-reorder (Komikku's side uses a legacy `RecyclerView`+`ItemTouchHelper` via `AndroidView`) was
deliberately not ported — this is a pure-Compose project and a custom drag-reorder `LazyColumn`
would be a much larger, separate effort; flagged here in case it's wanted as a future item.

## Previous Session: Reader (item #4) — done, no gaps

Diffed against Komikku's `ViewerNavigation`/`KindlishNavigation`/`EdgeNavigation`/
`RightAndLeftNavigation`/`LNavigation`, `PagerConfig`, `WebtoonConfig`, `ReadingModePage.kt`
(settings), `ChapterNavigator`. Otaku's `feature/reader/ui/TapZoneOverlay.kt` and `PageSlider.kt`
explicitly document themselves as ports of these Komikku systems (exact `NavigationRegion` color
values, same 6 navigation-mode layouts, same tapping-invert-mode semantics). Real-time settings
(brightness/color filter/crop borders) are plain `StateFlow` fields consumed directly in
`ReaderScreen.kt`, so they apply live. Chapter transitions include gap-warning detection matching
Komikku. Rotation override, volume-key paging, and save/share hooks are all present in
`ReaderMvi.kt`/`ReaderViewModel.kt`. Conclusion: no PR needed for this item.

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
