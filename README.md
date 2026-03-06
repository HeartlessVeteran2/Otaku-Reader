# Otaku Reader

A modular Android reader app scaffold built to host the 2026 Komikku improvements. This repository currently contains the base project layout, convention plugins, and CI scaffolding so new features can be integrated incrementally.

## Project structure
- `app/` – Android application entry point, Hilt setup, and Compose host
- `core/` – Shared Android components
  - `common/` – cross-cutting utilities
  - `network/` – networking and serialization primitives
  - `database/` – persistence abstractions
  - `preferences/` – user preferences and configuration
  - `ui/` – Compose theme and reusable UI pieces
  - `navigation/` – route definitions and navigation helpers
- `domain/` – business models and interfaces
- `data/` – repositories and data orchestration
- `source-api/` – extension surface for content sources
- `feature/` – user-facing features (library, reader, browse, updates, history, settings)
- `build-logic/` – shared Gradle convention plugins

## Getting started
Ensure Java 17 is available, then build the project:

```bash
./gradlew assembleDebug
```

Run checks:

```bash
./gradlew test
```

## Contributing
Additional features and the prepared base code will be layered on top of this scaffold. Keep modules lean, rely on the convention plugins, and prefer Compose-first implementations.
