<div align="center">
  <img src="./.github/banner.svg" alt="Otaku Reader" width="100%"/>

  <h1>Otaku Reader</h1>

  <p>🌸 The ultimate manga reader. Blazing-fast, beautiful, and packed with 2000+ sources.</p>

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?style=flat-square&logo=kotlin&logoColor=white&labelColor=27303D)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-26+-3DDC84?style=flat-square&logo=android&logoColor=white&labelColor=27303D)](https://developer.android.com/)
  [![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square&labelColor=27303D)](LICENSE)
  [![CI](https://github.com/Heartless-Veteran/Otaku-Reader/actions/workflows/ci.yml/badge.svg)](https://github.com/Heartless-Veteran/Otaku-Reader/actions/workflows/ci.yml)

  <sub><i>Requires Android 8.0 (API 26) or higher.</i></sub>
</div>

---

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

---

## ✨ Features

### 📚 Library Management
- **Grid View** — Customizable columns (2–5) with unread badges on covers
- **Categories** — Drag-and-drop organization with multi-select batch operations
- **Sorting & Filtering** — Sort by name/last read/date/unread; filter by downloaded/unread/tracking
- **NSFW Toggle** — Global and per-source content filter

### 🔍 Browse & Discovery
- **2000+ Sources** — Access the full Tachiyomi extension ecosystem
- **Global Search** — Search across all installed sources at once
- **Extension Catalog** — Browse, install, and update extensions with repository management

### 📖 Ultimate Reader
- **4 Reading Modes** — `Single Page`, `Dual Page`, `Webtoon`, `Smart Panels`
- **Color Filters** — `Sepia`, `Grayscale`, `Invert`, `Custom Tint`
- **Navigation** — 3×3 configurable tap zones, volume-key page turn, pinch zoom
- **Gallery View** — Thumbnail strip for quick chapter navigation
- **Incognito Mode** — Private reading without history tracking

### ⬇️ Downloads & Offline
- **Background Downloads** — Queue with pause/resume support
- **CBZ Export** — Archive chapters in standard CBZ format
- **Auto-Download** — Automatically grab new chapters (Wi-Fi only option)

### 📊 Tracking
- **MyAnimeList** · **AniList** · **Kitsu** · **MangaUpdates** · **Shikimori**

### 🔔 Smart Notifications
- **New Chapter Alerts** — Rich notifications with manga covers, grouped per series
- **Tappable** — Tap notification to jump directly into the reader

### ☁️ Data & Sync
- **Scheduled Backups** — Automatic backups via WorkManager
- **Manual Backup/Restore** — JSON backup with manga, categories, and preferences
- **Cloud Sync** — Cross-device library sync *(🚧 in progress)*

---

## 🔌 Extensions

Compatible with the entire **Tachiyomi extension ecosystem** — no extra setup required.

| Repository | Sources |
|------------|---------|
| [Keiyoushi](https://github.com/keiyoushi/extensions) | 1000+ |
| [Komikku](https://github.com/komikku-app/komikku-extensions) | 1000+ |

- Browse extensions by language
- Install, update, and uninstall from within the app
- Manage multiple repositories
- NSFW content filtering per extension

---

## 🗺️ Roadmap

**🚧 In Progress**
- **Cloud Sync** — Google Drive integration for library sync
- **OPDS Support** — Komga/Kavita catalog browsing

**🔮 Future**
- **AI Features** — Smart recommendations and auto-categorization
- **Widget Improvements** — Enhanced data hookup & customization for home screen widgets
- **Panel-by-Panel** — Advanced panel navigation mode

---

<details>
<summary>📊 Project Status</summary>

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

</details>

<details>
<summary>🏗️ Architecture</summary>

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

| Module | Purpose |
|--------|---------|
| `app` | Main application entry |
| `source-api` | Extension API contracts |
| `domain` | Use cases and domain models |
| `data` | Repository, downloads, backup, sync |
| `core/common` | Shared utilities |
| `core/database` | Room database (v8 with migrations) |
| `core/network` | Retrofit + OkHttp |
| `core/preferences` | DataStore preferences |
| `core/ui` | Shared Compose components |
| `core/navigation` | Navigation routing |
| `core/extension` | Extension loading & install |
| `core/tachiyomi-compat` | Tachiyomi compatibility |
| `core/discord` | Discord Rich Presence |
| `feature/library` | Library screen |
| `feature/browse` | Browse & search |
| `feature/details` | Manga details |
| `feature/reader` | Ultimate reader |
| `feature/history` | Reading history |
| `feature/settings` | Settings & backup |
| `feature/statistics` | Reading stats |
| `feature/updates` | Updates & downloads |
| `feature/tracking` | Tracker integration |
| `feature/migration` | Source migration |

</details>

<details>
<summary>🛠️ Tech Stack</summary>

| Category | Technology |
|----------|-----------|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVI |
| DI | Hilt (Dagger) |
| Database | Room (v8 with migrations) |
| Preferences | DataStore |
| Network | Retrofit + OkHttp |
| Images | Coil 3 |
| Background Work | WorkManager |
| Paging | Paging 3 |

</details>

<details>
<summary>🎨 Reader Details</summary>

### Reading Modes
- **Single Page** — Classic manga reading with tap-to-navigate
- **Dual Page** — Spread view for tablets and wide screens
- **Webtoon** — Vertical continuous scrolling for manhwa/manhua
- **Smart Panels** — Navigate by automatically detected panels

### Navigation Controls
- **Gallery View** — Thumbnail strip for at-a-glance chapter navigation
- **3×3 Tap Zones** — Each zone fully configurable (next, previous, menu, etc.)
- **Pinch Zoom** — Smooth scaling with double-tap to reset
- **Volume Keys** — Hardware key page turning
- **Brightness Control** — In-reader brightness slider overlay

</details>

---

<div align="center">

### Contributing

Contributions, issues, and feature requests are welcome!  
Feel free to open an [issue](https://github.com/Heartless-Veteran/Otaku-Reader/issues) or submit a pull request.

**License:** [Apache 2.0](LICENSE) © 2026 Otaku Reader Contributors

<sub>Built with ❤️‍🔥 by manga lovers, for manga lovers</sub>

</div>

