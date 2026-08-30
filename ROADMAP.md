# ROADMAP.md — Otaku Reader

**Status:** All pre-release phases complete | **Current Phase:** v1.0.0 release preparation — no blockers remain
**Updated:** 2026-08-29
**Website:** https://heartless-veteran.github.io/Otaku-Reader/

---

## Phase Status

| Phase | Status | Notes |
|-------|--------|-------|
| Alpha | ✅ **SHIPPED** | All gates green. Build passes, tests pass, security audit clean. |
| Beta | ✅ **FEATURE PARITY COMPLETE** | All 35 parity issues (#926–#958) plus the QoL/extension-system audit batches shipped 2026-06-06 → 2026-06-10. |
| Beta hardening | ✅ **DONE 2026-06-13** | EH sync + pagination (#1090/#1092), custom covers + onboarding (#1093), extension repo fixes (#1094), bulk download fix (#1095), full-app bug sweep (#1097), reader comments (#1098), project website (#1099). |
| P3 post-beta polish | ✅ **DONE 2026-06-20** | Page-level bookmark system + collections (PR #1130, schema v39). Category timestamp encoding fixed. ⚠️ This row used to claim "last stub removed; share is fully wired" — it was wrong. Bookmark **export** was still a snackbar reading "image export coming in v1.1", and **share** sent a text list, not images. Both are real as of #1277. |
| Data-integrity batch | ✅ **DONE 2026-08-29** | Chapter and manga id stability (#1254, #1269), orphaned tracker rows (#1248), source-lookup startup race (#1258), download folder names (#1256), certificate pins (#1218), backup format v5 carrying tracker links (#1271). Bookmark page export/share (#1132/#1133) and open-at-page (#1128). |
| JavaScript source backend | ✅ **DONE 2026-08-25** | Mangayomi JS sources run unmodified (#1262, #1264). Added **alongside** the APK backend — the planned APK retirement is cancelled, see below. |
| v1.0.0 | 📋 **NEXT — unblocked** | Push `v1.0.0` tag → `release.yml` builds signed APK → GitHub Release. The tag was held back only while the APK backend was slated for removal; that plan is cancelled, so nothing gates it. |

---

## ✅ Alpha Complete (Shipped 2026-05-25)

All alpha readiness gates pass:

- [x] Build: `assembleDebug` green ✅
- [x] Tests: All unit tests passing ✅
- [x] Security: No unencrypted creds, AES-256-GCM, HTTPS-only extensions ✅
- [x] Architecture: Clean Architecture enforced, zero layer violations ✅
- [x] DB: explicit migrations only (schema v34 at the time of alpha; **v46** today), no destructive fallback in production ✅
- [x] Extension system: Tachiyomi API intact, classloader isolation ✅
- [x] Notification system: UpdateNotifier, DownloadNotifier, ReadingReminderWorker ✅
- [x] Tracker auto-sync: ReaderViewModel → TrackerSyncRepository wired ✅
- [x] CI/CD: Detekt, unit tests, signed APK on every `v*` tag ✅

**Alpha PRs merged:** #920–#925
- Backup navigation wiring
- Unverified extension install dialog + trust banner
- Detekt cleanup
- SelectionManager + race fixes
- LibraryScreen decomposition
- Alpha readiness fixes (Detekt, DI, tests, resources)

---

## ✅ Beta Phase: Feature Parity (35 Issues) — COMPLETE

All 35 feature parity issues (#926–#958) have shipped and are closed. Tables kept for the issue-to-feature mapping.

### P0 — Beta Blockers (Must Have)

| Issue | Feature | Status |
|-------|---------|--------|
| #926 | Library Search | ✅ Shipped — FTS4 library search (PR #1011, 2026-06-06) |
| #927 | Advanced Search & Filtering | ✅ Shipped |
| #928 | Biometric App Lock | ✅ Shipped |
| #929 | Tachiyomi Backup Import | ✅ Shipped |
| #930 | Auto-Backup Scheduling UI | ✅ Shipped |

### P1 — Competitive Features (Strongly Needed)

| Issue | Feature | Status |
|-------|---------|--------|
| #931 | Dynamic Categories | ✅ Shipped |
| #932 | Hidden Categories | ✅ Shipped |
| #933 | Smart Download Rules | ✅ Shipped |
| #934 | Per-Manga Reader Settings UI | ✅ Shipped |
| #935 | Page Bookmark Management Screen | ✅ Shipped |
| #936 | Chapter Notes UI | ✅ Shipped |
| #937 | Search History & Suggestions | ✅ Shipped |
| #938 | Download Queue Manager | ✅ Shipped |
| #939 | Extension Auto-Update | ✅ Shipped |
| #940 | Smart Notification Batching UI | ✅ Shipped |
| #941 | Statistics Sharing | ✅ Shipped |
| #942 | Tracker Batch Sync | ✅ Shipped |

### P2 — Nice to Have

| Issue | Feature | Status |
|-------|---------|--------|
| #943 | Recommendation Engine | ✅ Shipped |
| #944 | Customizable Feeds & Discovery | ✅ Shipped |
| #945 | Reading List Collections | ✅ Shipped |
| #946 | Completed & Dropped Series Sections | ✅ Shipped |
| #947 | Per-Manga Dynamic Theme | ✅ Shipped |
| #948 | Pure Black AMOLED Mode | ✅ Shipped |
| #949 | Home Screen Widget | ✅ Shipped |
| #950 | QR Code Library Sharing | ✅ Shipped |
| #951 | Read Time Estimation | ✅ Shipped |
| #952 | Crash Reporting Integration | ✅ Shipped |
| #953 | Extension Repository Management | ✅ Shipped |

### P3 — Post-Beta

| Issue | Feature | Status |
|-------|---------|--------|
| #954 | Cloud Backup | ✅ Shipped |
| #955 | Reading Challenges & Achievements | ✅ Shipped |
| #956 | Data Usage Dashboard | ✅ Shipped |
| #957 | WebView Integration | ✅ Shipped |
| #958 | Reader Progress Sync Across Devices | ✅ Shipped |

---

## 📋 How to Pick Up Beta Work

1. Check the open follow-up issues (listed in the shipped-batch section above)
2. Comment on the issue to claim it
3. Branch from `main`: `git checkout -b feat/issue-NNN-short-name`
4. Reference the issue in your PR: `Closes #NNN`

---

## ✅ Beta QoL + Extension Trust Batch Shipped (2026-06-07 → 2026-06-10)

The post-rollout batch from the QoL/layout and extension-system audits, plus the komikku-HV gap audit. All PRs squash-merged to `main` with green CI.

| PR | Feature | Closed issue |
|----|---------|--------------|
| #1015 | Library no-op actions wired/removed, Gradle srcDir fix | — |
| #1016 | WebView hardening + MangaUpdates credential warning | — |
| #1017 | Reader presets expanded 6 → 13 settings | — |
| #1028 | ReindexDownloads domain use case | #1026 |
| #1029 | Cert pin verification dates + rotation docs | tracks #994 |
| #1030 | QR library sharing wired to library menu | — |
| #1035 | Auto-download new chapters by category | #1031 |
| #1036 | Biometric lock time/day scheduling | #1032, #1058 (dup) |
| #1037 | CBZ password/encryption (AES-256-GCM) | #1033 |
| #1055 | Reader preset human-readable labels | — |
| #1056 | Storage analytics delete actions | — |
| #1060/#1063 | Library maintenance center | #1040 |
| #1061 | Local source hidden folders | #1059, #1034 |
| #1062 | Bulk action confirmation dialogs | — |
| #1064 | Saved library filter/sort views | #1039 |
| #1065 | Source health diagnostics | #1048 |
| #1066 | Tracking health page | #1043 |
| #1067 | Update history and diagnostics | #1041 |
| #1068 | Nav tab drag-and-drop reorder + hide/show | #1038 |
| #1069 | Source categories and pinning | #1050 |
| #1070 | Data usage drill-down + monthly budget | #1045 |
| #1071 | Widget configuration studio | #1044 |
| #1072 | Extension Detail Screen 2.0 | #1047 |
| #1073 | Backup checklist + restore preflight | #1042 |
| #1074 | Saved source searches | #1051 |
| #1075 | Extension signer hash provenance | #1049 |
| #1076 | WebView session bridge (Cloudflare) | #1052 |
| #1077 | Cross-source duplicate detection display | #997 |

**Follow-ups since shipped:** extension blocklist (#1018 ✅), repository provenance tracking (#1019 ✅), privacy docs (#1021 ✅), macrobenchmarks (#1022 ✅), screenshot tests (#1023 ✅), EH favorites sync (#1024 ✅, full pagination in #1092), remote library sync (#1025 ✅), reader preset round 2 (#1027 ✅), cross-source merge workflow (#1053 ✅), extension smoke-test harness (#1054 ✅). **Still open:** MangaUpdates OAuth (blocked upstream, #1020), cert pin live verification (#994), backup coverage for reader comments (follow-up from #1098).

---

## ✅ Beta Hardening Batch (2026-06-11 → 2026-06-13)

| PR | Change |
|----|--------|
| #1090 | E-Hentai/ExHentai favorites sync (NSFW-gated, WebView session cookies) |
| #1092 | EH favorites full pagination + real orphaned-file cleanup with size reporting |
| #1093 | Custom cover art + onboarding appearance step |
| #1094 | Extension repository loading — five root-cause fixes (per-repo isolation, atomic replace, install fallback, apk/apk_url tolerance, error reporting) |
| #1095 | Library bulk download missing `sourceName` + locale-safe byte formatting |
| #1097 | Full-app bug sweep: backup v4 (all user customizations round-trip), reader pager bounds guards, Keystore corruption recovery across 9 encrypted stores, tracker sync retry cap, EH error routing, WebView scheme validation |
| #1098 | Reader comments — chapter/book scoped private comments + in-reader chapter note + tracker discussion links (DB v37) |
| #1099 | Project website (VitePress on GitHub Pages) with guides, live download version, auto-synced changelog |

---

## ✅ P3 QoL Batch Shipped (PR #1011, 2026-06-06)

Mihon/Komikku parity improvements and reader enhancements shipped alongside the beta rollout:

| Feature | Notes |
|---------|-------|
| FTS4 library search | Title, author, artist full-text search (closes #926) |
| Reader quick-settings overlay | Long-press center tap zone → settings sheet |
| Reader chapter-list overlay | Right-slide panel with current chapter highlighted |
| Reader presets quick-switch | FilterChip row in menu overlay |
| Edit manga info | User overrides for title, description, status, genres |
| Merge duplicate library entries | Merge screen + action from library overflow |
| Per-reader-mode volume key behavior | Inherit / Disabled / Normal / Inverted per mode |
| Chapter list text search | Live search in Details screen chapter list |
| Swipe-to-delete in History | EndToStart swipe removes entry |
| Swipe-to-mark-read in Updates | EndToStart swipe marks chapter read |
| Statistics date range selector | All / 90d / 30d / 7d FilterChip row |
| Library sort mode indicator chip | Dismissible chip shows active sort; X resets |
| Reading list export (CSV/JSON) | Export from reading list detail overflow |
| Dark mode scheduling | Scheduled on/off times in display settings |
| Backup encryption | Password-protected local backups |
| Bottom nav tab reorder | Nav Order settings screen |

---

## ✅ P3 Post-Beta Polish (2026-06-20)

| PR / Change | Detail |
|-------------|--------|
| PR #1130 | Page-level bookmark system: persistent per-page bookmarks, collections (named groups), multi-select, export queue, share via Sharesheet. DB v37 → v39 (two migrations). |
| Bookmark share stub removed | `shareSelected()` now emits `BookmarksEffect.ShareSelected` → Screen launches `Intent.ACTION_SEND` with formatted text lines. No more "coming soon" snackbar. |
| Category timestamp encoding fixed | Replaced fragile comma-CSV `"id:timestamp,id:timestamp"` with per-category `DataStore<Long>` keys (`category_last_update_ms_<id>`). Legacy key migrated transparently on first write. |
| Dead string removed | `library_reindex_not_available` removed from `feature/library/strings.xml` (unreferenced). |
| ComicInfo.xml wired (#1134) | `CbzCreator.createCbz()` now receives `ComicInfoMetadata` at both call sites — auto-download path fetches chapter number from DB; manual export path uses title/series strings. |
| LibraryUpdateFilter extracted (#1136) | Smart-skip and per-category frequency logic moved out of `LibraryUpdateWorker` into a dedicated `@Singleton` class. Worker injects and delegates to `filter.apply(manga, now)`. |

**Deferred to v1.1 (issues created #1132–#1137):**
- #1132 — Bookmark image export via MediaStore (Coil disk cache → gallery)
- #1133 — Bookmark share via images (FileProvider + ACTION\_SEND\_MULTIPLE)
- #1135 — Local manga source directory picker UI (backend already complete)
- #1137 — Clean up extension compat shim (blocked: ext-lib 1.5 community migration)

---

## ✅ Komikku Parity Deferred-Gaps Batch (#1192, 2026-07-05 → 2026-07-06)

Issue #1192 tracked gaps deliberately deferred during the Komikku parity audit (see
`.claude/skills/komikku-parity/SKILL.md`) — worked through as a 7-PR sequence, then closed.

| PR | Feature |
|----|---------|
| #1197 | Statistics: Trackers card (tracked-title count, mean score, tracker count) + downloaded-chapter count tile |
| #1202 | Settings wiring batch: require-charging update restriction, hide-notification-content toggle, sync-on-chapter-read opt-out, per-category skip-updates toggle |
| #1203 | Keep-last-N-read-chapters delete-after-read mode |
| #1204 | Onboarding storage-location step (persisted folder permission) |
| #1205 | Dedicated Update Errors screen (sticky headers, multi-select, migrate-selected, swipe-to-dismiss) — replaces the flat dialog |
| #1206 | Configurable migration options (`MigrationFlag` toggles) + custom-cover migration |
| #1207 | Selective backup/restore — per-category `BackupOptions` toggles, checkbox-list UI on both pre-backup and pre-restore dialogs |

**Spun out to #1208** (not part of this batch): the Advanced settings screen (needs cookie-jar/DoH/user-agent network infrastructure Otaku doesn't have yet) and a reading-history-migration follow-up found while building #1206 (blocked on `ChapterRepository` needing a per-manga-scoped history read method).

---

## ✅ JavaScript Source Backend (2026-08-17 → 2026-08-25)

| PR | Change |
|----|--------|
| #1262 | Run published Mangayomi JavaScript extensions unmodified — `prelude.js` compatibility layer, `MProvider`, real-index decoding, declared preference defaults |
| #1264 | Honest transport errors (a request that never completed throws instead of decoding as an empty response) + Mangayomi's actual url-accessor semantics |
| #1265 | Decision record: keep both backends |

**The APK backend is not being retired.** The original plan was to replace it. Measured against the
live Mangayomi index, the JavaScript half is **18 distinct sources (16 usable)** against the several
hundred the APK path reaches — the 363 index entries are published per *language*, all pointing at
the same scripts. Removing the APK backend would also have re-pointed every library row at a source
that no longer exists (`Manga.sourceId` is a one-way hash) and orphaned every downloaded chapter on
disk (#1256). Both backends now ship side by side behind the `MangaSource` seam. Full rationale in
`CLAUDE.md` → *Extension System*.

**Still open, no longer blocking:** #1256 (download folders are named by the numeric source key), and
DOM traversal in the prelude — deferred as a deliberate isolation-boundary decision, costing two
sources (Asura Scans, Mangafire).

---

## ✅ Developer Screen (2026-08-25)

| PR | Change |
|----|--------|
| #1266 | Hidden developer screen for bulk-adding extension repository URLs |

Reached by tapping the About screen's version line seven times and entering a passphrase. Ships
**inert**: the passphrase is unset (and blank means refuse every input), and the URL list lives in a
gitignored assets file that a fresh clone does not have. Setup is two steps, both local to the
developer's machine — `tools/devcode/devcode.sh` for the digest, `app/src/main/assets/dev-repos.txt`
for the list.

The gate is obscurity rather than security, and the code says so; see *Shipped Feature Inventory →
Developer screen* in `CLAUDE.md` for the properties that must not be traded away.

---

## 🏗️ Architecture Maintenance

Status as of 2026-06-10:

- [x] F-Droid metadata (fastlane) + reproducible-build flags (`dependenciesInfo` disabled in app/build.gradle.kts)
- [x] Macrobenchmark harness — `StartupBenchmarks`/`PerformanceBenchmarks` in `baselineprofile/`, runnable via benchmark.yml manual dispatch
- [x] Baseline profile — curated `app/src/main/baseline-prof.txt` shipped (PR #1080); regenerate on-device when startup code changes significantly
- [x] `<queries>` element in manifest for Android 11+ package visibility
- [x] WorkManager `PendingIntent` mutability flags for API 31+ (all 5 usages use FLAG_IMMUTABLE)
- [x] Import-level layer violations — actually 0; enforced by Detekt ForbiddenImport rules (the "8 remaining" note was stale)
- [x] Kover coverage gates for all of `:domain`, `:data`, `:core:database`, `:feature:reader`, `:feature:tracking`, `:feature:settings` — **note:** Kover 0.8.x could not instrument AGP 9 modules, so the old 60% gates passed vacuously. Kover 0.9.8 now measures for real; gates are honest per-module ratchet floors (domain 60 · data 35 · database 25 · reader 15 · tracking 15 · settings 5). Raise floors as coverage improves; never lower them.

---

## 🗑️ Legacy Audit Artifacts

The full-system audit from 2026-05-24 has been archived. Raw audit files (`AUDIT_*.md`, `PATCH_QUEUE.md`) are preserved in `.github/audit-archive/` for reference but are no longer actively maintained.

---

*Roadmap maintained by the core team. For questions, open a Discussion.*
