# Changelog

All notable changes to Otaku Reader will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Onboarding flow for first-time users (5-page intro)
- `onboarding_completed` preference tracking
- Comprehensive system audit documentation

### Fixed
- H-6: DataStore write failures now show snackbar (no longer silent)
- H-12: Reader chapter load failures show error message (no longer blank)
- Onboarding screen now triggers for new users

### Security
- HTTPS-only extension downloads (C-3 compliance)
- Child-first classloader isolation for extensions
- Not exported broadcast receiver for extension lifecycle

## [0.1.0-beta] - 2026-04-15

### Added
- Complete manga reader with 4 reading modes (Single, Dual, Webtoon, Smart Panels)
- Extension system with 2000+ Tachiyomi-compatible sources
- Library management with categories and favorites
- Download system with offline reading and CBZ export
- AI features: categorization, recommendations, smart search
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
