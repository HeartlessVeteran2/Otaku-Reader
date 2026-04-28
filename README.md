# Otaku Reader

<div align="center">
  <img src="./.github/logo.jpg" alt="Otaku Reader" width="200"/>

  <p><em>A modern, manga-only Android reader — built to be better than every Tachiyomi fork.</em></p>

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
  [![License](https://img.shields.io/badge/License-Apache%202.0-0877d2?style=flat)](LICENSE)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>

---

> **Privacy First:** All data stays on your device. No accounts, no tracking, no cloud required.
> <br>**Local-first, sync-optional, never lock-in.**

---

## 📥 Download

| Build | Description | Download |
|-------|-------------|----------|
| **Full** | All features | [Latest Release](https://github.com/HeartlessVeteran2/Otaku-Reader/releases/latest) |
| **FOSS** | Open-source only, no proprietary SDKs | [Latest Release](https://github.com/HeartlessVeteran2/Otaku-Reader/releases/latest) |

**Minimum Requirements:** Android 8.0 (API 26) · target APK < 10 MB

---

## ✨ What Makes It Different

Every Tachiyomi fork is a maintenance burden with half-finished features. Otaku Reader is intentionally **manga-only**, **Compose-native**, and **built to actually work** on day one.

### Core Philosophy
- **One app, one job:** Read manga. Nothing else.
- **AI is an extension, not bloat:** AI-powered features (OCR translation, recommendations, auto-tagging) live in a [separate companion repo](https://github.com/HeartlessVeteran2/Otaku-Reader-AI) and ship as an optional add-on APK.
- **Zero accounts required:** No Google, no Firebase, no sign-up. Ever.
- **Restores in 60 seconds:** First launch → restore from Mihon/Komikku backup → reading. No empty library anxiety.

### Features

- 📚 **Library Management** — Grid/list views, categories, sorting, filtering, NSFW toggle, unread badges
- 🔍 **Browse & Discovery** — Source extensions, global search, catalog browsing
- 📖 **Reader** — Paged, webtoon, continuous scroll; smart page-stitching; per-manga zoom memory; volume-key navigation
- ⬇️ **Downloads & Offline** — Background queue, CBZ export, local source import (CBZ/CBR/folders)
- 📊 **Tracking** — MAL, AniList, Kitsu (opt-in, no account required for core features)
- 🔌 **Extension System** — Tachiyomi/Komikku-compatible sources (Keiyoushi, Komikku repos)
- 🔔 **Notifications** — New chapter alerts, grouped by manga
- ☁️ **Sync (Optional)** — Self-hosted server or local Wi-Fi sync; no cloud lock-in
- 🌐 **OPDS** — Client + server interop with Komga, Kavita, Calibre-Web

### 📖 Reader — The Part That Actually Matters

| Feature | Otaku Reader | Typical Fork |
|---------|-------------|--------------|
| Smooth webtoon scroll | ✅ Pre-rendered, no jank | ❌ Stutters on long chapters |
| Page-stitching | ✅ Smart chunk merge | ❌ Manual zoom required |
| Per-manga zoom memory | ✅ Remembered per title | ❌ Global only |
| Volume-key paging | ✅ Debounced, reliable | ⚠️ Spotty |
| Battery-aware brightness | ✅ Auto curve | ❌ Manual slider only |
| Predictive back (Android 14+) | ✅ Fullscreen gesture | ❌ System default |

**Reading Modes:** Paged · Webtoon · Continuous Scroll · Smart Panels

**Navigation:** Gallery thumbnails · 3×3 tap zones · Pinch zoom · Hardware key support

**Accessibility:** TalkBack-readable · Dyslexia-friendly font option · High-contrast theme · Color-blind safe palettes

---

## 🔐 Privacy & Security

- ✅ **No data collection** — Everything stays local
- ✅ **No accounts required** — Use without any registration
- ✅ **No analytics or tracking** — Reading habits are yours alone
- ✅ **Encrypted preferences** — Secure local storage for tracker API keys
- ✅ **HTTPS-only extensions** — Enforced secure source downloads
- ✅ **Sandboxed extensions** — Isolated classloading for untrusted sources

**Data stored locally:** library, downloaded chapters, preferences, extension sources, backup files.

**Optional internet use:** manga source browsing · tracker sync (opt-in) · OPDS server (opt-in)

---

## 📸 Screenshots

<div align="center">

| Library | Browse | Reader | Settings |
|---------|--------|--------|----------|
| <img src="docs/screenshots/library.png" width="180" alt="Library screen"/> | <img src="docs/screenshots/browse.png" width="180" alt="Browse screen"/> | <img src="docs/screenshots/reader.png" width="180" alt="Reader screen"/> | <img src="docs/screenshots/settings.png" width="180" alt="Settings screen"/> |

<em>Screenshots taken on Pixel 7 (Android 14). See <a href="docs/screenshots/">docs/screenshots/</a> for full-resolution images.</em>

</div>

---

## 🗺️ Roadmap

### Phase 0: Clean Slate (Now)
- [ ] Pin stable Kotlin / KSP / Compose / AGP versions
- [ ] Remove AI, cloud sync, and server modules from core repo → [Otaku-Reader-AI](https://github.com/HeartlessVeteran2/Otaku-Reader-AI)
- [ ] Flat single-product build (no `full`/`foss` flavors)
- [ ] Green CI: detekt, ktlint, unit tests, signed APK on every tag

### Phase 1: Core App Wiring
- [ ] Hilt DI audit — no cycles, all bindings present
- [ ] Single Compose navigation graph
- [ ] Material3 theme (light / dark / dynamic / per-manga palette)
- [ ] DataStore settings backbone
- [ ] Base MVI pattern for every screen

### Phase 2: Manga Core Loop
- [ ] Room database: Manga, Chapter, History, Category
- [ ] Source API (Komikku/Keiyoushi compatible)
- [ ] Extension system: install, verify, configure index
- [ ] Library + Browse screens (Compose-native)
- [ ] Manga details, download, bookmark
- [ ] Reader with all modes + accessibility
- [ ] Downloader (CBZ, notifications)
- [ ] Local source import (CBZ/CBR/folders)
- [ ] History, updates, search, settings

### Phase 3: Trackers, Backup, Polish
- [ ] Tracker integration (AniList/MAL/Kitsu)
- [ ] Backup/restore (human-readable JSON in ZIP)
- [ ] Source-to-source migration
- [ ] Update check via GitHub Releases
- [ ] OPDS client + server

### Phase 4: Quality Gates & Release
- [ ] 60%+ domain/data test coverage
- [ ] Critical Compose UI tests (library, reader)
- [ ] Branch protection enforced
- [ ] F-Droid metadata + reproducible builds
- [ ] Macrobenchmark module to prevent regressions

### Post-Phase 2 Differentiators (see [#711](https://github.com/HeartlessVeteran2/Otaku-Reader/issues/711))
- Curated default extension index (opt-out)
- One-tap Mihon/Komikku backup restore
- Adaptive layouts for foldables/tablets
- Smart download rules (auto-queue next chapters)
- Per-source rate limiting with visible queue
- Reading streaks + stats dashboard
- QR-code library sharing (local, no server)
- Optional ActivityPub federation for read-status

---

## 🤖 AI Companion (Optional)

AI-powered features — OCR translation, semantic recommendations, auto-tagging, cover upscaling — are being developed in a **separate repository** and will ship as an optional add-on APK.

> **Why separate?** The AI stack (Gemini SDK, ML Kit, Firebase) adds significant build complexity, binary size, and dependency fragility. Keeping it out of core means Otaku Reader stays small, stable, and always builds on CI.

- **Core repo (this):** manga reader, < 10 MB, zero proprietary SDKs
- **AI repo:** companion APK, optional install, full AI feature set

See [Otaku-Reader-AI](https://github.com/HeartlessVeteran2/Otaku-Reader-AI) for progress.

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose 100% — no XML layouts |
| Architecture | Clean Architecture + MVI |
| Dependency Injection | Hilt |
| Database | Room |
| Preferences | DataStore |
| Networking | OkHttp + Coil |
| Background Work | WorkManager |
| Build | Gradle 9.x + convention plugins + version catalogs |

---

## 🏗️ Architecture

```
app/                    — Application module (DI wiring, manifest)
├── core/
│   ├── common/         — Shared utilities, Result type
│   ├── ui/             — Compose design system, theme, components
│   ├── navigation/     — Type-safe navigation graph
│   ├── preferences/    — DataStore wrappers
│   ├── database/       — Room entities, DAOs, migrations
│   └── discord/        — Discord RPC (optional)
├── domain/             — Use cases, repository interfaces, models
├── data/               — Repository implementations, workers, network
├── source-api/         — Extension SDK (source interface + loader)
└── feature/
    ├── library/        — Library grid, categories, filters
    ├── browse/         — Sources, extensions, search
    ├── details/        — Manga info, chapters, download
    ├── reader/         — All reading modes + controls
    ├── history/        — Reading history
    ├── updates/        — New chapter feed
    ├── tracking/       — Tracker settings + sync
    ├── settings/       — App preferences
    ├── migration/      — Source-to-source migration
    ├── onboarding/     — First-launch setup wizard
    ├── about/          — Credits, licenses, updates
    ├── recommendations/— Discovery / related manga
    ├── statistics/     — Reading stats dashboard
    ├── feed/           — OPDS / external catalogs
    └── opds/           — OPDS server mode
```

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](docs/contributing/CONTRIBUTING.md) for guidelines.

### Quick Start

```bash
# Clone
git clone https://github.com/HeartlessVeteran2/Otaku-Reader.git
cd Otaku-Reader

# Build debug APK
./gradlew assembleDebug

# Run checks
./gradlew detekt
./gradlew testDebugUnitTest
```

See [docs/contributing/ci.md](docs/contributing/ci.md) for the full CI command reference.

---

## 🔗 See Also

- **[Otaku-Reader-AI](https://github.com/HeartlessVeteran2/Otaku-Reader-AI)** — The companion AI extension module (Gemini-powered summaries, OCR translation, SFX translation, reading insights, smart search, and recommendations). Designed to plug into this app without modifying the core codebase.

---

<div align="center">

```
Copyright 2025 Manny Carter

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 🙏 Acknowledgments

- [Komikku](https://github.com/komikku-app/komikku) — Architecture & feature baseline
- [Keiyoushi](https://github.com/keiyoushi) — Extension repository
- Tachiyomi community — Extension ecosystem foundation
