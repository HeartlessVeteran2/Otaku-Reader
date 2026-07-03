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
| 5 | Updates / History / Downloads | **Next** | J2K grouping, swipe actions, real-time progress |
| 6 | Browse: global search / migrate / feed ordering | Pending | |
| 7 | Settings | Pending | Match Komikku's settings tree, immediate-apply semantics |
| 8 | More / stats / remaining screens | Pending | |

## Current Session: Updates / History / Downloads (item #5)

Komikku spec files to read:
- `eu.kanade.presentation.updates.UpdatesScreen` + `UpdatesScreenModel` — J2K-style date grouping,
  swipe/long-press actions, per-item download/read state
- `eu.kanade.presentation.history.HistoryScreen` + `HistoryScreenModel` — timeline grouping, resume
- `eu.kanade.presentation.download.DownloadQueueScreen` — real-time progress, reorder, pause/resume

Key elements to check:
- Date-header grouping (Today/Yesterday/this week/older) matching J2K conventions
- Swipe-to-* actions (mark read, delete, bookmark) vs long-press context menus
- Real-time download queue progress (bytes/percent, reorder via drag, pause/resume/cancel per item)
- Bulk actions + undo snackbars (compare against Otaku's existing undo patterns — CLAUDE.md
  documents Pattern A/B already used for Library/History)

Gap areas likely in Otaku:
- `feature/updates/`, `feature/history/`, `feature/more/downloads` (or wherever the download
  manager screen lives) — Screen + ViewModel

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
