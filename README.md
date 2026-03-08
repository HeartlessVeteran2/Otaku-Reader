# Otaku Reader

A modern Android manga reader built with Kotlin and Jetpack Compose.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-26+-green.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

## 📊 Project Status

| Component | Status | Notes |
|-----------|--------|-------|
| **Core Architecture** | ✅ Complete | Clean Architecture, MVI, Hilt DI |
| **Library Screen** | ✅ Complete | Grid, categories, favorites |
| **Browse Screen** | ✅ Complete | Sources, filters, manga grid |
| **Manga Details** | ✅ Complete | Chapter list, info, status |
| **Reader** | ✅ Complete | 4 modes, zoom, brightness, gallery |
| **Updates Screen** | ⚠️ Placeholder | UI exists, needs logic |
| **History Screen** | ⚠️ Placeholder | UI exists, needs logic |
| **Settings Screen** | ⚠️ Placeholder | UI exists, needs prefs |
| **Extension System** | 🚧 Partial | Core loader ready, needs APK install |
| **Downloads** | ❌ Not started | Offline reading |
| **Cloud Sync** | ❌ Not started | Firebase or P2P |
| **AI Features** | ❌ Not started | Recommendations, summaries |

**Current:** App compiles, navigates, reads manga from sources.  
**Next:** Extension APK installation, downloads, polish.

## 🎯 Vision

A modern, blazing-fast manga reader built from scratch with:
- **🔌 Extension System** - Access 2000+ sources via Tachiyomi extensions
- **📖 Ultimate Reader** - Perfect Viewer-level smoothness with modern UX
- **☁️ Cloud Sync** - Cross-device sync
- **🤖 AI Features** - Smart recommendations

## 🚀 Completed Features

- ✅ Modern Jetpack Compose UI with Material 3
- ✅ Clean Architecture (MVI + MVVM)
- ✅ Room + SQLDelight database
- ✅ Navigation with bottom bar
- ✅ Extension system foundation (loader, API)
- ✅ Tachiyomi compatibility layer
- ✅ Ultimate reader with 4 modes
- ✅ Manga details with chapter list
- ✅ Browse with source filtering

## 🚧 In Progress / TODO

- ⏳ Extension APK installation UI
- ⏳ Chapter downloads for offline
- ⏳ Settings persistence
- ⏳ Reading history tracking
- ⏳ New chapter update checking
- ⏳ Search functionality

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
│  ┌──────────┐ ┌────────┼──┐ ┌──────────┐ ┌──────────┐  │
│  │  Room    │ │SQLDelight│ │  APIs    │ │Extension │  │
│  │ Database │ │  Schema  │ │(Sources) │ │  Loader  │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 📦 Modules

| Module | Purpose | Status |
|--------|---------|--------|
| `app` | Main application entry | ✅ |
| `source-api` | Extension API contracts | ✅ |
| `core/common` | Shared utilities | ✅ |
| `core/database` | Room + SQLDelight | ✅ |
| `core/extension` | Extension loading | 🚧 |
| `feature/library` | Library screen | ✅ |
| `feature/browse` | Browse sources | ✅ |
| `feature/details` | Manga details | ✅ |
| `feature/reader` | Ultimate reader | ✅ |
| `feature/updates` | Updates screen | ⚠️ |
| `feature/history` | History screen | ⚠️ |
| `feature/settings` | Settings screen | ⚠️ |

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

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3
- **Architecture**: Clean Architecture + MVI
- **DI**: Hilt
- **Database**: Room + SQLDelight
- **Network**: Retrofit + OkHttp
- **Images**: Coil 3

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
