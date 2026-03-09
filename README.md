# Otaku Reader

A modern Android manga reader built with Kotlin and Jetpack Compose.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-26+-green.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

## 📊 Project Status

| Component | Status | Notes |
|-----------|--------|-------|
| **Core Architecture** | ✅ Complete | Clean Architecture, MVI, Hilt DI |
| **Library Screen** | ✅ Complete | Grid, categories, favorites, unread badges |
| **Browse Screen** | ✅ Complete | Sources, filters, manga grid, global search |
| **Manga Details** | ✅ Complete | Chapter list, info, status, bookmarks |
| **Reader** | ✅ Complete | 4 modes, zoom, brightness, gallery, volume keys, incognito |
| **History Screen** | ✅ Complete | Reading history with timestamps and duration |
| **Settings Screen** | ✅ Complete | DataStore persistence, backup/restore, reader prefs |
| **Downloads** | ✅ Complete | Offline reading, queue management, progress tracking |
| **Updates Screen** | 🚧 Partial | Download queue UI complete; auto-update notification UI pending |
| **Extension System** | 🚧 Partial | Core loader ready, needs APK install UI |
| **Cloud Sync** | ❌ Not started | Firebase or P2P |
| **AI Features** | ❌ Not started | Recommendations, summaries |

**Current:** App compiles, navigates, reads manga from sources with full download and history support.  
**Next:** Extension APK installation UI, updates notification system, polish.

## 🎯 Vision

A modern, blazing-fast manga reader built from scratch with:
- **🔌 Extension System** - Access 2000+ sources via Tachiyomi extensions
- **📖 Ultimate Reader** - Perfect Viewer-level smoothness with modern UX
- **☁️ Cloud Sync** - Cross-device sync
- **🤖 AI Features** - Smart recommendations

## 🚀 Completed Features

- ✅ Modern Jetpack Compose UI with Material 3
- ✅ Clean Architecture (MVI + MVVM)
- ✅ Room database with migrations
- ✅ Navigation with bottom bar
- ✅ Extension system foundation (loader, API, Tachiyomi compatibility)
- ✅ Ultimate reader with 4 modes (Single Page, Dual Page, Webtoon, Smart Panels)
- ✅ Manga details with chapter list, bookmarks, and read progress
- ✅ Browse with source filtering and global search
- ✅ Reading history with timestamps and duration tracking
- ✅ Chapter download system with queue, pause/resume, and progress tracking
- ✅ Settings persistence via DataStore (reader, library, general preferences)
- ✅ Backup and restore (JSON export/import of library, chapters, history, preferences)
- ✅ Incognito mode (session-only, no history recorded)
- ✅ Background library update worker (WorkManager)

## 🚧 In Progress / TODO

- ⏳ Extension APK installation UI
- ⏳ Updates screen notification system (new chapter alerts)
- ⏳ New chapter update checking UI
- ⏳ Search functionality improvements

## 📋 Future Ideas

- 🔮 Cloud sync (Firebase or Syncthing)
- 🔮 AI recommendations
- 🔮 Reading time estimation
- 🔮 Auto-categorization
- 🔮 Panel-by-panel reader
- 🔮 SFX translation

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
| `data` | Repository implementations, downloads, backup | ✅ |
| `core/common` | Shared utilities and extensions | ✅ |
| `core/database` | Room database (v3) | ✅ |
| `core/network` | Retrofit + OkHttp networking | ✅ |
| `core/preferences` | DataStore preferences, IncognitoManager | ✅ |
| `core/ui` | Shared Compose UI components | ✅ |
| `core/navigation` | Navigation routing | ✅ |
| `core/extension` | Extension loading | 🚧 |
| `core/tachiyomi-compat` | Tachiyomi extension compatibility | ✅ |
| `feature/library` | Library screen | ✅ |
| `feature/browse` | Browse sources | ✅ |
| `feature/details` | Manga details | ✅ |
| `feature/reader` | Ultimate reader | ✅ |
| `feature/history` | History screen | ✅ |
| `feature/settings` | Settings screen | ✅ |
| `feature/updates` | Updates & downloads screen | 🚧 |

## 🎨 The Reader

### Reading Modes
- **Single Page** - Classic manga reading
- **Dual Page** - Spread view for tablets
- **Webtoon** - Vertical scrolling
- **Smart Panels** - Navigate by detected panels

### Navigation
- **Gallery View** - Thumbnail strip
- **3x3 Tap Zones** - Fully configurable
- **Pinch Zoom** - Perfect Viewer-level smoothness
- **Brightness Control** - In-reader overlay

## 🔌 Extensions

Access the entire Tachiyomi ecosystem:

| Repository | Extensions |
|------------|------------|
| Keiyoushi | 1000+ |
| Komikku | 1000+ |

**Status:** Core loader implemented. APK installation UI needed.

## 🛠️ Tech Stack

- **Language**: Kotlin 2.3
- **UI**: Jetpack Compose + Material 3
- **Architecture**: Clean Architecture + MVI
- **DI**: Hilt
- **Database**: Room (v3 with migrations)
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
  <sub>Built with ❤️ and 🍜 by manga lovers, for manga lovers</sub>
</p>
