# Otaku Reader

🌸 The ultimate manga reader. Better than Perfect Viewer.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-26+-green.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

## 📊 Project Status

**Current: Feature-complete Beta** — Core functionality ready for daily use  
*Last Updated: March 10, 2026*

| Component | Status | Notes |
|-----------|--------|-------|
| **Core Architecture** | ✅ Complete | Clean Architecture, MVI, Hilt DI |
| **Library Screen** | ✅ Complete | Grid, categories, sorting, filtering, NSFW toggle |
| **Browse Screen** | ✅ Complete | Sources, global search, extension catalog |
| **Manga Details** | ✅ Complete | Chapter list, tracking, bookmarks, download |
| **Reader** | ✅ Complete | 4 modes, zoom, brightness, gallery, color filters |
| **History Screen** | ✅ Complete | Reading history with timestamps and duration |
| **Settings Screen** | ✅ Complete | Themes, backups, migration, tracking login |
| **Downloads** | ✅ Complete | Offline reading, queue management, CBZ export |
| **Updates Screen** | ✅ Complete | New chapter notifications with covers |
| **Extension System** | ✅ Complete | Install, update, repository management |
| **Tracking** | ✅ Complete | MAL, AniList, Kitsu, MangaUpdates, Shikimori UI |
| **Cloud Sync** | 🚧 In Progress | Google Drive sync architecture designed |
| **Scheduled Backups** | ✅ Complete | Auto-backup with WorkManager |
| **AI Features** | 📋 Planned | Post-v1.0 feature |

## 🚀 Recently Completed

- ✅ **Extension Catalog** — Browse, install, update extensions with NSFW filter
- ✅ **Tracking UI** — Full tracker integration (MAL, AniList, Kitsu, MangaUpdates, Shikimori)
- ✅ **New Chapter Notifications** — Rich notifications with manga covers, grouped by manga
- ✅ **Library Sorting & Filtering** — Sort by name/read/date, filter by downloaded/unread/tracking
- ✅ **Scheduled Backups** — Automatic local/cloud backups via WorkManager
- ✅ **Reader Color Filters** — Sepia, grayscale, invert, custom tint
- ✅ **NSFW Content Filter** — Global and per-source toggle
- ✅ **Migration Tool** — Move manga between sources with tracker preservation

## 🎯 Vision

A modern, blazing-fast manga reader built from scratch with:
- **🔌 Extension System** — Access 2000+ sources via Tachiyomi extensions
- **📖 Ultimate Reader** — Perfect Viewer-level smoothness with modern UX
- **☁️ Cloud Sync** — Cross-device library sync
- **🤖 AI Features** — Smart recommendations (future)

## ✨ Features

### Library Management
- Grid view with customizable columns (2-5)
- Categories with drag-and-drop organization
- Advanced sorting: Alphabetical, Last Read, Date Added, Unread Count, Source
- Advanced filtering: All, Downloaded, Unread, Completed, Tracking
- Unread badges on covers
- Multi-select for batch operations

### Browse & Discovery
- Browse 2000+ sources via extensions
- Global search across all sources
- Extension catalog with repository management
- NSFW content filtering

### Manga Details
- Full chapter list with read progress
- One-tap download/read
- Tracking integration (5 services)
- Bookmark chapters
- Migration between sources

### Ultimate Reader
- **4 Reading Modes**: Single Page, Dual Page, Webtoon, Smart Panels
- **Color Filters**: Sepia, Grayscale, Invert, Custom tint
- **Gallery View**: Thumbnail navigation
- **3x3 Tap Zones**: Fully configurable
- **Pinch Zoom**: Smooth scaling
- **Brightness Control**: In-reader overlay
- **Incognito Mode**: Private reading session
- **Volume Key Navigation**: Turn pages with hardware keys

### Downloads & Offline
- Background chapter downloads
- Download queue with pause/resume
- CBZ archive export
- Auto-download new chapters (Wi-Fi only option)

### Tracking
- **MyAnimeList** — Login and sync progress
- **AniList** — Full integration
- **Kitsu** — Track your library
- **MangaUpdates** — Follow series
- **Shikimori** — Russian tracker support

### Data & Sync
- Automatic scheduled backups
- Manual backup/restore (JSON)
- Cross-device sync architecture (in progress)
- Reading history with duration tracking

### Notifications
- New chapter alerts with manga covers
- Grouped notifications
- Per-manga notification settings
- Tappable to open manga

## 🚧 In Progress

- ⏳ **Cloud Sync** — Google Drive integration for library sync
- ⏳ **OPDS Support** — Komga/Kavita catalog browsing

## 📋 Future Roadmap

- 🔮 **AI Features** — Recommendations, auto-categorization (post-v1.0)
- 🔮 **Widgets** — Home screen continue reading
- 🔮 **SFX Translation** — Sound effect detection/translation
- 🔮 **Panel-by-Panel** — Advanced panel navigation

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │  Library │ │  Browse  │ │  Reader  │ │ Settings │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘   │
└───────┼────────────┼────────────┼────────────┼─────────┘
        │            │            │            │
        └────────────┴────────────┴────────────┘
                          │
┌─────────────────────────┼───────────────────────────────┐
│  Domain Layer           │                               │
│  ┌──────────────────────┴──────────────────────┐       │
│  │  Use Cases (GetLibrary, BrowseSource, etc.)  │       │
│  └──────────────────────┬──────────────────────┘       │
└─────────────────────────┼───────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────┐
│  Data Layer             │                               │
│  ┌──────────┐ ┌─────────┴┐ ┌──────────┐ ┌──────────┐  │
│  │  Room    │ │DataStore │ │  APIs    │ │Extension │  │
│  │ Database │ │  Prefs   │ │(Sources) │ │  Loader  │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 📦 Modules

| Module | Purpose | Status |
|--------|---------|--------|
| `app` | Main application entry | ✅ |
| `source-api` | Extension API contracts | ✅ |
| `domain` | Use cases and domain models | ✅ |
| `data` | Repository, downloads, backup, sync | ✅ |
| `core/common` | Shared utilities | ✅ |
| `core/database` | Room database (v6) | ✅ |
| `core/network` | Retrofit + OkHttp | ✅ |
| `core/preferences` | DataStore preferences | ✅ |
| `core/ui` | Shared Compose components | ✅ |
| `core/navigation` | Navigation routing | ✅ |
| `core/extension` | Extension loading & install | ✅ |
| `core/tachiyomi-compat` | Tachiyomi compatibility | ✅ |
| `core/discord` | Discord Rich Presence | ✅ |
| `feature/library` | Library screen | ✅ |
| `feature/browse` | Browse & search | ✅ |
| `feature/details` | Manga details | ✅ |
| `feature/reader` | Ultimate reader | ✅ |
| `feature/history` | Reading history | ✅ |
| `feature/settings` | Settings & backup | ✅ |
| `feature/statistics` | Reading stats | ✅ |
| `feature/updates` | Updates & downloads | ✅ |
| `feature/tracking` | Tracker integration | ✅ |
| `feature/migration` | Source migration | ✅ |

## 🎨 The Reader

### Reading Modes
- **Single Page** — Classic manga reading
- **Dual Page** — Spread view for tablets
- **Webtoon** — Vertical scrolling
- **Smart Panels** — Navigate by detected panels

### Navigation
- **Gallery View** — Thumbnail strip
- **3x3 Tap Zones** — Fully configurable
- **Pinch Zoom** — Smooth scaling
- **Brightness Control** — In-reader overlay

## 🔌 Extensions

Access the entire Tachiyomi ecosystem:

| Repository | Extensions |
|------------|------------|
| Keiyoushi | 1000+ |
| Komikku | 1000+ |

**Features:**
- Browse extensions by language
- Install/update/uninstall
- Repository management
- NSFW filtering
- Update all button

## 🛠️ Tech Stack

- **Language**: Kotlin 2.3
- **UI**: Jetpack Compose + Material 3
- **Architecture**: Clean Architecture + MVI
- **DI**: Hilt
- **Database**: Room (v6 with migrations)
- **Preferences**: DataStore
- **Network**: Retrofit + OkHttp
- **Images**: Coil 3
- **Background Work**: WorkManager
- **Paging**: Paging 3

## 🚀 Getting Started

```bash
# Clone
git clone https://github.com/Heartless-Veteran/otaku-reader.git
cd otaku-reader

# Build
./gradlew assembleDebug

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📜 License

Apache 2.0 © 2026 Otaku Reader Contributors

---

<p align="center">
  <sub>Built with ❤️‍🔥 by manga lovers, for manga lovers</sub>
</p>
