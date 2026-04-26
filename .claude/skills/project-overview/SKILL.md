# Otaku Reader — Project Overview

## Identity
- **Name:** Otaku Reader
- **Platform:** Android (8.0+)
- **Language:** Kotlin 2.3
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Clean Architecture + MVI

## Module Structure

```
app/                    Main entry point
data/                   Repository implementations, downloads, backup, sync
domain/                 Use cases, domain models, repository interfaces
source-api/             Extension API contracts (what extensions implement)
core/                   Shared infrastructure
  ├── common/           Utilities, extensions
  ├── database/         Room (v11 with migrations)
  ├── network/          Retrofit + OkHttp
  ├── preferences/      DataStore
  ├── ui/               Compose components, theme
  ├── navigation/       Routing
  ├── extension/        Extension loader/installer
  ├── tachiyomi-compat/ Tachiyomi extension compatibility layer
  ├── ai/               Gemini client, AI feature gate
  └── discord/          Discord Rich Presence
feature/                UI + ViewModel per feature
  ├── library/
  ├── browse/
  ├── details/
  ├── reader/
  ├── history/
  ├── settings/
  ├── statistics/
  ├── updates/
  ├── tracking/
  └── migration/
```

## Key Conventions

### Architecture Pattern: MVI
Every feature follows Model-View-Intent:
- **State:** Immutable data class representing UI state
- **Event:** Sealed class representing user/system actions
- **Effect:** One-shot side effects (navigation, snackbars, etc.)
- **ViewModel:** Processes events → updates state → emits effects

### Dependency Injection
- Uses **Hilt** throughout
- Repository interfaces in `domain/` → implementations in `data/`
- ViewModels receive UseCases, not Repositories directly
- `@ApplicationScope` CoroutineScope for long-lived background work

### Navigation
- Compose Navigation with type-safe routes
- Deep linking support for manga pages

### Async Patterns
- Flow for streams of data
- `viewModelScope` for ViewModel-bound work
- `async/await` for parallel independent operations
- DataStore reads via `.first()` (but batch them — see `loadSettings()` pattern)

## Extension System
- Extensions are APKs that implement `source-api` interfaces
- Two extension repos supported: Keiyoushi (~1000), Komikku (~1000)
- Extensions are loaded dynamically via `ExtensionLoader`
- `TrustedSignatureStore` validates extension signing certificates
- Private extensions are auto-trusted; shared extensions require approval

## AI Features (Gated)
- Gemini-powered recommendations, auto-categorization, chapter summaries
- Requires user-provided API key (opt-in)
- All AI features behind `AIFeatureGate` — can be completely disabled

## Reader Engine
Four reader modes:
1. **Single Page** — Horizontal pager, classic manga
2. **Dual Page** — Spread view with landscape auto-detection
3. **Webtoon** — Vertical scroll with configurable gap
4. **Smart Panels** — ML Kit panel detection for navigation

Reader events are grouped by domain (Navigation, Zoom, Display, etc.) via sealed interfaces.

## Testing
- Unit tests for ViewModels and UseCases
- `DatabaseMigrationTest` validates all Room migrations
- UI tests via Compose Test framework

## When Working on This Project
- Always check if a pattern exists in another feature before inventing a new one
- Prefer `data class` states with `copy()` over mutable state
- Use `rememberSaveable` for UI state that survives config changes
- Coil 3 for images — always consider memory impact
- RGB_565 enabled for opaque pages (2x memory reduction)
- Never call `source.fetchPageList()` directly — always route through `SourceRepository`
