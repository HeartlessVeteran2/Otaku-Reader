<div align="center">
  <img src="./media/logo/v1-oto-monogram.png" alt="Otaku Reader" width="200"/>

  <h1>Otaku Reader</h1>

  <p>🌸 The ultimate manga reader. Blazing-fast, beautiful, and packed with 2000+ sources.</p>

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?style=flat-square&logo=kotlin&logoColor=white&labelColor=27303D)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-26+-3DDC84?style=flat-square&logo=android&logoColor=white&labelColor=27303D)](https://developer.android.com/)
  [![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square&labelColor=27303D)](LICENSE)
  [![CI](https://github.com/Heartless-Veteran/Otaku-Reader/actions/workflows/ci.yml/badge.svg)](https://github.com/Heartless-Veteran/Otaku-Reader/actions/workflows/ci.yml)

  <sub><i>Requires Android 8.0 (API 26) or higher.</i></sub>
</div>

---

## 🚀 Quick Start

```bash
git clone https://github.com/Heartless-Veteran/otaku-reader.git
cd otaku-reader
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## ✨ Features

| Category | Features |
|----------|----------|
| **📚 Library** | Grid/column views, categories, drag-and-drop, batch operations, NSFW toggle |
| **🔍 Browse** | 2000+ sources, global search, extension catalog with repository management |
| **📖 Reader** | 4 reading modes (Single, Dual, Webtoon, Smart Panels), color filters, 3×3 tap zones, pinch zoom, gallery view |
| **🤖 Smart Panels** | Automatic panel detection, guided panel-by-panel navigation with smooth animations |
| **⚡ Smart Prefetch** | Adaptive page/chapter prefetching based on reading behavior, 4 prefetch strategies |
| **⬇️ Downloads** | Background queue, CBZ export, auto-download on Wi-Fi, pause/resume |
| **📊 Tracking** | MyAnimeList, AniList, Kitsu, MangaUpdates, Shikimori |
| **🔔 Notifications** | Rich new chapter alerts with covers, grouped by series |
| **☁️ Backup** | Scheduled backups, JSON export/import |
| **🔄 Cloud Sync** | Google Drive sync, conflict resolution, background periodic sync |
| **📡 OPDS** | Self-hosted catalog support (Komga, Kavita), add/browse/search OPDS servers |
| **🤖 AI Recommendations** | Gemini-powered manga recommendations based on reading history |
| **🎮 Discord RPC** | Rich Presence showing currently reading manga |
| **📈 Statistics** | Reading analytics, charts, reading streaks, and insights |
| **🔄 Migration** | Migrate manga between sources with progress and chapter mapping |

**See [Full Features Guide](docs/features/) for details.**

---

## 🔌 Extensions

Compatible with the entire **Tachiyomi extension ecosystem**:

| Repository | Sources |
|------------|---------|
| [Keiyoushi](https://github.com/keiyoushi/extensions) | 1000+ |
| [Komikku](https://github.com/komikku-app/komikku-extensions) | 1000+ |

---

## 📐 Architecture

Clean Architecture + MVI with modular feature structure.

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│     UI      │────▶│   Domain    │────▶│    Data     │
│  (Compose)  │     │  (Use Cases)│     │ (Room/Api)  │
└─────────────┘     └─────────────┘     └─────────────┘
```

**See [Architecture Docs](docs/architecture/) for the full picture.**

---

## 📚 Documentation

| Topic | Link |
|-------|------|
| **Features** | [docs/features/](docs/features/) |
| **Architecture** | [docs/architecture/](docs/architecture/) |
| **API Reference** | [docs/architecture/api.md](docs/architecture/api.md) |
| **Cloud Sync** | [docs/architecture/sync.md](docs/architecture/sync.md) |
| **Contributing** | [docs/contributing/contributing.md](docs/contributing/contributing.md) |
| **Changelog** | [CHANGELOG.md](CHANGELOG.md) |

---

## 🗺️ Roadmap

- **✅ Shipped:** Smart Panels (panel-by-panel navigation), Smart Prefetch, OPDS Catalog, AI Recommendations (Gemini), Discord Rich Presence, Cloud Sync (Google Drive), Statistics, Migration
- **🚧 In Progress:** Cloud Sync provider expansion (Dropbox, WebDAV), Smart Panels ML model (TensorFlow Lite)
- **🔮 Future:** Widget improvements, KMP expansion, Macrobenchmark baseline profiles

---

<div align="center">

**[Contributing](docs/contributing/contributing.md)** · **[Issues](https://github.com/Heartless-Veteran/Otaku-Reader/issues)** · **[License](LICENSE)**

<sub>Built with ❤️‍🔥 by manga lovers, for manga lovers</sub>

</div>
