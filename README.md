# Otaku Reader

A modern Android manga reader built with Kotlin and Jetpack Compose.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-26+-green.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

## 🎯 Vision

A modern, blazing-fast manga reader built from scratch with:
- **🔌 Extension System** - Access 2000+ sources via Tachiyomi extensions
- **📖 Ultimate Reader** - Perfect Viewer-level smoothness with modern UX
- **☁️ Cloud Sync** - Firebase-powered cross-device sync
- **🤖 AI Features** - Smart recommendations (coming soon)

## 🚀 Features

### Current
- ✅ Modern Jetpack Compose UI with Material 3
- ✅ Clean Architecture (MVI + MVVM)
- ✅ Room + SQLDelight database
- ✅ Navigation with bottom bar
- ✅ Extension system core (APK loading)
- ✅ Tachiyomi compatibility layer
- ✅ Ultimate reader with 4 modes

### Coming
- 🔄 Cloud sync (Firebase)
- 🔍 Cross-source search
- ⬇️ Downloads for offline reading
- 🔔 New chapter notifications
- 🤖 AI recommendations

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

| Module | Purpose |
|--------|---------|
| `app` | Main application entry |
| `source-api` | Extension API contracts |
| `core/common` | Shared utilities |
| `core/database` | Room + SQLDelight |
| `core/extension` | Extension loading |
| `feature/library` | Library screen |
| `feature/browse` | Browse sources |
| `feature/reader` | Ultimate reader |
| `feature/settings` | App settings |

## 🎨 The Reader

### Reading Modes
- **Single Page** - Classic manga reading
- **Dual Page** - Spread view for tablets
- **Webtoon** - Vertical scrolling
- **Smart Panels** - Navigate by detected panels

### Navigation
- **Gallery View** - Thumbnail strip instead of slider rail
- **3x3 Tap Zones** - Fully configurable
- **Pinch Zoom** - Perfect Viewer-level smoothness
- **Brightness Control** - In-reader overlay

## 🔌 Extensions

Access the entire Tachiyomi ecosystem:

| Repository | Extensions |
|------------|------------|
| Keiyoushi | 1000+ |
| Komikku | 1000+ |
| Trackers | 4 (self-hosted) |

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3
- **Architecture**: Clean Architecture + MVI
- **DI**: Hilt
- **Database**: Room + SQLDelight
- **Network**: Retrofit + OkHttp
- **Images**: Coil 3
- **Cloud**: Firebase (planned)

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

## 📋 Roadmap

### Phase 1: MVP (Now)
- [x] Core app structure
- [x] Extension system
- [x] Ultimate reader
- [ ] Browse integration
- [ ] Manga details

### Phase 2: Polish
- [ ] Cloud sync
- [ ] Downloads
- [ ] Search
- [ ] Notifications

### Phase 3: AI
- [ ] Smart recommendations
- [ ] Reading time estimation
- [ ] Auto-categorization

## 🤝 Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md)

## 📜 License

Apache 2.0 © 2026 Otaku Reader Contributors

---

<p align="center">
  <sub>Built with ❤️ and 🍜 by manga lovers, for manga lovers</sub>
</p>
