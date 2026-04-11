<div align="center">
  <img src="./.github/banner.svg" alt="Otaku Reader" width="100%"/>

  <p><em>🌸 The ultimate manga reader. Better than Perfect Viewer.</em></p>

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
  [![License](https://img.shields.io/badge/License-Apache%202.0-0877d2?style=flat)](LICENSE)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>

---

**🚀 Feature-complete Beta** — Core functionality ready for daily use

## ✨ Features

- 📚 **Library Management** — Grid view, categories, sorting, filtering, NSFW toggle, unread badges
- 🔍 **Browse & Discovery** — 2000+ sources, global search, extension catalog
- 📖 **Ultimate Reader** — 4 reading modes, color filters, gallery view, tap zones, zoom
- ⬇️ **Downloads & Offline** — Background downloads, queue management, CBZ export
- 📊 **Tracking** — MAL, AniList, Kitsu, MangaUpdates, Shikimori
- 🔌 **Extension System** — Full Tachiyomi ecosystem (Keiyoushi, Komikku repos)
- 🔔 **Smart Notifications** — New chapter alerts with covers, grouped by manga
- ☁️ **Cloud Sync** — Cross-device library sync (in progress)

<details>
<summary>📖 Reader Details</summary>

### Reading Modes
- **Single Page** — Classic manga reading
- **Dual Page** — Spread view for tablets
- **Webtoon** — Vertical scrolling
- **Smart Panels** — Navigate by detected panels

### Navigation & Controls
- **Gallery View** — Thumbnail strip for quick navigation
- **3x3 Tap Zones** — Fully configurable
- **Pinch Zoom** — Smooth scaling
- **Brightness Control** — In-reader overlay
- **Incognito Mode** — Private reading session
- **Volume Key Navigation** — Turn pages with hardware keys

### Color Filters
Sepia · Grayscale · Invert · Custom tint

</details>

<details>
<summary>🔌 Extension System</summary>

Access the entire Tachiyomi ecosystem:

| Repository | Extensions |
|------------|------------|
| Keiyoushi  | 1000+      |
| Komikku    | 1000+      |

Browse extensions by language, install/update/uninstall, manage repositories, and filter NSFW content.

</details>

## 📸 Screenshots

<div align="center">

<!-- Screenshots will be added here -->
<em>Screenshots coming soon</em>

</div>

## 🗺️ Roadmap

- ⏳ **Cloud Sync** — Google Drive integration for library sync
- ⏳ **OPDS Support** — Komga/Kavita catalog browsing
- 🔮 **AI Features** — Recommendations, auto-categorization
- 🔮 **Widgets** — Home screen continue reading
- 🔮 **SFX Translation** — Sound effect detection/translation
- 🔮 **Panel-by-Panel** — Advanced panel navigation

## 🛠️ Tech Stack & Architecture

<details>
<summary>Tech Stack</summary>

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVI |
| DI | Hilt |
| Database | Room (v8 with migrations) |
| Preferences | DataStore |
| Network | Retrofit + OkHttp |
| Images | Coil 3 |
| Background Work | WorkManager |
| Paging | Paging 3 |

</details>

<details>
<summary>Architecture Diagram</summary>

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

</details>

<details>
<summary>Module Structure</summary>

| Module | Purpose |
|--------|---------|
| `app` | Main application entry |
| `source-api` | Extension API contracts |
| `domain` | Use cases and domain models |
| `data` | Repository, downloads, backup, sync |
| `core/common` | Shared utilities |
| `core/database` | Room database (v8) |
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
<summary>🚀 Getting Started</summary>

```bash
# Clone
git clone https://github.com/Heartless-Veteran/otaku-reader.git
cd otaku-reader

# Build
./gradlew assembleDebug

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

</details>

---

<div align="center">

### Contributing

Pull requests are welcome! For major changes, please open an issue first.

---

<sub>Built with ❤️‍🔥 by manga lovers, for manga lovers</sub>

<br/>

Apache 2.0 © 2026 Otaku Reader Contributors

</div>

