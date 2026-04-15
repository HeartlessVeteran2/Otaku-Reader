<div align="center">
  <img src="./.github/logo.jpg" alt="Otaku Reader" width="200"/>

  <p><em>🌸 The ultimate manga reader. Better than Perfect Viewer.</em></p>

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
  [![License](https://img.shields.io/badge/License-Apache%202.0-0877d2?style=flat)](LICENSE)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>

---

**🚀 Production-Ready** — Feature-complete · Security-audited · Enterprise-grade architecture

> **Privacy First:** All data stays on your device. No servers, no tracking, no cloud required.

---

## 📥 Download

| Build | Description | Download |
|-------|-------------|----------|
| **Full** | All features including AI | [Latest Release](https://github.com/HeartlessVeteran2/Otaku-Reader/releases/latest) |
| **FOSS** | Open-source only, no AI SDK | [Latest Release](https://github.com/HeartlessVeteran2/Otaku-Reader/releases/latest) |

**Minimum Requirements:** Android 8.0 (API 26) · ~30MB storage

---

## 🔐 Privacy & Security

**Otaku Reader is privacy-first by design:**

- ✅ **No data collection** — Everything stays on your device
- ✅ **No accounts required** — Use without registration
- ✅ **No analytics** — We don't track your reading habits
- ✅ **Encrypted preferences** — Secure API key storage (AI features)
- ✅ **HTTPS-only extensions** — Security-enforced source downloads
- ✅ **Sandboxed extensions** — Isolated classloading for untrusted sources

**Data stored locally:**
- Your library (manga, chapters, reading progress)
- Downloaded chapters (offline reading)
- App preferences (themes, settings)
- Extension sources (your choice)

**Optional features that use internet:**
- Manga source browsing (downloads manga info/covers)
- Tracker sync (MAL, AniList, etc. — only if you enable)
- AI features (Gemini — only if you add API key)

---

**🚀 Feature-complete Beta** — Core functionality ready for daily use · Security-audited

## ✨ Features

- 📚 **Library Management** — Grid view, categories, sorting, filtering, NSFW toggle, unread badges
- 🔍 **Browse & Discovery** — 2000+ sources, global search, extension catalog
- 📖 **Ultimate Reader** — 4 reading modes, color filters, gallery view, tap zones, zoom
- ⬇️ **Downloads & Offline** — Background downloads, queue management, CBZ export
- 📊 **Tracking** — MAL, AniList, Kitsu, MangaUpdates, Shikimori
- 🔌 **Extension System** — Full Tachiyomi ecosystem (Keiyoushi, Komikku repos)
- 🔔 **Smart Notifications** — New chapter alerts with covers, grouped by manga
- ☁️ **Cloud Sync** — Cross-device library sync (Google Drive, Wi-Fi optional)
- 🤖 **AI Features** — Gemini-powered recommendations, auto-categorization, chapter summaries (gated toggle)

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

- ✅ **Cloud Sync** — Google Drive integration for library sync
- ✅ **OPDS Support** — Komga/Kavita catalog browsing
- ✅ **AI Features** — Gemini recommendations + auto-categorization (gated; requires API key)
- ⏳ **SFX Translation** — Sound effect detection/translation (AI-powered, in progress)
- 🔮 **Widgets** — Home screen continue reading
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
| Database | Room (v11 with migrations) |
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
| `core/database` | Room database (v11) |
| `core/network` | Retrofit + OkHttp |
| `core/preferences` | DataStore preferences |
| `core/ui` | Shared Compose components |
| `core/navigation` | Navigation routing |
| `core/extension` | Extension loading & install |
| `core/tachiyomi-compat` | Tachiyomi compatibility |
| `core/ai` | Gemini client, AI feature gate, secure key storage |
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
<summary>🚀 Building from Source</summary>

### Debug Build (Development)
```bash
# Clone
git clone https://github.com/HeartlessVeteran2/Otaku-Reader.git
cd Otaku-Reader

# Build debug APK
./gradlew assembleDebug

# Install to device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release Build (Distribution)

**1. Generate Release Keystore (One-time setup)**
```bash
keytool -genkey -v -keystore otaku-reader-release.keystore \
        -alias otaku-reader -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Otaku Reader, O=YourName, C=US"
```

**⚠️ CRITICAL:** Back up this keystore file securely. If you lose it, you cannot update your app.

**2. Configure Signing**
```bash
# Copy template
cp keystore.properties.template keystore.properties

# Edit with your actual values
nano keystore.properties
```

**3. Build Release APK**
```bash
./gradlew assembleRelease

# Output: app/build/outputs/apk/full/release/app-full-release.apk
```

</details>

<details>
<summary>📦 Distribution Setup</summary>

### Manual Distribution (Sideloading)
For investor demos or beta testing:

1. Build release APK (see above)
2. Upload to GitHub Releases or share directly
3. Users enable "Install from unknown sources" in Android settings
4. Install APK

### CI/CD (GitHub Actions)
The project includes GitHub Actions workflows that:
- Build on every push to `main`
- Run tests and detekt
- Upload APK artifacts
- Create releases automatically

**Setting up release signing in CI:**
1. Go to GitHub → Settings → Secrets and variables → Actions
2. Add secrets:
   - `KEYSTORE_BASE64` — Base64-encoded keystore
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
3. CI will use these to sign release builds

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

