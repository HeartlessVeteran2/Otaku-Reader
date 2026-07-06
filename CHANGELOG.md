# Changelog

All notable changes to Otaku Reader will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added — Settings & Backup
- Selective backup/restore — choose exactly which data categories (library entries, chapters, categories, tracking, preferences, OPDS servers, feed, tracker sync settings) go into a backup or get applied from one, via a checkbox list on both the pre-backup and pre-restore dialogs; deselected sections are simply empty in the backup file, so existing full backups still restore fine (#1207)
- Require-charging library-update restriction, hide-notification-content toggle, per-tracker sync-on-chapter-read opt-out, and per-category skip-updates toggle (#1202)
- Keep-last-N-read-chapters delete-after-read mode, alongside the existing "delete the just-read chapter" option (#1203)
- Onboarding storage-location step — pick the download folder during first-run setup, with the permission grant persisted across reboots (#1204)

### Added — Migration
- Configurable migration options — per-category toggles (chapters, categories, tracking, notes, downloads, custom cover) control exactly what a source migration carries over, instead of migrating everything unconditionally (#1206)
- Custom-cover migration — a manga's custom cover now carries over to the migrated entry (#1206)

### Added — Updates
- Dedicated Update Errors screen — replaces the flat dialog with a full screen: sticky-header grouping by error message, long-press multi-select (select-all/invert/delete-selected), a "migrate selected" bulk action, and per-item swipe-to-dismiss (#1205)

### Added — Statistics
- Trackers card — tracked-title count, mean score, and active tracker-service count, shown once at least one manga is tracked (#1197)
- Downloaded-chapter count tile on the library stats card (#1197)

### Added — Reader
- Reader comments — private, timestamped comments scoped to the current chapter or the whole series, in a bottom sheet opened from the reader menu; the existing chapter note is editable in the same place, and linked tracker pages (MAL, AniList, …) open in one tap (#1098)

### Added — Library & Sources
- E-Hentai/ExHentai favorites sync — additive library import from EH favorites using WebView-captured session cookies, gated behind the NSFW preference (#1090)
- EH favorites full pagination — sync now walks every favorites page instead of only the first 25 entries (#1092)
- Real orphaned-file cleanup — Library Maintenance now scans the downloads tree against the database and reports folder count and size before deleting (#1092)
- Custom cover art — set any image as a manga's cover from the Details overflow menu; covers show across library, details, and widgets and survive metadata refreshes (#1093)
- Onboarding appearance step — pick system/light/dark theme during first-run, applied live (#1093)

### Added — Project
- Project website at https://heartless-veteran.github.io/Otaku-Reader/ — landing page, download with live latest-version lookup, guides (getting started, extensions, library, reader, tracking, backups), FAQ, and an auto-synced changelog; deployed from `website/` via GitHub Pages on every merge (#1099)

### Fixed
- Extension repository loading — five root-cause fixes: per-repo failure isolation, atomic replace of the available list, install fallback when the repo entry is missing, tolerance for both `apk` and `apk_url` index fields, and clearer error reporting (#1094)
- Library bulk download enqueued chapters without `sourceName`, which failed downloads from the queue; byte sizes now format locale-safely (#1095)
- Backup silently discarded user customization — backup format v4 now round-trips custom titles/covers/authors, per-manga reader settings, completion/dropped flags, chapter notes, and category update schedules; v3 backups still restore (#1097)
- Reader crash at chapter transitions — pager could compose a stale page index while the page list shrank (#1097)
- Tracker sync retried forever on permanently failing trackers (e.g. revoked tokens); now capped at 3 attempts, and transient EH HTTP errors retry instead of failing permanently (#1097)

### Security
- Android Keystore corruption recovery — all nine encrypted credential/session stores now recover (reset + re-auth) instead of crash-looping the app after OS updates or device restores (#1097)
- WebView address bar only loads http/https — typed `javascript:`, `data:`, `file:`, and `intent:` URLs are rejected before reaching the WebView (#1097)

### Added — Extension Trust & Health
- Extension Detail Screen 2.0 — full-screen extension page with version, package name, trust badge, signer hash, repo link, capability chips, expandable source list, and trust/untrust action (#1072, closes #1047)
- Extension signer hash provenance — first-seen signer hash recorded per extension; a changed signing certificate after install shows a red warning icon and label in the extensions list (#1075, closes #1049)
- Source health diagnostics — per-source failure tracking with warning badges and a diagnostic sheet in Browse (#1065)
- WebView session bridge — sources can request a WebView Cloudflare challenge and share the resulting cookies with OkHttp (#1076, closes #1052)

### Added — Browse & Discovery
- Source categories and pinning — pin sources to a top "Pinned" section and group the rest under custom category labels via long-press (#1069, closes #1050)
- Saved source searches — name and save the current query as a chip row above the source list; tap to re-run, × to delete (#1074, closes #1051)
- Local source hidden folders — optional scanning of dot-prefixed directories in the local source path (#1061, closes #1059)

### Added — Library
- Saved library views — save named filter+sort combinations and re-apply them from a chip row (#1064, closes #1039)
- Library maintenance center — dedicated screen for cover refresh, metadata refresh, reindex downloads, and orphaned-file cleanup (#1060, #1063)
- Cross-source duplicate detection — duplicate groups show a "Cross-source" chip and resolved source names in the merge screen (#1077, closes #997)
- Bulk action confirmation dialogs — destructive library selection actions (remove, mark completed/dropped) now confirm first (#1062)
- Update history and diagnostics — last-run stats with checked/skipped/failed counts and per-manga skip reasons (#1067)
- Auto-download new chapters by category — per-category include/exclude lists control which categories trigger auto-download (#1035)

### Added — Downloads & Storage
- CBZ password protection — AES-256-GCM encryption for downloaded CBZ archives with Keystore-backed passphrase storage and transparent decrypt-on-read (#1037, closes #1033)
- Storage analytics delete actions — per-manga delete buttons with confirmation dialog in the storage dashboard (#1056)
- Reindex downloads — domain use case reconciling on-disk chapter files with database state, wired to the library menu (#1028)

### Added — Settings & System
- Widget configuration studio — per-widget count limit, tap action, category filter, thumbnail toggle, and live preview (#1071, closes #1044)
- Navigation tab drag-and-drop reorder with per-tab hide/show switches (#1068, closes #1038)
- Backup contents checklist and restore preflight — live item counts before backup; filename + irreversibility warning before restore (#1073, closes #1042)
- Tracking health page — per-tracker token expiry, last sync, and failed-update queue (#1066)
- Data usage per-source drill-down and monthly budget with progress bar (#1070, closes #1045)
- Biometric lock scheduling — time-of-day and day-of-week enforcement windows (#1036)
- Reader preset human-readable labels — "Single Page · Fit Width" instead of raw mode/scale integers (#1055)
- Reader presets expanded from 6 to 13 captured settings — tap zones, volume keys, page number, skip flags (#1017)
- QR library sharing wired into the library overflow menu (#1030)

### Fixed
- Library no-op menu actions wired or removed; deprecated Gradle `srcDir` API replaced (#1015)
- Certificate pin rotation — per-tracker verification dates and openssl rotation instructions documented (#1029, tracks #994)

### Security
- WebView hardening — file/content access disabled, Safe Browsing enabled (#1016)
- MangaUpdates credential login now shows a security note explaining password-based auth (#1016)
- CBZ archives can be encrypted at rest; passphrase held in EncryptedSharedPreferences backed by the Android Keystore (#1037)
- Extension signer continuity check guards against silent supply-chain package replacement (#1075)

## [0.1.0-beta] - 2026-06-06

### Added
- FTS4-powered library search — title, author, artist full-text search (closes #926)
- Reader quick-settings overlay — long-press center tap zone opens a settings sheet
- Reader chapter-list overlay — right-slide panel with current chapter highlighted
- Reader presets quick-switch — FilterChip row in menu overlay for one-tap profile switch
- Edit manga info — user metadata overrides (title, description, status, genres)
- Merge duplicate library entries — merge screen accessible from library overflow menu
- Per-reader-mode volume key behavior — Inherit / Disabled / Normal / Inverted per reading mode
- Chapter list text search in Details screen — live filtering by chapter name
- Swipe-to-delete in History — EndToStart swipe removes a history entry
- Swipe-to-mark-read in Updates — EndToStart swipe marks a chapter as read
- Statistics date range selector — All / 90d / 30d / 7d FilterChip row
- Library sort mode indicator chip — dismissible chip shows active sort; tap × resets to Alphabetical
- Reading list export as CSV and JSON — from reading list detail overflow menu
- Dark mode scheduling — configurable on/off times in display settings
- Backup encryption with password prompt — AES-256-GCM encrypted local backups
- Bottom nav tab reorder screen — drag-to-reorder in Nav Order settings
- Onboarding flow for first-time users (5-page intro)
- `onboarding_completed` preference tracking
- Beta feature parity backlog: 35 GitHub issues created (#926–#958)

### Changed
- `versionCode` bumped to 2; `versionName` set to `0.1.0-beta`
- Build is now a single flat artifact: removed `full` / `foss` product flavors and the `distribution` flavor dimension. Use `./gradlew assembleDebug` / `assembleRelease` directly.
- Statistics screen period filtering applied in-memory; library count and streaks always show all-time data

### Fixed
- H-6: DataStore write failures now show snackbar (no longer silent)
- H-12: Reader chapter load failures show error message (no longer blank)
- Onboarding screen now triggers for new users
- Alpha readiness: all gates green (build ✅, tests ✅, security ✅, architecture ✅, extension compat ✅, notifications ✅, tracker sync ✅)
- Override default-param Kotlin compile error in `StatisticsRepositoryImpl`
- Removed stale `ImageDecoderDecoder` reference from `OtakuReaderApplication` (Coil 3 compat)
- `MangaRepositoryImplTest` constructor mismatch after `mangaCategoryDao` was added
- `ReaderViewModelTest` missing `readerPreferences` mock and `getChaptersByMangaId` stub

### Security
- HTTPS-only extension downloads (C-3 compliance)
- Child-first classloader isolation for extensions
- Not exported broadcast receiver for extension lifecycle

## [0.1.0-alpha] - 2026-05-25

### Added
- Complete manga reader with 4 reading modes (Single, Dual, Webtoon, Smart Panels)
- Extension system with 2000+ Tachiyomi-compatible sources
- Library management with categories and favorites
- Download system with offline reading and CBZ export
- Tracker sync (MAL, AniList, Kitsu, MangaUpdates, Shikimori)
- Discord Rich Presence integration
- OPDS catalog support
- Feed system for content discovery
- Material 3 UI with dynamic theming
- Edge-to-edge display support
- Home screen widgets (Continue Reading, Recent Updates)
- Dynamic shortcuts (Library, Updates, Continue Reading)
- Deep link support (MangaDex URLs, share intents)

### Technical
- Clean Architecture with 26 modules
- MVI pattern throughout
- Jetpack Compose UI
- Room database with 13 entities
- Hilt dependency injection
- WorkManager background tasks
- DataStore preferences
- Coil 3 image loading
- Full Komikku feature parity

## Release Template

When creating a new release, include:

```markdown
## [VERSION] - YYYY-MM-DD

### Added
- New features

### Changed
- Changes to existing functionality

### Deprecated
- Soon-to-be removed features

### Removed
- Now removed features

### Fixed
- Bug fixes

### Security
- Security improvements
```
