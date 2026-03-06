# Komikku 2026

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="120" height="120" alt="Komikku Logo">
</p>

<p align="center">
  <b>A modern Android manga reader app built with Jetpack Compose</b>
</p>

<p align="center">
  <a href="https://github.com/yourusername/komikku-2026/releases">
    <img src="https://img.shields.io/github/v/release/yourusername/komikku-2026?include_prereleases" alt="Release">
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-2.0-blue.svg?logo=kotlin" alt="Kotlin">
  </a>
  <a href="https://developer.android.com/jetpack/compose">
    <img src="https://img.shields.io/badge/Jetpack%20Compose-2024.06.00-green.svg" alt="Compose">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-orange.svg" alt="License">
  </a>
</p>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Build Configuration](#build-configuration)
- [Contributing](#contributing)
- [License](#license)

## Overview

Komikku 2026 is a next-generation manga reader for Android, designed with a focus on performance, extensibility, and user experience. Built entirely with Jetpack Compose and following modern Android development best practices, it offers a smooth, intuitive reading experience with powerful features for manga enthusiasts.

### Key Highlights

- **Modern UI/UX**: Material Design 3 with dynamic theming
- **Extensible Sources**: Plugin-based system for manga providers
- **Offline First**: Download chapters for offline reading
- **Smart Recommendations**: AI-powered manga suggestions
- **Cross-Device Sync**: Cloud backup and synchronization
- **Performance Optimized**: Baseline profiles and R8 optimization

## Features

### Core Features

| Feature | Description | Status |
|---------|-------------|--------|
| **Library Management** | Organize manga with custom categories | ✅ Complete |
| **Multiple Sources** | Browse from various manga providers | ✅ Complete |
| **Offline Reading** | Download chapters for offline access | ✅ Complete |
| **Smart Updates** | Automatic chapter updates with notifications | ✅ Complete |
| **Reader** | Smooth reading with multiple viewing modes | ✅ Complete |
| **Search** | Global and per-source search functionality | ✅ Complete |
| **History** | Track reading progress across devices | ✅ Complete |
| **Bookmarks** | Save and organize favorite chapters | ✅ Complete |

### 2026 Enhancements

| Feature | Description | Status |
|---------|-------------|--------|
| **AI Recommendations** | Smart manga suggestions based on reading history | ✅ Complete |
| **Reading Stats** | Detailed analytics and reading insights | ✅ Complete |
| **Cross-Device Sync** | Cloud backup with automatic sync | ✅ Complete |
| **Material You** | Dynamic theming with Material You | ✅ Complete |
| **Predictive Back** | Android 15 predictive back gestures | ✅ Complete |
| **Per-App Language** | Android 13+ language preferences | ✅ Complete |
| **Edge-to-Edge** | Immersive full-screen experience | ✅ Complete |
| **Baseline Profiles** | Optimized app startup performance | ✅ Complete |

### Reader Features

- **Viewing Modes**: Left-to-Right, Right-to-Left, Vertical (Webtoon)
- **Reading Directions**: Standard manga and webtoon formats
- **Zoom & Pan**: Smooth pinch-to-zoom and pan gestures
- **Brightness Control**: In-app brightness adjustment
- **Screen Rotation**: Auto-rotate and lock options
- **Page Layout**: Single page, double page spread
- **Tapping Zones**: Customizable tap zones for navigation
- **Keep Screen On**: Prevent screen timeout while reading

## Screenshots

<p align="center">
  <img src="docs/screenshots/library.png" width="200" alt="Library">
  <img src="docs/screenshots/browse.png" width="200" alt="Browse">
  <img src="docs/screenshots/reader.png" width="200" alt="Reader">
  <img src="docs/screenshots/settings.png" width="200" alt="Settings">
</p>

## Architecture

Komikku 2026 follows **Clean Architecture** principles with a multi-module structure:

```
┌─────────────────────────────────────────────────────────────┐
│                        Presentation                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ Library  │ │  Browse  │ │  Reader  │ │ Settings │       │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘       │
└───────┼────────────┼────────────┼────────────┼─────────────┘
        │            │            │            │
        └────────────┴─────┬──────┴────────────┘
                           │
┌──────────────────────────┼──────────────────────────────────┐
│                          │                                   │
│  ┌───────────────────────┴───────────────────────────────┐  │
│  │                      Domain Layer                       │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────────────┐  │  │
│  │  │  Use Cases │ │  Models    │ │   Repositories     │  │  │
│  │  └────────────┘ └────────────┘ └────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                      Data Layer                         │  │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────────────┐  │  │
│  │  │  Database  │ │    API     │ │   DataStore        │  │  │
│  │  └────────────┘ └────────────┘ └────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

For detailed architecture documentation, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Tech Stack

### Core Technologies

| Category | Technology | Version |
|----------|------------|---------|
| **Language** | Kotlin | 2.0 |
| **UI Framework** | Jetpack Compose | BOM 2024.06.00 |
| **Architecture** | MVVM + MVI | - |
| **DI Framework** | Hilt | 2.51.1 |
| **Async** | Kotlin Coroutines & Flow | 1.8.0 |

### Data & Storage

| Category | Technology | Version |
|----------|------------|---------|
| **Database** | Room | 2.6.1 |
| **Preferences** | DataStore | 1.1.1 |
| **Serialization** | Kotlinx Serialization | 1.6.3 |

### Networking & Images

| Category | Technology | Version |
|----------|------------|---------|
| **HTTP Client** | OkHttp | 4.12.0 |
| **REST Client** | Retrofit | 2.11.0 |
| **Image Loading** | Coil | 3.0.0-alpha07 |

### Navigation & UI

| Category | Technology | Version |
|----------|------------|---------|
| **Navigation** | Navigation Compose | 2.8.0-beta05 |
| **Paging** | Paging 3 | 3.3.0 |
| **Material Design** | Material 3 | 1.3.0-beta04 |
| **Icons** | Material Icons Extended | 1.6.8 |

### Background Work

| Category | Technology | Version |
|----------|------------|---------|
| **Work Manager** | WorkManager | 2.9.0 |
| **Notifications** | NotificationCompat | 1.0.0 |

### Testing

| Category | Technology | Version |
|----------|------------|---------|
| **Unit Tests** | JUnit 5 | 5.10.2 |
| **UI Tests** | Compose UI Test | BOM 2024.06.00 |
| **Mocking** | MockK | 1.13.11 |
| **Assertions** | AssertK | 0.28.1 |

## Project Structure

```
komikku-2026/
├── app/                          # Main application module
│   ├── src/main/
│   │   ├── java/app/komikku/     # Application code
│   │   └── res/                  # Android resources
│   └── build.gradle.kts          # App module build config
│
├── build-logic/                  # Gradle convention plugins
│   └── convention/
│       └── src/main/kotlin/      # Convention plugin implementations
│
├── core/                         # Core modules
│   ├── common/                   # Shared utilities and extensions
│   ├── database/                 # Room database entities and DAOs
│   ├── navigation/               # Navigation utilities
│   ├── notifications/            # Notification handling
│   ├── preferences/              # DataStore preferences
│   ├── sync/                     # Synchronization logic
│   └── ui/                       # Shared UI components and theme
│
├── data/                         # Data layer implementations
│   └── src/main/
│       └── java/app/komikku/data/
│           ├── repository/       # Repository implementations
│           ├── source/           # Source data handling
│           └── sync/             # Sync implementations
│
├── domain/                       # Domain layer (pure Kotlin)
│   └── src/main/
│       └── java/app/komikku/domain/
│           ├── model/            # Domain models
│           ├── repository/       # Repository interfaces
│           ├── usecase/          # Use cases
│           └── enums/            # Domain enums
│
├── feature/                      # Feature modules
│   ├── browse/                   # Browse feature
│   ├── library/                  # Library feature
│   ├── reader/                   # Reader feature
│   ├── search/                   # Search feature
│   ├── settings/                 # Settings feature
│   ├── stats/                    # Statistics feature
│   └── updates/                  # Updates feature
│
└── source-api/                   # Extension API for manga sources
    └── src/main/
        └── java/app/komikku/source/
            ├── api/              # Source API interfaces
            ├── model/            # Source models
            └── parser/           # HTML parsers
```

## Getting Started

### Prerequisites

- **Android Studio**: Koala (2024.1.1) or newer
- **JDK**: 21 or newer
- **Android SDK**: API 35 with build tools 35.0.0
- **Gradle**: 8.7 (via wrapper)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/komikku-2026.git
   cd komikku-2026
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Choose the `komikku-2026` directory

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Install debug build**
   ```bash
   ./gradlew installDebug
   ```

### Running Tests

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run all tests
./gradlew check

# Generate test coverage report
./gradlew koverHtmlReport
```

### Generating Baseline Profile

```bash
# Generate baseline profile
./gradlew :app:generateBaselineProfile

# Install with baseline profile
./gradlew :app:installRelease
```

## Build Configuration

### SDK Versions

| Configuration | Value |
|---------------|-------|
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **Compile SDK** | 35 |
| **Java/Kotlin Target** | 21 |

### Build Types

| Type | Description | Configuration |
|------|-------------|---------------|
| **Debug** | Development build | Debug symbols, no minification |
| **Release** | Production build | R8 full mode, resource shrinking |
| **Benchmark** | Performance testing | Baseline profile, release config |

### Optimization Features

- **R8 Full Mode**: Enabled for maximum code shrinking
- **Resource Shrinking**: Removes unused resources
- **Baseline Profiles**: Improves app startup and runtime performance
- **Compose Compiler Metrics**: Tracks recompositions for optimization
- **Gradle Build Cache**: Speeds up incremental builds

## Convention Plugins

The project uses Gradle convention plugins for consistent build configuration:

| Plugin | Purpose |
|--------|---------|
| `komikku.android.application` | Android application configuration |
| `komikku.android.library` | Android library configuration |
| `komikku.android.feature` | Feature module configuration |
| `komikku.hilt` | Hilt DI configuration |
| `komikku.room` | Room database configuration |

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use [ktlint](https://ktlint.github.io/) for code formatting
- Write unit tests for new features
- Update documentation as needed

## Documentation

- [Architecture Overview](ARCHITECTURE.md) - Detailed architecture documentation
- [Feature Documentation](FEATURES.md) - Complete feature list and usage
- [Extension API](API.md) - Guide for extension developers
- [Changelog](CHANGELOG.md) - Version history and changes

## License

```
Copyright 2024 Komikku Contributors

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

## Acknowledgments

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI toolkit
- [Material Design 3](https://m3.material.io/) - Design system
- [Kotlin](https://kotlinlang.org/) - Programming language
- [Android Open Source Project](https://source.android.com/) - Platform

---

<p align="center">
  Made with ❤️ by the Komikku Team
</p>
