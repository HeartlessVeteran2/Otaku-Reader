# 📝 Changelog

All notable changes to Otaku Reader will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0-alpha] - 2026-04-12

### Added
- 🤖 **AI Feature Suite (Gemini)**: Full AI stack behind Settings → AI Features toggles
  - **SFX Translation**: AI-powered sound effect detection and translation
  - **Chapter Summary**: Auto-generated chapter summaries, cached in Room
  - **Source Intelligence**: AI-enhanced source health and recommendations
  - All features gated behind `AiFeatureGate` (master toggle + API key + per-feature toggle)
  - Graceful degradation: no crashes when AI is disabled or API key is absent
- 🔐 **Secure API Key Storage**: End-to-end encrypted key management
  - `EncryptedApiKeyStore` — AES-256-GCM via Android Keystore / EncryptedSharedPreferences
  - `SecureApiKeyDataStore` — key lifecycle (save, validate, rotate) in `core/ai`
  - Hardware-backed storage on devices with StrongBox
  - Keys never cached in JVM heap between reads
  - `android:allowBackup="false"` + custom `ai_backup_rules.xml` to prevent backup leakage
- 📋 **Auto-Categorization**: AI-powered manga category suggestions with `CategorizationRepository`
  - Suggestions stored in Room v11 (`categorization_results` table)
  - kotlinx-serialization used for JSON column encoding
- 🔒 **Comprehensive Security Audit** — zero critical or high issues found
  - Cleartext traffic blocked via `network_security_config` (`cleartextTrafficPermitted="false"`)
  - HTTP logging enabled only in debug builds
  - Tracker OAuth credentials injected at build time (not hard-coded)
- 🧪 **Test Coverage Expansion**: domain use cases and feature ViewModels

### Changed
- 🗄️ Database upgraded to **version 11** (adds `categorization_results` table; migrations v1 → v11 maintained)
- 🔒 Netty pinned to 4.2.10.Final (MadeYouReset DoS fix)
- 🔒 jose4j pinned to 0.9.6+ (compressed JWE DoS fix)

### Fixed
- ⚙️ `BrowseViewModel` injection fixed; use-case `@Inject` annotations corrected
- ⚙️ `ArchitectureTest` compilation errors resolved
- 🔁 Two-way sync conflict resolution for Google Drive library sync

## [0.3.0-alpha] - 2026-03-15

### Added
- 🤖 **Smart Panels**: Automatic manga panel detection with guided panel-by-panel navigation
  - Edge detection algorithm (Sobel-like gradient), horizontal/vertical separator line detection
  - Animated zoom/pan with spring easing via `PanelNavigationView`
  - Configurable sensitivity (edge threshold, min line length, min panel size)
  - Reading-order support: RTL (manga) and LTR (comics)
  - Graceful fallback to full-page view when detection fails
- ⚡ **Smart Prefetch**: Adaptive page/chapter prefetching based on reading behavior
  - Four strategies: Conservative, Balanced, Aggressive, Adaptive
  - `ReadingBehaviorTracker` records navigation events and computes reading stats
  - `AdaptiveChapterPrefetcher` adjusts prefetch depth to reading speed and completion rate
  - `PrefetchTelemetry` for hit-rate and efficiency monitoring
- 🔄 **Cloud Sync (Google Drive)**: Background cross-device library synchronization
  - `SyncManager` / `SyncProvider` abstraction (Google Drive prototype; Dropbox & WebDAV stubs)
  - Four conflict-resolution strategies: PREFER_NEWER, PREFER_LOCAL, PREFER_REMOTE, MERGE
  - `SyncWorker` (WorkManager) with configurable interval and Wi-Fi-only constraint
  - `SyncNotifier` for in-progress/success/failure notifications
  - `SyncPreferences` with validated interval (1–168 h) and strategy storage
- 📡 **OPDS Catalog**: Self-hosted server support (Komga, Kavita, etc.)
  - Add/edit/delete OPDS servers with authentication
  - Browse catalog hierarchy, search feeds, and download archives (CBZ/CBR)
- 🤖 **AI Recommendations (Gemini)**: `GeminiClient` for manga recommendations
  - SHA-256 config fingerprint of `(apiKey, modelName)` stored as hex string (reduces raw API key exposure in memory)
  - `AiRepositoryImpl` with `TimeoutCancellationException` → `Result.failure` mapping
- 🎮 **Discord Rich Presence**: `DiscordRpcService` shows currently reading manga
  - Graceful fallback when Discord is not installed or not running
- 📈 **Statistics**: Reading analytics screen with charts and reading streaks
- 🔄 **Migration**: Migrate manga between sources with chapter mapping and progress transfer
- 🛳️ **Onboarding**: First-run onboarding flow for new users

### Changed
- 🗄️ Database upgraded to version 9 (indexes on `manga`, `chapter`, and related tables)
- 📦 `DownloadManager` refactored to O(1) lookup via internal `LinkedHashMap` (was O(n) list scan)

### Performance
- ⚡ `MangaEntity` / `ChapterEntity` indexes added (sourceId, title, favorite, sourceId+url composite)
- ⚡ Batch `UPDATE … WHERE id IN (:ids)` queries chunked to ≤997 IDs to respect SQLite 999-parameter limit

## [0.2.0-alpha] - 2026-03-09

### Added
- 📜 Reading history screen with timestamps and read-duration tracking
- 💾 Chapter download system: queue management, pause/resume, per-chapter progress
- 🔔 Download notifications with progress (requires POST_NOTIFICATIONS on Android 13+)
- 🗂️ Backup & restore: JSON export/import of library, chapters, categories, history, and preferences
- 🕵️ Incognito mode (session-only via IncognitoManager, no history or progress saved)
- ⚙️ Settings persistence via DataStore (general, library, and reader preferences)
- 🔄 Background library update worker via WorkManager
- 🖥️ Downloads screen with queue status (QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED)

### Changed
- 🗄️ Database upgraded to version 3 with explicit MIGRATION_2_3 (adds `reading_history` table)
- 📦 Removed SQLDelight; database layer now uses Room exclusively

## [0.1.0-alpha] - 2026-03-07

### Added
- 🚀 Initial project setup
- 📱 Core Android architecture (Clean Architecture + MVI)
- 🗄️ Database layer (Room)
- 📖 Library screen with manga grid
- 📖 Basic reader with pager
- 🔌 Extension system foundation
- 🔗 Tachiyomi extension compatibility
- 🎯 Ultimate reader with 4 modes (Single, Dual, Webtoon, Smart Panels)
- 🖼️ Gallery view navigation (no slider rail!)
- 🔍 Pinch zoom and tap zones
- 🎨 Material 3 UI design

### Technical
- 125+ Kotlin files
- 2000+ extension support via Tachiyomi repos
- Perfect Viewer-inspired smoothness
- Jetpack Compose UI
- Hilt dependency injection

---

**Legend:**
- 🚀 Major feature
- ✨ Enhancement
- 🐛 Bug fix
- 📚 Documentation
- ⚡ Performance
- 🔒 Security
