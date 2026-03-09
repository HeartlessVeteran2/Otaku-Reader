# 📝 Changelog

All notable changes to Otaku Reader will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- 🎨 New app icons and branding
- 📝 Professional GitHub project setup
- 📚 Improved documentation

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
