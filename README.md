<div align="center">
  <img src="./.github/logo.jpg" alt="Otaku Reader" width="200"/>

  <p><em>A modern manga reader for Android</em></p>

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
  [![License](https://img.shields.io/badge/License-Apache%202.0-0877d2?style=flat)](LICENSE)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>

---

> **Privacy First:** All data stays on your device. No servers, no tracking, no cloud required.

---

## 📥 Download

| Build | Description | Download |
|-------|-------------|----------|
| **Full** | All features including AI | [Latest Release](https://github.com/HeartlessVeteran2/Otaku-Reader/releases/latest) |
| **FOSS** | Open-source only, no proprietary SDKs | [Latest Release](https://github.com/HeartlessVeteran2/Otaku-Reader/releases/latest) |

**Minimum Requirements:** Android 8.0 (API 26) · ~30MB storage

---

## ✨ Features

- 📚 **Library Management** — Grid view, categories, sorting, filtering, NSFW toggle, unread badges
- 🔍 **Browse & Discovery** — 2000+ sources, global search, extension catalog
- 📖 **Reader** — 4 reading modes, color filters, gallery view, tap zones, zoom
- ⬇️ **Downloads & Offline** — Background downloads, queue management, CBZ export
- 📊 **Tracking** — MAL, AniList, Kitsu, MangaUpdates, Shikimori
- 🔌 **Extension System** — Full Tachiyomi-compatible ecosystem (Keiyoushi, Komikku repos)
- 🔔 **Notifications** — New chapter alerts with covers, grouped by manga
- ☁️ **Cloud Sync** — Cross-device library sync (Google Drive, Wi-Fi optional)
- 🤖 **AI Features** — Gemini-powered recommendations, auto-categorization, chapter summaries (optional; requires API key)

### 📖 Reader

#### Reading Modes
- **Single Page** — Classic manga reading
- **Dual Page** — Spread view for tablets
- **Webtoon** — Vertical scrolling
- **Smart Panels** — Navigate by detected panels

#### Navigation & Controls
- **Gallery View** — Thumbnail strip for quick navigation
- **3×3 Tap Zones** — Fully configurable
- **Pinch Zoom** — Smooth scaling
- **Brightness Control** — In-reader overlay
- **Incognito Mode** — Private reading session
- **Volume Key Navigation** — Turn pages with hardware keys

#### Color Filters
Sepia · Grayscale · Invert · Custom tint

### 🔌 Extension System

Access the entire Tachiyomi-compatible ecosystem:

| Repository | Extensions |
|------------|------------|
| Keiyoushi  | 1000+      |
| Komikku    | 1000+      |

Browse extensions by language, install/update/uninstall, manage repositories, and filter NSFW content.

---

## 🔐 Privacy & Security

- ✅ **No data collection** — Everything stays on your device
- ✅ **No accounts required** — Use without registration
- ✅ **No analytics** — Reading habits are never tracked
- ✅ **Encrypted preferences** — Secure API key storage for AI features
- ✅ **HTTPS-only extensions** — Security-enforced source downloads
- ✅ **Sandboxed extensions** — Isolated classloading for untrusted sources

**Data stored locally:** library, downloaded chapters, preferences, extension sources.

**Optional internet use:** manga source browsing · tracker sync (opt-in) · AI features (opt-in, requires Gemini API key)

---

## 📸 Screenshots

<div align="center">
<em>Screenshots coming soon</em>
</div>

---

## 🗺️ Roadmap

- ✅ Cloud Sync (Google Drive)
- ✅ OPDS Support (Komga/Kavita)
- ✅ AI Features (Gemini, opt-in)
- ⏳ SFX Translation (AI-powered, in progress)
- 🔮 Home screen widgets
- 🔮 Panel-by-panel navigation

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVI |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Network | Retrofit + OkHttp |
| Images | Coil 3 |
| Background Work | WorkManager |

<details>
<summary>Architecture Overview</summary>

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │  Library │ │  Browse  │ │  Reader  │ │ Settings │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘   │
└───────┼────────────┼────────────┼────────────┼─────────┘
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
| `app` | Application entry point |
| `source-api` | Extension API contracts |
| `domain` | Use cases and domain models |
| `data` | Repositories, downloads, backup, sync |
| `core/common` | Shared utilities |
| `core/database` | Room database |
| `core/network` | Retrofit + OkHttp |
| `core/preferences` | DataStore preferences |
| `core/ui` | Shared Compose components |
| `core/navigation` | Navigation routing |
| `core/extension` | Extension loading & install |
| `core/tachiyomi-compat` | Tachiyomi compatibility layer |
| `core/ai` | Gemini client, feature gate, secure key storage |
| `feature/library` | Library screen |
| `feature/browse` | Browse & search |
| `feature/details` | Manga details |
| `feature/reader` | Reader |
| `feature/history` | Reading history |
| `feature/settings` | Settings & backup |
| `feature/statistics` | Reading stats |
| `feature/updates` | Updates & downloads |
| `feature/tracking` | Tracker integration |
| `feature/migration` | Source migration |

</details>

---

## 🔨 Building from Source

```bash
git clone https://github.com/HeartlessVeteran2/Otaku-Reader.git
cd Otaku-Reader

# Debug build
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# Release build (requires signing config — see keystore.properties.template)
./gradlew assembleRelease
```

See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) for full release instructions.

---

<div align="center">

### Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you'd like to change.

---

<sub>Built with ❤️‍🔥 by manga lovers, for manga lovers</sub>

<br/>

Apache 2.0 © 2026 Otaku Reader Contributors

</div>

