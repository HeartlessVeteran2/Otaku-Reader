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
| 4 | Reader | **Next** | Modes, tap zones, end/start overlays, slider snap, rotation, real-time settings |
| 5 | Updates / History / Downloads | Pending | J2K grouping, swipe actions, real-time progress |
| 6 | Browse: global search / migrate / feed ordering | Pending | |
| 7 | Settings | Pending | Match Komikku's settings tree, immediate-apply semantics |
| 8 | More / stats / remaining screens | Pending | |

## Current Session: Reader (item #4)

Komikku spec files to read:
- `eu.kanade.presentation.reader.ReaderPageActionsDialog`, `ReaderContentOverlay`,
  `ReaderAppBars.kt` (composables)
- `eu.kanade.tachiyomi.ui.reader.ReaderActivity`, `ReaderViewModel` (state/behavior)
- Per-mode viewers: `eu.kanade.tachiyomi.ui.reader.viewer.pager.*` (L2R/R2L/vertical pager),
  `eu.kanade.tachiyomi.ui.reader.viewer.webtoon.*` (continuous vertical)

Key elements to check (known Komikku behaviors):
- Reading modes: L2R, R2L, vertical, webtoon, continuous-vertical — per-manga override + global default
- Tap zones: configurable (L/R/edge) with a "tap zones" preview/config screen; navigation vs menu-toggle zones
- Top/bottom overlay bars: chapter title, page slider with snap-to-page, page count, settings shortcut
- Slider: drag to seek pages within the chapter, snapping to discrete page positions
- Volume-key paging toggle
- Auto-rotation / forced orientation per-manga override
- Real-time settings changes (brightness, color filter, crop borders, background color) applied without reopening the reader
- Chapter transition screens (previous/next chapter preview at the edges of a chapter)
- Double-tap to zoom (pager modes); pinch-to-zoom
- Long-press page → save/share/set-as-cover context menu

Gap areas likely in Otaku:
- `feature/reader/` Screen + ViewModel (4 modes already shipped per CLAUDE.md — verify tap zones,
  slider snap, chapter transition screens, and real-time settings match Komikku exactly)

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
