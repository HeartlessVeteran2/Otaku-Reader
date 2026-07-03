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
| 6 | Browse: global search / migrate / feed ordering | Done (PR #1188) | Global search already matched Komikku. Migrate: fixed notes not being copied to the target manga (Komikku's NOTES flag equivalent); custom-cover migration and a full configurable migration-options dialog (matching Komikku's `MigrationFlag` toggles) remain unimplemented — Otaku migrates everything unconditionally, a reasonable default but not user-configurable — flagged as a possible future item, not blocking. Feed ordering: consolidated two competing saved-search concepts onto `SavedSourceSearch` (added `order` field, removed dead `FeedSavedSearch` UI wiring, added move-earlier/move-later reorder buttons) per user decision; `FeedRepository`/`FeedDao`/backup integration left untouched since it's a real Room-backed subsystem, not dead code. |
| 7 | Settings | **Next** | Match Komikku's settings tree, immediate-apply semantics |
| 8 | More / stats / remaining screens | Pending | |

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
